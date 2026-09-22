package ir.danak.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ir.danak.app.model.Category
import ir.danak.app.model.ThemeMode
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Everything about a user that has to survive the app being closed. */
data class UserPrefs(
    val interests: Set<Category> = emptySet(),
    val hasChosenInterests: Boolean = false,
    /** Oldest first; order is what "most recently saved" is built on. */
    val savedIds: List<String> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.Dark,
)

val Context.danakDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "danak",
    // A corrupt file costs the user their saved list, not the ability to open the app.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * The one place Danak reads and writes persistent state. It stores a single snapshot,
 * because every write in V0 is small and replacing the whole record is simpler to reason
 * about than patching individual keys.
 */
class DanakStore(private val dataStore: DataStore<Preferences>) {

    suspend fun read(): UserPrefs = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toUserPrefs() }
        .first()

    suspend fun write(prefs: UserPrefs) {
        dataStore.edit {
            it[INTERESTS] = prefs.interests.mapTo(mutableSetOf()) { c -> c.name }
            it[HAS_CHOSEN] = prefs.hasChosenInterests
            // A string set would lose the order, so saved ids are kept as one joined string.
            it[SAVED] = prefs.savedIds.joinToString(SEPARATOR)
            it[THEME] = prefs.themeMode.name
        }
    }

    private fun Preferences.toUserPrefs() = UserPrefs(
        // Unknown names are dropped rather than crashing: a category may be renamed later.
        interests = this[INTERESTS].orEmpty()
            .mapNotNullTo(mutableSetOf()) { name -> Category.entries.firstOrNull { it.name == name } },
        hasChosenInterests = this[HAS_CHOSEN] ?: false,
        savedIds = this[SAVED].orEmpty().split(SEPARATOR).filter { it.isNotBlank() },
        themeMode = ThemeMode.entries.firstOrNull { it.name == this[THEME] } ?: ThemeMode.Dark,
    )

    private companion object {
        const val SEPARATOR = ","
        val INTERESTS = stringSetPreferencesKey("interests")
        val HAS_CHOSEN = booleanPreferencesKey("has_chosen_interests")
        val SAVED = stringPreferencesKey("saved_ids")
        val THEME = stringPreferencesKey("theme_mode")
    }
}
