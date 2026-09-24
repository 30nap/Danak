package ir.danak.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Danak's palette is built around one idea: the content is lit, everything else recedes.
 * The dark scheme is the reference design — the light scheme mirrors its structure rather
 * than inverting it channel by channel.
 */

// Brand
val DanakGreen = Color(0xFF5CE08C)
val DanakGreenDim = Color(0xFF3FBF6F)
val DanakGreenDeep = Color(0xFF14361F)

// The bright green only works on dark ground: on white it falls to 2.4:1. The light scheme
// uses this deeper green instead, which keeps white labels and green text above 4.5:1.
val DanakGreenOnLight = Color(0xFF187A43)

// Dark surfaces
val NightBase = Color(0xFF080A0B)
val NightSurface = Color(0xFF14181A)
val NightSurfaceHigh = Color(0xFF1E2325)
val NightOutline = Color(0xFF2C3335)
val NightText = Color(0xFFF2F5F3)
val NightTextMuted = Color(0xFFA5B0AC)
val NightTextFaint = Color(0xFF808B87)

// Light surfaces
val DayBase = Color(0xFFF7F9F7)
val DaySurface = Color(0xFFFFFFFF)
val DaySurfaceHigh = Color(0xFFEDF1EE)
val DayOutline = Color(0xFFD7DEDA)
val DayText = Color(0xFF101512)
val DayTextMuted = Color(0xFF5A6560)
val DayTextFaint = Color(0xFF646F6A)

// Per-category accents. Kept low-chroma so a chip never out-shouts the title.
val AccentTechnology = Color(0xFF6FB8FF)
val AccentProgramming = Color(0xFF7BE0D6)
val AccentScience = Color(0xFF9C8CFF)
val AccentPsychology = Color(0xFFFFB278)
val AccentEconomy = Color(0xFF63D88F)
val AccentHistory = Color(0xFFE08A8A)
val AccentProductivity = Color(0xFFFFD36E)
val AccentCuriosities = Color(0xFFB7C6D1)
