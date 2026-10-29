package com.mymoney.app.ui.bills

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mymoney.app.core.Dates
import com.mymoney.app.core.Money
import com.mymoney.app.data.db.Account
import com.mymoney.app.data.db.Bill
import com.mymoney.app.data.db.BillFrequency
import com.mymoney.app.data.db.CategoryKind
import com.mymoney.app.data.db.PaymentMethod
import com.mymoney.app.notifications.ReminderScheduler
import com.mymoney.app.ui.appViewModel
import com.mymoney.app.ui.components.AmountField
import com.mymoney.app.ui.components.AppCard
import com.mymoney.app.ui.components.AppIcons
import com.mymoney.app.ui.components.AppTopBar
import com.mymoney.app.ui.components.ConfirmDialog
import com.mymoney.app.ui.components.DateField
import com.mymoney.app.ui.components.EmptyState
import com.mymoney.app.ui.components.IconBadge
import com.mymoney.app.ui.components.InfoNote
import com.mymoney.app.ui.components.LegendDot
import com.mymoney.app.ui.components.LoadingBox
import com.mymoney.app.ui.components.MoneyText
import com.mymoney.app.ui.components.PeriodSelector
import com.mymoney.app.ui.components.SelectField
import com.mymoney.app.ui.components.StatRow
import com.mymoney.app.ui.components.paletteColor
import com.mymoney.app.ui.theme.FinanceTheme
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val vm = appViewModel { BillsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var payDialog by remember { mutableStateOf<Bill?>(null) }
    var skipDialog by remember { mutableStateOf<Bill?>(null) }
    val context = LocalContext.current
    var canNotify by remember { mutableStateOf(ReminderScheduler.canNotify(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted && ReminderScheduler.canNotify(context)
        if (canNotify) vm.checkReminders()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("Bills & subscriptions", onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onEdit(0) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New bill") })
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding)); return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    StatRow("Regular payments per month", Money.format(state.monthlyTotal, state.currency), bold = true)
                    StatRow("Of which subscriptions", Money.format(state.subscriptionsMonthly, state.currency))
                    StatRow("Per year", Money.format(state.monthlyTotal * 12, state.currency))
                }
            }
            if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && state.bills.isNotEmpty()) {
                item {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.NotificationsActive, MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(12.dp))
                            Text("Allow notifications to get reminders before bills are due.", style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Turn on reminders") }
                    }
                }
            }
            item {
                val tabs = listOf("Upcoming", "Calendar", "All bills")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    tabs.forEachIndexed { i, label ->
                        SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, tabs.size)) { Text(label) }
                    }
                }
            }
            if (state.bills.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.EventRepeat,
                        "No bills yet",
                        "Add rent, energy, phone, insurance and subscriptions. MyMoney reminds you before they're due — nothing is deducted until you mark it as paid.",
                        action = { Button(onClick = { onEdit(0) }) { Text("Add a bill") } },
                    )
                }
            }
            when (tab) {
                0 -> {
                    if (state.overdue.isNotEmpty()) {
                        item { Text("Overdue", style = MaterialTheme.typography.titleSmall, color = FinanceTheme.colors.expense) }
                        items(state.overdue, key = { "o${it.id}" }) { b ->
                            BillRow(b, Dates.of(b.nextDueDate), state.currency, onPay = { payDialog = b }, onSkip = { skipDialog = b }, onEdit = { onEdit(b.id) })
                        }
                    }
                    if (state.upcoming.isNotEmpty()) {
                        item { Text("Next 60 days", style = MaterialTheme.typography.titleSmall) }
                        items(state.upcoming, key = { "u${it.bill.id}-${it.date}" }) { o ->
                            val isNext = o.date.toEpochDay() == o.bill.nextDueDate
                            BillRow(o.bill, o.date, state.currency,
                                onPay = if (isNext) ({ payDialog = o.bill }) else null,
                                onSkip = if (isNext) ({ skipDialog = o.bill }) else null,
                                onEdit = { onEdit(o.bill.id) })
                        }
                    } else if (state.bills.isNotEmpty() && state.overdue.isEmpty()) {
                        item { Text("Nothing due in the next 60 days.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                1 -> item { CalendarCard(state, vm) }
                else -> items(state.bills, key = { "a${it.id}" }) { b ->
                    val cat = state.categories.firstOrNull { it.id == b.categoryId }
                    AppCard(onClick = { onEdit(b.id) }, contentPadding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(
                                if (b.isSubscription) Icons.Rounded.Subscriptions else AppIcons.category(cat?.iconKey),
                                if (b.active) paletteColor(cat?.colorIndex ?: 0) else MaterialTheme.colorScheme.outline,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(
                                    if (b.active) "${b.frequency.label} · next ${Dates.medium(b.nextDueDate)}" else "Paused",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                state.lastPayments[b.id]?.let {
                                    Text("Last paid ${Dates.medium(it.date)}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            MoneyText(b.amountCents, state.currency, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            item {
                InfoNote("Bills are reminders only. Your balances change when you tap “Paid”, which records the payment as an expense on the account you choose.")
            }
        }
    }

    payDialog?.let { bill ->
        PayBillDialog(bill, state.currency, state.accounts, onDismiss = { payDialog = null }) { amount, date, accountId, method ->
            vm.markPaid(bill, amount, date, accountId, method)
        }
    }
    skipDialog?.let { bill ->
        ConfirmDialog(
            "Skip this payment?",
            "Moves ${bill.name} to its next due date without recording a payment.",
            "Skip",
            onConfirm = { vm.skip(bill) },
            onDismiss = { skipDialog = null },
        )
    }
}

@Composable
private fun BillRow(bill: Bill, date: LocalDate, currency: String, onPay: (() -> Unit)?, onSkip: (() -> Unit)?, onEdit: () -> Unit) {
    val fc = FinanceTheme.colors
    val overdue = date.isBefore(Dates.today())
    AppCard(onClick = onEdit, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateBadge(date, if (overdue) fc.expense else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(bill.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(Dates.dueText(date.toEpochDay()), style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) fc.expense else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MoneyText(bill.amountCents, currency, style = MaterialTheme.typography.bodyLarge)
        }
        if (onPay != null) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onPay) {
                    Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Paid")
                }
                if (onSkip != null) TextButton(onClick = onSkip) { Text("Skip") }
            }
        }
    }
}

@Composable
private fun DateBadge(date: LocalDate, color: Color) {
    Column(
        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.14f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        Text(Dates.shortMonth(java.time.YearMonth.from(date)), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun CalendarCard(state: BillsState, vm: BillsViewModel) {
    val fc = FinanceTheme.colors
    var selected by remember(state.calendarMonth) { mutableStateOf<LocalDate?>(null) }
    val ym = state.calendarMonth
    val first = ym.atDay(1)
    val leading = (first.dayOfWeek.value - DayOfWeek.MONDAY.value)
    val days = ym.lengthOfMonth()
    val cells = leading + days
    val rows = (cells + 6) / 7
    val today = Dates.today()

    AppCard {
        PeriodSelector(Dates.month(ym), vm::previousMonth, vm::nextMonth)
        Row(Modifier.fillMaxWidth()) {
            listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach {
                Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val index = r * 7 + c
                    val dayNum = index - leading + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..days) {
                            val date = ym.atDay(dayNum)
                            val entries = state.calendar[date].orEmpty()
                            val isSelected = selected == date
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .border(
                                        if (date == today) 1.5.dp else 0.dp,
                                        if (date == today) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable { selected = if (isSelected) null else date },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(dayNum.toString(), style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (entries.isNotEmpty()) FontWeight.Bold else FontWeight.Normal)
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    entries.take(3).forEach { e ->
                                        Box(Modifier.size(5.dp).clip(CircleShape).background(statusColor(e.status)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            LegendDot(statusColor(OccurrenceStatus.DUE), "Due")
            LegendDot(statusColor(OccurrenceStatus.OVERDUE), "Overdue")
            LegendDot(statusColor(OccurrenceStatus.PAID), "Paid")
        }
        val sel = selected
        if (sel != null) {
            Spacer(Modifier.height(10.dp))
            Text(Dates.medium(sel), style = MaterialTheme.typography.titleSmall)
            val entries = state.calendar[sel].orEmpty()
            if (entries.isEmpty()) {
                Text("Nothing due on this day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            entries.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor(e.status)))
                    Spacer(Modifier.width(8.dp))
                    Text(e.name, modifier = Modifier.weight(1f))
                    Text(
                        when (e.status) {
                            OccurrenceStatus.PAID -> "Paid"
                            OccurrenceStatus.DUE -> "Due"
                            OccurrenceStatus.OVERDUE -> "Overdue"
                        },
                        style = MaterialTheme.typography.labelMedium, color = statusColor(e.status),
                    )
                    Spacer(Modifier.width(8.dp))
                    MoneyText(e.amount, state.currency, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun statusColor(s: OccurrenceStatus): Color = when (s) {
    OccurrenceStatus.PAID -> FinanceTheme.colors.income
    OccurrenceStatus.DUE -> MaterialTheme.colorScheme.secondary
    OccurrenceStatus.OVERDUE -> FinanceTheme.colors.expense
}

@Composable
private fun PayBillDialog(
    bill: Bill,
    currency: String,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long, Long, PaymentMethod) -> Unit,
) {
    var amountText by remember { mutableStateOf(Money.toInput(bill.amountCents)) }
    val today = Dates.todayEpoch()
    var date by remember { mutableStateOf(minOf(bill.nextDueDate, today)) }
    var account by remember { mutableStateOf(accounts.firstOrNull { it.id == bill.accountId } ?: accounts.firstOrNull()) }
    var method by remember { mutableStateOf(bill.paymentMethod) }
    val amount = Money.parseToCents(amountText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record payment") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${bill.name} will be recorded as an expense and moved to its next due date.", style = MaterialTheme.typography.bodyMedium)
                AmountField(amountText, { amountText = it }, "Amount paid", currency)
                DateField("Paid on", date, { if (it != null) date = it })
                if (accounts.isEmpty()) {
                    Text("Add an account first (More → Accounts).", color = MaterialTheme.colorScheme.error)
                } else {
                    SelectField("Paid from", accounts, account, { it.name }, { account = it })
                }
                SelectField("Payment method", PaymentMethod.entries.toList(), method, { it.label }, { method = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && amount > 0 && account != null,
                onClick = {
                    val a = account
                    if (amount != null && a != null) onConfirm(amount, date, a.id, method)
                    onDismiss()
                },
            ) { Text("Mark as paid") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// --------------------------------------------------------------------------------------------- editor

@Composable
fun EditBillScreen(billId: Long, onDone: () -> Unit) {
    val vm = appViewModel(key = "bill-$billId") { EditBillViewModel(it, billId) }
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(vm.done) { if (vm.done) onDone() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(if (vm.isNew) "New bill" else "Edit bill", onBack = onDone) {
                if (!vm.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                vm.name, { if (it.length <= 40) vm.name = it }, label = { Text("Name") }, placeholder = { Text("e.g. Rent, Spotify, Car insurance") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            AmountField(vm.amountText, { vm.amountText = it }, "Amount", vm.currency)
            SelectField("How often", BillFrequency.entries.toList(), vm.frequency, { it.label }, { vm.frequency = it })
            DateField("Next due date", vm.nextDue, { if (it != null) vm.nextDue = it })
            val expenseCats = categories.filter { it.kind == CategoryKind.EXPENSE && !it.archived }
            SelectField("Category", expenseCats, expenseCats.firstOrNull { it.id == vm.categoryId }, { it.name }, { vm.categoryId = it.id })
            val accs = accounts.filter { !it.archived }
            SelectField("Usually paid from", accs, accs.firstOrNull { it.id == vm.accountId }, { it.name }, { vm.accountId = it.id },
                placeholder = "Choose when paying")
            SelectField("Payment method", PaymentMethod.entries.toList(), vm.paymentMethod, { it.label }, { vm.paymentMethod = it })
            SelectField(
                "Reminder", listOf(0, 1, 2, 3, 5, 7, 14), vm.reminderDays,
                { if (it == 0) "On the due date" else "$it day${if (it == 1) "" else "s"} before" }, { vm.reminderDays = it },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("This is a subscription", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = vm.isSubscription, onCheckedChange = { vm.isSubscription = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Active", style = MaterialTheme.typography.bodyLarge)
                    Text("Turn off to pause reminders, e.g. after cancelling.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = vm.active, onCheckedChange = { vm.active = it })
            }
            OutlinedTextField(vm.notes, { if (it.length <= 300) vm.notes = it }, label = { Text("Notes (optional)") }, minLines = 2,
                modifier = Modifier.fillMaxWidth())
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save bill") }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete bill?", "Past payments stay in your transactions.", "Delete",
            onConfirm = vm::delete, onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
}
