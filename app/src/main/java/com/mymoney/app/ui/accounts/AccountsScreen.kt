package com.mymoney.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mymoney.app.AppContainer
import com.mymoney.app.core.Money
import com.mymoney.app.data.db.Account
import com.mymoney.app.data.db.AccountType
import com.mymoney.app.ui.appViewModel
import com.mymoney.app.ui.components.AmountField
import com.mymoney.app.ui.components.AppCard
import com.mymoney.app.ui.components.AppIcons
import com.mymoney.app.ui.components.AppTopBar
import com.mymoney.app.ui.components.ColorPicker
import com.mymoney.app.ui.components.ConfirmDialog
import com.mymoney.app.ui.components.EmptyState
import com.mymoney.app.ui.components.InfoNote
import com.mymoney.app.ui.components.ListRow
import com.mymoney.app.ui.components.MoneyText
import com.mymoney.app.ui.components.SectionHeader
import com.mymoney.app.ui.components.SelectField
import com.mymoney.app.ui.components.paletteColor
import com.mymoney.app.ui.home.AccountBalance
import com.mymoney.app.ui.theme.FinanceTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn

data class AccountsState(
    val loading: Boolean = true,
    val currency: String = "EUR",
    val active: List<AccountBalance> = emptyList(),
    val archived: List<AccountBalance> = emptyList(),
    val assets: Long = 0,
    val liabilities: Long = 0,
)

class AccountsViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository
    val state: StateFlow<AccountsState> = combine(repo.accounts, repo.balances, container.settings.settings) { accs, bal, s ->
        val list = accs.map { AccountBalance(it, bal[it.id] ?: it.openingBalanceCents) }
        val active = list.filter { !it.account.archived }
        AccountsState(
            loading = false,
            currency = s.currency,
            active = active,
            archived = list.filter { it.account.archived },
            assets = active.filter { it.balance > 0 }.sumOf { it.balance },
            liabilities = active.filter { it.balance < 0 }.sumOf { -it.balance },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsState())
}

@Composable
fun AccountsScreen(onBack: () -> Unit, onEdit: (Long) -> Unit, onTransfer: () -> Unit) {
    val vm = appViewModel { AccountsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("Accounts", onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onEdit(0) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New account") })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AppCard {
                    Text("Net worth in accounts", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(state.assets - state.liabilities, state.currency, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("You have", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MoneyText(state.assets, state.currency, color = FinanceTheme.colors.income)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("You owe on cards", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MoneyText(state.liabilities, state.currency, color = FinanceTheme.colors.expense)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onTransfer, enabled = state.active.size >= 2) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Transfer between accounts")
                    }
                }
            }
            if (!state.loading && state.active.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.AccountBalance,
                        "No accounts yet",
                        "Add the places you keep money: your current account, savings, cash wallet or credit card.",
                        action = { Button(onClick = { onEdit(0) }) { Text("Add account") } },
                    )
                }
            }
            items(state.active, key = { it.account.id }) { ab ->
                AppCard(onClick = { onEdit(ab.account.id) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    ListRow(
                        title = ab.account.name,
                        subtitle = ab.account.type.label + if (!ab.account.includeInSpendable) " · not in safe-to-spend" else "",
                        icon = AppIcons.account(ab.account.type),
                        iconTint = paletteColor(ab.account.colorIndex),
                        trailing = {
                            MoneyText(ab.balance, state.currency,
                                color = if (ab.balance < 0) FinanceTheme.colors.expense else MaterialTheme.colorScheme.onSurface)
                        },
                    )
                }
            }
            if (state.archived.isNotEmpty()) {
                item { SectionHeader("Archived") }
                items(state.archived, key = { it.account.id }) { ab ->
                    AppCard(onClick = { onEdit(ab.account.id) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        ListRow(ab.account.name, "Archived · kept for history", AppIcons.account(ab.account.type),
                            MaterialTheme.colorScheme.outline, trailing = { MoneyText(ab.balance, state.currency) })
                    }
                }
            }
            item {
                InfoNote("Balances are calculated from each account's opening balance plus every income, expense and transfer you record. Transfers move money between accounts and never count as spending.")
            }
        }
    }
}

// --------------------------------------------------------------------------------------------- editor

class EditAccountViewModel(container: AppContainer, private val id: Long) : ViewModel() {
    private val repo = container.repository
    var currency by mutableStateOf("EUR")
        private set
    var name by mutableStateOf("")
    var type by mutableStateOf(AccountType.CURRENT)
    var openingText by mutableStateOf("")
    var openingNegative by mutableStateOf(false)
    var includeInSpendable by mutableStateOf(true)
    var colorIndex by mutableStateOf(0)
    var archived by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var done by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    private var original: Account? = null
    val isNew get() = id == 0L

    init {
        viewModelScope.launch {
            currency = container.settings.current().currency
            if (id != 0L) {
                repo.allAccounts().firstOrNull { it.id == id }?.let { a ->
                    original = a
                    name = a.name
                    type = a.type
                    openingText = Money.toInput(kotlin.math.abs(a.openingBalanceCents))
                    openingNegative = a.openingBalanceCents < 0
                    includeInSpendable = a.includeInSpendable
                    colorIndex = a.colorIndex
                    archived = a.archived
                }
            } else {
                colorIndex = repo.allAccounts().size
            }
            loaded = true
        }
    }

    fun changeType(t: AccountType) {
        type = t
        includeInSpendable = t.spendableByDefault
        if (t == AccountType.CREDIT_CARD) openingNegative = true
    }

    fun save() {
        val opening = if (openingText.isBlank()) 0L else Money.parseToCents(openingText)
        error = when {
            name.isBlank() -> "Give the account a name."
            opening == null -> "Enter the balance like 1250.00"
            else -> null
        }
        if (error != null || opening == null) return
        val signed = if (openingNegative) -opening else opening
        viewModelScope.launch {
            val base = original ?: Account(name = name.trim(), type = type, openingBalanceCents = signed)
            repo.saveAccount(
                base.copy(
                    name = name.trim(), type = type, openingBalanceCents = signed,
                    includeInSpendable = includeInSpendable, colorIndex = colorIndex, archived = archived,
                )
            )
            done = true
        }
    }

    fun delete(onArchived: () -> Unit) {
        val a = original ?: return
        viewModelScope.launch {
            if (repo.deleteOrArchiveAccount(a)) onArchived()
            done = true
        }
    }
}

@Composable
fun EditAccountScreen(accountId: Long, onDone: () -> Unit) {
    val vm = appViewModel(key = "acc-$accountId") { EditAccountViewModel(it, accountId) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(vm.done) { if (vm.done) onDone() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(if (vm.isNew) "New account" else "Edit account", onBack = onDone) {
                if (!vm.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = vm.name, onValueChange = { if (it.length <= 40) vm.name = it },
                label = { Text("Account name") }, placeholder = { Text("e.g. Main bank account") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            SelectField("Type", AccountType.entries.toList(), vm.type, { "${it.label} — ${it.description}" }, vm::changeType)
            AmountField(
                vm.openingText, { vm.openingText = it },
                label = if (vm.isNew) "Current balance" else "Opening balance",
                currency = vm.currency,
                supportingText = if (vm.isNew) "What's in this account today. Leave empty for zero." else
                    "The balance before your first recorded transaction.",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (vm.type == AccountType.CREDIT_CARD) "This is money I owe" else "Balance is negative (overdrawn)", style = MaterialTheme.typography.bodyLarge)
                    Text("Turn on if the balance is below zero.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = vm.openingNegative, onCheckedChange = { vm.openingNegative = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Count in safe-to-spend", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Include this account when working out how much you can spend each day. Usually on for current accounts, cash and credit cards; off for savings.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = vm.includeInSpendable, onCheckedChange = { vm.includeInSpendable = it })
            }
            if (!vm.isNew) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Archived (hidden from pickers)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = vm.archived, onCheckedChange = { vm.archived = it })
                }
            }
            Text("Colour", style = MaterialTheme.typography.labelLarge)
            ColorPicker(vm.colorIndex) { vm.colorIndex = it }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = vm::save, enabled = vm.loaded, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save account") }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete account?",
            "If this account has transactions it will be archived instead, so your history and reports stay correct.",
            "Delete",
            onConfirm = { vm.delete {} },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}
