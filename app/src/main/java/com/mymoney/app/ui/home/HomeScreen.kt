package com.mymoney.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mymoney.app.core.Dates
import com.mymoney.app.core.Money
import com.mymoney.app.ui.appViewModel
import com.mymoney.app.ui.components.AnimatedProgressBar
import com.mymoney.app.ui.components.AppCard
import com.mymoney.app.ui.components.AppIcons
import com.mymoney.app.ui.components.BarChart
import com.mymoney.app.ui.components.BarGroup
import com.mymoney.app.ui.components.ChartSlice
import com.mymoney.app.ui.components.DonutChart
import com.mymoney.app.ui.components.EmptyState
import com.mymoney.app.ui.components.IconBadge
import com.mymoney.app.ui.components.LegendDot
import com.mymoney.app.ui.components.ListRow
import com.mymoney.app.ui.components.LoadingBox
import com.mymoney.app.ui.components.MoneyText
import com.mymoney.app.ui.components.PeriodSelector
import com.mymoney.app.ui.components.SectionHeader
import com.mymoney.app.ui.components.paletteColor
import com.mymoney.app.ui.theme.FinanceTheme
import com.mymoney.app.ui.transactions.TransactionRow
import java.time.LocalTime

@Composable
fun HomeScreen(
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onSeeAllTransactions: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenGoal: (Long) -> Unit,
    onOpenSavings: () -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTransaction,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Greeting() }
            item {
                PeriodSelector(
                    label = Dates.month(state.month),
                    onPrevious = vm::previousMonth,
                    onNext = vm::nextMonth,
                    canGoNext = !state.isCurrentMonth,
                )
            }
            item { HeroCard(state, onOpenAccounts) }

            if (state.accounts.isEmpty()) {
                item {
                    AppCard {
                        EmptyState(
                            icon = Icons.Rounded.AccountBalance,
                            title = "Add your first account",
                            message = "Tell MyMoney where your money is — a bank account, savings or cash — and its current balance.",
                            action = { Button(onClick = onOpenAccounts) { Text("Add account") } },
                        )
                    }
                }
            }

            item { MonthStats(state) }

            state.safeToSpend?.let { safe ->
                item {
                    AppCard(onClick = onOpenBudgets) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.Today, MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Safe to spend today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                MoneyText(safe.dailyAllowance, state.currency, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val untilText = if (safe.untilPayday) "until payday" else "until the end of the month"
                        Text(
                            "${Money.format(safe.safeTotal.coerceAtLeast(0), state.currency)} left for ${safe.daysRemaining} days $untilText, " +
                                "after ${Money.format(safe.upcomingBills, state.currency)} in upcoming bills" +
                                if (safe.savingsReserve > 0) " and ${Money.format(safe.savingsReserve, state.currency)} planned savings." else ".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (safe.safeTotal < 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "You're ${Money.format(-safe.safeTotal, state.currency)} short for this period. Consider pausing non-essential spending.",
                                style = MaterialTheme.typography.bodySmall,
                                color = FinanceTheme.colors.expense,
                            )
                        }
                    }
                }
            }

            state.budgetTotal?.let { total ->
                item {
                    val remaining = state.budgetRemaining ?: 0
                    AppCard(onClick = onOpenBudgets) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Monthly budget", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(
                                if (remaining >= 0) "${Money.format(remaining, state.currency)} left" else "${Money.format(-remaining, state.currency)} over",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (remaining >= 0) FinanceTheme.colors.income else FinanceTheme.colors.expense,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        val frac = Money.fraction(state.expense, total)
                        AnimatedProgressBar(
                            frac,
                            color = when {
                                state.expense > total -> FinanceTheme.colors.expense
                                frac >= 0.8f -> FinanceTheme.colors.warning
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${Money.format(state.expense, state.currency)} spent of ${Money.format(total, state.currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { TrendCard(state) }

            if (state.spendByCategory.isNotEmpty()) {
                item { CategoryCard(state) }
            }

            item {
                SectionHeader("Upcoming payments", action = "Calendar", onAction = onOpenBills)
                AppCard {
                    if (state.upcoming.isEmpty()) {
                        Text(
                            "No bills due in the next 30 days. Add your rent, subscriptions and other regular payments to see them here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.upcoming.forEach { u ->
                            val overdue = u.date.isBefore(Dates.today())
                            ListRow(
                                title = u.bill.name,
                                subtitle = Dates.dueText(u.date.toEpochDay()),
                                icon = Icons.Rounded.EventNote,
                                iconTint = if (overdue) FinanceTheme.colors.expense else MaterialTheme.colorScheme.tertiary,
                                trailing = { MoneyText(u.bill.amountCents, state.currency, style = MaterialTheme.typography.bodyLarge) },
                                onClick = onOpenBills,
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader("Savings goals", action = "All goals", onAction = onOpenSavings)
                if (state.goals.isEmpty()) {
                    AppCard(onClick = onOpenSavings) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.Savings, MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Create a goal for an emergency fund, a trip or a house deposit and track your progress.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.goals, key = { it.goal.id }) { g ->
                            AppCard(modifier = Modifier.width(220.dp), onClick = { onOpenGoal(g.goal.id) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconBadge(AppIcons.category(g.goal.iconKey), paletteColor(g.goal.colorIndex), size = 36.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(g.goal.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.height(12.dp))
                                MoneyText(g.progress.saved, state.currency, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "of ${Money.format(g.progress.target, state.currency)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(10.dp))
                                AnimatedProgressBar(g.progress.fraction, color = paletteColor(g.goal.colorIndex))
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader("Recent transactions", action = "See all", onAction = onSeeAllTransactions)
                AppCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    if (state.recent.isEmpty()) {
                        Text(
                            "Nothing recorded yet. Tap Add to record your first expense or income — it takes a few seconds.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    } else {
                        state.recent.forEach { item ->
                            TransactionRow(item, state.currency, onClick = { onOpenTransaction(item.tx.id) }, showDate = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Greeting() {
    val hour = remember { LocalTime.now().hour }
    val text = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    Column(Modifier.padding(top = 8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Your money", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun HeroCard(state: HomeState, onOpenAccounts: () -> Unit) {
    val fc = FinanceTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(fc.heroGradient)
            .clickable(onClick = onOpenAccounts)
            .padding(20.dp)
    ) {
        Column {
            Text("Total across accounts", style = MaterialTheme.typography.labelLarge, color = fc.onHero.copy(alpha = 0.8f))
            Spacer(Modifier.height(4.dp))
            AnimatedContent(
                targetState = state.totalBalance,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "balance",
            ) { value ->
                MoneyText(value, state.currency, style = MaterialTheme.typography.displaySmall, color = fc.onHero)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${state.accounts.size} account${if (state.accounts.size == 1) "" else "s"} · tap to manage",
                style = MaterialTheme.typography.bodySmall,
                color = fc.onHero.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroPill(Icons.Rounded.ArrowDownward, "Income", Money.format(state.income, state.currency), Modifier.weight(1f))
                HeroPill(Icons.Rounded.ArrowUpward, "Expenses", Money.format(state.expense, state.currency), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroPill(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
            Text(value, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MonthStats(state: HomeState) {
    val fc = FinanceTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("Saved this month", Money.format(state.savings, state.currency),
            if (state.savings >= 0) fc.income else fc.expense, Modifier.weight(1f))
        StatTile("Savings rate", Money.percentText(state.savingsRate),
            if ((state.savingsRate?.signum() ?: 0) >= 0) MaterialTheme.colorScheme.onSurface else fc.expense, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TrendCard(state: HomeState) {
    val fc = FinanceTheme.colors
    var selected by rememberSaveable(state.month) { mutableStateOf<Int?>(null) }
    AppCard {
        Text("Income vs expenses", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        val sel = selected?.let { state.trend.getOrNull(it) }
        Text(
            if (sel != null) {
                "${Dates.month(sel.month)}: in ${Money.format(sel.income, state.currency)}, out ${Money.format(sel.expense, state.currency)}"
            } else "Tap a month to see the details",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        BarChart(
            groups = state.trend.map { BarGroup(Dates.shortMonth(it.month), listOf(it.income, it.expense)) },
            colors = listOf(fc.income, fc.expense),
            selectedIndex = selected,
            onSelect = { selected = it },
        )
        Spacer(Modifier.height(8.dp))
        Row {
            LegendDot(fc.income, "Income")
            LegendDot(fc.expense, "Expenses")
        }
    }
}

@Composable
private fun CategoryCard(state: HomeState) {
    var selected by rememberSaveable(state.month) { mutableStateOf<Int?>(null) }
    val top = state.spendByCategory.take(7)
    val rest = state.spendByCategory.drop(7).sumOf { it.total }
    val slices = top.map { ChartSlice(it.category?.name ?: "Uncategorised", it.total, paletteColor(it.category?.colorIndex ?: 0)) } +
        (if (rest > 0) listOf(ChartSlice("Everything else", rest, MaterialTheme.colorScheme.outline)) else emptyList())
    AppCard {
        Text("Spending by category", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = slices,
                selectedIndex = selected,
                onSelect = { selected = it },
                size = 150.dp,
                thickness = 20.dp,
            ) {
                val s = selected?.let { slices.getOrNull(it) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        s?.label ?: "Spent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(90.dp),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        Money.format(s?.value ?: state.expense, state.currency, compact = true),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                slices.forEachIndexed { i, s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selected = if (selected == i) null else i }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LegendDot(s.color, s.label, Modifier.weight(1f))
                        Text(
                            Money.percentText(Money.percent(s.value, state.expense)),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected == i) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
