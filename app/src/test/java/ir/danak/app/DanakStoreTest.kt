package ir.danak.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import ir.danak.app.data.DanakStore
import ir.danak.app.data.UserPrefs
import ir.danak.app.model.Category
import ir.danak.app.model.ThemeMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DanakStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `an empty store reads as a first launch`() = runTest {
        assertEquals(UserPrefs(), newStore(folder).read())
    }

    @Test
    fun `everything written is read back, including saved order`() = runTest {
        val store = newStore(folder)
        val prefs = UserPrefs(
            interests = setOf(Category.History, Category.Science),
            hasChosenInterests = true,
            savedIds = listOf("c", "a", "b"),
            themeMode = ThemeMode.Light,
        )
        store.write(prefs)
        assertEquals(prefs, store.read())
    }

    @Test
    fun `unknown categories and themes from an older build are ignored`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(folder.root, "legacy.preferences_pb") },
        )
        dataStore.edit {
            it[stringSetPreferencesKey("interests")] = setOf("History", "Astrology")
            it[stringPreferencesKey("theme_mode")] = "Sepia"
        }
        val prefs = DanakStore(dataStore).read()
        assertEquals(setOf(Category.History), prefs.interests)
        assertEquals(ThemeMode.Dark, prefs.themeMode)
    }
}
