package com.mymoney.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymoney.app.AppContainer
import com.mymoney.app.core.Dates
import com.mymoney.app.core.Money
import com.mymoney.app.core.expenseTotal
import com.mymoney.app.core.incomeTotal
import com.mymoney.app.data.db.Account
import com.mymoney.app.data.db.Category
import com.mymoney.app.data.db.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

enum class TypeFilter(val label: String) { ALL("All"), EXPENSE("Expenses"), INCOME("Income"), TRANSFER("Transfers") }

data class TxFilters(
    val query: String = "",
    val type: TypeFilter = TypeFilter.ALL,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    /** Null shows all months. */
    val month: YearMonth? = YearMonth.now(),
)

data class DayGroup(val epochDay: Long, val items: List<TxItem>, val net: Long)

data class TransactionsState(
    val loading: Boolean = true,
    val currency: String = "EUR",
    val filters: TxFilters = TxFilters(),
    val groups: List<DayGroup> = emptyList(),
    val income: Long = 0,
    val expense: Long = 0,
    val count: Int = 0,
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val hasAnyTransactions: Boolean = false,
)

class TransactionsViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val filters = MutableStateFlow(TxFilters())

    val state: StateFlow<TransactionsState> = combine(
        repo.transactions, repo.categories, repo.accounts, container.settings.settings, filters,
    ) { txs, cats, accs, settings, f ->
        val q = f.query.trim().lowercase()
        val catMap = cats.associateBy { it.id }
        val accMap = accs.associateBy { it.id }
        val filtered = txs.filter { t ->
            val monthOk = f.month == null || t.date in Dates.monthStart(f.month)..Dates.monthEnd(f.month)
            val typeOk = when (f.type) {
                TypeFilter.ALL -> true
                TypeFilter.EXPENSE -> t.type == TransactionType.EXPENSE
                TypeFilter.INCOME -> t.type == TransactionType.INCOME
                TypeFilter.TRANSFER -> t.type == TransactionType.TRANSFER || t.type == TransactionType.DEBT_IN || t.type == TransactionType.DEBT_OUT
            }
            val catOk = f.categoryId == null || t.categoryId == f.categoryId
            val accOk = f.accountId == null || t.accountId == f.accountId || t.toAccountId == f.accountId
            val queryOk = q.isEmpty() || t.description.lowercase().contains(q) || t.notes.lowercase().contains(q) ||
                (t.categoryId?.let { catMap[it]?.name?.lowercase()?.contains(q) } == true) ||
                (accMap[t.accountId]?.name?.lowercase()?.contains(q) == true) ||
                Money.toInput(t.amountCents).contains(q.replace(',', '.'))
            monthOk && typeOk && catOk && accOk && queryOk
        }
        val items = buildTxItems(filtered, cats, accs)
        val groups = items.groupBy { it.tx.date }.map { (day, list) ->
            DayGroup(day, list, list.map { it.tx }.incomeTotal() - list.map { it.tx }.expenseTotal())
        }.sortedByDescending { it.epochDay }
        TransactionsState(
            loading = false,
            currency = settings.currency,
            filters = f,
            groups = groups,
            income = filtered.incomeTotal(),
            expense = filtered.expenseTotal(),
            count = filtered.size,
            categories = cats,
            accounts = accs,
            hasAnyTransactions = txs.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsState())

    fun setQuery(q: String) { filters.value = filters.value.copy(query = q) }
    fun setType(t: TypeFilter) { filters.value = filters.value.copy(type = t) }
    fun setCategory(id: Long?) { filters.value = filters.value.copy(categoryId = id) }
    fun setAccount(id: Long?) { filters.value = filters.value.copy(accountId = id) }
    fun setMonth(m: YearMonth?) { filters.value = filters.value.copy(month = m) }
    fun previousMonth() { filters.value.month?.let { setMonth(it.minusMonths(1)) } }
    fun nextMonth() { filters.value.month?.let { setMonth(it.plusMonths(1)) } }
    fun clearFilters() { filters.value = TxFilters(month = filters.value.month) }
}
