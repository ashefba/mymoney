package com.mymoney.app.ui.debts

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mymoney.app.core.Dates
import com.mymoney.app.core.DebtStatus
import com.mymoney.app.core.Money
import com.mymoney.app.core.Rates
import com.mymoney.app.data.db.Account
import com.mymoney.app.data.db.Debt
import com.mymoney.app.data.db.DebtEntryKind
import com.mymoney.app.data.db.DebtKind
import com.mymoney.app.ui.appViewModel
import com.mymoney.app.ui.components.AmountField
import com.mymoney.app.ui.components.AnimatedProgressBar
import com.mymoney.app.ui.components.AppCard
import com.mymoney.app.ui.components.AppTopBar
import com.mymoney.app.ui.components.ConfirmDialog
import com.mymoney.app.ui.components.DateField
import com.mymoney.app.ui.components.EmptyState
import com.mymoney.app.ui.components.IconBadge
import com.mymoney.app.ui.components.InfoNote
import com.mymoney.app.ui.components.LineChart
import com.mymoney.app.ui.components.LineSeries
import com.mymoney.app.ui.components.ListRow
import com.mymoney.app.ui.components.LoadingBox
import com.mymoney.app.ui.components.MoneyText
import com.mymoney.app.ui.components.SectionHeader
import com.mymoney.app.ui.components.SelectField
import com.mymoney.app.ui.components.StatRow
import com.mymoney.app.ui.theme.FinanceTheme
import java.time.YearMonth

private fun debtIcon(kind: DebtKind) = when (kind) {
    DebtKind.LOAN -> Icons.Rounded.AccountBalance
    DebtKind.BORROWED -> Icons.Rounded.Person
    DebtKind.LENT -> Icons.Rounded.Payments
}

@Composable
fun DebtsScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, onNew: () -> Unit) {
    val vm = appViewModel { DebtsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val fc = FinanceTheme.colors
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("Debts & loans", onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add debt") })
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding)); return@Scaffold
        }
        val empty = state.iOwe.isEmpty() && state.owedToMe.isEmpty() && state.closed.isEmpty()
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (empty) {
                item {
                    EmptyState(
                        Icons.Rounded.AccountBalance,
                        "No debts tracked",
                        "Track loans, money you borrowed from friends, or money others owe you. MyMoney shows what's left and when it will be paid off.",
                        action = { Button(onClick = onNew) { Text("Add a debt") } },
                    )
                }
            } else {
                item {
                    AppCard {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("You owe", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                MoneyText(state.totalOwed, state.currency, style = MaterialTheme.typography.headlineSmall, color = fc.expense)
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text("Owed to you", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                MoneyText(state.totalOwedToMe, state.currency, style = MaterialTheme.typography.headlineSmall, color = fc.income)
                            }
                        }
                        if (state.monthlyRepayments > 0) {
                            Spacer(Modifier.height(8.dp))
                            StatRow("Planned repayments per month", Money.format(state.monthlyRepayments, state.currency), bold = true)
                        }
                    }
                }
                debtSection("Money you owe", state.iOwe, state.currency, onOpen)
                debtSection("Money owed to you", state.owedToMe, state.currency, onOpen)
                debtSection("Closed", state.closed, state.currency, onOpen)
                item {
                    InfoNote("Repayments are split into principal and interest. Only interest counts as an expense (or income when you lent money) — principal just moves money, so it never inflates your spending.")
                }
            }
        }
    }
}

private fun LazyListScope.debtSection(title: String, items: List<DebtItem>, currency: String, onOpen: (Long) -> Unit) {
    if (items.isEmpty()) return
    item { SectionHeader(title) }
    items(items, key = { it.debt.id }) { DebtCard(it, currency) { onOpen(it.debt.id) } }
}

@Composable
private fun DebtCard(item: DebtItem, currency: String, onClick: () -> Unit) {
    val fc = FinanceTheme.colors
    val s = item.status
    val color = if (item.debt.kind.iOwe) fc.expense else fc.income
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(debtIcon(item.debt.kind), color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.debt.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(item.debt.kind.label, item.debt.counterparty, if (item.debt.annualRateBps > 0) Rates.format(item.debt.annualRateBps) else "")
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoneyText(s.outstanding, currency, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        AnimatedProgressBar(s.fraction, color = color)
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                s.outstanding == 0L -> "Fully repaid"
                s.projection != null -> "Paid off ${Dates.medium(s.projection.payoffDate)} at the current repayment"
                else -> s.projectionProblem ?: ""
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (s.projection == null && s.outstanding > 0) fc.warning else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --------------------------------------------------------------------------------------------- detail

@Composable
fun DebtDetailScreen(debtId: Long, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val vm = appViewModel(key = "debt-$debtId") { DebtDetailViewModel(it, debtId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var repaymentDialog by remember { mutableStateOf(false) }
    var increaseDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var selectedPoint by remember { mutableStateOf<Int?>(null) }
    val debt = state.debt
    val fc = FinanceTheme.colors

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(debt?.name ?: "Debt", onBack = onBack) {
                if (debt != null) {
                    IconButton(onClick = { onEdit(debt.id) }) { Icon(Icons.Rounded.Edit, "Edit") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete") }
                }
            }
        },
    ) { padding ->
        val s = state.status
        if (state.loading) {
            LoadingBox(Modifier.padding(padding)); return@Scaffold
        }
        if (debt == null || s == null) {
            EmptyState(Icons.Rounded.AccountBalance, "Debt not found", "It may have been deleted.", Modifier.padding(padding))
            return@Scaffold
        }
        val iOwe = debt.kind.iOwe
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    Text(if (iOwe) "Still to repay" else "Still owed to you", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(s.outstanding, state.currency, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(10.dp))
                    AnimatedProgressBar(s.fraction, color = if (iOwe) fc.expense else fc.income, height = 12.dp)
                    Spacer(Modifier.height(10.dp))
                    StatRow("Original amount", Money.format(debt.principalCents, state.currency))
                    if (s.increases > 0) StatRow("Added since", Money.format(s.increases, state.currency))
                    StatRow("Principal repaid", Money.format(s.principalRepaid, state.currency))
                    StatRow("Interest paid", Money.format(s.interestPaid, state.currency))
                    StatRow("Interest rate", if (debt.annualRateBps > 0) "${Rates.format(debt.annualRateBps)} per year" else "None")
                    if (s.accruedInterest > 0) {
                        StatRow("Interest accrued since ${Dates.medium(s.lastInterestDate)}", Money.format(s.accruedInterest, state.currency))
                    }
                    StatRow("Started", Dates.medium(debt.startDate))
                    if (debt.counterparty.isNotBlank()) StatRow(if (iOwe) "Lender" else "Borrower", debt.counterparty)
                    Spacer(Modifier.height(12.dp))
                    if (debt.closed) {
                        OutlinedButton(onClick = { vm.setClosed(false) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Replay, null); Spacer(Modifier.width(6.dp)); Text("Reopen")
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { repaymentDialog = true }, modifier = Modifier.weight(1f), enabled = s.outstanding > 0) {
                                Text(if (iOwe) "Record repayment" else "Record payment")
                            }
                            OutlinedButton(onClick = { increaseDialog = true }, modifier = Modifier.weight(1f)) {
                                Text(if (iOwe) "Borrowed more" else "Lent more")
                            }
                        }
                        if (s.outstanding == 0L) {
                            TextButton(onClick = { vm.setClosed(true) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text("Mark as closed")
                            }
                        }
                    }
                }
            }
            if (s.outstanding > 0) {
                item { PayoffCard(debt, s, state.currency, selectedPoint) { selectedPoint = it } }
            }
            item { SectionHeader("History") }
            if (state.entries.isEmpty()) {
                item { Text("No repayments recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.entries, key = { it.id }) { e ->
                var confirm by remember { mutableStateOf(false) }
                val isRepayment = e.kind == DebtEntryKind.REPAYMENT
                AppCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    ListRow(
                        title = if (isRepayment) "Repayment" else if (iOwe) "Borrowed more" else "Lent more",
                        subtitle = listOf(
                            Dates.medium(e.date),
                            if (isRepayment && e.interestCents > 0) {
                                "${Money.format(e.principalCents, state.currency)} principal + ${Money.format(e.interestCents, state.currency)} interest"
                            } else "",
                            if (e.accountId == null) "not linked to an account" else "",
                            e.note,
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        icon = if (isRepayment) Icons.Rounded.CheckCircle else Icons.Rounded.Add,
                        iconTint = if (isRepayment) fc.income else fc.warning,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MoneyText(e.principalCents + e.interestCents, state.currency, style = MaterialTheme.typography.bodyLarge)
                                IconButton(onClick = { confirm = true }) {
                                    Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                    )
                }
                if (confirm) {
                    ConfirmDialog(
                        "Delete this entry?",
                        "The balance and any account transactions recorded with it will be removed.",
                        "Delete", onConfirm = { vm.deleteEntry(e) }, onDismiss = { confirm = false }, destructive = true,
                    )
                }
            }
        }
    }

    if (repaymentDialog && state.status != null) {
        RepaymentDialog(
            currency = state.currency,
            outstanding = state.status!!.outstanding,
            defaultPayment = debt?.monthlyPaymentCents ?: 0,
            accounts = state.accounts,
            defaultAccountId = debt?.accountId,
            hasInterest = (debt?.annualRateBps ?: 0) > 0,
            suggest = vm::suggestSplit,
            onSave = vm::addRepayment,
            onDismiss = { repaymentDialog = false },
        )
    }
    if (increaseDialog) {
        IncreaseDialog(
            currency = state.currency,
            iOwe = debt?.kind?.iOwe ?: true,
            accounts = state.accounts,
            defaultAccountId = debt?.accountId,
            onSave = vm::addIncrease,
            onDismiss = { increaseDialog = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete debt?",
            "The debt and its repayment history will be removed. Account transactions already recorded stay in your history.",
            "Delete", onConfirm = { vm.delete(onBack) }, onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
}

@Composable
private fun PayoffCard(debt: Debt, s: DebtStatus, currency: String, selected: Int?, onSelect: (Int?) -> Unit) {
    val fc = FinanceTheme.colors
    AppCard {
        Text("Payoff projection", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val p = s.projection
        if (p == null) {
            Text(s.projectionProblem ?: "", color = fc.warning, style = MaterialTheme.typography.bodyMedium)
        } else {
            val start = YearMonth.now()
            val step = ((p.balances.size + 23) / 24).coerceAtLeast(1)
            val sampled = p.balances.filterIndexed { i, _ -> i % step == 0 || i == p.balances.lastIndex }
            val indices = p.balances.indices.filter { it % step == 0 || it == p.balances.lastIndex }
            val labels = indices.map { Dates.shortMonthYear(start.plusMonths(it.toLong())) }
            LineChart(
                series = listOf(LineSeries("Balance", sampled, if (debt.kind.iOwe) fc.expense else fc.income)),
                labels = labels,
                selectedIndex = selected,
                onSelect = onSelect,
            )
            selected?.let { i ->
                if (i in sampled.indices) {
                    Text("${labels[i]}: ${Money.format(sampled[i], currency)} left", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
            StatRow("Monthly repayment", Money.format(debt.monthlyPaymentCents, currency))
            StatRow("Payments left", p.months.toString())
            StatRow("Debt-free by", Dates.medium(p.payoffDate), bold = true)
            StatRow("Interest still to pay", Money.format(p.totalInterest, currency))
        }
        debt.targetPayoffDate?.let { t ->
            Spacer(Modifier.height(6.dp))
            StatRow("Target date", Dates.medium(t))
            s.requiredMonthlyForTarget?.let { req ->
                StatRow("Needed per month to hit it", Money.format(req, currency), bold = true,
                    valueColor = if (req > debt.monthlyPaymentCents) fc.warning else fc.income)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Assumes the rate stays at ${Rates.format(debt.annualRateBps)} with interest charged monthly and the same repayment every month.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RepaymentDialog(
    currency: String,
    outstanding: Long,
    defaultPayment: Long,
    accounts: List<Account>,
    defaultAccountId: Long?,
    hasInterest: Boolean,
    suggest: (Long, Long) -> Pair<Long, Long>,
    onSave: (Long, Long, Long, Long?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var date by remember { mutableStateOf(Dates.todayEpoch()) }
    var totalText by remember {
        mutableStateOf(if (defaultPayment > 0) Money.toInput(minOf(defaultPayment, outstanding)) else "")
    }
    var interestText by remember { mutableStateOf("") }
    var interestEdited by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf(accounts.firstOrNull { it.id == defaultAccountId } ?: accounts.firstOrNull()) }
    var linkAccount by remember { mutableStateOf(accounts.isNotEmpty()) }
    var note by remember { mutableStateOf("") }

    val total = Money.parseToCents(totalText)
    LaunchedEffect(total, date, interestEdited) {
        if (!interestEdited && hasInterest && total != null) {
            interestText = Money.toInput(suggest(total, date).second)
        }
    }
    val interest = if (interestText.isBlank()) 0L else Money.parseToCents(interestText)
    val principal = if (total != null && interest != null) total - interest else null
    val error = when {
        total == null || total <= 0 -> null
        interest == null -> "Enter the interest part like 4.20"
        principal == null || principal < 0 -> "Interest can't be more than the payment."
        principal > outstanding -> "That's more than the ${Money.format(outstanding, currency)} still owed."
        else -> null
    }
    val valid = total != null && total > 0 && error == null && principal != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record repayment") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AmountField(totalText, { totalText = it }, "Total paid", currency, isError = error != null, supportingText = error)
                DateField("Date", date, { if (it != null) date = it })
                if (hasInterest) {
                    AmountField(
                        interestText, { interestText = it; interestEdited = true }, "Of which interest", currency,
                        supportingText = "Suggested from the rate and days since the last payment. Change it to match your statement.",
                    )
                }
                if (principal != null && principal >= 0 && total != null && total > 0) {
                    StatRow("Reduces the balance by", Money.format(principal, currency), bold = true)
                }
                if (accounts.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Record in an account", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = linkAccount, onCheckedChange = { linkAccount = it })
                    }
                    if (linkAccount) SelectField("Account", accounts, account, { it.name }, { account = it })
                }
                OutlinedTextField(note, { if (it.length <= 60) note = it }, label = { Text("Note (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                if (principal != null && interest != null) {
                    onSave(date, principal, interest, if (linkAccount) account?.id else null, note.trim())
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun IncreaseDialog(
    currency: String,
    iOwe: Boolean,
    accounts: List<Account>,
    defaultAccountId: Long?,
    onSave: (Long, Long, Long?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var date by remember { mutableStateOf(Dates.todayEpoch()) }
    var amountText by remember { mutableStateOf("") }
    var account by remember { mutableStateOf(accounts.firstOrNull { it.id == defaultAccountId } ?: accounts.firstOrNull()) }
    var linkAccount by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val amount = Money.parseToCents(amountText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (iOwe) "Borrowed more" else "Lent more") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AmountField(amountText, { amountText = it }, "Amount", currency)
                DateField("Date", date, { if (it != null) date = it })
                if (accounts.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (iOwe) "Money arrived in an account" else "Money left an account",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = linkAccount, onCheckedChange = { linkAccount = it })
                    }
                    if (linkAccount) SelectField("Account", accounts, account, { it.name }, { account = it })
                }
                OutlinedTextField(note, { if (it.length <= 60) note = it }, label = { Text("Note (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Text(
                    "Leave the account switch off for interest or fees added to the balance without money moving.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = amount != null && amount > 0, onClick = {
                if (amount != null) onSave(date, amount, if (linkAccount) account?.id else null, note.trim())
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// --------------------------------------------------------------------------------------------- editor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDebtScreen(debtId: Long, onDone: () -> Unit) {
    val vm = appViewModel(key = "edit-debt-$debtId") { EditDebtViewModel(it, debtId) }
    LaunchedEffect(vm.done) { if (vm.done) onDone() }
    val kinds = DebtKind.entries
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(if (vm.isNew) "New debt" else "Edit debt", onBack = onDone) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                kinds.forEachIndexed { i, k ->
                    SegmentedButton(selected = vm.kind == k, onClick = { vm.kind = k }, shape = SegmentedButtonDefaults.itemShape(i, kinds.size)) {
                        Text(k.label)
                    }
                }
            }
            Text(vm.kind.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                vm.name, { if (it.length <= 40) vm.name = it }, label = { Text("Name") }, singleLine = true,
                placeholder = { Text(if (vm.kind == DebtKind.LOAN) "Car loan" else "Money from Alex") },
                modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            OutlinedTextField(
                vm.counterparty, { if (it.length <= 40) vm.counterparty = it },
                label = { Text(if (vm.kind.iOwe) "Lender (optional)" else "Who owes you (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            AmountField(vm.principalText, { vm.principalText = it }, if (vm.kind.iOwe) "Amount borrowed" else "Amount lent", vm.currency,
                supportingText = if (vm.isNew) "If you've already repaid some, enter the original amount and record repayments, or enter what's left today." else null)
            DateField("Start date", vm.startDate, { if (it != null) vm.startDate = it })
            OutlinedTextField(
                vm.rateText, { vm.rateText = it }, label = { Text("Interest rate per year (optional)") }, singleLine = true,
                suffix = { Text("%") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            AmountField(vm.monthlyText, { vm.monthlyText = it }, "Monthly repayment (optional)", vm.currency,
                supportingText = "Used to project when it will be paid off.")
            DateField("Target payoff date (optional)", vm.targetDate, { vm.targetDate = it }, clearable = true, placeholder = "No target")
            if (vm.accounts.isNotEmpty()) {
                SelectField("Linked account (optional)", vm.accounts, vm.accounts.firstOrNull { it.id == vm.accountId }, { it.name },
                    { vm.accountId = it.id }, placeholder = "None")
                if (vm.isNew) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (vm.kind.iOwe) "Add the borrowed money to this account" else "Take the lent money from this account",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "Turn on only if this happened recently and isn't already in your balance. It's not counted as income or spending.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = vm.recordInAccount, onCheckedChange = { vm.recordInAccount = it })
                    }
                }
            }
            OutlinedTextField(vm.notes, { if (it.length <= 200) vm.notes = it }, label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(), minLines = 2)
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save", fontWeight = FontWeight.SemiBold) }
        }
    }
}
