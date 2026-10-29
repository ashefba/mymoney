package com.mymoney.app.ui.debts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymoney.app.AppContainer
import com.mymoney.app.core.DailyClock
import com.mymoney.app.core.Dates
import com.mymoney.app.core.DebtCalculator
import com.mymoney.app.core.DebtStatus
import com.mymoney.app.core.Money
import com.mymoney.app.core.Rates
import com.mymoney.app.data.db.Account
import com.mymoney.app.data.db.Debt
import com.mymoney.app.data.db.DebtEntry
import com.mymoney.app.data.db.DebtKind
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DebtItem(val debt: Debt, val status: DebtStatus)

data class DebtsState(
    val loading: Boolean = true,
    val currency: String = "EUR",
    val iOwe: List<DebtItem> = emptyList(),
    val owedToMe: List<DebtItem> = emptyList(),
    val closed: List<DebtItem> = emptyList(),
    val totalOwed: Long = 0,
    val totalOwedToMe: Long = 0,
    val monthlyRepayments: Long = 0,
)

class DebtsViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository
    val state: StateFlow<DebtsState> = combine(
        repo.debts, repo.debtEntries, container.settings.settings, DailyClock.dates(),
    ) { debts, entries, settings, today ->
        val items = debts.map { DebtItem(it, DebtCalculator.status(it, entries, today)) }
        val open = items.filter { !it.debt.closed }
        val iOwe = open.filter { it.debt.kind.iOwe }
        val owedToMe = open.filter { !it.debt.kind.iOwe }
        DebtsState(
            loading = false,
            currency = settings.currency,
            iOwe = iOwe,
            owedToMe = owedToMe,
            closed = items.filter { it.debt.closed },
            totalOwed = iOwe.sumOf { it.status.outstanding },
            totalOwedToMe = owedToMe.sumOf { it.status.outstanding },
            monthlyRepayments = iOwe.filter { it.status.outstanding > 0 }.sumOf { it.debt.monthlyPaymentCents },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtsState())
}

data class DebtDetailState(
    val loading: Boolean = true,
    val currency: String = "EUR",
    val debt: Debt? = null,
    val status: DebtStatus? = null,
    val entries: List<DebtEntry> = emptyList(),
    val accounts: List<Account> = emptyList(),
)

class DebtDetailViewModel(container: AppContainer, private val debtId: Long) : ViewModel() {
    private val repo = container.repository
    val state: StateFlow<DebtDetailState> = combine(
        repo.debts, repo.debtEntries, repo.accounts, container.settings.settings, DailyClock.dates(),
    ) { debts, entries, accounts, settings, today ->
        val debt = debts.firstOrNull { it.id == debtId }
        DebtDetailState(
            loading = false,
            currency = settings.currency,
            debt = debt,
            status = debt?.let { DebtCalculator.status(it, entries, today) },
            entries = entries.filter { it.debtId == debtId }
                .sortedWith(compareByDescending<DebtEntry> { it.date }.thenByDescending { it.id }),
            accounts = accounts.filter { !it.archived },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtDetailState())

    /** Suggested (principal, interest) split for a repayment of [payment] made on [date]. */
    fun suggestSplit(payment: Long, date: Long): Pair<Long, Long> {
        val debt = state.value.debt ?: return payment to 0L
        return DebtCalculator.suggestSplit(payment, debt, state.value.entries, Dates.of(date))
    }

    fun addRepayment(date: Long, principal: Long, interest: Long, accountId: Long?, note: String) {
        val debt = state.value.debt ?: return
        viewModelScope.launch { repo.addDebtRepayment(debt, date, principal, interest, accountId, note) }
    }

    fun addIncrease(date: Long, amount: Long, accountId: Long?, note: String) {
        val debt = state.value.debt ?: return
        viewModelScope.launch { repo.addDebtIncrease(debt, date, amount, accountId, note) }
    }

    fun deleteEntry(entry: DebtEntry) = viewModelScope.launch { repo.deleteDebtEntry(entry) }

    fun setClosed(closed: Boolean) {
        val debt = state.value.debt ?: return
        viewModelScope.launch { repo.setDebtClosed(debt, closed) }
    }

    fun delete(onDeleted: () -> Unit) {
        val debt = state.value.debt ?: return
        viewModelScope.launch {
            repo.deleteDebt(debt)
            onDeleted()
        }
    }
}

class EditDebtViewModel(container: AppContainer, private val debtId: Long) : ViewModel() {
    private val repo = container.repository
    var currency by mutableStateOf("EUR")
        private set
    var accounts by mutableStateOf<List<Account>>(emptyList())
        private set
    var kind by mutableStateOf(DebtKind.LOAN)
    var name by mutableStateOf("")
    var counterparty by mutableStateOf("")
    var principalText by mutableStateOf("")
    var rateText by mutableStateOf("")
    var startDate by mutableStateOf(Dates.todayEpoch())
    var monthlyText by mutableStateOf("")
    var targetDate by mutableStateOf<Long?>(null)
    var accountId by mutableStateOf<Long?>(null)
    var recordInAccount by mutableStateOf(false)
    var notes by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var done by mutableStateOf(false)
        private set
    private var original: Debt? = null
    val isNew get() = debtId == 0L

    init {
        viewModelScope.launch {
            currency = container.settings.current().currency
            accounts = repo.allAccounts().filter { !it.archived }
            if (debtId != 0L) {
                repo.debts.first().firstOrNull { it.id == debtId }?.let { d ->
                    original = d
                    kind = d.kind
                    name = d.name
                    counterparty = d.counterparty
                    principalText = Money.toInput(d.principalCents)
                    rateText = if (d.annualRateBps > 0) Rates.toInput(d.annualRateBps) else ""
                    startDate = d.startDate
                    monthlyText = if (d.monthlyPaymentCents > 0) Money.toInput(d.monthlyPaymentCents) else ""
                    targetDate = d.targetPayoffDate
                    accountId = d.accountId
                    notes = d.notes
                }
            }
        }
    }

    fun save() {
        val principal = Money.parseToCents(principalText)
        val rate = Rates.parseToBps(rateText)
        val monthly = if (monthlyText.isBlank()) 0L else Money.parseToCents(monthlyText)
        error = when {
            name.isBlank() -> "Give this debt a name."
            principal == null || principal <= 0 -> "Enter the amount, for example 2500.00"
            rate == null -> "Enter the yearly interest rate as a percentage between 0 and 100."
            monthly == null -> "Enter the monthly repayment, for example 150.00"
            targetDate != null && targetDate!! <= Dates.todayEpoch() -> "The target payoff date must be in the future."
            recordInAccount && accountId == null -> "Choose the account the money went into or came from."
            else -> null
        }
        if (error != null || principal == null || rate == null || monthly == null) return
        viewModelScope.launch {
            val base = original ?: Debt(name = name.trim(), kind = kind, principalCents = principal, startDate = startDate)
            repo.saveDebt(
                base.copy(
                    name = name.trim(),
                    kind = kind,
                    counterparty = counterparty.trim(),
                    principalCents = principal,
                    annualRateBps = rate,
                    startDate = startDate,
                    monthlyPaymentCents = monthly,
                    targetPayoffDate = targetDate,
                    accountId = accountId,
                    notes = notes.trim(),
                ),
                recordInitialInAccount = isNew && recordInAccount,
            )
            done = true
        }
    }
}
