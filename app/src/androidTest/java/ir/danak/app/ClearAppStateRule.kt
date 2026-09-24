package ir.danak.app

import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import ir.danak.app.data.RemoteConfig
import ir.danak.app.data.RemoteContentSettings
import ir.danak.app.data.danakDataStore
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import java.io.File

/**
 * State now persists, so every journey must start as a first launch. Runs before the
 * activity is created; clearing through DataStore (rather than deleting the file) also
 * resets the in-memory cache of the process-wide instance.
 *
 * Downloaded content is wiped too, and the app is pointed at [remote] — by default nowhere,
 * so a journey runs on the bundled content alone and never depends on the live site.
 */
class ClearAppStateRule(private val remote: RemoteConfig? = null) : ExternalResource() {
    override fun before() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.danakDataStore.edit { it.clear() }
        File(context.noBackupFilesDir, "content").deleteRecursively()
        RemoteContentSettings.config = remote
    }
}
