package ir.danak.app.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import ir.danak.app.data.MockDanaks
import ir.danak.app.model.Category
import ir.danak.app.model.Danak
import ir.danak.app.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The whole of Danak's state. V0 has one screen's worth of state per destination and no
 * persistence, so one ViewModel scoped to the activity is the right size — splitting it
 * would only add wiring.
 */
class DanakViewModel : ViewModel() {

    private val _state = MutableStateFlow(DanakUiState())
    val state: StateFlow<DanakUiState> = _state.asStateFlow()

    fun toggleInterest(category: Category) = _state.update { current ->
        val next = LinkedHashSet(current.interests)
        if (!next.remove(category)) next += category
        current.copy(interests = next)
    }

    fun confirmInterests() = _state.update { it.copy(hasChosenInterests = true) }

    fun toggleSaved(id: String) = _state.update { current ->
        // LinkedHashSet: insertion order is what makes "most recently saved" meaningful.
        val next = LinkedHashSet(current.savedIds)
        if (!next.remove(id)) next += id
        current.copy(savedIds = next)
    }

    fun setThemeMode(mode: ThemeMode) = _state.update { it.copy(themeMode = mode) }

    fun danakById(id: String): Danak? = MockDanaks.all.firstOrNull { it.id == id }
}

@Immutable
data class DanakUiState(
    val interests: Set<Category> = emptySet(),
    val hasChosenInterests: Boolean = false,
    val savedIds: Set<String> = emptySet(),
    val themeMode: ThemeMode = ThemeMode.Dark,
) {
    /**
     * The feed respects the chosen interests, but never goes empty: with nothing selected
     * — or after the picker is reopened and cleared — every Danak is shown.
     *
     * Computed once per state instance so passing it to a composable does not defeat
     * recomposition skipping.
     */
    val feed: List<Danak> by lazy {
        if (interests.isEmpty()) MockDanaks.all else MockDanaks.all.filter { it.category in interests }
    }

    /** Saved items, most recently saved first. */
    val saved: List<Danak> by lazy {
        savedIds.reversed().mapNotNull { id -> MockDanaks.all.firstOrNull { it.id == id } }
    }

    fun isSaved(id: String): Boolean = id in savedIds
}
