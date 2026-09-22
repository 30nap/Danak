package ir.danak.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import ir.danak.app.model.ThemeMode

private val DanakDarkColors = darkColorScheme(
    primary = DanakGreen,
    onPrimary = NightBase,
    primaryContainer = DanakGreenDeep,
    onPrimaryContainer = DanakGreen,
    secondary = DanakGreenDim,
    onSecondary = NightBase,
    background = NightBase,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = NightTextMuted,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceHigh,
    outline = NightOutline,
    outlineVariant = NightOutline,
    scrim = Color.Black,
)

private val DanakLightColors = lightColorScheme(
    primary = DanakGreenDim,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F5E1),
    onPrimaryContainer = Color(0xFF0C3E22),
    secondary = DanakGreenDim,
    onSecondary = Color.White,
    background = DayBase,
    onBackground = DayText,
    surface = DaySurface,
    onSurface = DayText,
    surfaceVariant = DaySurfaceHigh,
    onSurfaceVariant = DayTextMuted,
    surfaceContainer = DaySurface,
    surfaceContainerHigh = DaySurfaceHigh,
    outline = DayOutline,
    outlineVariant = DayOutline,
    scrim = Color.Black,
)

@Composable
fun DanakTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val colors = if (dark) DanakDarkColors else DanakLightColors

    val context = LocalContext.current
    SideEffect {
        val activity = context.findActivity() ?: return@SideEffect
        // The app draws edge to edge; only the system icon tint has to follow the theme.
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    // Danak is a Persian app end to end, so the layout direction is a product decision,
    // not a locale one.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colors,
            typography = DanakTypography,
            shapes = DanakShapes,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
