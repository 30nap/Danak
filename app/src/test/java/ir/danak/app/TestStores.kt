package ir.danak.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import ir.danak.app.data.DanakStore
import ir.danak.app.data.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.rules.TemporaryFolder
import java.io.File

/** A real DataStore on a temp file, bound to the test's background scope. */
fun TestScope.newStore(folder: TemporaryFolder): DanakStore = DanakStore(
    PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { File(folder.root, "danak-${System.nanoTime()}.preferences_pb") },
    ),
)

/**
 * Waits in real time for a write to land. ViewModel writes are fire-and-forget, and
 * DataStore may finish them off the test scheduler, so virtual time cannot be used here.
 */
suspend fun DanakStore.awaitStored(predicate: (UserPrefs) -> Boolean): UserPrefs =
    withContext(Dispatchers.Default) {
        withTimeout(5_000) {
            var prefs = read()
            while (!predicate(prefs)) {
                delay(20)
                prefs = read()
            }
            prefs
        }
    }
