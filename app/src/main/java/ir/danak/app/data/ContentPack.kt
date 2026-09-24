package ir.danak.app.data

import ir.danak.app.model.Category
import ir.danak.app.model.Danak
import ir.danak.app.model.DanakImage
import ir.danak.app.model.DanakSection
import ir.danak.app.model.PhotoCredit
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads a content pack: the JSON form of a set of Danaks, as described by
 * `schema/danak-v1.schema.json`. The same format is bundled in the APK and, later,
 * published, so both go through here.
 *
 * Parsing is per Danak: one malformed or unknown entry is dropped and reported, not
 * allowed to take the whole pack down with it.
 */
object ContentPack {

    const val SCHEMA_VERSION = 1

    /** The only relative image path a pack may use, as in the schema. */
    private val RELATIVE_IMAGE = Regex("images/[a-z0-9_]+\\.(webp|jpg|png)")

    private val json = Json {
        // Newer packs may add fields; an older app should still read what it knows.
        ignoreUnknownKeys = true
    }

    /**
     * @param resolveImage turns a pack-relative image path (`images/x.webp`) into
     * something Coil can load; absolute https URLs never reach it.
     */
    fun parse(text: String, resolveImage: (String) -> DanakImage): Parsed {
        val root = json.parseToJsonElement(text) as? JsonObject
            ?: throw SerializationException("a content pack is a JSON object")
        val version = root["schemaVersion"]?.jsonPrimitive?.int
        if (version != SCHEMA_VERSION) {
            throw SerializationException("unsupported schemaVersion $version")
        }
        val danaks = mutableListOf<Danak>()
        val rejected = mutableListOf<String>()
        for (element in root["danaks"]?.jsonArray.orEmpty()) {
            val danak = runCatching { toDanak(element, resolveImage) }.getOrNull()
            if (danak == null || danaks.any { it.id == danak.id }) {
                rejected += (element as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull ?: "(no id)"
            } else {
                danaks += danak
            }
        }
        return Parsed(interleaveByCategory(danaks), rejected)
    }

    class Parsed(val danaks: List<Danak>, val rejected: List<String>)

    private fun toDanak(element: JsonElement, resolveImage: (String) -> DanakImage): Danak? {
        val entry = json.decodeFromJsonElement<DanakJson>(element)
        // A category this version does not know yet: skip it rather than mislabel it.
        val category = Category.fromId(entry.category) ?: return null
        val texts = listOf(entry.id, entry.title, entry.summary, entry.keyTakeaway, entry.source.publisher) +
            entry.sections.map { it.body }
        if (texts.any { it.isBlank() } || entry.sections.isEmpty() || entry.readingSeconds <= 0) return null
        // Both links are handed to ACTION_VIEW, and the image may be fetched: nothing but
        // https gets through, so a bad pack can never smuggle in an intent: or file: URI.
        val links = listOf(entry.source.url, entry.image.credit.pageUrl)
        if (links.any { !it.startsWith("https://") }) return null
        if (!entry.image.src.startsWith("https://") && !RELATIVE_IMAGE.matches(entry.image.src)) return null
        return Danak(
            id = entry.id,
            category = category,
            title = entry.title,
            summary = entry.summary,
            sections = entry.sections.map { DanakSection(heading = it.heading, body = it.body) },
            readingSeconds = entry.readingSeconds,
            image = entry.image.src.let { src ->
                if (src.startsWith("https://")) DanakImage.Remote(src) else resolveImage(src)
            },
            sourceName = entry.source.publisher,
            sourceUrl = entry.source.url,
            keyTakeaway = entry.keyTakeaway,
            photoCredit = entry.image.credit.let {
                PhotoCredit(author = it.author, license = it.license, pageUrl = it.pageUrl)
            },
        )
    }

    /**
     * Spreads categories out across the feed, round-robin in order of first appearance.
     * Without it, content written topic by topic would open on six psychology Danaks in a
     * row and the feed would feel narrower than it is.
     */
    internal fun interleaveByCategory(items: List<Danak>): List<Danak> {
        val buckets = items.groupBy { it.category }.values.map { it.toMutableList() }
        val result = ArrayList<Danak>(items.size)
        while (result.size < items.size) {
            for (bucket in buckets) {
                if (bucket.isNotEmpty()) result += bucket.removeAt(0)
            }
        }
        return result
    }
}

// The wire format, field for field with schema v1. Fields the app does not use yet
// (source title, licence, snapshot hash) are left to ignoreUnknownKeys.

@Serializable
private class DanakJson(
    val id: String,
    val category: String,
    val title: String,
    val summary: String,
    val sections: List<SectionJson>,
    val keyTakeaway: String,
    val readingSeconds: Int,
    val source: SourceJson,
    val image: ImageJson,
)

@Serializable
private class SectionJson(val heading: String? = null, val body: String)

@Serializable
private class SourceJson(val url: String, val publisher: String)

@Serializable
private class ImageJson(val src: String, val credit: CreditJson)

@Serializable
private class CreditJson(val author: String, val license: String, val pageUrl: String)
