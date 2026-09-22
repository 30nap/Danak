package ir.danak.app

import ir.danak.app.data.MockDanaks
import ir.danak.app.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the content itself — these are the mistakes that are easy to make by hand. */
class MockDanaksTest {

    private val all = MockDanaks.all

    @Test
    fun `ships at least twenty danaks`() {
        assertTrue("expected 20+ danaks, found ${all.size}", all.size >= 20)
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
}
