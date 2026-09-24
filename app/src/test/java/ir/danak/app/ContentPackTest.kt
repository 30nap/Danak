package ir.danak.app

import ir.danak.app.data.ContentPack
import ir.danak.app.model.Category
import ir.danak.app.model.DanakImage
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentPackTest {

    private fun entry(
        id: String,
        category: String = "science",
        sourceUrl: String = "https://fa.wikipedia.org/wiki/x",
        imageSrc: String = "images/$id.webp",
        extra: String = "",
    ) = """
        {
          "id": "$id", "category": "$category",
          "title": "عنوانی برای آزمون", "summary": "خلاصه‌ای کوتاه برای آزمون پارسر.",
          "sections": [{"body": "متنی برای بخش نخست."}, {"heading": "عنوان", "body": "متن دوم."}],
          "keyTakeaway": "یک نکتهٔ کلیدی.", "readingSeconds": 40,
          "source": {"url": "$sourceUrl", "publisher": "ویکی‌پدیا"},
          "image": {"src": "$imageSrc", "credit": {"author": "A", "license": "CC0", "pageUrl": "https://commons.wikimedia.org/wiki/File:x.jpg"}}
          $extra
        }
    """

    private fun pack(vararg entries: String) = """{"schemaVersion": 1, "danaks": [${entries.joinToString(",")}]}"""

    private fun parse(text: String) = ContentPack.parse(text) { DanakImage.Asset("content/$it") }

    @Test
    fun `a valid entry maps onto the app model`() {
        val danak = parse(pack(entry("a"))).danaks.single()
        assertEquals(Category.Science, danak.category)
        assertEquals(null, danak.sections[0].heading)
        assertEquals("عنوان", danak.sections[1].heading)
        assertEquals("ویکی‌پدیا", danak.sourceName)
        assertEquals(DanakImage.Asset("content/images/a.webp"), danak.image)
        assertEquals("CC0", danak.photoCredit?.license)
    }

    @Test
    fun `fields from a newer schema are ignored`() {
        val parsed = parse(pack(entry("a", extra = """, "futureField": {"x": 1}""")))
        assertEquals(listOf("a"), parsed.danaks.map { it.id })
    }

    @Test
    fun `one broken entry does not take the pack down`() {
        val parsed = parse(pack(entry("a"), """{"id": "broken"}""", entry("b")))
        assertEquals(listOf("a", "b"), parsed.danaks.map { it.id })
        assertEquals(listOf("broken"), parsed.rejected)
    }

    @Test
    fun `an unknown category is skipped rather than mislabelled`() {
        val parsed = parse(pack(entry("a", category = "astrology"), entry("b")))
        assertEquals(listOf("b"), parsed.danaks.map { it.id })
    }

    @Test
    fun `a duplicate id keeps the first entry`() {
        val parsed = parse(pack(entry("a"), entry("a", category = "history")))
        assertEquals(Category.Science, parsed.danaks.single().category)
        assertEquals(listOf("a"), parsed.rejected)
    }

    @Test
    fun `only https links get through`() {
        val parsed = parse(
            pack(
                entry("ok"),
                entry("intent", sourceUrl = "intent://evil#Intent;end"),
                entry("plain", sourceUrl = "http://example.com"),
                entry("traversal", imageSrc = "images/../../secret.webp"),
                entry("file", imageSrc = "file:///data/x.webp"),
            ),
        )
        assertEquals(listOf("ok"), parsed.danaks.map { it.id })
    }

    @Test
    fun `an absolute image url is used as it is`() {
        val danak = parse(pack(entry("a", imageSrc = "https://example.org/a.webp"))).danaks.single()
        assertEquals(DanakImage.Remote("https://example.org/a.webp"), danak.image)
    }

    @Test(expected = SerializationException::class)
    fun `a pack from another schema version is refused`() {
        parse("""{"schemaVersion": 2, "danaks": []}""")
    }

    @Test
    fun `categories are spread out in first-seen order`() {
        val parsed = parse(
            pack(
                entry("s1", "science"), entry("s2", "science"), entry("s3", "science"),
                entry("h1", "history"), entry("h2", "history"),
                entry("t1", "technology"),
            ),
        )
        assertEquals(listOf("s1", "h1", "t1", "s2", "h2", "s3"), parsed.danaks.map { it.id })
    }
}
