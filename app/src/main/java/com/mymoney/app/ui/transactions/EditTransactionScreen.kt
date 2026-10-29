package com.mymoney.app.ui.transactions

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mymoney.app.core.Dates
import com.mymoney.app.core.Money
import com.mymoney.app.data.db.Account
import com.mymoney.app.data.db.CategoryKind
import com.mymoney.app.data.db.PaymentMethod
import com.mymoney.app.data.db.TransactionType
import com.mymoney.app.ui.appViewModel
import com.mymoney.app.ui.components.AppIcons
import com.mymoney.app.ui.components.AppTopBar
import com.mymoney.app.ui.components.ConfirmDialog
import com.mymoney.app.ui.components.DateField
import com.mymoney.app.ui.components.InfoNote
import com.mymoney.app.ui.components.ReceiptThumbnail
import com.mymoney.app.ui.components.ReceiptViewer
import com.mymoney.app.ui.components.SelectField
import com.mymoney.app.ui.components.paletteColor
import com.mymoney.app.ui.theme.FinanceTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditTransactionScreen(transactionId: Long, initialType: TransactionType, onDone: () -> Unit) {
    val vm = appViewModel(key = "tx-$transactionId-$initialType") { EditTransactionViewModel(it, transactionId, initialType) }
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var viewReceipt by remember { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val amountFocus = remember { FocusRequester() }

    LaunchedEffect(vm.finished) { if (vm.finished) onDone() }
    LaunchedEffect(vm.loaded) {
        if (vm.loaded && vm.isNew) runCatching { amountFocus.requestFocus() }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let(vm::attachReceipt)
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) vm.attachReceipt(Uri.parse(uri))
    }

    val readOnly = vm.readOnlyReason != null
    val title = when {
        !vm.isNew -> "Edit transaction"
        vm.type == TransactionType.INCOME -> "New income"
        vm.type == TransactionType.TRANSFER -> "New transfer"
        else -> "New expense"
    }
    val activeAccounts = accounts.filter { !it.archived || it.id == vm.accountId || it.id == vm.toAccountId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title, onBack = onDone) {
                if (!vm.isNew && !readOnly) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
                }
            }
        },
        bottomBar = {
            if (!readOnly) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) {
                        vm.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().height(54.dp), enabled = vm.loaded && !vm.processingReceipt) {
                            Text(if (vm.isNew) "Save" else "Save changes", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            vm.readOnlyReason?.let { InfoNote(it) }

            if (!readOnly && vm.type != TransactionType.DEBT_IN && vm.type != TransactionType.DEBT_OUT) {
                val types = listOf(TransactionType.EXPENSE to "Expense", TransactionType.INCOME to "Income", TransactionType.TRANSFER to "Transfer")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    types.forEachIndexed { i, (t, label) ->
                        SegmentedButton(
                            selected = vm.type == t,
                            onClick = { vm.changeType(t) },
                            shape = SegmentedButtonDefaults.itemShape(i, types.size),
                        ) { Text(label) }
                    }
                }
            }

            val amountColor = when (vm.type) {
                TransactionType.INCOME -> FinanceTheme.colors.income
                TransactionType.EXPENSE -> FinanceTheme.colors.expense
                else -> FinanceTheme.colors.transfer
            }
            OutlinedTextField(
                value = vm.amountText,
                onValueChange = { new -> if (new.length <= 14 && new.all { it.isDigit() || it == '.' || it == ',' }) vm.amountText = new },
                readOnly = readOnly,
                placeholder = { Text("0.00", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                prefix = { Text(Money.symbol(vm.currency), style = MaterialTheme.typography.displaySmall, color = amountColor) },
                textStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, color = amountColor, fontSize = 40.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = vm.amountText.isNotBlank() && Money.parseToCents(vm.amountText) == null,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().focusRequester(amountFocus),
            )

            if (vm.type == TransactionType.TRANSFER) {
                Text("From", style = MaterialTheme.typography.labelLarge)
                AccountChips(activeAccounts, vm.accountId, enabled = !readOnly) { vm.accountId = it }
                Text("To", style = MaterialTheme.typography.labelLarge)
                AccountChips(activeAccounts.filter { it.id != vm.accountId }, vm.toAccountId, enabled = !readOnly) { vm.toAccountId = it }
                InfoNote("Transfers move money between your own accounts (for example to savings or to pay off a credit card). They are never counted as income or spending.")
            } else if (vm.type == TransactionType.EXPENSE || vm.type == TransactionType.INCOME) {
                Text("Category", style = MaterialTheme.typography.labelLarge)
                val kind = if (vm.type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                val options = categories.filter { it.kind == kind && (!it.archived || it.id == vm.categoryId) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    options.forEach { c ->
                        val color = paletteColor(c.colorIndex)
                        FilterChip(
                            selected = vm.categoryId == c.id,
                            onClick = { if (!readOnly) vm.categoryId = if (vm.categoryId == c.id) null else c.id },
                            label = { Text(c.name) },
                            leadingIcon = { Icon(AppIcons.category(c.iconKey), contentDescription = null, tint = color, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
                Text(if (vm.type == TransactionType.INCOME) "Received into" else "Paid from", style = MaterialTheme.typography.labelLarge)
                AccountChips(activeAccounts, vm.accountId, enabled = !readOnly) { vm.accountId = it }
            } else {
                Text("Account: ${accounts.firstOrNull { it.id == vm.accountId }?.name ?: "–"}", style = MaterialTheme.typography.bodyLarge)
            }
            if (activeAccounts.isEmpty()) {
                InfoNote("You don't have any accounts yet. Add one under More → Accounts, e.g. your current account with its balance.")
            }

            Text("Date", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val today = Dates.todayEpoch()
                FilterChip(selected = vm.date == today, onClick = { if (!readOnly) vm.date = today }, label = { Text("Today") })
                FilterChip(selected = vm.date == today - 1, onClick = { if (!readOnly) vm.date = today - 1 }, label = { Text("Yesterday") })
                Box(Modifier.weight(1f)) {
                    DateField("Other date", vm.date, onChange = { if (it != null && !readOnly) vm.date = it })
                }
            }

            OutlinedTextField(
                value = vm.description,
                onValueChange = { if (it.length <= 80) vm.description = it },
                label = { Text("Description (optional)") },
                placeholder = { Text(if (vm.type == TransactionType.INCOME) "e.g. October salary" else "e.g. Weekly shop at Lidl") },
                singleLine = true,
                readOnly = readOnly,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            if (vm.type == TransactionType.EXPENSE || vm.type == TransactionType.INCOME) {
                SelectField(
                    label = "Payment method",
                    options = PaymentMethod.entries.toList(),
                    selected = vm.paymentMethod,
                    optionLabel = { it.label },
                    onSelect = { if (!readOnly) vm.paymentMethod = it },
                )
            }

            OutlinedTextField(
                value = vm.notes,
                onValueChange = { if (it.length <= 500) vm.notes = it },
                label = { Text("Notes (optional)") },
                minLines = 2,
                readOnly = readOnly,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            if (!readOnly) {
                Text("Receipt (optional, stays on this phone)", style = MaterialTheme.typography.labelLarge)
                val receipt = vm.receiptFile
                if (vm.processingReceipt) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                } else if (receipt != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ReceiptThumbnail(vm.receipts.file(receipt), Modifier.size(96.dp)) { viewReceipt = true }
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(onClick = vm::removeReceipt) {
                            Icon(Icons.Rounded.Close, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Remove")
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Choose photo")
                        }
                        OutlinedButton(onClick = {
                            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                            val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            pendingCameraUri = uri.toString()
                            try {
                                takePicture.launch(uri)
                            } catch (e: ActivityNotFoundException) {
                                vm.error = "No camera app is available on this device."
                            }
                        }) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Take photo")
                        }
                    }
                }
            } else {
                vm.receiptFile?.let { r ->
                    ReceiptThumbnail(vm.receipts.file(r), Modifier.size(96.dp)) { viewReceipt = true }
                }
            }

            if (!vm.isNew && vm.type == TransactionType.EXPENSE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Changes update your balances, budgets and reports immediately.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete transaction?",
            message = "This removes it permanently and updates your balances.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = vm::delete,
            onDismiss = { confirmDelete = false },
        )
    }
    if (viewReceipt) {
        vm.receiptFile?.let { ReceiptViewer(vm.receipts.file(it)) { viewReceipt = false } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountChips(accounts: List<Account>, selected: Long?, enabled: Boolean, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        accounts.forEach { a ->
            FilterChip(
                selected = selected == a.id,
                onClick = { if (enabled) onSelect(a.id) },
                label = { Text(a.name) },
                leadingIcon = { Icon(AppIcons.account(a.type), contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}
