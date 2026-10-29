package com.mymoney.app.ui.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mymoney.app.core.CategoryAmount
import com.mymoney.app.core.Money
import com.mymoney.app.core.ReportData
import com.mymoney.app.core.ReportPeriod
import com.mymoney.app.ui.appViewModel
import com.mymoney.app.ui.components.AnimatedProgressBar
import com.mymoney.app.ui.components.AppCard
import com.mymoney.app.ui.components.AppIcons
import com.mymoney.app.ui.components.AppTopBar
import com.mymoney.app.ui.components.BarChart
import com.mymoney.app.ui.components.BarGroup
import com.mymoney.app.ui.components.ChartSlice
import com.mymoney.app.ui.components.DonutChart
import com.mymoney.app.ui.components.EmptyState
import com.mymoney.app.ui.components.IconBadge
import com.mymoney.app.ui.components.InfoNote
import com.mymoney.app.ui.components.LegendDot
import com.mymoney.app.ui.components.LineChart
import com.mymoney.app.ui.components.LineSeries
import com.mymoney.app.ui.components.LoadingBox
import com.mymoney.app.ui.components.MoneyText
import com.mymoney.app.ui.components.PeriodSelector
import com.mymoney.app.ui.components.StatRow
import com.mymoney.app.ui.components.paletteColor
import com.mymoney.app.ui.theme.FinanceTheme
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/** Percentage change from [previous] to [current], e.g. "+12%". Null when there is nothing to compare. */
private fun changeText(current: Long, previous: Long): String? {
    if (previous == 0L) return null
    val pct = BigDecimal.valueOf(current - previous).multiply(BigDecimal(100))
        .divide(BigDecimal.valueOf(abs(previous)), 0, RoundingMode.HALF_UP)
    return (if (pct.signum() > 0) "+" else "") + pct.toPlainString() + "%"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit) {
    val vm = appViewModel { ReportsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.exportPdf(uri)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar("Reports", onBack = onBack) {
                IconButton(onClick = { pdfLauncher.launch(vm.suggestedFileName("pdf")) }, enabled = state.report != null) {
                    Icon(Icons.Rounded.PictureAsPdf, "Export PDF")
                }
                IconButton(onClick = { csvLauncher.launch(vm.suggestedFileName("csv")) }, enabled = state.report != null) {
                    Icon(Icons.Rounded.TableChart, "Export CSV")
                }
            }
        },
    ) { padding ->
        val report = state.report
        if (state.loading || report == null) {
            LoadingBox(Modifier.padding(padding)); return@Scaffold
        }
        val periods = ReportPeriod.entries
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                periods.forEachIndexed { i, p ->
                    SegmentedButton(selected = state.period == p, onClick = { vm.setPeriod(p) },
                        shape = SegmentedButtonDefaults.itemShape(i, periods.size)) { Text(p.label) }
                }
            }
            PeriodSelector(report.range.label, vm::previous, vm::next, canGoNext = state.canGoNext)
            SummaryCard(report, state.currency)
            val nothingRecorded = report.income == 0L && report.expense == 0L &&
                report.trend.all { it.income == 0L && it.expense == 0L }
            if (nothingRecorded) {
                EmptyState(Icons.Rounded.BarChart, "Nothing recorded yet", "Add income and expenses to see your breakdowns and trends here.")
            } else {
                TrendCard(report, state.currency)
                if (report.expenseByCategory.any { it.total > 0 }) {
                    CategoryCard("Where your money went", report.expenseByCategory, report.expense, state.currency, isExpense = true)
                }
                if (report.incomeByCategory.any { it.total > 0 }) {
                    CategoryCard("Where your money came from", report.incomeByCategory, report.income, state.currency, isExpense = false)
                }
                if (report.budgetLines.isNotEmpty()) BudgetCard(report, state.currency)
            }
            InfoNote("Transfers between your accounts, savings goal allocations and debt principal are not counted as income or spending. Export this report as PDF or CSV with the buttons at the top — files are created on your device only.")
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SummaryCard(report: ReportData, currency: String) {
    val fc = FinanceTheme.colors
    val previousLabel = when (report.period) {
        ReportPeriod.WEEK -> "last week"
        ReportPeriod.MONTH -> "last month"
        ReportPeriod.YEAR -> "last year"
    }
    AppCard {
        Row {
            Column(Modifier.weight(1f)) {
                Text("Income", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(report.income, currency, style = MaterialTheme.typography.titleLarge, color = fc.income)
                changeText(report.income, report.previousIncome)?.let {
                    Text("$it vs $previousLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Expenses", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(report.expense, currency, style = MaterialTheme.typography.titleLarge, color = fc.expense)
                changeText(report.expense, report.previousExpense)?.let {
                    Text("$it vs $previousLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        StatRow("Net savings", Money.format(report.net, currency, showPlus = true), bold = true,
            valueColor = if (report.net >= 0) fc.income else fc.expense)
        StatRow("Savings rate", Money.percentText(report.savingsRate))
        StatRow("Average spending per day", Money.format(report.averageDailySpend, currency))
        val prevNet = report.previousIncome - report.previousExpense
        StatRow("Net savings $previousLabel", Money.format(prevNet, currency, showPlus = true))
    }
}

@Composable
private fun TrendCard(report: ReportData, currency: String) {
    val fc = FinanceTheme.colors
    var selectedBar by remember(report.range) { mutableStateOf<Int?>(null) }
    var selectedLine by remember(report.range) { mutableStateOf<Int?>(null) }
    AppCard {
        Text("Income vs expenses", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        BarChart(
            groups = report.trend.map { BarGroup(it.label, listOf(it.income, it.expense)) },
            colors = listOf(fc.income, fc.expense),
            selectedIndex = selectedBar,
            onSelect = { selectedBar = it },
        )
        Row(Modifier.padding(top = 6.dp)) {
            LegendDot(fc.income, "Income")
            LegendDot(fc.expense, "Expenses")
        }
        selectedBar?.let { i ->
            report.trend.getOrNull(i)?.let { t ->
                Text("${t.label}: in ${Money.format(t.income, currency)}, out ${Money.format(t.expense, currency)}, net ${Money.format(t.net, currency, showPlus = true)}",
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Savings trend", style = MaterialTheme.typography.titleMedium)
        Text("Cumulative net savings across the chart", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        val cumulative = report.trend.runningFold(0L) { acc, t -> acc + t.net }.drop(1)
        LineChart(
            series = listOf(LineSeries("Net savings", cumulative, MaterialTheme.colorScheme.primary)),
            labels = report.trend.map { it.label },
            selectedIndex = selectedLine,
            onSelect = { selectedLine = it },
            height = 160.dp,
        )
        selectedLine?.let { i ->
            cumulative.getOrNull(i)?.let { v ->
                Text("${report.trend[i].label}: ${Money.format(v, currency, showPlus = true)} saved so far",
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun CategoryCard(title: String, items: List<CategoryAmount>, total: Long, currency: String, isExpense: Boolean) {
    val fc = FinanceTheme.colors
    val shown = items.filter { it.total > 0 }
    var selected by remember(items) { mutableStateOf<Int?>(null) }
    AppCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        val slices = shown.map { ChartSlice(it.name, it.total, paletteColor(it.colorIndex)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            DonutChart(slices, selected, { selected = it }, size = 180.dp) {
                val sel = selected?.let { shown.getOrNull(it) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(sel?.name ?: "Total", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    MoneyText(sel?.total ?: total, currency, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        items.take(12).forEach { c ->
            val color = paletteColor(c.colorIndex)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(AppIcons.category(c.iconKey), color, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(c.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (selected != null && shown.indexOf(c) == selected) color else Color.Unspecified)
                        Text(Money.format(c.total, currency), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    AnimatedProgressBar(Money.fraction(c.total, total), color = color, height = 6.dp)
                    Row(Modifier.padding(top = 2.dp)) {
                        Text(Money.percentText(Money.percent(c.total, total)), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        val change = changeText(c.total, c.previousTotal)
                        val up = c.total > c.previousTotal
                        Text(
                            change?.let { "$it vs previous" } ?: if (c.previousTotal == 0L) "new" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                change == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                up == isExpense -> fc.expense
                                else -> fc.income
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetCard(report: ReportData, currency: String) {
    val fc = FinanceTheme.colors
    AppCard {
        Text("Budget performance", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        report.budgetLines.forEach { b ->
            val over = b.spent > b.budget
            Column(Modifier.padding(vertical = 6.dp)) {
                Row {
                    Text(b.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${Money.format(b.spent, currency)} / ${Money.format(b.budget, currency)}", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(4.dp))
                AnimatedProgressBar(Money.fraction(b.spent, b.budget), color = if (over) fc.expense else fc.income, height = 8.dp)
                Text(
                    if (over) "Over by ${Money.format(b.spent - b.budget, currency)}" else "${Money.format(b.budget - b.spent, currency)} left",
                    style = MaterialTheme.typography.labelSmall, color = if (over) fc.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
