package ir.danak.app.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.danak.app.data.BundledContent
import ir.danak.app.data.DanakStore
import ir.danak.app.data.UserPrefs
import ir.danak.app.data.danakDataStore
import ir.danak.app.model.Category
import ir.danak.app.model.Danak
import ir.danak.app.model.ThemeMode
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * The whole of Danak's state. One ViewModel scoped to the activity: V0 has little state and
 * every screen reads the same few values, so splitting it would only add wiring.
 *
 * The user's state is loaded from [DanakStore] and the Danaks from [loadContent], both once
 * at start-up; the state is written back after every change. The UI waits for
 * [DanakUiState.isLoaded] (behind the splash screen) so it never flashes onboarding for a
 * returning user, or an empty feed.
 */
class DanakViewModel(
    private val store: DanakStore,
    private val loadContent: suspend () -> List<Danak>,
) : ViewModel() {

    private val _state = MutableStateFlow(DanakUiState())
    val state: StateFlow<DanakUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = async { store.read() }
            val danaks = async { loadContent() }
            _state.update { it.copy(prefs = prefs.await(), danaks = danaks.await(), isLoaded = true) }
        }
    }

    fun toggleInterest(category: Category) = edit { prefs ->
        val next = LinkedHashSet(prefs.interests)
        if (!next.remove(category)) next += category
        prefs.copy(interests = next)
    }

    fun confirmInterests() = edit { it.copy(hasChosenInterests = true) }

    fun toggleSaved(id: String) = edit { prefs ->
        if (id in prefs.savedIds) {
            prefs.copy(savedIds = prefs.savedIds - id)
        } else {
            prefs.copy(savedIds = prefs.savedIds + id)
        }
    }

    /**
     * Removes [id] from saved and returns where it was, so an undo can put it back in the
     * same place instead of promoting it to "most recent".
     */
    fun removeSaved(id: String): Int {
        val index = _state.value.prefs.savedIds.indexOf(id)
        if (index >= 0) edit { it.copy(savedIds = it.savedIds - id) }
        return index
    }

    fun restoreSaved(id: String, index: Int) = edit { prefs ->
        if (id in prefs.savedIds) return@edit prefs
        val list = prefs.savedIds.toMutableList()
        list.add(index.coerceIn(0, list.size), id)
        prefs.copy(savedIds = list)
    }

    fun setThemeMode(mode: ThemeMode) = edit { it.copy(themeMode = mode) }

    fun danakById(id: String): Danak? = _state.value.danaks.firstOrNull { it.id == id }

    private fun edit(transform: (UserPrefs) -> UserPrefs) {
        val updated = _state.updateAndGet { it.copy(prefs = transform(it.prefs)) }.prefs
        // Each write carries the full snapshot and DataStore applies edits in order, so the
        // last change always wins on disk.
        viewModelScope.launch { store.write(updated) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = requireNotNull(this[APPLICATION_KEY])
                DanakViewModel(
                    store = DanakStore(app.danakDataStore),
                    loadContent = { BundledContent.load(app) },
                )
            }
        }
    }
}

@Immutable
data class DanakUiState(
    val prefs: UserPrefs = UserPrefs(),
    /** Every Danak the app has, in feed order. */
    val danaks: List<Danak> = emptyList(),
    val isLoaded: Boolean = false,
) {
    val interests: Set<Category> get() = prefs.interests
    val hasChosenInterests: Boolean get() = prefs.hasChosenInterests
    val themeMode: ThemeMode get() = prefs.themeMode

    /**
     * The feed respects the chosen interests, but never goes empty: with nothing selected,
     * every Danak is shown.
     *
     * Computed once per state instance so passing it to a composable does not defeat
     * recomposition skipping.
     */
    val feed: List<Danak> by lazy {
        if (interests.isEmpty()) danaks else danaks.filter { it.category in interests }
    }

    /** Saved items, most recently saved first. */
    val saved: List<Danak> by lazy {
        val byId = danaks.associateBy { it.id }
        // An id whose Danak is no longer in the content is skipped, not shown broken.
        prefs.savedIds.asReversed().mapNotNull { id -> byId[id] }
    }

    fun isSaved(id: String): Boolean = id in prefs.savedIds
}
