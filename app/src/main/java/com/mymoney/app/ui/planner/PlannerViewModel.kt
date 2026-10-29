package com.mymoney.app.ui.planner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymoney.app.AppContainer
import com.mymoney.app.core.BillSchedule
import com.mymoney.app.core.DailyClock
import com.mymoney.app.core.Dates
import com.mymoney.app.core.GoalCalculator
import com.mymoney.app.core.Money
import com.mymoney.app.core.SafeToSpend
import com.mymoney.app.core.SafeToSpendCalculator
import com.mymoney.app.core.expenseTotal
import com.mymoney.app.core.incomeTotal
import com.mymoney.app.data.db.AccountType
import com.mymoney.app.data.db.Bill
import com.mymoney.app.data.db.GoalContribution
import com.mymoney.app.data.db.SavingsGoal
import com.mymoney.app.data.prefs.AppSettings
import com.mymoney.app.ui.home.GoalItem
import com.mymoney.app.ui.home.HomeViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class PlannerBase(
    val loading: Boolean = true,
    val currency: String = "EUR",
    /** Number of full past months the averages are based on (0 = only the current month was available). */
    val monthsOfData: Int = 0,
    val avgIncome: Long = 0,
    val avgExpenses: Long = 0,
    val totalBalance: Long = 0,
    val savingsBalance: Long = 0,
    val monthlyBills: Long = 0,
    val goals: List<GoalItem> = emptyList(),
    val safeToSpend: SafeToSpend? = null,
)

class PlannerViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository

    val base: StateFlow<PlannerBase> = combine(
        repo.accounts, repo.balances, repo.transactions, repo.budgets,
        combine(repo.bills, repo.goals, repo.contributions, container.settings.settings, DailyClock.dates()) { bi, g, c, s, today ->
            Extra(bi, g, c, s, today)
        },
    ) { accounts, balances, transactions, budgets, extra ->
        val today = extra.today
        val thisMonth = YearMonth.from(today)
        val firstDate = transactions.minOfOrNull { it.date }
        val pastMonths = (1L..3L).map { thisMonth.minusMonths(it) }
            .filter { ym -> firstDate != null && Dates.monthEnd(ym) >= firstDate }
        val (avgIncome, avgExpenses) = if (pastMonths.isNotEmpty()) {
            val txs = transactions.filter { t -> pastMonths.any { t.date in Dates.monthStart(it)..Dates.monthEnd(it) } }
            Money.divideFloor(txs.incomeTotal(), pastMonths.size.toLong()) to Money.divideFloor(txs.expenseTotal(), pastMonths.size.toLong())
        } else {
            val txs = transactions.filter { it.date in Dates.monthStart(thisMonth)..Dates.monthEnd(thisMonth) }
            txs.incomeTotal() to txs.expenseTotal()
        }
        val active = accounts.filter { !it.archived }
        val balanceOf = { id: Long, opening: Long -> balances[id] ?: opening }
        val monthTxs = transactions.filter { it.date in Dates.monthStart(thisMonth)..Dates.monthEnd(thisMonth) }
        val overall = budgets.firstOrNull { it.categoryId == null }?.amountCents
            ?: budgets.filter { it.categoryId != null }.sumOf { it.amountCents }.takeIf { it > 0 }
        val savingsIds = active.filter { it.type == AccountType.SAVINGS }.map { it.id }.toSet()
        val safe = SafeToSpendCalculator.calculate(
            today = today,
            paydayDayOfMonth = extra.settings.paydayDayOfMonth,
            spendableBalance = active.filter { it.includeInSpendable }.sumOf { balanceOf(it.id, it.openingBalanceCents) },
            bills = extra.bills,
            plannedMonthlySavings = extra.settings.plannedMonthlySavings,
            savedThisMonth = HomeViewModel.savedThisMonth(monthTxs, savingsIds),
            overallBudget = overall,
            expensesThisMonth = monthTxs.expenseTotal(),
        )
        PlannerBase(
            loading = false,
            currency = extra.settings.currency,
            monthsOfData = pastMonths.size,
            avgIncome = avgIncome,
            avgExpenses = avgExpenses,
            totalBalance = active.sumOf { balanceOf(it.id, it.openingBalanceCents) },
            savingsBalance = active.filter { it.type == AccountType.SAVINGS }.sumOf { balanceOf(it.id, it.openingBalanceCents) },
            monthlyBills = extra.bills.filter { it.active }.sumOf { BillSchedule.monthlyEquivalent(it.amountCents, it.frequency) },
            goals = extra.goals.filter { !it.archived }.map { GoalItem(it, GoalCalculator.progress(it, extra.contributions, today)) },
            safeToSpend = safe,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlannerBase())

    var incomeText by mutableStateOf("")
    var expensesText by mutableStateOf("")
    var startText by mutableStateOf("")
    var returnText by mutableStateOf("")
    var reducePercent by mutableIntStateOf(10)
    var goalTargetText by mutableStateOf("")
    var affordText by mutableStateOf("")
    var prefilled by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val b = base.first { !it.loading }
            resetToActuals(b)
        }
    }

    fun resetToActuals(b: PlannerBase = base.value) {
        incomeText = Money.toInput(b.avgIncome)
        expensesText = Money.toInput(b.avgExpenses)
        startText = Money.toInput(b.totalBalance.coerceAtLeast(0))
        if (goalTargetText.isBlank()) {
            b.goals.firstOrNull { !it.progress.reached }?.let { goalTargetText = Money.toInput(it.progress.target) }
        }
        prefilled = true
    }
}

private data class Extra(
    val bills: List<Bill>,
    val goals: List<SavingsGoal>,
    val contributions: List<GoalContribution>,
    val settings: AppSettings,
    val today: LocalDate,
)
