package ir.danak.app

import ir.danak.app.ui.util.formatReadingTime
import ir.danak.app.ui.util.formatReadingTimeShort
import ir.danak.app.ui.util.sourceDomain
import ir.danak.app.ui.util.toPersianDigits
import org.junit.Assert.assertEquals
import org.junit.Test

class PersianTextTest {

    @Test
    fun `digits are converted to Persian`() {
        assertEquals("۴۵", 45.toPersianDigits())
        assertEquals("۱۰۲۴", "1024".toPersianDigits())
        assertEquals("۱.۰.۰", "1.0.0".toPersianDigits())
    }

    @Test
    fun `non digit characters are left alone`() {
        assertEquals("نسخهٔ ۱", "نسخهٔ 1".toPersianDigits())
        assertEquals("بدون عدد", "بدون عدد".toPersianDigits())
    }

    @Test
    fun `reading time under a minute is shown in seconds`() {
        assertEquals("۴۵ ثانیه", formatReadingTime(45))
        assertEquals("۵۹ ثانیه", formatReadingTime(59))
    }

    @Test
    fun `whole minutes drop the seconds part`() {
        assertEquals("۱ دقیقه", formatReadingTime(60))
        assertEquals("۳ دقیقه", formatReadingTime(180))
    }

    @Test
    fun `mixed durations keep both parts`() {
        assertEquals("۱ دقیقه و ۳۰ ثانیه", formatReadingTime(90))
    }

    @Test
    fun `short form rounds to whole minutes`() {
        assertEquals("۴۵ ثانیه", formatReadingTimeShort(45))
        assertEquals("۱ دقیقه", formatReadingTimeShort(70))
        assertEquals("۲ دقیقه", formatReadingTimeShort(100))
    }

    @Test
    fun `source domain drops scheme path and www`() {
        assertEquals("en.wikipedia.org", sourceDomain("https://en.wikipedia.org/wiki/Honey"))
        assertEquals("example.org", sourceDomain("https://www.example.org/a/b?c=d"))
    }
}
