package com.mymoney.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymoney.app.data.prefs.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B7A5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC2F2DF),
    onPrimaryContainer = Color(0xFF00281B),
    secondary = Color(0xFF3F5BD8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE2FF),
    onSecondaryContainer = Color(0xFF0B1A63),
    tertiary = Color(0xFFB4690E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF2C1600),
    error = Color(0xFFC8372D),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410001),
    background = Color(0xFFF5F7F6),
    onBackground = Color(0xFF151C19),
    surface = Color(0xFFF5F7F6),
    onSurface = Color(0xFF151C19),
    surfaceVariant = Color(0xFFE3EAE6),
    onSurfaceVariant = Color(0xFF56625D),
    outline = Color(0xFF8A9691),
    outlineVariant = Color(0xFFD5DDD9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEFF3F1),
    surfaceContainerHigh = Color(0xFFE9EEEC),
    surfaceContainerHighest = Color(0xFFE3E9E6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5FD9AE),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF00513B),
    onPrimaryContainer = Color(0xFFC2F2DF),
    secondary = Color(0xFFB9C3FF),
    onSecondary = Color(0xFF14267F),
    secondaryContainer = Color(0xFF2D3FA8),
    onSecondaryContainer = Color(0xFFDDE2FF),
    tertiary = Color(0xFFFFB86B),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6A3C00),
    onTertiaryContainer = Color(0xFFFFDDB8),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF680003),
    errorContainer = Color(0xFF930006),
    onErrorContainer = Color(0xFFFFDAD5),
    background = Color(0xFF0D1311),
    onBackground = Color(0xFFDEE5E1),
    surface = Color(0xFF0D1311),
    onSurface = Color(0xFFDEE5E1),
    surfaceVariant = Color(0xFF29322E),
    onSurfaceVariant = Color(0xFFA9B5AF),
    outline = Color(0xFF74807A),
    outlineVariant = Color(0xFF333D39),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF151C19),
    surfaceContainer = Color(0xFF19211E),
    surfaceContainerHigh = Color(0xFF212A26),
    surfaceContainerHighest = Color(0xFF2A3430),
)

/** Extra colors that Material's scheme doesn't cover. */
@Immutable
data class FinanceColors(
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val warning: Color,
    val heroGradient: Brush,
    val onHero: Color,
    val chartPalette: List<Color>,
    val track: Color,
)

private val chartLight = listOf(
    Color(0xFF0B7A5C), Color(0xFF3F5BD8), Color(0xFFE07A1F), Color(0xFFD64569), Color(0xFF7B4FD6),
    Color(0xFF1C9DB8), Color(0xFFC9A40E), Color(0xFF5A8F29), Color(0xFFB8501F), Color(0xFF2E7D9A),
    Color(0xFF9C3FB0), Color(0xFF4A6572),
)
private val chartDark = listOf(
    Color(0xFF5FD9AE), Color(0xFF8FA2FF), Color(0xFFFFA65C), Color(0xFFFF7FA0), Color(0xFFB794FF),
    Color(0xFF5FD0EA), Color(0xFFF0CF4A), Color(0xFF9BD46A), Color(0xFFFF9466), Color(0xFF7FC6E0),
    Color(0xFFE08CF0), Color(0xFF9DB5C0),
)

private val LightFinance = FinanceColors(
    income = Color(0xFF0E8F5F),
    expense = Color(0xFFD0453A),
    transfer = Color(0xFF3F5BD8),
    warning = Color(0xFFD9822B),
    heroGradient = Brush.linearGradient(listOf(Color(0xFF0B3D2E), Color(0xFF0B7A5C), Color(0xFF19A97B))),
    onHero = Color.White,
    chartPalette = chartLight,
    track = Color(0xFFE3EAE6),
)

private val DarkFinance = FinanceColors(
    income = Color(0xFF5FD9AE),
    expense = Color(0xFFFF8A7E),
    transfer = Color(0xFF8FA2FF),
    warning = Color(0xFFFFB86B),
    heroGradient = Brush.linearGradient(listOf(Color(0xFF0C2A21), Color(0xFF0D5541), Color(0xFF138A66))),
    onHero = Color.White,
    chartPalette = chartDark,
    track = Color(0xFF29322E),
)

val LocalFinanceColors = staticCompositionLocalOf { LightFinance }

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val MoneyTextStyle = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)

@Composable
fun MyMoneyTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors: ColorScheme = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalFinanceColors provides if (dark) DarkFinance else LightFinance) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

object FinanceTheme {
    val colors: FinanceColors
        @Composable get() = LocalFinanceColors.current
}

@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background == DarkColors.background
