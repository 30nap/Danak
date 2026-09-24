package ir.danak.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
    primary = DanakGreenOnLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F5E1),
    onPrimaryContainer = Color(0xFF0C3E22),
    secondary = DanakGreenOnLight,
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

/**
 * Metadata that should sit back — a source's domain, a photo credit, a timestamp. Fading
 * the muted colour with alpha took these to 2–3:1, so this is a solid colour chosen to be
 * as quiet as possible while still reading at 4.5:1 on the page and card backgrounds.
 */
val ColorScheme.faintText: Color
    get() = if (background.luminance() < 0.5f) NightTextFaint else DayTextFaint

/** Whether [mode] resolves to dark right now. */
@Composable
fun ThemeMode.resolvesToDark(): Boolean = when (this) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System -> isSystemInDarkTheme()
}

@Composable
fun DanakTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val colors = if (themeMode.resolvesToDark()) DanakDarkColors else DanakLightColors

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

/**
 * The feed and the detail are always dark, whatever the app theme. Their artwork is built
 * for a dark ground: over a light background the gradient turns muddy grey and the chip
 * and top-bar controls lose their contrast. The chosen theme still applies to every list
 * and settings screen.
 */
@Composable
fun ImmersiveSurface(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DanakDarkColors,
        typography = DanakTypography,
        shapes = DanakShapes,
        content = content,
    )
}

/** Keeps the status and navigation bar icons readable over whatever is behind them. */
@Composable
fun SystemBarsEffect(darkBackground: Boolean) {
    val context = LocalContext.current
    SideEffect {
        val activity = context.findActivity() ?: return@SideEffect
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !darkBackground
            isAppearanceLightNavigationBars = !darkBackground
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
