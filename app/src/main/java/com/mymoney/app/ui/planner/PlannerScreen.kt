package com.mymoney.app.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mymoney.app.core.Dates
import com.mymoney.app.core.Forecast
import com.mymoney.app.core.Money
import com.mymoney.app.core.Rates
import com.mymoney.app.ui.appViewModel
import com.mymoney.app.ui.components.AmountField
import com.mymoney.app.ui.components.AppCard
import com.mymoney.app.ui.components.AppTopBar
import com.mymoney.app.ui.components.InfoNote
import com.mymoney.app.ui.components.LegendDot
import com.mymoney.app.ui.components.LineChart
import com.mymoney.app.ui.components.LineSeries
import com.mymoney.app.ui.components.LoadingBox
import com.mymoney.app.ui.components.MoneyText
import com.mymoney.app.ui.components.StatRow
import com.mymoney.app.ui.theme.FinanceTheme
import java.time.YearMonth
import kotlin.math.roundToInt

private fun monthsText(months: Int): String {
    val years = months / 12
    val rest = months % 12
    return when {
        months == 0 -> "Already there"
        years == 0 -> "$months month${if (months == 1) "" else "s"}"
        rest == 0 -> "$years year${if (years == 1) "" else "s"}"
        else -> "$years y $rest m"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlannerScreen(onBack: () -> Unit) {
    val vm = appViewModel { PlannerViewModel(it) }
    val base by vm.base.collectAsStateWithLifecycle()
    val fc = FinanceTheme.colors
    var selectedPoint by remember { mutableStateOf<Int?>(null) }
    var goalSavedText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("Planning", onBack = onBack) },
    ) { padding ->
        if (base.loading || !vm.prefilled) {
            LoadingBox(Modifier.padding(padding)); return@Scaffold
        }
        val currency = base.currency
        val income = Money.parseToCents(vm.incomeText) ?: 0L
        val expenses = Money.parseToCents(vm.expensesText) ?: 0L
        val start = Money.parseToCents(vm.startText) ?: 0L
        val rateBps = Rates.parseToBps(vm.returnText) ?: 0
        val surplus = income - expenses
        val reducedExpenses = Forecast.reduceBy(expenses, vm.reducePercent)
        val reducedSurplus = income - reducedExpenses
        val asNow = Forecast.project(start, surplus, 12, rateBps)
        val reduced = Forecast.project(start, reducedSurplus, 12, rateBps)
        val labels = (0..12).map { Dates.shortMonth(YearMonth.now().plusMonths(it.toLong())) }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your monthly numbers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.resetToActuals() }) {
                        Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Use my data")
                    }
                }
                Text(
                    if (base.monthsOfData > 0) "Pre-filled with your average over the last ${base.monthsOfData} full month${if (base.monthsOfData == 1) "" else "s"}. Change anything to try a scenario."
                    else "Pre-filled with this month so far. Add a few months of transactions for better averages.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                AmountField(vm.incomeText, { vm.incomeText = it }, "Monthly income", currency)
                Spacer(Modifier.height(8.dp))
                AmountField(vm.expensesText, { vm.expensesText = it }, "Monthly spending", currency,
                    supportingText = "Includes bills (about ${Money.format(base.monthlyBills, currency)} a month).")
                Spacer(Modifier.height(8.dp))
                AmountField(vm.startText, { vm.startText = it }, "Starting balance", currency,
                    supportingText = "Total across your accounts today: ${Money.format(base.totalBalance, currency)}")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    vm.returnText, { vm.returnText = it }, label = { Text("Yearly interest on savings (optional)") },
                    suffix = { Text("%") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = Rates.parseToBps(vm.returnText) == null,
                )
                Spacer(Modifier.height(8.dp))
                StatRow("Left over each month", Money.format(surplus, currency, showPlus = true), bold = true,
                    valueColor = if (surplus >= 0) fc.income else fc.expense)
            }

            AppCard {
                Text("Savings forecast", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LineChart(
                    series = listOf(
                        LineSeries("As now", asNow, MaterialTheme.colorScheme.primary),
                        LineSeries("Spending −${vm.reducePercent}%", reduced, fc.income, dashed = true),
                    ),
                    labels = labels,
                    selectedIndex = selectedPoint,
                    onSelect = { selectedPoint = it },
                )
                Row(Modifier.padding(top = 6.dp)) {
                    LegendDot(MaterialTheme.colorScheme.primary, "As now")
                    LegendDot(fc.income, "Spending −${vm.reducePercent}%")
                }
                selectedPoint?.let { i ->
                    if (i in asNow.indices) {
                        Text("${labels[i]}: ${Money.format(asNow[i], currency)} · with cuts ${Money.format(reduced[i], currency)}",
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                StatRow("In 6 months", Money.format(asNow[6], currency), bold = true)
                StatRow("In 12 months", Money.format(asNow[12], currency), bold = true)
                StatRow("Saved over 12 months", Money.format(asNow[12] - start, currency, showPlus = true),
                    valueColor = if (asNow[12] >= start) fc.income else fc.expense)
                if (surplus < 0) {
                    Text("You're spending more than you earn, so your balance shrinks every month.",
                        color = fc.expense, style = MaterialTheme.typography.bodySmall)
                }
            }

            AppCard {
                Text("If you spend less", style = MaterialTheme.typography.titleMedium)
                Text("Reduce spending by ${vm.reducePercent}%", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = vm.reducePercent.toFloat(),
                    onValueChange = { vm.reducePercent = it.roundToInt() },
                    valueRange = 0f..50f,
                    steps = 9,
                )
                StatRow("New monthly spending", Money.format(reducedExpenses, currency))
                StatRow("Extra saved per month", Money.format(expenses - reducedExpenses, currency, showPlus = true), valueColor = fc.income)
                StatRow("Extra after 12 months", Money.format(reduced[12] - asNow[12], currency, showPlus = true), bold = true, valueColor = fc.income)
            }

            AppCard {
                Text("How long until I reach…", style = MaterialTheme.typography.titleMedium)
                if (base.goals.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        base.goals.forEach { g ->
                            AssistChip(onClick = {
                                vm.goalTargetText = Money.toInput(g.progress.target)
                                goalSavedText = Money.toInput(g.progress.saved)
                            }, label = { Text(g.goal.name) })
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                AmountField(vm.goalTargetText, { vm.goalTargetText = it }, "Target amount", currency)
                Spacer(Modifier.height(8.dp))
                AmountField(goalSavedText, { goalSavedText = it }, "Already saved", currency,
                    supportingText = "Leave empty to start from zero.")
                val target = Money.parseToCents(vm.goalTargetText)
                val saved = if (goalSavedText.isBlank()) 0L else Money.parseToCents(goalSavedText) ?: 0L
                if (target != null && target > 0) {
                    Spacer(Modifier.height(8.dp))
                    val months = Forecast.monthsToReach(target, saved, surplus, rateBps)
                    val monthsReduced = Forecast.monthsToReach(target, saved, reducedSurplus, rateBps)
                    StatRow(
                        "Saving ${Money.format(surplus.coerceAtLeast(0), currency)}/month",
                        months?.let { "${monthsText(it)} · ${Dates.medium(Dates.today().plusMonths(it.toLong()))}" } ?: "Not reachable",
                        bold = true,
                    )
                    if (vm.reducePercent > 0) {
                        StatRow(
                            "With spending −${vm.reducePercent}%",
                            monthsReduced?.let { monthsText(it) } ?: "Not reachable",
                            valueColor = fc.income,
                        )
                    }
                    if (months == null) {
                        Text("With nothing left over each month, this target can't be reached. Try reducing spending.",
                            color = fc.warning, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            base.safeToSpend?.let { safe ->
                AppCard {
                    Text("What can I afford this month?", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        MoneyText(safe.safeTotal.coerceAtLeast(0), currency, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "  until ${Dates.medium(safe.periodEnd)}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                    Text("That's ${Money.format(safe.dailyAllowance, currency)} a day after upcoming bills and planned savings.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    AmountField(vm.affordText, { vm.affordText = it }, "Price of something you want", currency)
                    val price = Money.parseToCents(vm.affordText)
                    if (price != null && price > 0) {
                        Spacer(Modifier.height(8.dp))
                        val left = safe.safeTotal - price
                        if (left >= 0) {
                            val newDaily = Money.divideFloor(left, safe.daysRemaining.toLong())
                            Text(
                                "Yes. You'd still have ${Money.format(left, currency)} — about ${Money.format(newDaily, currency)} a day.",
                                color = fc.income, style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            val short = -left
                            val wait = if (surplus > 0) Money.divideCeil(short, surplus) else null
                            Text(
                                buildString {
                                    append("Not this month — you'd be ${Money.format(short, currency)} short without dipping into bills or savings.")
                                    if (wait != null) append(" Setting aside your monthly surplus, you could buy it in about ${monthsText(wait.toInt())}.")
                                },
                                color = fc.warning, style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            InfoNote(
                "Assumptions: income and spending stay the same every month; bills are part of spending; " +
                    "interest (if entered) is added monthly on positive balances at ${Rates.format(rateBps)} a year; " +
                    "no inflation, taxes or one-off costs. Forecasts are estimates, not guarantees.",
            )
        }
    }
}
