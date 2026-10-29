package com.mymoney.app.ui.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mymoney.app.core.Dates
import com.mymoney.app.core.Money
import com.mymoney.app.data.db.Account
import com.mymoney.app.data.db.AccountType
import com.mymoney.app.ui.appViewModel
import com.mymoney.app.ui.components.AmountField
import com.mymoney.app.ui.components.AnimatedProgressBar
import com.mymoney.app.ui.components.AppCard
import com.mymoney.app.ui.components.AppIcons
import com.mymoney.app.ui.components.AppTopBar
import com.mymoney.app.ui.components.ColorPicker
import com.mymoney.app.ui.components.ConfirmDialog
import com.mymoney.app.ui.components.DateField
import com.mymoney.app.ui.components.EmptyState
import com.mymoney.app.ui.components.IconBadge
import com.mymoney.app.ui.components.IconPicker
import com.mymoney.app.ui.components.InfoNote
import com.mymoney.app.ui.components.ListRow
import com.mymoney.app.ui.components.LoadingBox
import com.mymoney.app.ui.components.MoneyText
import com.mymoney.app.ui.components.SectionHeader
import com.mymoney.app.ui.components.SelectField
import com.mymoney.app.ui.components.StatRow
import com.mymoney.app.ui.components.paletteColor
import com.mymoney.app.ui.home.GoalItem
import com.mymoney.app.ui.theme.FinanceTheme

@Composable
fun SavingsScreen(onOpenGoal: (Long) -> Unit, onNewGoal: () -> Unit) {
    val vm = appViewModel { SavingsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewGoal, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New goal") })
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding)); return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Savings goals", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp)) }
            if (state.active.isNotEmpty()) {
                item {
                    AppCard {
                        Text("Saved towards your goals", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.Bottom) {
                            MoneyText(state.totalSaved, state.currency, style = MaterialTheme.typography.headlineMedium)
                            Text(" of ${Money.format(state.totalTarget, state.currency)}", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        AnimatedProgressBar(Money.fraction(state.totalSaved, state.totalTarget), height = 12.dp)
                        Spacer(Modifier.height(10.dp))
                        if (state.requiredMonthly > 0) {
                            StatRow("Needed per month to hit target dates", Money.format(state.requiredMonthly, state.currency), bold = true)
                        }
                        StatRow("Balance in savings accounts", Money.format(state.savingsAccountsBalance, state.currency))
                    }
                }
            } else {
                item {
                    EmptyState(
                        Icons.Rounded.Savings,
                        "Start your first goal",
                        "An emergency fund, a holiday, a car or a house deposit — set a target and MyMoney tells you how much to put aside each month.",
                        action = { Button(onClick = onNewGoal) { Text("Create a goal") } },
                    )
                }
            }
            items(state.active, key = { it.goal.id }) { g -> GoalCard(g, state.currency) { onOpenGoal(g.goal.id) } }
            if (state.archived.isNotEmpty()) {
                item { SectionHeader("Completed & archived") }
                items(state.archived, key = { it.goal.id }) { g -> GoalCard(g, state.currency) { onOpenGoal(g.goal.id) } }
            }
            item {
                InfoNote("Goal contributions mark money you already have as set aside. They are not income and are not counted twice — your account balances only change if you also record a transfer.")
            }
        }
    }
}

@Composable
private fun GoalCard(item: GoalItem, currency: String, onClick: () -> Unit) {
    val p = item.progress
    val color = paletteColor(item.goal.colorIndex)
    val fc = FinanceTheme.colors
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcons.category(item.goal.iconKey), color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.goal.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.goal.targetDate?.let { "Target ${Dates.medium(it)}" } ?: "No target date",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Money.percentText(Money.percent(p.saved, p.target)), style = MaterialTheme.typography.titleMedium, color = color)
        }
        Spacer(Modifier.height(12.dp))
        AnimatedProgressBar(p.fraction, color = color, height = 10.dp)
        Spacer(Modifier.height(8.dp))
        Row {
            Text("${Money.format(p.saved, currency)} of ${Money.format(p.target, currency)}",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            when {
                p.reached -> Text("Goal reached!", style = MaterialTheme.typography.labelMedium, color = fc.income)
                p.requiredMonthly != null -> Text("${Money.format(p.requiredMonthly, currency)}/month needed",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (p.onTrack == false) fc.warning else MaterialTheme.colorScheme.onSurfaceVariant)
                p.estimatedCompletion != null -> Text("Done by ${Dates.medium(p.estimatedCompletion)}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// --------------------------------------------------------------------------------------------- detail

@Composable
fun GoalDetailScreen(goalId: Long, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val vm = appViewModel(key = "goal-$goalId") { GoalDetailViewModel(it, goalId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var contributionDialog by remember { mutableStateOf<Boolean?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val goal = state.goal
    val fc = FinanceTheme.colors

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(goal?.name ?: "Goal", onBack = onBack) {
                if (goal != null) {
                    IconButton(onClick = { onEdit(goal.id) }) { Icon(Icons.Rounded.Edit, "Edit") }
                    IconButton(onClick = { vm.setArchived(!goal.archived) }) {
                        Icon(if (goal.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, if (goal.archived) "Restore" else "Archive")
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete") }
                }
            }
        },
    ) { padding ->
        val p = state.progress
        if (state.loading) {
            LoadingBox(Modifier.padding(padding)); return@Scaffold
        }
        if (goal == null || p == null) {
            EmptyState(Icons.Rounded.Savings, "Goal not found", "It may have been deleted.", Modifier.padding(padding))
            return@Scaffold
        }
        val color = paletteColor(goal.colorIndex)
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(AppIcons.category(goal.iconKey), color, size = 52.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            MoneyText(p.saved, state.currency, style = MaterialTheme.typography.headlineMedium)
                            Text("saved of ${Money.format(p.target, state.currency)}", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    AnimatedProgressBar(p.fraction, color = color, height = 14.dp)
                    Spacer(Modifier.height(14.dp))
                    StatRow("Still to save", Money.format(p.remaining, state.currency), bold = true)
                    StatRow("Target date", goal.targetDate?.let { Dates.medium(it) } ?: "Not set")
                    p.monthsLeft?.let { StatRow("Months left", it.toString()) }
                    p.requiredMonthly?.let { StatRow("Required monthly saving", Money.format(it, state.currency), bold = true) }
                    StatRow("Your recent pace", "${Money.format(p.averageMonthly, state.currency)} / month")
                    StatRow(
                        "Estimated completion",
                        when {
                            p.reached -> "Reached 🎉"
                            p.estimatedCompletion != null -> Dates.medium(p.estimatedCompletion)
                            else -> "Add contributions to estimate"
                        },
                    )
                    when (p.onTrack) {
                        true -> if (!p.reached) Text("You're on track at your current pace.", color = fc.income, style = MaterialTheme.typography.bodySmall)
                        false -> Text(
                            "At your current pace you'll miss the target date. Saving ${Money.format(p.requiredMonthly ?: 0, state.currency)} a month gets you there.",
                            color = fc.warning, style = MaterialTheme.typography.bodySmall,
                        )
                        null -> Unit
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { contributionDialog = false }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add money")
                        }
                        OutlinedButton(onClick = { contributionDialog = true }, modifier = Modifier.weight(1f), enabled = p.saved > 0) {
                            Icon(Icons.Rounded.Remove, null); Spacer(Modifier.width(6.dp)); Text("Withdraw")
                        }
                    }
                }
            }
            item { SectionHeader("History") }
            if (state.contributions.isEmpty() && goal.initialSavedCents == 0L) {
                item { Text("No contributions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.contributions, key = { it.id }) { c ->
                var confirm by remember { mutableStateOf(false) }
                AppCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    ListRow(
                        title = if (c.amountCents >= 0) "Added" else "Withdrawn",
                        subtitle = listOf(Dates.medium(c.date), c.note, if (c.transferTransactionId != null) "with transfer" else "")
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        icon = if (c.amountCents >= 0) Icons.Rounded.Add else Icons.Rounded.Remove,
                        iconTint = if (c.amountCents >= 0) fc.income else fc.expense,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MoneyText(c.amountCents, state.currency, showPlus = true, style = MaterialTheme.typography.bodyLarge)
                                IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        },
                    )
                }
                if (confirm) {
                    ConfirmDialog(
                        "Delete this entry?",
                        if (c.transferTransactionId != null) "The transfer recorded with it will be deleted too." else "The goal's saved amount will be updated.",
                        "Delete", onConfirm = { vm.deleteContribution(c) }, onDismiss = { confirm = false }, destructive = true,
                    )
                }
            }
            if (goal.initialSavedCents != 0L) {
                item {
                    AppCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        ListRow("Already saved when the goal was created", null, Icons.Rounded.Savings, color,
                            trailing = { MoneyText(goal.initialSavedCents, state.currency, style = MaterialTheme.typography.bodyLarge) })
                    }
                }
            }
        }
    }

    contributionDialog?.let { withdraw ->
        ContributionDialog(
            withdraw = withdraw,
            currency = state.currency,
            accounts = state.accounts,
            maxWithdraw = state.progress?.saved ?: 0,
            onSave = { amount, date, note, from, to -> vm.addContribution(amount, withdraw, date, note, from, to) },
            onDismiss = { contributionDialog = null },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete goal?",
            "The goal and its contribution history will be removed. Transfers you recorded between accounts stay in your transactions.",
            "Delete", onConfirm = { vm.delete(onBack) }, onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
}

@Composable
private fun ContributionDialog(
    withdraw: Boolean,
    currency: String,
    accounts: List<Account>,
    maxWithdraw: Long,
    onSave: (Long, Long, String, Long?, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(Dates.todayEpoch()) }
    var note by remember { mutableStateOf("") }
    var recordTransfer by remember { mutableStateOf(false) }
    val savingsAccount = accounts.firstOrNull { it.type == AccountType.SAVINGS }
    val everyday = accounts.firstOrNull { it.type == AccountType.CURRENT } ?: accounts.firstOrNull()
    var from by remember { mutableStateOf(if (withdraw) savingsAccount else everyday) }
    var to by remember { mutableStateOf(if (withdraw) everyday else savingsAccount) }
    val amount = Money.parseToCents(amountText)
    val error = when {
        amount == null || amount <= 0 -> null
        withdraw && amount > maxWithdraw -> "You can withdraw at most ${Money.format(maxWithdraw, currency)}."
        recordTransfer && (from == null || to == null || from?.id == to?.id) -> "Pick two different accounts."
        else -> null
    }
    val valid = amount != null && amount > 0 && error == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (withdraw) "Withdraw from goal" else "Add money to goal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AmountField(amountText, { amountText = it }, "Amount", currency, isError = error != null, supportingText = error)
                DateField("Date", date, { if (it != null) date = it })
                OutlinedTextField(note, { if (it.length <= 60) note = it }, label = { Text("Note (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                if (accounts.size >= 2) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Also move the money between accounts", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = recordTransfer, onCheckedChange = { recordTransfer = it })
                    }
                    if (recordTransfer) {
                        SelectField("From", accounts, from, { it.name }, { from = it })
                        SelectField("To", accounts, to, { it.name }, { to = it })
                    }
                }
                Text(
                    "This only marks money for the goal. It isn't income or spending, so your totals aren't counted twice.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                if (amount != null) onSave(amount, date, note.trim(), if (recordTransfer) from?.id else null, if (recordTransfer) to?.id else null)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// --------------------------------------------------------------------------------------------- editor

private val goalTemplates = listOf(
    "Emergency fund" to "safety",
    "House deposit" to "house",
    "Holiday" to "beach",
    "New car" to "car",
    "Education" to "school",
    "Wedding" to "celebration",
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditGoalScreen(goalId: Long, onDone: () -> Unit) {
    val vm = appViewModel(key = "edit-goal-$goalId") { EditGoalViewModel(it, goalId) }
    LaunchedEffect(vm.done) { if (vm.done) onDone() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(if (vm.isNew) "New goal" else "Edit goal", onBack = onDone) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (vm.isNew) {
                Text("Ideas", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    goalTemplates.forEach { (name, icon) ->
                        AssistChip(onClick = { vm.applyTemplate(name, icon) }, label = { Text(name) },
                            leadingIcon = { Icon(AppIcons.category(icon), null) })
                    }
                }
            }
            OutlinedTextField(
                vm.name, { if (it.length <= 40) vm.name = it }, label = { Text("Goal name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            AmountField(vm.targetText, { vm.targetText = it }, "Target amount", vm.currency)
            AmountField(vm.savedText, { vm.savedText = it }, "Already saved (optional)", vm.currency,
                supportingText = "Money you've already put aside for this goal.")
            DateField("Target date (optional)", vm.targetDate, { vm.targetDate = it }, clearable = true, placeholder = "No target date")
            Text("Icon", style = MaterialTheme.typography.labelLarge)
            IconPicker(AppIcons.goalIconKeys, vm.iconKey, paletteColor(vm.colorIndex)) { vm.iconKey = it }
            Text("Colour", style = MaterialTheme.typography.labelLarge)
            ColorPicker(vm.colorIndex) { vm.colorIndex = it }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save goal", fontWeight = FontWeight.SemiBold) }
        }
    }
}
