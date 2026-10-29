package com.mymoney.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("Follow system"), LIGHT("Light"), DARK("Dark") }

data class AppSettings(
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val currency: String = "EUR",
    val appLockEnabled: Boolean = false,
    /** 0 means "end of month"; otherwise the day of month your income usually arrives. */
    val paydayDayOfMonth: Int = 0,
    val plannedMonthlySavings: Long = 0,
    val remindersEnabled: Boolean = true,
    val defaultAccountId: Long = 0,
    val lastBackupMillis: Long = 0,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val theme = stringPreferencesKey("theme_mode")
        val currency = stringPreferencesKey("currency")
        val appLock = booleanPreferencesKey("app_lock")
        val payday = intPreferencesKey("payday")
        val plannedSavings = longPreferencesKey("planned_savings")
        val reminders = booleanPreferencesKey("reminders")
        val defaultAccount = longPreferencesKey("default_account")
        val lastBackup = longPreferencesKey("last_backup")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            onboardingDone = p[Keys.onboarding] ?: false,
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            currency = p[Keys.currency] ?: "EUR",
            appLockEnabled = p[Keys.appLock] ?: false,
            paydayDayOfMonth = p[Keys.payday] ?: 0,
            plannedMonthlySavings = p[Keys.plannedSavings] ?: 0L,
            remindersEnabled = p[Keys.reminders] ?: true,
            defaultAccountId = p[Keys.defaultAccount] ?: 0L,
            lastBackupMillis = p[Keys.lastBackup] ?: 0L,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setOnboardingDone(done: Boolean) = context.dataStore.edit { it[Keys.onboarding] = done }
    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setCurrency(code: String) = context.dataStore.edit { it[Keys.currency] = code }
    suspend fun setAppLock(enabled: Boolean) = context.dataStore.edit { it[Keys.appLock] = enabled }
    suspend fun setPayday(day: Int) = context.dataStore.edit { it[Keys.payday] = day.coerceIn(0, 31) }
    suspend fun setPlannedSavings(cents: Long) = context.dataStore.edit { it[Keys.plannedSavings] = cents.coerceAtLeast(0) }
    suspend fun setReminders(enabled: Boolean) = context.dataStore.edit { it[Keys.reminders] = enabled }
    suspend fun setDefaultAccount(id: Long) = context.dataStore.edit { it[Keys.defaultAccount] = id }
    suspend fun setLastBackup(millis: Long) = context.dataStore.edit { it[Keys.lastBackup] = millis }

    /** Settings that are included in backups (security settings are intentionally excluded). */
    suspend fun exportForBackup(): Map<String, String> {
        val s = current()
        return mapOf(
            "themeMode" to s.themeMode.name,
            "currency" to s.currency,
            "payday" to s.paydayDayOfMonth.toString(),
            "plannedSavings" to s.plannedMonthlySavings.toString(),
            "reminders" to s.remindersEnabled.toString(),
            "defaultAccount" to s.defaultAccountId.toString(),
        )
    }

    suspend fun importFromBackup(values: Map<String, String>) {
        context.dataStore.edit { p ->
            values["themeMode"]?.let { v -> runCatching { ThemeMode.valueOf(v) }.getOrNull()?.let { p[Keys.theme] = it.name } }
            values["currency"]?.let { p[Keys.currency] = it }
            values["payday"]?.toIntOrNull()?.let { p[Keys.payday] = it.coerceIn(0, 31) }
            values["plannedSavings"]?.toLongOrNull()?.let { p[Keys.plannedSavings] = it.coerceAtLeast(0) }
            values["reminders"]?.toBooleanStrictOrNull()?.let { p[Keys.reminders] = it }
            values["defaultAccount"]?.toLongOrNull()?.let { p[Keys.defaultAccount] = it }
            p[Keys.onboarding] = true
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
