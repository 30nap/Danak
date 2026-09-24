package ir.danak.app

import ir.danak.app.FakeSite.Published
import ir.danak.app.data.ContentCache
import ir.danak.app.data.ContentFetcher
import ir.danak.app.data.ContentRepository
import ir.danak.app.data.ContentRepository.Refresh
import ir.danak.app.data.RemoteConfig
import ir.danak.app.model.Danak
import ir.danak.app.model.DanakImage
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.UnknownHostException

/**
 * Published content against a local fake of the site: every way an update can fail must
 * leave the app on its previous valid content, and every valid update must land whole.
 */
class ContentRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var site: FakeSite
    private lateinit var base: String
    private lateinit var cacheDir: File
    private val bundled = FakeSite.bundled("b1" to "science", "b2" to "history", "b3" to "psychology")

    @Before
    fun setUp() {
        site = FakeSite()
        base = site.baseUrl
        cacheDir = folder.newFolder("cache")
    }

    @After
    fun tearDown() {
        runCatching { site.shutdown() }
    }

    private fun repository(
        client: OkHttpClient = ContentFetcher.defaultClient(),
        online: Boolean = true,
        imageDecodes: (File) -> Boolean = { true },
    ) = ContentRepository(
        loadBundled = { bundled },
        cache = ContentCache(cacheDir),
        fetcher = if (online) ContentFetcher(RemoteConfig(base, requireHttps = false), client) else null,
        imageDecodes = imageDecodes,
    )

    private fun ids(danaks: List<Danak>) = danaks.map { it.id }
    private fun List<Danak>.byId(id: String) = first { it.id == id }
    private fun ContentRepository.refreshNow() = runBlocking { refresh() }
    private fun ContentRepository.local() = runBlocking { loadLocal() }

    private fun assertFailedAndUnchanged(repo: ContentRepository, before: List<Danak>) {
        val result = repo.refreshNow()
        assertTrue("expected Failed, got $result", result is Refresh.Failed)
        assertEquals(ids(before), ids(repo.local()))
    }

    // ------------------------------------------------------------------ first launch

    @Test
    fun `offline first launch shows the bundle`() {
        site.shutdown()
        val repo = repository()
        assertEquals(ids(bundled), ids(repo.local()))
        assertTrue(repo.refreshNow() is Refresh.Failed)
        assertEquals(ids(bundled), ids(repo.local()))
    }

    @Test
    fun `a dns failure is a failed refresh, nothing more`() {
        val noDns = ContentFetcher.defaultClient().newBuilder()
            .dns(object : Dns { override fun lookup(hostname: String) = throw UnknownHostException(hostname) })
            .build()
        site.publish(Published("b1"))
        assertFailedAndUnchanged(repository(client = noDns), bundled)
    }

    @Test
    fun `http errors leave the bundle in place`() {
        site.publish(Published("b1"))
        for (code in listOf(500, 503, 404, 403)) {
            site.fault = { if (it == "index.json") MockResponse().setResponseCode(code) else null }
            assertFailedAndUnchanged(repository(), bundled)
        }
    }

    @Test
    fun `redirects are not followed`() {
        site.publish(Published("b1"))
        site.fault = {
            if (it == "index.json") MockResponse().setResponseCode(302).setHeader("Location", "https://example.com/v1/index.json") else null
        }
        assertFailedAndUnchanged(repository(), bundled)
    }

    // ------------------------------------------------------------------ bad index

    @Test
    fun `malformed indexes are refused`() {
        val sha = "a".repeat(64)
        val bad = listOf(
            "not json",
            "[]",
            """{"schemaVersion":2,"danaks":[{"id":"x","path":"content/x_aaaaaaaa.json","sha256":"$sha"}]}""",
            """{"schemaVersion":1,"danaks":[]}""",
            """{"schemaVersion":1,"danaks":[{"id":"x","path":"content/x_aaaaaaaa.json","sha256":"$sha"},{"id":"x","path":"content/x_aaaaaaaa.json","sha256":"$sha"}]}""",
            """{"schemaVersion":1,"danaks":[{"id":"x","path":"../../secret.json","sha256":"$sha"}]}""",
            """{"schemaVersion":1,"danaks":[{"id":"x","path":"https://evil.example/x.json","sha256":"$sha"}]}""",
            """{"schemaVersion":1,"danaks":[{"id":"x","path":"/v1/content/x_aaaaaaaa.json","sha256":"$sha"}]}""",
            """{"schemaVersion":1,"danaks":[{"id":"x","path":"content/y_aaaaaaaa.json","sha256":"$sha"}]}""",
            """{"schemaVersion":1,"danaks":[{"id":"X!","path":"content/X!_aaaaaaaa.json","sha256":"$sha"}]}""",
            """{"schemaVersion":1,"danaks":[{"id":"x","path":"content/x_aaaaaaaa.json","sha256":"short"}]}""",
        )
        for (index in bad) {
            site.files.clear()
            site.files["index.json"] = index.toByteArray()
            assertFailedAndUnchanged(repository(), bundled)
        }
        assertTrue("no content file may be requested", site.contentRequests().isEmpty())
    }

    @Test
    fun `only https published content is accepted`() {
        assertThrows(IllegalArgumentException::class.java) { RemoteConfig("http://30nap.github.io/Danak/v1/") }
        assertThrows(IllegalArgumentException::class.java) { RemoteConfig("https://30nap.github.io/Danak/") }
        RemoteConfig.Production // the production URL itself passes its own checks
    }

    // ------------------------------------------------------------------ bad files

    @Test
    fun `a hash mismatch rejects the whole update`() {
        site.publish(Published("r1"), Published("b1", claimedSha = "0".repeat(64)))
        assertFailedAndUnchanged(repository(), bundled)
    }

    @Test
    fun `an oversized file rejects the whole update`() {
        val big = FakeSite.danakJson("r1", "science", "عنوان " + "ب".repeat(40_000), "images/r1_00000000.webp")
        site.publish(Published("r1", rawJson = big))
        assertFailedAndUnchanged(repository(), bundled)
    }

    @Test
    fun `a malformed danak never replaces a bundled one`() {
        site.publish(
            Published("b1", rawJson = """{"id":"b1","title":"بی‌بخش"}"""),
            Published("b2", category = "astrology"),
            Published("r1"),
            Published("r2", rawJson = "{ not json"),
        )
        val result = repository().refreshNow() as Refresh.Updated
        // b1 and b2 are invalid as published: the bundled versions stay. r2 is invalid and
        // has no bundled version: it is left out. r1 is new and valid.
        assertEquals(setOf("b1", "b2", "r1"), ids(result.danaks).toSet())
        assertEquals("عنوان بسته‌ای b1", result.danaks.byId("b1").title)
        assertEquals("عنوان بسته‌ای b2", result.danaks.byId("b2").title)
    }

    @Test
    fun `an index entry whose file carries another id is not used`() {
        site.publish(Published("r1", rawJson = FakeSite.danakJson("b3", "science", "عنوانی دیگر", "images/b3.webp")))
        assertTrue(repository().refreshNow() is Refresh.Failed) // nothing usable in the index
    }

    // ------------------------------------------------------------------ images

    @Test
    fun `a missing or broken photo costs the photo, not the danak`() {
        site.publish(
            Published("b1", serveImage = false),                                  // 404
            Published("r1", serveImage = false),                                  // 404, no bundled photo
            Published("r2", image = "GIF89a not a webp".toByteArray()),           // wrong format
            Published("r3", imageSrc = "images/r3_00000000.webp"),                // hash8 does not match
            Published("r4", imageSrc = "https://example.com/r4.webp"),            // hosted elsewhere
            Published("r5"),                                                      // fine
        )
        site.files["images/r3_00000000.webp"] = FakeSite.webp("r3")               // served, wrong bytes
        val danaks = (repository().refreshNow() as Refresh.Updated).danaks
        assertEquals(DanakImage.Asset("content/images/b1.webp"), danaks.byId("b1").image)
        for (id in listOf("r1", "r2", "r3", "r4")) assertEquals(id, DanakImage.None, danaks.byId(id).image)
        assertTrue(danaks.byId("r5").image is DanakImage.Cached)
        assertFalse("hosted-elsewhere photos are never fetched", site.requests.any { "example.com" in it })
    }

    @Test
    fun `a photo that does not decode is not used`() {
        site.publish(Published("r1"))
        val danaks = (repository(imageDecodes = { false }).refreshNow() as Refresh.Updated).danaks
        assertEquals(DanakImage.None, danaks.byId("r1").image)
    }

    @Test
    fun `a photo that fails on the network stops the update, to be retried`() {
        site.publish(Published("r1"))
        site.fault = { if (it.startsWith("images/")) MockResponse().setResponseCode(503) else null }
        assertFailedAndUnchanged(repository(), bundled)
    }

    // ------------------------------------------------------------------ interrupted updates

    @Test
    fun `a partial download is never used and the next refresh resumes`() {
        site.publish(Published("r1"), Published("r2"), Published("r3"))
        site.fault = {
            if (it.startsWith("content/r3_")) MockResponse().setBody("{\"id\":\"r3\"").setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY) else null
        }
        val repo = repository()
        assertFailedAndUnchanged(repo, bundled)

        site.fault = { null }
        site.requests.clear()
        val result = repo.refreshNow() as Refresh.Updated
        assertEquals(setOf("r1", "r2", "r3"), ids(result.danaks).toSet())
        assertEquals("only the file that failed is fetched again", 1, site.contentRequests().size)
    }

    @Test
    fun `a failed update keeps the previous published set`() {
        site.publish(Published("r1"), Published("b1", title = "نسخهٔ منتشرشدهٔ نخست"))
        val repo = repository()
        val first = (repo.refreshNow() as Refresh.Updated).danaks

        site.publish(Published("r1"), Published("r2"), Published("b1", claimedSha = "f".repeat(64)))
        assertFailedAndUnchanged(repo, first)
        assertEquals("نسخهٔ منتشرشدهٔ نخست", repo.local().byId("b1").title)
    }

    @Test
    fun `an empty index is refused and the previous set stays`() {
        site.publish(Published("r1"))
        val repo = repository()
        val first = (repo.refreshNow() as Refresh.Updated).danaks
        site.files["index.json"] = """{"schemaVersion":1,"danaks":[]}""".toByteArray()
        assertFailedAndUnchanged(repo, first)
    }

    // ------------------------------------------------------------------ merge rules

    @Test
    fun `a published danak replaces the bundled one with the same id`() {
        site.publish(Published("b1", title = "نسخهٔ تازهٔ b1"), Published("b2"), Published("b3", category = "psychology"))
        val danaks = (repository().refreshNow() as Refresh.Updated).danaks
        assertEquals("نسخهٔ تازهٔ b1", danaks.byId("b1").title)
        assertTrue(danaks.byId("b1").image is DanakImage.Cached)
    }

    @Test
    fun `a published-only danak is added`() {
        site.publish(Published("b1"), Published("new_one", category = "technology"))
        assertTrue("new_one" in ids((repository().refreshNow() as Refresh.Updated).danaks))
    }

    @Test
    fun `the feed follows the index order, spread by category`() {
        site.publish(
            Published("s1", "science"), Published("s2", "science"),
            Published("h1", "history"), Published("t1", "technology"),
        )
        assertEquals(listOf("s1", "h1", "t1", "s2"), ids((repository().refreshNow() as Refresh.Updated).danaks))
    }

    @Test
    fun `a danak taken out of the index leaves the feed and the cache`() {
        site.publish(Published("b1"), Published("r1"), Published("r2"))
        val repo = repository()
        repo.refreshNow()
        site.publish(Published("b1"), Published("r2"))
        val danaks = (repo.refreshNow() as Refresh.Updated).danaks
        assertEquals(listOf("b1", "r2"), ids(danaks))
        // b2 and b3 are still in the bundle but not in the published feed: the published set
        // defines the feed. Their bundled files are untouched.
        assertEquals(2, File(cacheDir, "objects").list()!!.size)
        assertEquals(2, File(cacheDir, "images").list()!!.size)
        assertTrue(File(cacheDir, "tmp").listFiles().orEmpty().isEmpty())
    }

    // ------------------------------------------------------------------ restart and cache

    @Test
    fun `a restart uses the cached set without the network`() {
        site.publish(Published("b1", title = "نسخهٔ منتشرشده"), Published("r1"))
        repository().refreshNow()
        site.shutdown()
        val restarted = repository(online = false).local()
        assertEquals(setOf("b1", "r1"), ids(restarted).toSet())
        assertEquals("نسخهٔ منتشرشده", restarted.byId("b1").title)
    }

    @Test
    fun `a damaged cache falls back to the bundle and heals on the next refresh`() {
        site.publish(Published("b1", title = "نسخهٔ منتشرشده"), Published("r1"))
        repository().refreshNow()
        File(cacheDir, "objects").listFiles()!!.first().writeText("{\"tampered\":true}")

        val repo = repository()
        assertEquals(ids(bundled), ids(repo.local()))
        assertTrue(repo.refreshNow() is Refresh.Updated)
        assertEquals(setOf("b1", "r1"), ids(repo.local()).toSet())
    }

    @Test
    fun `an unchanged index is answered with 304 and nothing is downloaded`() {
        site.etag = "\"v1\""
        site.publish(Published("r1"), Published("r2"))
        val repo = repository()
        repo.refreshNow()
        site.requests.clear()
        assertEquals(Refresh.UpToDate, repo.refreshNow())
        assertEquals(listOf("index.json"), site.requests)
    }

    @Test
    fun `without an etag an identical index still downloads nothing`() {
        site.publish(Published("r1"), Published("r2"))
        val repo = repository()
        repo.refreshNow()
        site.requests.clear()
        assertEquals(Refresh.UpToDate, repo.refreshNow())
        assertEquals(listOf("index.json"), site.requests)
    }

    @Test
    fun `an update downloads only what changed`() {
        site.publish(Published("r1"), Published("r2"), Published("r3"))
        val repo = repository()
        repo.refreshNow()
        site.requests.clear()
        site.publish(Published("r1"), Published("r2", title = "عنوان ویرایش‌شده"), Published("r3"), Published("r4"))
        val danaks = (repo.refreshNow() as Refresh.Updated).danaks
        assertEquals("عنوان ویرایش‌شده", danaks.byId("r2").title)
        assertEquals(2, site.contentRequests().size) // r2 (changed) and r4 (new)
        assertEquals(1, site.imageRequests().size)   // r4's photo; r2's photo did not change
    }

    @Test
    fun `published content switched off means bundle only, and no requests`() {
        site.publish(Published("r1"))
        val repo = repository(online = false)
        assertEquals(Refresh.UpToDate, repo.refreshNow())
        assertEquals(ids(bundled), ids(repo.local()))
        assertTrue(site.requests.isEmpty())
    }
}
