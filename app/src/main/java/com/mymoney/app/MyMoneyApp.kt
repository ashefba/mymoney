package com.mymoney.app

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mymoney.app.data.backup.BackupManager
import com.mymoney.app.data.db.AppDatabase
import com.mymoney.app.data.export.ReportExporter
import com.mymoney.app.data.prefs.SettingsRepository
import com.mymoney.app.data.repo.FinanceRepository
import com.mymoney.app.data.repo.ReceiptStorage
import com.mymoney.app.notifications.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Simple manual dependency container; everything lives on the device. */
class AppContainer(context: Context) {
    val context: Context = context.applicationContext
    val database: AppDatabase = AppDatabase.build(context)
    val receipts = ReceiptStorage(context)
    val repository = FinanceRepository(database, receipts)
    val settings = SettingsRepository(context)
    val backup = BackupManager(context, repository, settings)
    val exporter = ReportExporter(context)
}

/**
 * Tracks whether the app lock has been passed. It survives configuration changes and re-locks
 * once the whole app has been in the background for longer than [RELOCK_AFTER_MS].
 */
class AppLockState : DefaultLifecycleObserver {
    var unlocked by mutableStateOf(false)
    private var backgroundedAt = 0L

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > RELOCK_AFTER_MS) unlocked = false
    }

    private companion object {
        const val RELOCK_AFTER_MS = 60_000L
    }
}

class MyMoneyApp : Application() {
    lateinit var container: AppContainer
        private set

    val lockState = AppLockState()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lockState)
        ReminderScheduler.createChannel(this)
        appScope.launch {
            container.repository.ensureDefaults()
            if (container.settings.current().remindersEnabled) ReminderScheduler.schedule(this@MyMoneyApp)
            ReminderScheduler.checkAndNotify(this@MyMoneyApp)
        }
    }
}
