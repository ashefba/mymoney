package com.mymoney.app.ui.savings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymoney.app.AppContainer
import com.mymoney.app.core.DailyClock
import com.mymoney.app.core.Dates
import com.mymoney.app.core.GoalCalculator
import com.mymoney.app.core.GoalProgress
import com.mymoney.app.core.Money
import com.mymoney.app.data.db.Account
import com.mymoney.app.data.db.AccountType
import com.mymoney.app.data.db.GoalContribution
import com.mymoney.app.data.db.SavingsGoal
import com.mymoney.app.ui.home.GoalItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SavingsState(
    val loading: Boolean = true,
    val currency: String = "EUR",
    val active: List<GoalItem> = emptyList(),
    val archived: List<GoalItem> = emptyList(),
    val totalSaved: Long = 0,
    val totalTarget: Long = 0,
    val requiredMonthly: Long = 0,
    val savingsAccountsBalance: Long = 0,
)

class SavingsViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val settingsAndDate = combine(container.settings.settings, DailyClock.dates()) { settings, today -> settings to today }
    val state: StateFlow<SavingsState> = combine(
        repo.goals, repo.contributions, repo.accounts, repo.balances, settingsAndDate,
    ) { goals, contributions, accounts, balances, settingsDate ->
        val (settings, today) = settingsDate
        val items = goals.map { GoalItem(it, GoalCalculator.progress(it, contributions, today)) }
        val active = items.filter { !it.goal.archived }
        SavingsState(
            loading = false,
            currency = settings.currency,
            active = active,
            archived = items.filter { it.goal.archived },
            totalSaved = active.sumOf { it.progress.saved },
            totalTarget = active.sumOf { it.progress.target },
            requiredMonthly = active.sumOf { it.progress.requiredMonthly ?: 0L },
            savingsAccountsBalance = accounts.filter { !it.archived && it.type == AccountType.SAVINGS }
                .sumOf { balances[it.id] ?: it.openingBalanceCents },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavingsState())
}

data class GoalDetailState(
    val loading: Boolean = true,
    val currency: String = "EUR",
    val goal: SavingsGoal? = null,
    val progress: GoalProgress? = null,
    val contributions: List<GoalContribution> = emptyList(),
    val accounts: List<Account> = emptyList(),
)

class GoalDetailViewModel(container: AppContainer, private val goalId: Long) : ViewModel() {
    private val repo = container.repository
    val state: StateFlow<GoalDetailState> = combine(
        repo.goals, repo.contributions, repo.accounts, container.settings.settings, DailyClock.dates(),
    ) { goals, contributions, accounts, settings, today ->
        val goal = goals.firstOrNull { it.id == goalId }
        GoalDetailState(
            loading = false,
            currency = settings.currency,
            goal = goal,
            progress = goal?.let { GoalCalculator.progress(it, contributions, today) },
            contributions = contributions.filter { it.goalId == goalId }.sortedWith(compareByDescending<GoalContribution> { it.date }.thenByDescending { it.id }),
            accounts = accounts.filter { !it.archived },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalDetailState())

    fun addContribution(amount: Long, withdraw: Boolean, date: Long, note: String, from: Long?, to: Long?) {
        val goal = state.value.goal ?: return
        viewModelScope.launch {
            repo.addContribution(goal.id, if (withdraw) -amount else amount, date, note, from, to, goal.name)
        }
    }

    fun deleteContribution(c: GoalContribution) = viewModelScope.launch { repo.deleteContribution(c) }

    fun setArchived(archived: Boolean) {
        val goal = state.value.goal ?: return
        viewModelScope.launch { repo.saveGoal(goal.copy(archived = archived)) }
    }

    fun delete(onDeleted: () -> Unit) {
        val goal = state.value.goal ?: return
        viewModelScope.launch {
            repo.deleteGoal(goal)
            onDeleted()
        }
    }
}

class EditGoalViewModel(container: AppContainer, private val goalId: Long) : ViewModel() {
    private val repo = container.repository
    var currency by mutableStateOf("EUR")
        private set
    var name by mutableStateOf("")
    var targetText by mutableStateOf("")
    var savedText by mutableStateOf("")
    var targetDate by mutableStateOf<Long?>(null)
    var iconKey by mutableStateOf("savings")
    var colorIndex by mutableStateOf(0)
    var error by mutableStateOf<String?>(null)
    var done by mutableStateOf(false)
        private set
    private var original: SavingsGoal? = null
    val isNew get() = goalId == 0L

    init {
        viewModelScope.launch {
            currency = container.settings.current().currency
            if (goalId != 0L) {
                container.database.goalDao().getGoals().firstOrNull { it.id == goalId }?.let { g ->
                    original = g
                    name = g.name
                    targetText = Money.toInput(g.targetCents)
                    savedText = if (g.initialSavedCents != 0L) Money.toInput(g.initialSavedCents) else ""
                    targetDate = g.targetDate
                    iconKey = g.iconKey
                    colorIndex = g.colorIndex
                }
            }
        }
    }

    fun applyTemplate(templateName: String, icon: String) {
        if (name.isBlank()) name = templateName
        iconKey = icon
    }

    fun save() {
        val target = Money.parseToCents(targetText)
        val saved = if (savedText.isBlank()) 0L else Money.parseToCents(savedText)
        error = when {
            name.isBlank() -> "Give your goal a name."
            target == null || target <= 0 -> "Enter a target amount greater than zero."
            saved == null -> "Enter the amount already saved like 500.00"
            else -> null
        }
        if (error != null || target == null || saved == null) return
        viewModelScope.launch {
            val base = original ?: SavingsGoal(name = name.trim(), targetCents = target, initialSavedCents = saved, targetDate = targetDate)
            repo.saveGoal(
                base.copy(
                    name = name.trim(), targetCents = target, initialSavedCents = saved,
                    targetDate = targetDate, iconKey = iconKey, colorIndex = colorIndex,
                )
            )
            done = true
        }
    }
}
