package ir.danak.app

import ir.danak.app.data.MockDanaks
import ir.danak.app.model.Category
import ir.danak.app.model.ThemeMode
import ir.danak.app.ui.DanakUiState
import ir.danak.app.ui.DanakViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DanakViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun DanakViewModel.loaded(): DanakUiState = state.first { it.isLoaded }

    private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

    @Test
    fun `a first launch loads with no interests and nothing saved`() = test {
        val state = DanakViewModel(newStore(folder)).loaded()
        assertTrue(state.interests.isEmpty())
        assertTrue(state.saved.isEmpty())
        assertFalse(state.hasChosenInterests)
        assertEquals(ThemeMode.Dark, state.themeMode)
    }

    @Test
    fun `an empty interest set shows the whole feed`() = test {
        assertEquals(MockDanaks.all.size, DanakViewModel(newStore(folder)).loaded().feed.size)
    }

    @Test
    fun `the feed narrows to the chosen interests`() = test {
        val vm = DanakViewModel(newStore(folder)).also { it.loaded() }
        vm.toggleInterest(Category.History)
        vm.toggleInterest(Category.Economy)

        val feed = vm.state.value.feed
        assertTrue(feed.isNotEmpty())
        assertTrue(feed.all { it.category == Category.History || it.category == Category.Economy })
    }

    @Test
    fun `toggling an interest twice clears it`() = test {
        val vm = DanakViewModel(newStore(folder)).also { it.loaded() }
        vm.toggleInterest(Category.Science)
        assertEquals(setOf(Category.Science), vm.state.value.interests)
        vm.toggleInterest(Category.Science)
        assertTrue(vm.state.value.interests.isEmpty())
    }

    @Test
    fun `saved items are listed most recent first`() = test {
        val vm = DanakViewModel(newStore(folder)).also { it.loaded() }
        val (a, b, c) = MockDanaks.all.take(3)
        listOf(a, b, c).forEach { vm.toggleSaved(it.id) }
        assertEquals(listOf(c.id, b.id, a.id), vm.state.value.saved.map { it.id })
    }

    @Test
    fun `undo puts a removed item back where it was`() = test {
        val vm = DanakViewModel(newStore(folder)).also { it.loaded() }
        val (a, b, c) = MockDanaks.all.take(3)
        listOf(a, b, c).forEach { vm.toggleSaved(it.id) }

        val index = vm.removeSaved(b.id)
        assertEquals(listOf(c.id, a.id), vm.state.value.saved.map { it.id })

        vm.restoreSaved(b.id, index)
        assertEquals(listOf(c.id, b.id, a.id), vm.state.value.saved.map { it.id })
    }

    @Test
    fun `removing something that is not saved changes nothing`() = test {
        val vm = DanakViewModel(newStore(folder)).also { it.loaded() }
        assertEquals(-1, vm.removeSaved("not-saved"))
        assertTrue(vm.state.value.saved.isEmpty())
    }

    @Test
    fun `everything survives a restart`() = test {
        val store = newStore(folder)
        val first = DanakViewModel(store).also { it.loaded() }
        val saved = MockDanaks.all[4].id
        first.toggleInterest(Category.Productivity)
        first.confirmInterests()
        first.toggleSaved(saved)
        first.setThemeMode(ThemeMode.Light)
        store.awaitStored { it.themeMode == ThemeMode.Light && saved in it.savedIds }

        val restarted = DanakViewModel(store).loaded()
        assertEquals(setOf(Category.Productivity), restarted.interests)
        assertTrue(restarted.hasChosenInterests)
        assertTrue(restarted.isSaved(saved))
        assertEquals(ThemeMode.Light, restarted.themeMode)
    }

    @Test
    fun `lookup by id finds known danaks and nothing else`() = test {
        val vm = DanakViewModel(newStore(folder))
        val known = MockDanaks.all.first()
        assertEquals(known, vm.danakById(known.id))
        assertNull(vm.danakById("no-such-danak"))
    }
}
