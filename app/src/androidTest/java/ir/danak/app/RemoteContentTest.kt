package ir.danak.app

import android.graphics.BitmapFactory
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.danak.app.data.BundledContent
import ir.danak.app.data.ContentCache
import ir.danak.app.data.ContentFetcher
import ir.danak.app.data.ContentRepository
import ir.danak.app.data.RemoteConfig
import ir.danak.app.model.Category
import ir.danak.app.model.DanakImage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Published content end to end on a device: a local server stands in for GitHub Pages,
 * serving the same structure tools/danak_content.py publishes. The site is set up before
 * the activity starts, because the app checks for content as soon as it comes up.
 */
@RunWith(AndroidJUnit4::class)
class RemoteContentTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val edited = "چرا آسمان آبی است؟ (نسخهٔ منتشرشده)"
    private val arrival = "دانکی تازه که فقط منتشر شده است"

    /** The index is held back until the reader is already on a Danak. */
    private val release = CountDownLatch(1)
    private val site = PublishedSite().apply {
        publish(
            bundled("remote_arrival") { put("title", arrival); put("category", "science") },
            bundled("blue_sky") { put("title", edited) },
            bundled("zeigarnik"),
        )
        holdIndexUntil(release)
    }
    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(ClearAppStateRule(remote = site.config)).around(rule)

    @After
    fun tearDown() {
        release.countDown()
        site.shutdown()
    }

    @Test
    fun publishedContentArrivesWithoutMovingTheReader() {
        rule.waitUntil(10_000) { rule.onAllNodesWithText(Category.Science.label).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(Category.Science.label).performClick()
        rule.onNodeWithText("ادامه").performClick()
        val bundledBlueSky = BundledContent.read(context).first { it.id == "blue_sky" }
        rule.onNodeWithText(bundledBlueSky.title).assertIsDisplayed()

        release.countDown()
        // blue_sky moves from the first place to the second (remote_arrival comes before
        // it), yet the reader stays on it — and now sees the published version.
        rule.waitUntil(15_000) { runCatching { rule.visibleNodeWithText(edited) }.isSuccess }
        UiAudit.screenshot(rule, "16_feed_after_refresh")

        rule.onRoot().performTouchInput { swipeDown() }
        rule.visibleNodeWithText(arrival).assertIsDisplayed()
        UiAudit.screenshot(rule, "17_published_only_danak")
    }
}

/** With the site unreachable, the app starts and reads exactly as it does on the bundle. */
@RunWith(AndroidJUnit4::class)
class OfflineStartupTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val site = PublishedSite().apply { shutdown() }
    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(ClearAppStateRule(remote = site.config)).around(rule)

    @Test
    fun anUnreachableSiteLeavesTheAppFullyUsable() {
        rule.waitUntil(10_000) { rule.onAllNodesWithText(Category.Science.label).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(Category.Science.label).performClick()
        rule.onNodeWithText("ادامه").performClick()
        val first = BundledContent.read(context).first { it.category == Category.Science }
        rule.onNodeWithText(first.title).assertIsDisplayed()
    }
}

/**
 * Not a behaviour test: measures a full sync of the whole published set on a device (real
 * files, real image decoding) and writes the numbers into the CI report.
 */
@RunWith(AndroidJUnit4::class)
class ContentSyncMeasurement {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun measureAFullSync() {
        val site = PublishedSite()
        site.publish(*BundledContent.read(context).map { site.bundled(it.id) }.toTypedArray())
        val dir = File(context.cacheDir, "measure").apply { deleteRecursively() }
        val cache = ContentCache(dir)
        val decodes: (File) -> Boolean = { file ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, options)
            options.outWidth > 0
        }
        val repository = ContentRepository({ BundledContent.load(context) }, cache, ContentFetcher(site.config), decodes)
        fun ms(block: () -> Unit): Long = System.nanoTime().let { start -> block(); (System.nanoTime() - start) / 1_000_000 }

        val bundledMs = ms { runBlocking { BundledContent.load(context) } }
        var refresh: ContentRepository.Refresh? = null
        val syncMs = ms { refresh = runBlocking { repository.refresh() } }
        val synced = (refresh as ContentRepository.Refresh.Updated).danaks
        assertEquals(32, synced.size)
        assertTrue(synced.all { it.image is DanakImage.Cached })
        val requests = site.requestCount()
        val restartMs = ms {
            runBlocking { ContentRepository({ BundledContent.load(context) }, ContentCache(dir), null, decodes).loadLocal() }
        }
        val secondMs = ms { runBlocking { repository.refresh() } }
        UiAudit.note(
            "[perf] bundled load ${bundledMs}ms; full sync of ${synced.size} danaks ${syncMs}ms, " +
                "$requests requests, ${cache.sizeOnDisk() / 1024} KB on disk; " +
                "restart from cache ${restartMs}ms; second refresh ${secondMs}ms, " +
                "${site.requestCount() - requests} request(s)",
        )
        dir.deleteRecursively()
        site.shutdown()
    }
}

/** Serves a published set from MockWebServer, built from the app's own bundled content. */
private class PublishedSite {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val server = MockWebServer()
    private val files = mutableMapOf<String, ByteArray>()
    private var hold: CountDownLatch? = null
    private var requests = 0

    val config: RemoteConfig

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!.removePrefix("/v1/")
                synchronized(this@PublishedSite) { requests++ }
                // Stays under the client's read timeout, so a slow test never looks offline.
                if (path == "index.json") hold?.await(15, TimeUnit.SECONDS)
                val body = files[path] ?: return MockResponse().setResponseCode(404)
                return MockResponse().setBody(Buffer().write(body))
            }
        }
        server.start()
        config = RemoteConfig(server.url("/v1/").toString(), requireHttps = false)
    }

    fun requestCount() = synchronized(this) { requests }

    fun holdIndexUntil(latch: CountDownLatch) {
        hold = latch
    }

    fun shutdown() = runCatching { server.shutdown() }

    private val pack: List<JsonObject> by lazy {
        val text = context.assets.open("content/content.json").bufferedReader().use { it.readText() }
        Json.parseToJsonElement(text).jsonObject["danaks"]!!.jsonArray.map { it.jsonObject }
    }

    /** A bundled Danak as JSON, optionally edited, under a (possibly new) id. */
    fun bundled(id: String, edit: MutableMap<String, String>.() -> Unit = {}): Pair<JsonObject, ByteArray> {
        val base = pack.firstOrNull { it["id"]!!.jsonPrimitive.content == id } ?: pack.first { it["category"]!!.jsonPrimitive.content == "science" }
        val imageBytes = context.assets.open("content/" + base["image"]!!.jsonObject["src"]!!.jsonPrimitive.content).use { it.readBytes() }
        val fields = mutableMapOf("id" to id).apply(edit).mapValues { JsonPrimitive(it.value) }
        return JsonObject(base + fields) to imageBytes
    }

    fun publish(vararg danaks: Pair<JsonObject, ByteArray>) {
        files.clear()
        val entries = danaks.map { (danak, image) ->
            val id = danak["id"]!!.jsonPrimitive.content
            val imageName = "${id}_${sha256(image).take(8)}.webp"
            files["images/$imageName"] = image
            val credit = danak["image"]!!.jsonObject["credit"]!!
            val published = JsonObject(danak + ("image" to JsonObject(mapOf("src" to JsonPrimitive("images/$imageName"), "credit" to credit))))
            val body = published.toString().toByteArray()
            val sha = sha256(body)
            val path = "content/${id}_${sha.take(8)}.json"
            files[path] = body
            """{"id":"$id","path":"$path","sha256":"$sha"}"""
        }
        files["index.json"] = """{"schemaVersion":1,"danaks":[${entries.joinToString(",")}]}""".toByteArray()
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
