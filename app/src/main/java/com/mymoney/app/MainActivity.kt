package com.mymoney.app

import android.Manifest
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.mymoney.app.data.prefs.AppSettings
import com.mymoney.app.data.prefs.ThemeMode
import com.mymoney.app.notifications.ReminderScheduler
import com.mymoney.app.ui.lock.AppLock
import com.mymoney.app.ui.lock.LockScreen
import com.mymoney.app.ui.navigation.AppNavigation
import com.mymoney.app.ui.onboarding.OnboardingScreen
import com.mymoney.app.ui.theme.MyMoneyTheme

/** FragmentActivity (a ComponentActivity) is required by BiometricPrompt. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as MyMoneyApp
        setContent {
            val settings by app.container.settings.settings.collectAsState(initial = null)
            val s = settings
            val dark = when (s?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose { }
            }
            // Hide app content from the recent-apps preview and screenshots while app lock is on.
            DisposableEffect(s?.appLockEnabled) {
                if (s?.appLockEnabled == true) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                onDispose { }
            }
            MyMoneyTheme(themeMode = s?.themeMode ?: ThemeMode.SYSTEM) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when {
                        s == null -> Unit
                        !s.onboardingDone -> OnboardingScreen()
                        s.appLockEnabled && !AppLock.isAvailable(this@MainActivity) -> RecoverUnavailableAppLock(app)
                        s.appLockEnabled && !app.lockState.unlocked -> LockScreen(onUnlocked = { app.lockState.unlocked = true })
                        else -> {
                            NotificationPermissionOnce(s)
                            AppNavigation()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoverUnavailableAppLock(app: MyMoneyApp) {
    LaunchedEffect(app) {
        // Device credentials can be removed outside MyMoney. Never leave the user permanently
        // locked out when Android no longer has an authenticator available.
        app.lockState.unlocked = true
        app.container.settings.setAppLock(false)
    }
}

@Composable
private fun NotificationPermissionOnce(settings: AppSettings) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !settings.remindersEnabled) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) ReminderScheduler.schedule(context)
    }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("ui_state", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("asked_notifications", false) && !ReminderScheduler.canNotify(context)) {
            prefs.edit().putBoolean("asked_notifications", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
