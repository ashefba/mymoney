package com.mymoney.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mymoney.app.MyMoneyApp
import com.mymoney.app.core.Dates
import com.mymoney.app.core.Money
import com.mymoney.app.data.prefs.ThemeMode
import com.mymoney.app.ui.appViewModel
import com.mymoney.app.ui.components.AppCard
import com.mymoney.app.ui.components.AppTopBar
import com.mymoney.app.ui.components.ConfirmDialog
import com.mymoney.app.ui.components.InfoNote
import com.mymoney.app.ui.components.ListRow
import com.mymoney.app.ui.components.LoadingBox
import com.mymoney.app.ui.components.SectionHeader
import com.mymoney.app.ui.lock.AppLock
import java.time.Instant

private const val BACKUP_MIME = "application/octet-stream"

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    var themeDialog by remember { mutableStateOf(false) }
    var currencyDialog by remember { mutableStateOf(false) }
    var paydayDialog by remember { mutableStateOf(false) }
    var backupPasswordDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var lockUnavailable by remember { mutableStateOf(false) }

    val today = Dates.iso(Dates.today())
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME)) { uri ->
        if (uri != null) vm.writeBackup(uri) else vm.forgetBackupPassword()
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportJson(uri)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportTransactionsCsv(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.startRestore(uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("Settings", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val s = settings
        if (s == null) {
            LoadingBox(Modifier.padding(padding)); return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            SectionHeader("Preferences")
            AppCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                ListRow("Appearance", s.themeMode.label, Icons.Rounded.DarkMode, onClick = { themeDialog = true })
                ListRow("Currency", "${s.currency} (${Money.symbol(s.currency)})", Icons.Rounded.Euro, onClick = { currencyDialog = true })
                ListRow(
                    "Payday",
                    if (s.paydayDayOfMonth == 0) "Plan to the end of each month" else "Day ${s.paydayDayOfMonth} of each month",
                    Icons.Rounded.CalendarMonth, onClick = { paydayDialog = true },
                )
                ListRow(
                    "Bill reminders", "Local notifications before bills are due",
                    Icons.Rounded.NotificationsActive,
                    trailing = { Switch(checked = s.remindersEnabled, onCheckedChange = { vm.setReminders(it) }) },
                )
            }

            SectionHeader("Security")
            AppCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                ListRow(
                    "App lock", "Require fingerprint, face or screen lock to open MyMoney",
                    Icons.Rounded.Fingerprint,
                    trailing = {
                        Switch(checked = s.appLockEnabled, onCheckedChange = { enable ->
                            val activity = context as? FragmentActivity
                            when {
                                !enable -> vm.setAppLock(false)
                                !AppLock.isAvailable(context) || activity == null -> lockUnavailable = true
                                else -> AppLock.authenticate(
                                    activity, "Turn on app lock", "Confirm it's you",
                                    onSuccess = {
                                        (context.applicationContext as MyMoneyApp).lockState.unlocked = true
                                        vm.setAppLock(true)
                                    },
                                    onError = { },
                                )
                            }
                        })
                    },
                )
            }

            SectionHeader("Backup & export")
            AppCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                ListRow(
                    "Create encrypted backup",
                    if (s.lastBackupMillis > 0) {
                        "Last backup ${Dates.medium(Instant.ofEpochMilli(s.lastBackupMillis).atZone(Dates.zone()).toLocalDate())}"
                    } else "Password-protected file with all data and receipt photos",
                    Icons.Rounded.Backup, onClick = { backupPasswordDialog = true },
                )
                ListRow("Restore from backup", "Replaces all current data", Icons.Rounded.Restore,
                    onClick = { restoreLauncher.launch(arrayOf("*/*")) })
                ListRow("Export all data (JSON)", "Readable, not encrypted, without photos", Icons.Rounded.DataObject,
                    onClick = { jsonLauncher.launch("mymoney-export-$today.json") })
                ListRow("Export transactions (CSV)", "Open in Excel, Numbers or Sheets", Icons.Rounded.TableChart,
                    onClick = { csvLauncher.launch("mymoney-transactions-$today.csv") })
            }
            InfoNote("Backups are saved where you choose (e.g. Downloads or a USB drive). Encryption uses AES-256-GCM with a key derived from your password (PBKDF2, 150,000 rounds). If you forget the password, the backup can't be opened.")

            SectionHeader("Danger zone")
            AppCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                ListRow("Delete all data", "Permanently erase everything on this device", Icons.Rounded.DeleteForever,
                    iconTint = MaterialTheme.colorScheme.error, onClick = { deleteDialog = true })
            }

            SectionHeader("About")
            AppCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                ListRow(
                    "100% offline",
                    "MyMoney has no internet permission, no account, no ads and no tracking. Your data never leaves this device unless you export it.",
                    Icons.Rounded.WifiOff,
                )
            }
            Text("MyMoney 1.0.0", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp))
        }
    }

    val s = settings ?: return
    if (themeDialog) {
        ChoiceDialog("Appearance", ThemeMode.entries, s.themeMode, { it.label }, { vm.setTheme(it) }, { themeDialog = false })
    }
    if (currencyDialog) {
        ChoiceDialog("Currency", Money.supportedCurrencies, s.currency, { "$it (${Money.symbol(it)})" }, { vm.setCurrency(it) },
            { currencyDialog = false },
            note = "Changing the currency only changes how amounts are shown. Amounts are not converted.")
    }
    if (paydayDialog) {
        ChoiceDialog("When does your income arrive?", (0..31).toList(), s.paydayDayOfMonth,
            { if (it == 0) "End of the month" else "Day $it" }, { vm.setPayday(it) }, { paydayDialog = false })
    }
    if (backupPasswordDialog) {
        BackupPasswordDialog(
            onConfirm = { pw ->
                vm.rememberBackupPassword(pw)
                backupPasswordDialog = false
                backupLauncher.launch("mymoney-backup-$today.mymoney")
            },
            onDismiss = { backupPasswordDialog = false },
        )
    }
    vm.restoreNeedsPasswordFor?.let {
        RestorePasswordDialog(vm.restorePasswordError, vm.busy, onConfirm = vm::decryptForRestore, onDismiss = vm::cancelRestore)
    }
    vm.pendingRestore?.let { pending ->
        ConfirmDialog(
            "Restore this backup?",
            "${pending.summary}\n\nAll data currently in MyMoney will be replaced. This can't be undone.",
            "Restore", onConfirm = vm::confirmRestore, onDismiss = vm::cancelRestore, destructive = true,
        )
    }
    if (deleteDialog) {
        DeleteAllDialog(onConfirm = { vm.deleteEverything(); deleteDialog = false }, onDismiss = { deleteDialog = false })
    }
    if (lockUnavailable) {
        AlertDialog(
            onDismissRequest = { lockUnavailable = false },
            title = { Text("Set up a screen lock first") },
            text = { Text("App lock uses your device's fingerprint, face unlock or screen lock (PIN, pattern or password). Set one up in your phone's settings, then try again.") },
            confirmButton = { TextButton(onClick = { lockUnavailable = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = o == selected, onClick = { onSelect(o); onDismiss() })
                        TextButton(onClick = { onSelect(o); onDismiss() }) {
                            Text(label(o), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                if (note != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun BackupPasswordDialog(onConfirm: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val error = when {
        password.isNotEmpty() && password.length < 8 -> "Use at least 8 characters."
        repeat.isNotEmpty() && repeat != password -> "The passwords don't match."
        else -> null
    }
    val valid = password.length >= 8 && password == repeat
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Protect your backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose a password. You'll need it to restore — it can't be recovered if forgotten.",
                    style = MaterialTheme.typography.bodyMedium)
                PasswordField(password, { password = it }, "Password")
                PasswordField(repeat, { repeat = it }, "Repeat password", isError = error != null)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(password.toCharArray()) }) { Text("Choose location") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RestorePasswordDialog(error: String?, busy: Boolean, onConfirm: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter backup password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PasswordField(password, { password = it }, "Password", isError = error != null)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty() && !busy, onClick = { onConfirm(password.toCharArray()) }) { Text("Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean = false) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DeleteAllDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete all data?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Every account, transaction, budget, goal, bill, debt, receipt photo and setting will be permanently erased from this device. Make a backup first if you might need it.")
                OutlinedTextField(text, { text = it }, label = { Text("Type DELETE to confirm") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = text.trim() == "DELETE", onClick = onConfirm) {
                Text("Delete everything", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
