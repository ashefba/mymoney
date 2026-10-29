package com.mymoney.app.ui.transactions

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mymoney.app.core.Dates
import com.mymoney.app.core.Money
import com.mymoney.app.data.db.CategoryKind
import com.mymoney.app.ui.appViewModel
import com.mymoney.app.ui.components.AppCard
import com.mymoney.app.ui.components.EmptyState
import com.mymoney.app.ui.components.LoadingBox
import com.mymoney.app.ui.components.PeriodSelector
import com.mymoney.app.ui.theme.FinanceTheme
import java.time.YearMonth

@Composable
fun TransactionsScreen(onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val vm = appViewModel { TransactionsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val f = state.filters

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add") },
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding)); return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Transactions", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }
            item {
                OutlinedTextField(
                    value = f.query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("Search description, notes, category or amount") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (f.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                if (f.month != null) {
                    PeriodSelector(Dates.month(f.month), vm::previousMonth, vm::nextMonth)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = f.month == null,
                        onClick = { vm.setMonth(if (f.month == null) YearMonth.now() else null) },
                        label = { Text("All time") },
                    )
                    TypeFilter.entries.forEach { t ->
                        FilterChip(selected = f.type == t, onClick = { vm.setType(t) }, label = { Text(t.label) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val cats = state.categories.filter { !it.archived }.sortedBy { if (it.kind == CategoryKind.EXPENSE) 0 else 1 }
                    MenuChip(
                        label = f.categoryId?.let { id -> state.categories.firstOrNull { it.id == id }?.name } ?: "Any category",
                        selected = f.categoryId != null,
                        options = listOf<Pair<Long?, String>>(null to "Any category") + cats.map { it.id to it.name },
                        onSelect = vm::setCategory,
                    )
                    MenuChip(
                        label = f.accountId?.let { id -> state.accounts.firstOrNull { it.id == id }?.name } ?: "Any account",
                        selected = f.accountId != null,
                        options = listOf<Pair<Long?, String>>(null to "Any account") + state.accounts.map { it.id to it.name },
                        onSelect = vm::setAccount,
                    )
                    if (f.query.isNotEmpty() || f.type != TypeFilter.ALL || f.categoryId != null || f.accountId != null) {
                        TextButton(onClick = vm::clearFilters) { Text("Clear filters") }
                    }
                }
            }
            item {
                AppCard {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Money in", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Money.format(state.income, state.currency), style = MaterialTheme.typography.titleMedium, color = FinanceTheme.colors.income)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Money out", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Money.format(state.expense, state.currency), style = MaterialTheme.typography.titleMedium, color = FinanceTheme.colors.expense)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Entries", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.count.toString(), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            if (state.groups.isEmpty()) {
                item {
                    if (!state.hasAnyTransactions) {
                        EmptyState(
                            icon = Icons.Rounded.ReceiptLong,
                            title = "No transactions yet",
                            message = "Record what you spend and earn. Transfers between your own accounts are never counted as spending.",
                            action = { Button(onClick = onAdd) { Text("Add your first transaction") } },
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Rounded.SearchOff,
                            title = "Nothing matches",
                            message = "Try another search, a different month or clear the filters.",
                        )
                    }
                }
            }
            state.groups.forEach { group ->
                item(key = "h${group.epochDay}") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Dates.relative(group.epochDay), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (group.net != 0L) {
                            Text(Money.format(group.net, state.currency, showPlus = true), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item(key = "g${group.epochDay}") {
                    AppCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        group.items.forEach { item ->
                            TransactionRow(item, state.currency, onClick = { onOpen(item.tx.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuChip(label: String, selected: Boolean, options: List<Pair<Long?, String>>, onSelect: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.ExpandMore, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); open = false })
            }
        }
    }
}
