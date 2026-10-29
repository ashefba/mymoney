package com.mymoney.app.ui.bills

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymoney.app.AppContainer
import com.mymoney.app.core.BillSchedule
import com.mymoney.app.core.DailyClock
import com.mymoney.app.core.Dates
import com.mymoney.app.core.Money
import com.mymoney.app.data.db.Account
import com.mymoney.app.data.db.Bill
import com.mymoney.app.data.db.BillFrequency
import com.mymoney.app.data.db.Category
import com.mymoney.app.data.db.CategoryKind
import com.mymoney.app.data.db.PaymentMethod
import com.mymoney.app.data.db.Transaction
import com.mymoney.app.notifications.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class OccurrenceStatus { PAID, DUE, OVERDUE }

data class CalendarEntry(val bill: Bill?, val name: String, val amount: Long, val status: OccurrenceStatus)

data class BillOccurrence(val bill: Bill, val date: LocalDate)

data class BillsState(
    val loading: Boolean = true,
    val currency: String = "EUR",
    val bills: List<Bill> = emptyList(),
    val upcoming: List<BillOccurrence> = emptyList(),
    val overdue: List<Bill> = emptyList(),
    val monthlyTotal: Long = 0,
    val subscriptionsMonthly: Long = 0,
    val calendarMonth: YearMonth = YearMonth.now(),
    val calendar: Map<LocalDate, List<CalendarEntry>> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val lastPayments: Map<Long, Transaction> = emptyMap(),
)

private data class BillInputs(
    val bills: List<Bill>,
    val payments: List<Transaction>,
    val categories: List<Category>,
    val accounts: List<Account>,
)

class BillsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val month = MutableStateFlow(YearMonth.now())
    private var followsCurrentMonth = true

    private val inputs = combine(repo.bills, repo.billPayments, repo.categories, repo.accounts) { b, p, c, a -> BillInputs(b, p, c, a) }

    val state: StateFlow<BillsState> = combine(inputs, month, container.settings.settings, DailyClock.dates()) { i, ym, settings, today ->
        val active = i.bills.filter { it.active }
        val upcoming = active.flatMap { b ->
            BillSchedule.occurrences(b, today, today.plusDays(60), includeOverdue = false).map { BillOccurrence(b, it) }
        }.sortedBy { it.date }
        val overdue = active.filter { it.nextDueDate < today.toEpochDay() }

        val start = ym.atDay(1)
        val end = ym.atEndOfMonth()
        val cal = HashMap<LocalDate, MutableList<CalendarEntry>>()
        val billMap = i.bills.associateBy { it.id }
        i.payments.filter { it.date in start.toEpochDay()..end.toEpochDay() }.forEach { t ->
            val b = t.billId?.let { billMap[it] }
            cal.getOrPut(Dates.of(t.date)) { mutableListOf() } +=
                CalendarEntry(b, b?.name ?: t.description, t.amountCents, OccurrenceStatus.PAID)
        }
        active.forEach { b ->
            BillSchedule.occurrences(b, start, end, includeOverdue = false).forEach { d ->
                val status = if (d.isBefore(today)) OccurrenceStatus.OVERDUE else OccurrenceStatus.DUE
                cal.getOrPut(d) { mutableListOf() } += CalendarEntry(b, b.name, b.amountCents, status)
            }
        }
        BillsState(
            loading = false,
            currency = settings.currency,
            bills = i.bills,
            upcoming = upcoming,
            overdue = overdue,
            monthlyTotal = active.sumOf { BillSchedule.monthlyEquivalent(it.amountCents, it.frequency) },
            subscriptionsMonthly = active.filter { it.isSubscription }.sumOf { BillSchedule.monthlyEquivalent(it.amountCents, it.frequency) },
            calendarMonth = ym,
            calendar = cal,
            categories = i.categories,
            accounts = i.accounts.filter { !it.archived },
            lastPayments = i.payments.groupBy { it.billId ?: 0L }.mapValues { (_, v) -> v.maxBy { it.date } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillsState())

    init {
        viewModelScope.launch {
            DailyClock.dates().collect { today ->
                if (followsCurrentMonth) month.value = YearMonth.from(today)
            }
        }
    }

    fun previousMonth() {
        followsCurrentMonth = false
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
        followsCurrentMonth = month.value == YearMonth.from(Dates.today())
    }

    fun markPaid(bill: Bill, amount: Long, date: Long, accountId: Long, method: PaymentMethod) =
        viewModelScope.launch { repo.markBillPaid(bill, amount, date, accountId, method) }

    fun skip(bill: Bill) = viewModelScope.launch { repo.skipBillOccurrence(bill) }

    fun checkReminders() = viewModelScope.launch { ReminderScheduler.checkAndNotify(container.context) }
}

class EditBillViewModel(container: AppContainer, private val billId: Long) : ViewModel() {
    private val repo = container.repository
    val categories: StateFlow<List<Category>> = repo.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val accounts: StateFlow<List<Account>> = repo.accounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var currency by mutableStateOf("EUR")
        private set
    var name by mutableStateOf("")
    var amountText by mutableStateOf("")
    var frequency by mutableStateOf(BillFrequency.MONTHLY)
    var nextDue by mutableStateOf(Dates.todayEpoch())
    var categoryId by mutableStateOf<Long?>(null)
    var accountId by mutableStateOf<Long?>(null)
    var paymentMethod by mutableStateOf(PaymentMethod.DIRECT_DEBIT)
    var reminderDays by mutableStateOf(2)
    var isSubscription by mutableStateOf(false)
    var active by mutableStateOf(true)
    var notes by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var done by mutableStateOf(false)
        private set
    private var original: Bill? = null
    val isNew get() = billId == 0L

    init {
        viewModelScope.launch {
            currency = container.settings.current().currency
            if (billId != 0L) {
                repo.getBill(billId)?.let { b ->
                    original = b
                    name = b.name
                    amountText = Money.toInput(b.amountCents)
                    frequency = b.frequency
                    nextDue = b.nextDueDate
                    categoryId = b.categoryId
                    accountId = b.accountId
                    paymentMethod = b.paymentMethod
                    reminderDays = b.reminderDaysBefore
                    isSubscription = b.isSubscription
                    active = b.active
                    notes = b.notes
                }
            } else {
                val cats = repo.allCategories()
                categoryId = cats.firstOrNull { it.kind == CategoryKind.EXPENSE && it.iconKey == "bolt" }?.id
                accountId = repo.allAccounts().firstOrNull { !it.archived }?.id
            }
        }
    }

    fun save() {
        val amount = Money.parseToCents(amountText)
        error = when {
            name.isBlank() -> "Give the bill a name, e.g. Rent or Netflix."
            amount == null || amount <= 0 -> "Enter the amount you usually pay."
            else -> null
        }
        if (error != null || amount == null) return
        viewModelScope.launch {
            val anchor = Dates.of(nextDue).dayOfMonth
            val base = original ?: Bill(
                name = name.trim(), amountCents = amount, frequency = frequency, nextDueDate = nextDue,
                anchorDay = anchor, categoryId = categoryId, accountId = accountId,
            )
            repo.saveBill(
                base.copy(
                    name = name.trim(), amountCents = amount, frequency = frequency, nextDueDate = nextDue,
                    anchorDay = if (original?.nextDueDate == nextDue) base.anchorDay else anchor,
                    categoryId = categoryId, accountId = accountId, paymentMethod = paymentMethod,
                    reminderDaysBefore = reminderDays, isSubscription = isSubscription, active = active, notes = notes.trim(),
                )
            )
            done = true
        }
    }

    fun delete() {
        val b = original ?: return
        viewModelScope.launch {
            repo.deleteBill(b)
            done = true
        }
    }
}
