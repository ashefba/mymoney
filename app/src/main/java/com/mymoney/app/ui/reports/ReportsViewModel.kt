package com.mymoney.app.ui.reports

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymoney.app.AppContainer
import com.mymoney.app.core.DailyClock
import com.mymoney.app.core.Dates
import com.mymoney.app.core.ReportBuilder
import com.mymoney.app.core.ReportData
import com.mymoney.app.core.ReportPeriod
import com.mymoney.app.core.Periods
import com.mymoney.app.data.db.Account
import com.mymoney.app.data.db.Category
import com.mymoney.app.data.prefs.AppSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ReportsState(
    val loading: Boolean = true,
    val currency: String = "EUR",
    val period: ReportPeriod = ReportPeriod.MONTH,
    val anchor: LocalDate = LocalDate.now(),
    val canGoNext: Boolean = false,
    val report: ReportData? = null,
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
)

private data class ReportSelection(
    val period: ReportPeriod,
    val anchor: LocalDate,
    val settings: AppSettings,
    val today: LocalDate,
)

class ReportsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val period = MutableStateFlow(ReportPeriod.MONTH)
    private val anchor = MutableStateFlow(Dates.today())
    private var followsCurrentPeriod = true

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private val selection = combine(period, anchor, container.settings.settings, DailyClock.dates()) { p, a, s, today ->
        ReportSelection(p, a, s, today)
    }

    val state: StateFlow<ReportsState> = combine(
        repo.transactions, repo.categories, repo.accounts, repo.budgets,
        selection,
    ) { txs, categories, accounts, budgets, selected ->
        val p = selected.period
        val a = selected.anchor
        ReportsState(
            loading = false,
            currency = selected.settings.currency,
            period = p,
            anchor = a,
            canGoNext = Periods.range(p, a).end.isBefore(selected.today),
            report = ReportBuilder.build(p, a, txs, categories, budgets),
            categories = categories,
            accounts = accounts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsState())

    init {
        viewModelScope.launch {
            DailyClock.dates().collect { today ->
                if (followsCurrentPeriod) anchor.value = today
            }
        }
    }

    fun setPeriod(p: ReportPeriod) {
        period.value = p
        anchor.value = Dates.today()
        followsCurrentPeriod = true
    }

    fun previous() {
        followsCurrentPeriod = false
        anchor.value = Periods.shift(period.value, anchor.value, -1)
    }

    fun next() {
        if (state.value.canGoNext) anchor.value = Periods.shift(period.value, anchor.value, 1)
        followsCurrentPeriod = Periods.range(period.value, anchor.value).contains(Dates.todayEpoch())
    }

    fun suggestedFileName(extension: String): String {
        val r = state.value.report ?: return "mymoney-report.$extension"
        val label = when (r.period) {
            ReportPeriod.WEEK -> "week-${Dates.iso(r.range.start)}"
            ReportPeriod.MONTH -> "${r.range.start.year}-${"%02d".format(r.range.start.monthValue)}"
            ReportPeriod.YEAR -> r.range.start.year.toString()
        }
        return "mymoney-report-$label.$extension"
    }

    fun exportPdf(uri: Uri) = export("PDF") { s, r -> container.exporter.writeReportPdf(uri, r, s.categories, s.currency) }

    fun exportCsv(uri: Uri) = export("CSV") { s, r -> container.exporter.writeReportCsv(uri, r, s.categories, s.accounts, s.currency) }

    private fun export(kind: String, block: suspend (ReportsState, ReportData) -> Unit) {
        val s = state.value
        val r = s.report ?: return
        viewModelScope.launch {
            val result = runCatching { block(s, r) }
            _messages.emit(
                if (result.isSuccess) "$kind report saved."
                else "Couldn't save the $kind: ${result.exceptionOrNull()?.message ?: "unknown error"}",
            )
        }
    }
}
