package ir.danak.app

import ir.danak.app.ui.util.browsableUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {

    @Test
    fun `persian paths are percent encoded as utf-8`() {
        assertEquals(
            "https://fa.wikipedia.org/wiki/%D8%B9%D8%B3%D9%84",
            browsableUrl("https://fa.wikipedia.org/wiki/عسل"),
        )
    }

    @Test
    fun `already encoded english links are not double encoded`() {
        val url = browsableUrl("https://en.wikipedia.org/wiki/Parkinson%27s_law")
        assertFalse(url, url.contains("%25"))
        assertTrue(url, url.startsWith("https://en.wikipedia.org/wiki/Parkinson"))
    }

    @Test
    fun `every shipped source becomes a plain ascii url`() {
        for (danak in TestContent.all) {
            val url = browsableUrl(danak.sourceUrl)
            assertTrue("${danak.id}: $url", url.all { it.code < 128 })
        }
    }
}
