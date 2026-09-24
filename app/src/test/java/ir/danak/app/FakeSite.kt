package ir.danak.app

import ir.danak.app.data.ContentPack
import ir.danak.app.model.Danak
import ir.danak.app.model.DanakImage
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import java.security.MessageDigest

/**
 * A published site on a local MockWebServer, built the way tools/danak_content.py builds
 * the real one: content-addressed files under /v1/content and /v1/images, and an index.
 * Tests publish sets of Danaks, then break things on purpose.
 */
class FakeSite {
    val server = MockWebServer()
    val baseUrl: String get() = server.url("/v1/").toString()

    /** Served files, by path under /v1/. */
    val files = mutableMapOf<String, ByteArray>()

    /** Paths requested, in order (index requests included). */
    val requests = mutableListOf<String>()

    /** Answer an index request with this ETag, and 304 when it comes back; null for none. */
    var etag: String? = null

    /** Overrides the response for a path; return null to serve normally. */
    var fault: (path: String) -> MockResponse? = { null }

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!.removePrefix("/v1/")
                synchronized(requests) { requests += path }
                fault(path)?.let { return it }
                val body = files[path] ?: return MockResponse().setResponseCode(404)
                if (path == "index.json" && etag != null) {
                    if (request.getHeader("If-None-Match") == etag) return MockResponse().setResponseCode(304)
                    return MockResponse().setBody(Buffer().write(body)).setHeader("ETag", etag!!)
                }
                return MockResponse().setBody(Buffer().write(body))
            }
        }
        server.start()
    }

    fun shutdown() = server.shutdown()

    fun contentRequests() = requests.filter { it.startsWith("content/") }
    fun imageRequests() = requests.filter { it.startsWith("images/") }

    /** Publishes [danaks] as the whole site, in this order; returns the index entries' paths. */
    fun publish(vararg danaks: Published): List<String> {
        files.clear()
        val entries = danaks.map { d ->
            val imageName = "${d.id}_${sha256(d.image).take(8)}.webp"
            if (d.serveImage) files["images/$imageName"] = d.image
            val body = (d.rawJson ?: danakJson(d.id, d.category, d.title, d.imageSrc ?: "images/$imageName")).toByteArray()
            val sha = d.claimedSha ?: sha256(body)
            val path = "content/${d.id}_${sha.take(8)}.json"
            files[path] = body
            """{"id":"${d.id}","path":"$path","sha256":"$sha"}"""
        }
        files["index.json"] = """{"schemaVersion":1,"danaks":[${entries.joinToString(",")}]}""".toByteArray()
        return entries
    }

    class Published(
        val id: String,
        val category: String = "science",
        val title: String = "عنوان منتشرشدهٔ $id",
        val image: ByteArray = webp(id),
        val serveImage: Boolean = true,
        val imageSrc: String? = null,
        val rawJson: String? = null,
        val claimedSha: String? = null,
    )

    companion object {
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** Bytes with a WebP header; image decoding itself is stubbed in JVM tests. */
        fun webp(seed: String): ByteArray = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 $seed".toByteArray()

        fun danakJson(id: String, category: String, title: String, imageSrc: String) = """
            {"id":"$id","category":"$category","title":"$title",
             "summary":"خلاصهٔ کوتاه این دانک برای آزمون.",
             "sections":[{"body":"متن نخست این دانک."}],
             "keyTakeaway":"نکتهٔ کلیدی.","readingSeconds":40,
             "source":{"url":"https://fa.wikipedia.org/wiki/$id","publisher":"ویکی‌پدیا","license":"CC BY-SA 4.0"},
             "image":{"src":"$imageSrc","credit":{"author":"A","license":"CC0","pageUrl":"https://commons.wikimedia.org/wiki/File:$id.jpg"}}}
        """.trimIndent()

        /** A small bundled pack: the baseline the tests fall back to. */
        fun bundled(vararg ids: Pair<String, String>): List<Danak> {
            val pack = """{"schemaVersion":1,"danaks":[${
                ids.joinToString(",") { (id, category) -> danakJson(id, category, "عنوان بسته‌ای $id", "images/$id.webp") }
            }]}"""
            return ContentPack.parse(pack) { DanakImage.Asset("content/$it") }.danaks
        }
    }
}
