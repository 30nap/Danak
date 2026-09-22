package ir.danak.app

import ir.danak.app.data.MockDanaks
import ir.danak.app.model.Category
import ir.danak.app.model.ThemeMode
import ir.danak.app.ui.DanakViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DanakViewModelTest {

    private val viewModel = DanakViewModel()
    private val state get() = viewModel.state.value

    @Test
    fun `starts with no interests and nothing saved`() {
        assertTrue(state.interests.isEmpty())
        assertTrue(state.savedIds.isEmpty())
        assertFalse(state.hasChosenInterests)
    }

    @Test
    fun `an empty interest set shows the whole feed`() {
        assertEquals(MockDanaks.all.size, state.feed.size)
    }

    @Test
    fun `the feed narrows to the chosen interests`() {
        viewModel.toggleInterest(Category.History)
        viewModel.toggleInterest(Category.Economy)

        val feed = state.feed
        assertTrue(feed.isNotEmpty())
        assertTrue(feed.all { it.category == Category.History || it.category == Category.Economy })
    }

    @Test
    fun `toggling an interest twice clears it`() {
        viewModel.toggleInterest(Category.Science)
        assertEquals(setOf(Category.Science), state.interests)

        viewModel.toggleInterest(Category.Science)
        assertTrue(state.interests.isEmpty())
        assertEquals(MockDanaks.all.size, state.feed.size)
    }

    @Test
    fun `saving is a toggle`() {
        val id = MockDanaks.all.first().id

        viewModel.toggleSaved(id)
        assertTrue(state.isSaved(id))
        assertEquals(listOf(id), state.saved.map { it.id })

        viewModel.toggleSaved(id)
        assertFalse(state.isSaved(id))
        assertTrue(state.saved.isEmpty())
    }

    @Test
    fun `saved items are listed most recent first`() {
        val (first, second, third) = MockDanaks.all.take(3)
        viewModel.toggleSaved(first.id)
        viewModel.toggleSaved(second.id)
        viewModel.toggleSaved(third.id)

        assertEquals(listOf(third.id, second.id, first.id), state.saved.map { it.id })
    }

    @Test
    fun `re-saving an item moves it back to the top`() {
        val (first, second) = MockDanaks.all.take(2)
        viewModel.toggleSaved(first.id)
        viewModel.toggleSaved(second.id)
        viewModel.toggleSaved(first.id) // removes
        viewModel.toggleSaved(first.id) // saves again

        assertEquals(listOf(first.id, second.id), state.saved.map { it.id })
    }

    @Test
    fun `confirming interests is what leaves onboarding`() {
        viewModel.toggleInterest(Category.Technology)
        assertFalse(state.hasChosenInterests)

        viewModel.confirmInterests()
        assertTrue(state.hasChosenInterests)
    }

    @Test
    fun `theme mode is remembered`() {
        assertEquals(ThemeMode.Dark, state.themeMode)
        viewModel.setThemeMode(ThemeMode.System)
        assertEquals(ThemeMode.System, state.themeMode)
    }

    @Test
    fun `lookup by id finds known danaks and nothing else`() {
        val known = MockDanaks.all.first()
        assertEquals(known, viewModel.danakById(known.id))
        assertNull(viewModel.danakById("no-such-danak"))
    }
}
