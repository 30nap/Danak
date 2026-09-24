package ir.danak.app.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.danak.app.data.ContentRepository
import ir.danak.app.data.DanakStore
import ir.danak.app.data.UserPrefs
import ir.danak.app.data.danakDataStore
import ir.danak.app.model.Category
import ir.danak.app.model.Danak
import ir.danak.app.model.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * The whole of Danak's state. One ViewModel scoped to the activity: V0 has little state and
 * every screen reads the same few values, so splitting it would only add wiring.
 *
 * The user's state is loaded from [DanakStore] and the Danaks from [loadContent], both once
 * at start-up and from disk only; the state is written back after every change. The UI
 * waits for [DanakUiState.isLoaded] (behind the splash screen) so it never flashes
 * onboarding for a returning user, or an empty feed.
 *
 * Published content is checked with [refreshContent] whenever the app comes to the
 * foreground (at most every [REFRESH_INTERVAL_MS]), in the background: the feed is usable
 * long before, and a new set simply replaces the content when it has been verified.
 */
class DanakViewModel(
    private val store: DanakStore,
    private val loadContent: suspend () -> List<Danak>,
    private val refreshContent: suspend () -> ContentRepository.Refresh = { ContentRepository.Refresh.UpToDate },
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {

    private val _state = MutableStateFlow(DanakUiState())
    val state: StateFlow<DanakUiState> = _state.asStateFlow()

    /**
     * Danaks a refresh took out of the content during this session. A detail screen that is
     * open on one keeps showing it instead of being closed under the reader.
     */
    private val retired = mutableMapOf<String, Danak>()

    private var refreshJob: Job? = null
    private var lastSuccessMs: Long? = null
    private var lastAttemptMs: Long? = null

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

    fun danakById(id: String): Danak? = _state.value.danaks.firstOrNull { it.id == id } ?: retired[id]

    /**
     * Called when the app comes to the foreground. Checks for published content unless a
     * check succeeded recently or is already running; after a failure (offline, say) the
     * next foreground tries again, but not more than every [RETRY_INTERVAL_MS].
     */
    fun onForeground() {
        val now = clockMs()
        if (refreshJob?.isActive == true) return
        if (lastSuccessMs?.let { now - it < REFRESH_INTERVAL_MS } == true) return
        if (lastAttemptMs?.let { now - it < RETRY_INTERVAL_MS } == true) return
        lastAttemptMs = now
        refreshJob = viewModelScope.launch {
            // The local content must be in place first, or it would overwrite the update.
            _state.first { it.isLoaded }
            when (val result = refreshContent()) {
                is ContentRepository.Refresh.Updated -> {
                    applyContent(result.danaks)
                    lastSuccessMs = clockMs()
                }
                ContentRepository.Refresh.UpToDate -> lastSuccessMs = clockMs()
                is ContentRepository.Refresh.Failed -> Unit
            }
        }
    }

    private fun applyContent(danaks: List<Danak>) {
        val ids = danaks.mapTo(HashSet()) { it.id }
        _state.value.danaks.filter { it.id !in ids }.associateByTo(retired) { it.id }
        _state.update { it.copy(danaks = danaks) }
    }

    private fun edit(transform: (UserPrefs) -> UserPrefs) {
        val updated = _state.updateAndGet { it.copy(prefs = transform(it.prefs)) }.prefs
        // Each write carries the full snapshot and DataStore applies edits in order, so the
        // last change always wins on disk.
        viewModelScope.launch { store.write(updated) }
    }

    companion object {
        const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L
        const val RETRY_INTERVAL_MS = 30 * 1000L

        val Factory = viewModelFactory {
            initializer {
                val app = requireNotNull(this[APPLICATION_KEY])
                val content = ContentRepository.create(app)
                DanakViewModel(
                    store = DanakStore(app.danakDataStore),
                    loadContent = content::loadLocal,
                    refreshContent = content::refresh,
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
