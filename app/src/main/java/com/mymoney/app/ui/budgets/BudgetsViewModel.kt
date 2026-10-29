package com.mymoney.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymoney.app.AppContainer
import com.mymoney.app.core.Dates
import com.mymoney.app.core.GoalCalculator
import com.mymoney.app.core.Money
import com.mymoney.app.core.SafeToSpend
import com.mymoney.app.core.SafeToSpendCalculator
import com.mymoney.app.core.expenseTotal
import com.mymoney.app.data.db.Account
import com.mymoney.app.data.db.AccountType
import com.mymoney.app.data.db.Bill
import com.mymoney.app.data.db.Budget
import com.mymoney.app.data.db.GoalContribution
import com.mymoney.app.data.db.SavingsGoal
import com.mymoney.app.data.db.Category
import com.mymoney.app.data.db.CategoryKind
import com.mymoney.app.data.db.Transaction
import com.mymoney.app.data.db.TransactionType
import com.mymoney.app.ui.home.HomeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class CategoryBudgetLine(
    val category: Category,
    val budget: Long?,
    val spent: Long,
    val averageSpent: Long,
) {
    val remaining: Long? get() = budget?.let { it - spent }
    val fraction: Float get() = budget?.let { Money.fraction(spent, it) } ?: 0f
    val over: Boolean get() = budget != null && spent > budget
    val nearLimit: Boolean get() = budget != null && !over && budget > 0 && spent * 100 >= budget * 80
}

data class BudgetsState(
    val loading: Boolean = true,
    val currency: String = "EUR",
    val month: YearMonth = YearMonth.now(),
    val isCurrentMonth: Boolean = true,
    val overallBudget: Long? = null,
    val categoryBudgetTotal: Long = 0,
    val totalSpent: Long = 0,
    val averageMonthlySpend: Long = 0,
    val withBudget: List<CategoryBudgetLine> = emptyList(),
    val withoutBudget: List<CategoryBudgetLine> = emptyList(),
    val safeToSpend: SafeToSpend? = null,
    val paydayDayOfMonth: Int = 0,
    val plannedSavings: Long = 0,
    val goalsRequiredMonthly: Long = 0,
) {
    val effectiveBudget: Long? get() = overallBudget ?: categoryBudgetTotal.takeIf { it > 0 }
    val overCount: Int get() = withBudget.count { it.over }
    val nearCount: Int get() = withBudget.count { it.nearLimit }
}

private data class Inputs(
    val txs: List<Transaction>,
    val cats: List<Category>,
    val budgets: List<Budget>,
    val month: YearMonth,
)

private data class MoneyInputs(
    val accounts: List<Account>,
    val balances: Map<Long, Long>,
    val bills: List<Bill>,
    val goals: List<SavingsGoal>,
    val contributions: List<GoalContribution>,
)

class BudgetsViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val settingsRepo = container.settings
    private val month = MutableStateFlow(YearMonth.now())

    private val inputs = combine(repo.transactions, repo.categories, repo.budgets, month) { t, c, b, m -> Inputs(t, c, b, m) }
    private val money = combine(repo.accounts, repo.balances, repo.bills, repo.goals, repo.contributions) { a, b, bills, g, gc ->
        MoneyInputs(a, b, bills, g, gc)
    }

    val state: StateFlow<BudgetsState> = combine(inputs, money, settingsRepo.settings) { i, m, settings ->
        val accounts = m.accounts
        val balances = m.balances
        val bills = m.bills
        val goals = m.goals
        val contributions = m.contributions

        val today = Dates.today()
        val ym = i.month
        val start = Dates.monthStart(ym)
        val end = Dates.monthEnd(ym)
        val monthTx = i.txs.filter { it.date in start..end }
        val expenses = monthTx.filter { it.type == TransactionType.EXPENSE }
        val spentByCat = expenses.groupBy { it.categoryId }.mapValues { (_, v) -> v.sumOf { it.amountCents } }
        val avgStart = Dates.monthStart(ym.minusMonths(3))
        val avgEnd = Dates.monthEnd(ym.minusMonths(1))
        val prev = i.txs.filter { it.type == TransactionType.EXPENSE && it.date in avgStart..avgEnd }
        val avgByCat = prev.groupBy { it.categoryId }.mapValues { (_, v) -> Money.divideFloor(v.sumOf { it.amountCents }, 3) }

        val budgetByCat = i.budgets.filter { it.categoryId != null }.associate { it.categoryId!! to it.amountCents }
        val overall = i.budgets.firstOrNull { it.categoryId == null }?.amountCents
        val lines = i.cats.filter { it.kind == CategoryKind.EXPENSE && (!it.archived || budgetByCat.containsKey(it.id)) }.map { c ->
            CategoryBudgetLine(c, budgetByCat[c.id], spentByCat[c.id] ?: 0L, avgByCat[c.id] ?: 0L)
        }
        val totalSpent = expenses.sumOf { it.amountCents }
        val categoryBudgetTotal = budgetByCat.values.sum()
        val effective = overall ?: categoryBudgetTotal.takeIf { it > 0 }

        val isCurrent = ym == YearMonth.now()
        val safe = if (isCurrent) {
            val active = accounts.filter { !it.archived }
            val spendable = active.filter { it.includeInSpendable }.sumOf { balances[it.id] ?: it.openingBalanceCents }
            val savingsIds = active.filter { it.type == AccountType.SAVINGS }.map { it.id }.toSet()
            SafeToSpendCalculator.calculate(
                today = today,
                paydayDayOfMonth = settings.paydayDayOfMonth,
                spendableBalance = spendable,
                bills = bills,
                plannedMonthlySavings = settings.plannedMonthlySavings,
                savedThisMonth = HomeViewModel.savedThisMonth(monthTx, savingsIds),
                overallBudget = effective,
                expensesThisMonth = totalSpent,
            )
        } else null

        val goalsRequired = goals.filter { !it.archived }.sumOf { g ->
            GoalCalculator.progress(g, contributions, today).requiredMonthly ?: 0L
        }

        BudgetsState(
            loading = false,
            currency = settings.currency,
            month = ym,
            isCurrentMonth = isCurrent,
            overallBudget = overall,
            categoryBudgetTotal = categoryBudgetTotal,
            totalSpent = totalSpent,
            averageMonthlySpend = Money.divideFloor(prev.expenseTotal(), 3),
            withBudget = lines.filter { it.budget != null }.sortedByDescending { it.fraction },
            withoutBudget = lines.filter { it.budget == null }.sortedByDescending { it.spent },
            safeToSpend = safe,
            paydayDayOfMonth = settings.paydayDayOfMonth,
            plannedSavings = settings.plannedMonthlySavings,
            goalsRequiredMonthly = goalsRequired,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsState())

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }

    fun setOverall(cents: Long) = viewModelScope.launch { repo.setBudget(null, cents) }
    fun setCategoryBudget(categoryId: Long, cents: Long) = viewModelScope.launch { repo.setBudget(categoryId, cents) }
    fun setPayday(day: Int) = viewModelScope.launch { settingsRepo.setPayday(day) }
    fun setPlannedSavings(cents: Long) = viewModelScope.launch { settingsRepo.setPlannedSavings(cents) }
}
