package com.mymoney.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mymoney.app.MainActivity
import com.mymoney.app.MyMoneyApp
import com.mymoney.app.R
import com.mymoney.app.core.Dates
import com.mymoney.app.core.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

object ReminderScheduler {
    const val CHANNEL_ID = "bill_reminders"
    private const val REQUEST_CODE = 4100

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_bills_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_bills_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Schedules the next battery-friendly check at local 09:00 as a one-shot alarm. */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = LocalDateTime.now()
        var first = now.toLocalDate().atTime(LocalTime.of(9, 0))
        if (!first.isAfter(now)) first = first.plusDays(1)
        val triggerAt = first.atZone(Dates.zone()).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, BillReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Posts a notification for each active bill inside its reminder window (once per due date). */
    @SuppressLint("MissingPermission")
    suspend fun checkAndNotify(context: Context) {
        val app = context.applicationContext as MyMoneyApp
        val settings = app.container.settings.current()
        if (!settings.remindersEnabled || !canNotify(context)) return
        val prefs = context.getSharedPreferences("reminder_state", Context.MODE_PRIVATE)
        val today = Dates.todayEpoch()
        val bills = app.container.repository.allBills().filter { it.active }
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val nm = NotificationManagerCompat.from(context)
        bills.forEach { bill ->
            val windowStart = bill.nextDueDate - bill.reminderDaysBefore
            if (today < windowStart) return@forEach
            val key = "bill_${bill.id}"
            if (prefs.getLong(key, Long.MIN_VALUE) == bill.nextDueDate) return@forEach
            val text = "${Money.format(bill.amountCents, settings.currency)} · ${Dates.dueText(bill.nextDueDate)}"
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(bill.name)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$text. Open MyMoney to mark it as paid once you've paid it."))
                .setContentIntent(tapIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            try {
                nm.notify(10_000 + bill.id.toInt(), notification)
                prefs.edit().putLong(key, bill.nextDueDate).apply()
            } catch (e: SecurityException) {
                return
            }
        }
    }
}

class BillReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReminderScheduler.checkAndNotify(context)
            } finally {
                // Compute the next local 09:00 after every run so daylight-saving changes do not
                // shift reminder time.
                ReminderScheduler.schedule(context)
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as MyMoneyApp
                    if (app.container.settings.current().remindersEnabled) {
                        ReminderScheduler.schedule(context)
                    } else {
                        ReminderScheduler.cancel(context)
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
