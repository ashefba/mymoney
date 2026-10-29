package com.mymoney.app.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mymoney.app.core.Dates
import com.mymoney.app.core.Money
import com.mymoney.app.core.SafeToSpend
import com.mymoney.app.ui.appViewModel
import com.mymoney.app.ui.components.AmountField
import com.mymoney.app.ui.components.AnimatedProgressBar
import com.mymoney.app.ui.components.AppCard
import com.mymoney.app.ui.components.AppIcons
import com.mymoney.app.ui.components.IconBadge
import com.mymoney.app.ui.components.InfoNote
import com.mymoney.app.ui.components.LoadingBox
import com.mymoney.app.ui.components.MoneyText
import com.mymoney.app.ui.components.PeriodSelector
import com.mymoney.app.ui.components.SectionHeader
import com.mymoney.app.ui.components.SelectField
import com.mymoney.app.ui.components.StatRow
import com.mymoney.app.ui.components.paletteColor
import com.mymoney.app.ui.theme.FinanceTheme

private sealed interface BudgetDialog {
    data object Overall : BudgetDialog
    data class ForCategory(val line: CategoryBudgetLine) : BudgetDialog
    data object Planning : BudgetDialog
}

@Composable
fun BudgetsScreen() {
    val vm = appViewModel { BudgetsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<BudgetDialog?>(null) }
    val fc = FinanceTheme.colors

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding)); return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Budgets", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp)) }
            item { PeriodSelector(Dates.month(state.month), vm::previousMonth, vm::nextMonth) }

            if (state.overCount > 0 || state.nearCount > 0) {
                item {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.Warning, if (state.overCount > 0) fc.expense else fc.warning)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                buildString {
                                    if (state.overCount > 0) append("${state.overCount} budget${if (state.overCount > 1) "s are" else " is"} overspent. ")
                                    if (state.nearCount > 0) append("${state.nearCount} close to the limit.")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            state.safeToSpend?.let { safe -> item { SafeToSpendCard(safe, state, onEdit = { dialog = BudgetDialog.Planning }) } }

            item {
                AppCard(onClick = { dialog = BudgetDialog.Overall }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Overall monthly budget", style = MaterialTheme.typography.titleSmall)
                            Text(
                                when {
                                    state.overallBudget != null -> "Limit for all spending this month"
                                    state.categoryBudgetTotal > 0 -> "Using the sum of your category budgets"
                                    else -> "Not set — tap to choose a monthly limit"
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val total = state.effectiveBudget
                    if (total != null) {
                        Spacer(Modifier.height(12.dp))
                        val remaining = total - state.totalSpent
                        Row(verticalAlignment = Alignment.Bottom) {
                            MoneyText(state.totalSpent, state.currency, style = MaterialTheme.typography.headlineSmall)
                            Text(" of ${Money.format(total, state.currency)}", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        val frac = Money.fraction(state.totalSpent, total)
                        AnimatedProgressBar(frac, color = when {
                            remaining < 0 -> fc.expense
                            frac >= 0.8f -> fc.warning
                            else -> MaterialTheme.colorScheme.primary
                        }, height = 12.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (remaining >= 0) "${Money.format(remaining, state.currency)} left to spend" else "Overspent by ${Money.format(-remaining, state.currency)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (remaining >= 0) fc.income else fc.expense,
                        )
                    } else if (state.averageMonthlySpend > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("You've spent about ${Money.format(state.averageMonthlySpend, state.currency)} a month recently.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (state.withBudget.isNotEmpty()) {
                item { SectionHeader("Category budgets") }
                items(state.withBudget, key = { it.category.id }) { line ->
                    CategoryBudgetCard(line, state.currency) { dialog = BudgetDialog.ForCategory(line) }
                }
            }

            item {
                SectionHeader(if (state.withBudget.isEmpty()) "Set budgets by category" else "Other categories")
                Text("Tap a category to give it a monthly limit.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(state.withoutBudget, key = { "n${it.category.id}" }) { line ->
                AppCard(onClick = { dialog = BudgetDialog.ForCategory(line) }, contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(AppIcons.category(line.category.iconKey), paletteColor(line.category.colorIndex), size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(line.category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(if (line.spent > 0) "${Money.format(line.spent, state.currency)} spent" else "Add budget",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        BudgetDialog.Overall -> AmountDialog(
            title = "Overall monthly budget",
            message = "The most you want to spend in total each month. Leave empty to remove." +
                if (state.averageMonthlySpend > 0) " Recently you spent about ${Money.format(state.averageMonthlySpend, state.currency)} a month." else "",
            initial = state.overallBudget,
            currency = state.currency,
            onSave = { vm.setOverall(it) },
            onDismiss = { dialog = null },
        )
        is BudgetDialog.ForCategory -> AmountDialog(
            title = "Budget for ${d.line.category.name}",
            message = "Monthly limit for this category. Leave empty to remove." +
                if (d.line.averageSpent > 0) " Average over the last 3 months: ${Money.format(d.line.averageSpent, state.currency)}." else "",
            initial = d.line.budget,
            currency = state.currency,
            onSave = { vm.setCategoryBudget(d.line.category.id, it) },
            onDismiss = { dialog = null },
        )
        BudgetDialog.Planning -> PlanningDialog(state, vm, onDismiss = { dialog = null })
    }
}

@Composable
private fun SafeToSpendCard(safe: SafeToSpend, state: BudgetsState, onEdit: () -> Unit) {
    val fc = FinanceTheme.colors
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Today, MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Safe to spend per day", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(safe.dailyAllowance, state.currency, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Text(
            "For ${safe.daysRemaining} day${if (safe.daysRemaining == 1) "" else "s"}, until ${Dates.medium(safe.periodEnd)}" +
                if (safe.untilPayday) " (the day before payday)" else " (end of month)",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        StatRow("Money in spendable accounts", Money.format(safe.spendableBalance, state.currency))
        StatRow("Bills due before then", "− ${Money.format(safe.upcomingBills, state.currency)}")
        StatRow("Planned savings still to set aside", "− ${Money.format(safe.savingsReserve, state.currency)}")
        StatRow("Available", Money.format(safe.moneyAvailable, state.currency), bold = true)
        safe.budgetRemaining?.let {
            StatRow("Budget left after upcoming bills", Money.format(it, state.currency))
        }
        if (safe.limitedByBudget) {
            Text("Your budget is the tighter limit, so it's used for the daily amount.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (safe.safeTotal < 0) {
            Spacer(Modifier.height(4.dp))
            Text("You don't have enough for the rest of this period. Try to cut spending or move money from savings.",
                style = MaterialTheme.typography.bodySmall, color = fc.expense, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onEdit) { Text("Payday & planned savings") }
    }
}

@Composable
private fun CategoryBudgetCard(line: CategoryBudgetLine, currency: String, onClick: () -> Unit) {
    val fc = FinanceTheme.colors
    val budget = line.budget ?: 0L
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcons.category(line.category.iconKey), paletteColor(line.category.colorIndex), size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(line.category.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("${Money.format(line.spent, currency)} of ${Money.format(budget, currency)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val remaining = line.remaining ?: 0
            Text(
                if (remaining >= 0) "${Money.format(remaining, currency)} left" else "${Money.format(-remaining, currency)} over",
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    line.over -> fc.expense
                    line.nearLimit -> fc.warning
                    else -> fc.income
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        AnimatedProgressBar(line.fraction, color = when {
            line.over -> fc.expense
            line.nearLimit -> fc.warning
            else -> paletteColor(line.category.colorIndex)
        })
    }
}

@Composable
fun AmountDialog(
    title: String,
    message: String,
    initial: Long?,
    currency: String,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit,
    label: String = "Amount",
) {
    var text by remember { mutableStateOf(initial?.let { Money.toInput(it) } ?: "") }
    val parsed = if (text.isBlank()) 0L else Money.parseToCents(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                AmountField(text, { text = it }, label, currency)
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let(onSave); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PlanningDialog(state: BudgetsState, vm: BudgetsViewModel, onDismiss: () -> Unit) {
    var payday by remember { mutableStateOf(state.paydayDayOfMonth) }
    var savingsText by remember { mutableStateOf(if (state.plannedSavings > 0) Money.toInput(state.plannedSavings) else "") }
    val parsed = if (savingsText.isBlank()) 0L else Money.parseToCents(savingsText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Payday & planned savings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectField(
                    label = "When does your income arrive?",
                    options = (0..31).toList(),
                    selected = payday,
                    optionLabel = { if (it == 0) "Plan to the end of the month" else "On day $it of the month" },
                    onSelect = { payday = it },
                )
                AmountField(savingsText, { savingsText = it }, "Planned savings per month", state.currency)
                if (state.goalsRequiredMonthly > 0) {
                    TextButton(onClick = { savingsText = Money.toInput(state.goalsRequiredMonthly) }) {
                        Text("Use what my goals need: ${Money.format(state.goalsRequiredMonthly, state.currency)}")
                    }
                }
                InfoNote("Planned savings are held back from your daily allowance until you move them to a savings account with a transfer.")
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = {
                vm.setPayday(payday)
                parsed?.let { vm.setPlannedSavings(it) }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
