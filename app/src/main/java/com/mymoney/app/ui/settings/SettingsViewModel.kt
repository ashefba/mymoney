package com.mymoney.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymoney.app.AppContainer
import com.mymoney.app.core.Dates
import com.mymoney.app.data.backup.BackupData
import com.mymoney.app.data.backup.InvalidBackupException
import com.mymoney.app.data.backup.PasswordRequiredException
import com.mymoney.app.data.backup.WrongPasswordException
import com.mymoney.app.data.prefs.AppSettings
import com.mymoney.app.data.prefs.ThemeMode
import com.mymoney.app.notifications.ReminderScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/** A backup that has been read and decrypted, waiting for the user to confirm the restore. */
data class PendingRestore(val data: BackupData) {
    val summary: String
        get() = buildString {
            append("Created ${Dates.medium(Instant.ofEpochMilli(data.createdAt).atZone(Dates.zone()).toLocalDate())}. ")
            append("${data.transactions.size} transactions, ${data.accounts.size} accounts, ")
            append("${data.goals.size} goals, ${data.bills.size} bills, ${data.debts.size} debts")
            if (data.receipts.isNotEmpty()) append(", ${data.receipts.size} receipt photos")
            append(".")
        }
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val settingsRepo = container.settings
    private val context: Context get() = container.context

    val settings: StateFlow<AppSettings?> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    var busy by mutableStateOf(false)
        private set
    var restoreNeedsPasswordFor by mutableStateOf<Uri?>(null)
        private set
    var restorePasswordError by mutableStateOf<String?>(null)
        private set
    var pendingRestore by mutableStateOf<PendingRestore?>(null)
        private set
    private var backupPassword: CharArray? = null

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    fun setCurrency(code: String) = viewModelScope.launch { settingsRepo.setCurrency(code) }
    fun setPayday(day: Int) = viewModelScope.launch { settingsRepo.setPayday(day) }
    fun setAppLock(enabled: Boolean) = viewModelScope.launch { settingsRepo.setAppLock(enabled) }

    fun setReminders(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setReminders(enabled)
        if (enabled) {
            ReminderScheduler.schedule(context)
            ReminderScheduler.checkAndNotify(context)
        } else {
            ReminderScheduler.cancel(context)
        }
    }

    // ------------------------------------------------------------------ backup

    fun rememberBackupPassword(password: CharArray) {
        backupPassword?.fill('\u0000')
        backupPassword = password
    }

    fun forgetBackupPassword() {
        backupPassword?.fill('\u0000')
        backupPassword = null
    }

    fun writeBackup(uri: Uri) {
        val password = backupPassword ?: return
        perform("Encrypted backup saved. Keep the file and your password somewhere safe.", "Backup failed") {
            try {
                container.backup.writeEncryptedBackup(uri, password)
            } finally {
                forgetBackupPassword()
            }
        }
    }

    fun exportJson(uri: Uri) = perform("All data exported as JSON (not encrypted).", "Export failed") {
        container.backup.writePlainExport(uri)
    }

    fun exportTransactionsCsv(uri: Uri) = perform("Transactions exported as CSV.", "Export failed") {
        val repo = container.repository
        container.exporter.writeTransactionsCsv(
            uri, repo.allTransactions(), repo.allCategories(), repo.allAccounts(), settingsRepo.current().currency,
        )
    }

    // ------------------------------------------------------------------ restore

    fun startRestore(uri: Uri) {
        viewModelScope.launch {
            busy = true
            try {
                pendingRestore = PendingRestore(container.backup.read(uri, null))
            } catch (e: PasswordRequiredException) {
                restorePasswordError = null
                restoreNeedsPasswordFor = uri
            } catch (e: InvalidBackupException) {
                _messages.emit(e.message ?: "This file is not a MyMoney backup.")
            } catch (e: Exception) {
                _messages.emit("Couldn't read the file: ${e.message ?: "unknown error"}")
            } finally {
                busy = false
            }
        }
    }

    fun decryptForRestore(password: CharArray) {
        val uri = restoreNeedsPasswordFor ?: return
        viewModelScope.launch {
            busy = true
            try {
                pendingRestore = PendingRestore(container.backup.read(uri, password))
                restoreNeedsPasswordFor = null
            } catch (e: WrongPasswordException) {
                restorePasswordError = "Wrong password, or the file is damaged."
            } catch (e: InvalidBackupException) {
                restoreNeedsPasswordFor = null
                _messages.emit(e.message ?: "This file is not a MyMoney backup.")
            } catch (e: Exception) {
                restoreNeedsPasswordFor = null
                _messages.emit("Couldn't read the backup: ${e.message ?: "unknown error"}")
            } finally {
                password.fill('\u0000')
                busy = false
            }
        }
    }

    fun cancelRestore() {
        restoreNeedsPasswordFor = null
        pendingRestore = null
    }

    fun confirmRestore() {
        val pending = pendingRestore ?: return
        pendingRestore = null
        perform("Backup restored.", "Restore failed") {
            container.backup.restore(pending.data)
            ReminderScheduler.checkAndNotify(context)
        }
    }

    // ------------------------------------------------------------------ delete

    fun deleteEverything() = perform("All data deleted.", "Delete failed") {
        container.repository.wipeEverything()
        context.getSharedPreferences("reminder_state", Context.MODE_PRIVATE).edit().clear().apply()
        settingsRepo.clearAll()
    }

    private fun perform(success: String, failurePrefix: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            val result = runCatching { block() }
            busy = false
            _messages.emit(
                if (result.isSuccess) success else "$failurePrefix: ${result.exceptionOrNull()?.message ?: "unknown error"}",
            )
        }
    }
}
