package ir.danak.app

import androidx.compose.ui.graphics.Color
import ir.danak.app.ui.theme.DanakGreen
import ir.danak.app.ui.theme.DanakGreenOnLight
import ir.danak.app.ui.theme.DayBase
import ir.danak.app.ui.theme.DaySurface
import ir.danak.app.ui.theme.DaySurfaceHigh
import ir.danak.app.ui.theme.DayTextFaint
import ir.danak.app.ui.theme.NightBase
import ir.danak.app.ui.theme.NightSurface
import ir.danak.app.ui.theme.NightSurfaceHigh
import ir.danak.app.ui.theme.NightTextFaint
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG AA asks for 4.5:1 for body-size text. These pairs are the ones that slipped below it
 * before (faded metadata, white on the light theme's green), so they are pinned here.
 */
class ContrastTest {

    @Test
    fun `faint metadata text stays readable on dark backgrounds`() {
        for (background in listOf(NightBase, NightSurface, NightSurfaceHigh)) {
            assertReadable(NightTextFaint, background)
        }
    }

    @Test
    fun `faint metadata text stays readable on light backgrounds`() {
        for (background in listOf(DayBase, DaySurface, DaySurfaceHigh)) {
            assertReadable(DayTextFaint, background)
        }
    }

    @Test
    fun `the light theme green works as a button and as text`() {
        assertReadable(Color.White, DanakGreenOnLight)
        assertReadable(DanakGreenOnLight, DayBase)
        assertReadable(DanakGreenOnLight, DaySurfaceHigh)
    }

    @Test
    fun `the dark theme green works as a button and as text`() {
        assertReadable(NightBase, DanakGreen)
        assertReadable(DanakGreen, NightSurface)
    }

    private fun assertReadable(foreground: Color, background: Color) {
        val ratio = contrast(foreground, background)
        assertTrue("contrast %.2f:1 is below 4.5:1".format(ratio), ratio >= 4.5)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(c: Float): Double =
            if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
