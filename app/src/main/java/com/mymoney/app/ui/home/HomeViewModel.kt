package com.mymoney.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymoney.app.AppContainer
import com.mymoney.app.core.BillSchedule
import com.mymoney.app.core.DailyClock
import com.mymoney.app.core.Dates
import com.mymoney.app.core.GoalCalculator
import com.mymoney.app.core.GoalProgress
import com.mymoney.app.core.Money
import com.mymoney.app.core.SafeToSpend
import com.mymoney.app.core.SafeToSpendCalculator
import com.mymoney.app.core.expenseTotal
import com.mymoney.app.core.incomeTotal
import com.mymoney.app.data.db.Account
import com.mymoney.app.data.db.AccountType
import com.mymoney.app.data.db.Bill
import com.mymoney.app.data.db.Budget
import com.mymoney.app.data.db.Category
import com.mymoney.app.data.db.GoalContribution
import com.mymoney.app.data.db.SavingsGoal
import com.mymoney.app.data.db.Transaction
import com.mymoney.app.data.db.TransactionType
import com.mymoney.app.data.prefs.AppSettings
import com.mymoney.app.ui.transactions.TxItem
import com.mymoney.app.ui.transactions.buildTxItems
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

data class AccountBalance(val account: Account, val balance: Long)
data class UpcomingBill(val bill: Bill, val date: LocalDate)
data class GoalItem(val goal: SavingsGoal, val progress: GoalProgress)
data class MonthBar(val month: YearMonth, val income: Long, val expense: Long)
data class CategorySpend(val category: Category?, val total: Long)

data class HomeState(
    val loading: Boolean = true,
    val currency: String = "EUR",
    val month: YearMonth = YearMonth.now(),
    val isCurrentMonth: Boolean = true,
    val totalBalance: Long = 0,
    val accounts: List<AccountBalance> = emptyList(),
    val income: Long = 0,
    val expense: Long = 0,
    val savings: Long = 0,
    val savingsRate: BigDecimal? = null,
    val budgetTotal: Long? = null,
    val budgetRemaining: Long? = null,
    val safeToSpend: SafeToSpend? = null,
    val upcoming: List<UpcomingBill> = emptyList(),
    val recent: List<TxItem> = emptyList(),
    val goals: List<GoalItem> = emptyList(),
    val trend: List<MonthBar> = emptyList(),
    val spendByCategory: List<CategorySpend> = emptyList(),
)

private data class BaseData(
    val accounts: List<Account>,
    val balances: Map<Long, Long>,
    val transactions: List<Transaction>,
    val categories: List<Category>,
    val budgets: List<Budget>,
)

private data class ExtraData(
    val bills: List<Bill>,
    val goals: List<SavingsGoal>,
    val contributions: List<GoalContribution>,
    val settings: AppSettings,
    val month: YearMonth,
    val today: LocalDate,
)

class HomeViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val selectedMonth = MutableStateFlow(YearMonth.now())
    private var followsCurrentMonth = true

    private val base = combine(repo.accounts, repo.balances, repo.transactions, repo.categories, repo.budgets) { a, b, t, c, bu ->
        BaseData(a, b, t, c, bu)
    }
    private val dateAndMonth = combine(DailyClock.dates(), selectedMonth) { today, month -> today to month }
    private val extra = combine(repo.bills, repo.goals, repo.contributions, container.settings.settings, dateAndMonth) { bi, g, gc, s, dm ->
        ExtraData(bi, g, gc, s, dm.second, dm.first)
    }

    val state: StateFlow<HomeState> = combine(base, extra) { b, e -> compute(b, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    init {
        viewModelScope.launch {
            DailyClock.dates().collect { today ->
                if (followsCurrentMonth) selectedMonth.value = YearMonth.from(today)
            }
        }
    }

    fun previousMonth() {
        followsCurrentMonth = false
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        val current = YearMonth.from(Dates.today())
        if (selectedMonth.value < current) selectedMonth.value = selectedMonth.value.plusMonths(1)
        followsCurrentMonth = selectedMonth.value == current
    }

    private fun compute(b: BaseData, e: ExtraData): HomeState {
        val today = e.today
        val month = e.month
        val isCurrent = month == YearMonth.from(today)
        val start = Dates.monthStart(month)
        val end = Dates.monthEnd(month)
        val inMonth = b.transactions.filter { it.date in start..end }
        val income = inMonth.incomeTotal()
        val expense = inMonth.expenseTotal()
        val savings = income - expense
        val active = b.accounts.filter { !it.archived }
        val accountBalances = active.map { AccountBalance(it, b.balances[it.id] ?: it.openingBalanceCents) }

        val overall = b.budgets.firstOrNull { it.categoryId == null }?.amountCents
        val categoryBudgetSum = b.budgets.filter { it.categoryId != null }.sumOf { it.amountCents }
        val budgetTotal = overall ?: categoryBudgetSum.takeIf { it > 0 }
        val budgetRemaining = budgetTotal?.let { it - expense }

        val safe = if (isCurrent) {
            val spendableIds = active.filter { it.includeInSpendable }.map { it.id }.toSet()
            val spendable = accountBalances.filter { it.account.id in spendableIds }.sumOf { it.balance }
            val savingsIds = active.filter { it.type == AccountType.SAVINGS }.map { it.id }.toSet()
            val savedThisMonth = savedThisMonth(inMonth, savingsIds)
            SafeToSpendCalculator.calculate(
                today = today,
                paydayDayOfMonth = e.settings.paydayDayOfMonth,
                spendableBalance = spendable,
                bills = e.bills,
                plannedMonthlySavings = e.settings.plannedMonthlySavings,
                savedThisMonth = savedThisMonth,
                overallBudget = budgetTotal,
                expensesThisMonth = expense,
            )
        } else null

        val upcoming = e.bills.filter { it.active }.flatMap { bill ->
            BillSchedule.occurrences(bill, today, today.plusDays(30)).take(3).map { UpcomingBill(bill, it) }
        }.sortedBy { it.date }.take(5)

        val recent = buildTxItems(b.transactions.take(6), b.categories, b.accounts)
        val goals = e.goals.filter { !it.archived }.map { GoalItem(it, GoalCalculator.progress(it, e.contributions, today)) }

        val trend = (5L downTo 0L).map { back ->
            val ym = month.minusMonths(back)
            val txs = b.transactions.filter { it.date in Dates.monthStart(ym)..Dates.monthEnd(ym) }
            MonthBar(ym, txs.incomeTotal(), txs.expenseTotal())
        }
        val catMap = b.categories.associateBy { it.id }
        val spend = inMonth.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .map { (id, list) -> CategorySpend(id?.let { catMap[it] }, list.sumOf { it.amountCents }) }
            .sortedByDescending { it.total }

        return HomeState(
            loading = false,
            currency = e.settings.currency,
            month = month,
            isCurrentMonth = isCurrent,
            totalBalance = accountBalances.sumOf { it.balance },
            accounts = accountBalances,
            income = income,
            expense = expense,
            savings = savings,
            savingsRate = if (income > 0) Money.percent(savings, income) else null,
            budgetTotal = budgetTotal,
            budgetRemaining = budgetRemaining,
            safeToSpend = safe,
            upcoming = upcoming,
            recent = recent,
            goals = goals,
            trend = trend,
            spendByCategory = spend,
        )
    }

    companion object {
        /** Net money moved this month into savings accounts from other accounts. */
        fun savedThisMonth(monthTxs: List<Transaction>, savingsIds: Set<Long>): Long =
            monthTxs.filter { it.type == TransactionType.TRANSFER && it.toAccountId != null }.sumOf { t ->
                val fromSavings = t.accountId in savingsIds
                val toSavings = t.toAccountId?.let { it in savingsIds } == true
                when {
                    !fromSavings && toSavings -> t.amountCents
                    fromSavings && !toSavings -> -t.amountCents
                    else -> 0L
                }
            }.coerceAtLeast(0)
    }
}
