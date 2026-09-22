package ir.danak.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import ir.danak.app.R

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semi_bold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

/**
 * Persian needs more room between lines than Latin does — ascenders and descenders in
 * Vazirmatn overlap badly below roughly 1.5×, and body copy only becomes comfortable
 * around 1.8×. Every style here sets its line height explicitly for that reason.
 */
private fun persian(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = Vazirmatn,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    // Persian is not hyphenated, and paragraph-level breaking gives far better
    // line balance for long Persian sentences than the default simple strategy.
    hyphens = Hyphens.None,
    lineBreak = LineBreak.Paragraph,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

val DanakTypography = Typography(
    displayLarge = persian(40, 60, FontWeight.Bold),
    displayMedium = persian(34, 52, FontWeight.Bold),
    displaySmall = persian(30, 46, FontWeight.Bold),

    headlineLarge = persian(28, 44, FontWeight.Bold),
    headlineMedium = persian(24, 38, FontWeight.Bold),
    headlineSmall = persian(21, 34, FontWeight.SemiBold),

    titleLarge = persian(19, 31, FontWeight.SemiBold),
    titleMedium = persian(17, 28, FontWeight.SemiBold),
    titleSmall = persian(15, 25, FontWeight.Medium),

    bodyLarge = persian(16, 30, FontWeight.Normal),
    bodyMedium = persian(15, 28, FontWeight.Normal),
    bodySmall = persian(13, 24, FontWeight.Normal),

    labelLarge = persian(14, 22, FontWeight.Medium),
    labelMedium = persian(13, 20, FontWeight.Medium),
    labelSmall = persian(11, 18, FontWeight.Medium),
)
