package ir.danak.app

import ir.danak.app.model.Category
import ir.danak.app.model.DanakImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the bundled content itself — these are the mistakes that are easy to make by hand. */
class BundledContentTest {

    private val all = TestContent.all

    @Test
    fun `every entry in the pack is read`() {
        assertTrue("rejected: ${TestContent.parsed.rejected}", TestContent.parsed.rejected.isEmpty())
    }

    @Test
    fun `every photo exists and every photo file is used`() {
        val referenced = all.map { (it.image as DanakImage.Asset).path.removePrefix("content/") }.toSet()
        for (path in referenced) {
            assertTrue("missing $path", File(TestContent.dir, path).isFile)
        }
        val files = File(TestContent.dir, "images").list().orEmpty().map { "images/$it" }.toSet()
        assertEquals("unused photos", emptySet<String>(), files - referenced)
    }

    @Test
    fun `ships at least thirty danaks`() {
        assertTrue("expected 30+ danaks, found ${all.size}", all.size >= 30)
    }

    @Test
    fun `every category has at least four danaks`() {
        val perCategory = all.groupingBy { it.category }.eachCount()
        Category.entries.forEach { assertTrue("$it has ${perCategory[it]}", (perCategory[it] ?: 0) >= 4) }
    }

    @Test
    fun `ids are unique`() {
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }

    @Test
    fun `every category is represented`() {
        assertEquals(Category.entries.toSet(), all.map { it.category }.toSet())
    }

    @Test
    fun `every danak is complete`() {
        for (danak in all) {
            assertTrue("${danak.id}: blank title", danak.title.isNotBlank())
            assertTrue("${danak.id}: blank summary", danak.summary.isNotBlank())
            assertTrue("${danak.id}: no sections", danak.sections.isNotEmpty())
            assertTrue("${danak.id}: blank source", danak.sourceName.isNotBlank())
            assertTrue(
                "${danak.id}: source url is not https",
                danak.sourceUrl.startsWith("https://"),
            )
            assertTrue("${danak.id}: reading time <= 0", danak.readingSeconds > 0)
            assertTrue(
                "${danak.id}: empty section body",
                danak.sections.all { it.body.isNotBlank() },
            )
        }
    }

    @Test
    fun `consecutive danaks do not repeat a category`() {
        val repeats = all.zipWithNext().filter { (a, b) -> a.category == b.category }
        assertTrue("categories clump at: ${repeats.map { it.first.id }}", repeats.isEmpty())
    }

    @Test
    fun `feed summaries stay within two or three lines`() {
        // ~45–50 Persian characters fit a line at the feed's body size on a typical phone.
        for (danak in all) {
            assertTrue("${danak.id}: ${danak.summary.length} chars", danak.summary.length <= 115)
        }
    }

    @Test
    fun `every danak ends on a key takeaway`() {
        for (danak in all) {
            assertTrue("${danak.id}: no key takeaway", !danak.keyTakeaway.isNullOrBlank())
        }
    }

    @Test
    fun `every photo is credited to its commons page`() {
        for (danak in all) {
            val credit = danak.photoCredit
            assertTrue("${danak.id}: no photo credit", credit != null)
            assertTrue("${danak.id}: ${credit!!.pageUrl}", credit.pageUrl.startsWith("https://commons.wikimedia.org/wiki/File:"))
            assertTrue("${danak.id}: blank licence", credit.license.isNotBlank())
        }
    }
}
