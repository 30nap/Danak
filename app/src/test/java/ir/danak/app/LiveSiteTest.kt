package ir.danak.app

import ir.danak.app.data.ContentCache
import ir.danak.app.data.ContentFetcher
import ir.danak.app.data.ContentRepository
import ir.danak.app.data.ContentRepository.Refresh
import ir.danak.app.data.RemoteConfig
import ir.danak.app.model.DanakImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The app's own client against the live site. Skipped unless DANAK_LIVE_BASE_URL is set:
 * normal CI never depends on GitHub Pages; the Content workflow runs this after a deploy.
 */
class LiveSiteTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun theLiveSiteSyncsWholeAndThenStaysUpToDate() {
        val base = System.getenv("DANAK_LIVE_BASE_URL")
        assumeTrue("DANAK_LIVE_BASE_URL not set", !base.isNullOrBlank())
        val repository = ContentRepository(
            loadBundled = { TestContent.all },
            cache = ContentCache(folder.newFolder("cache")),
            fetcher = ContentFetcher(RemoteConfig(base!!)),
            imageDecodes = { true }, // headers and hashes are still checked; decoding needs a device
        )
        val first = runBlocking { repository.refresh() }
        assertTrue("first sync: $first", first is Refresh.Updated)
        val danaks = (first as Refresh.Updated).danaks
        assertTrue("published ${danaks.size}", danaks.size >= TestContent.all.size)
        assertTrue(danaks.all { it.image is DanakImage.Cached })
        assertEquals(Refresh.UpToDate, runBlocking { repository.refresh() })
        println("live: ${danaks.size} danaks synced and verified from $base")
    }
}
