package ir.danak.app

import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import ir.danak.app.data.danakDataStore
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * State now persists, so every journey must start as a first launch. Runs before the
 * activity is created; clearing through DataStore (rather than deleting the file) also
 * resets the in-memory cache of the process-wide instance.
 */
class ClearAppStateRule : ExternalResource() {
    override fun before() = runBlocking {
        InstrumentationRegistry.getInstrumentation().targetContext.danakDataStore.edit { it.clear() }
        Unit
    }
}
