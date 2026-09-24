package ir.danak.app

import ir.danak.app.data.ContentRepository
import ir.danak.app.data.DanakStore
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

    private fun viewModel(store: DanakStore) = DanakViewModel(store, loadContent = { TestContent.all })

    @Test
    fun `a first launch loads with no interests and nothing saved`() = test {
        val state = viewModel(newStore(folder)).loaded()
        assertTrue(state.interests.isEmpty())
        assertTrue(state.saved.isEmpty())
        assertFalse(state.hasChosenInterests)
        assertEquals(ThemeMode.Dark, state.themeMode)
    }

    @Test
    fun `an empty interest set shows the whole feed`() = test {
        assertEquals(TestContent.all.size, viewModel(newStore(folder)).loaded().feed.size)
    }

    @Test
    fun `the feed narrows to the chosen interests`() = test {
        val vm = viewModel(newStore(folder)).also { it.loaded() }
        vm.toggleInterest(Category.History)
        vm.toggleInterest(Category.Economy)

        val feed = vm.state.value.feed
        assertTrue(feed.isNotEmpty())
        assertTrue(feed.all { it.category == Category.History || it.category == Category.Economy })
    }

    @Test
    fun `toggling an interest twice clears it`() = test {
        val vm = viewModel(newStore(folder)).also { it.loaded() }
        vm.toggleInterest(Category.Science)
        assertEquals(setOf(Category.Science), vm.state.value.interests)
        vm.toggleInterest(Category.Science)
        assertTrue(vm.state.value.interests.isEmpty())
    }

    @Test
    fun `saved items are listed most recent first`() = test {
        val vm = viewModel(newStore(folder)).also { it.loaded() }
        val (a, b, c) = TestContent.all.take(3)
        listOf(a, b, c).forEach { vm.toggleSaved(it.id) }
        assertEquals(listOf(c.id, b.id, a.id), vm.state.value.saved.map { it.id })
    }

    @Test
    fun `undo puts a removed item back where it was`() = test {
        val vm = viewModel(newStore(folder)).also { it.loaded() }
        val (a, b, c) = TestContent.all.take(3)
        listOf(a, b, c).forEach { vm.toggleSaved(it.id) }

        val index = vm.removeSaved(b.id)
        assertEquals(listOf(c.id, a.id), vm.state.value.saved.map { it.id })

        vm.restoreSaved(b.id, index)
        assertEquals(listOf(c.id, b.id, a.id), vm.state.value.saved.map { it.id })
    }

    @Test
    fun `removing something that is not saved changes nothing`() = test {
        val vm = viewModel(newStore(folder)).also { it.loaded() }
        assertEquals(-1, vm.removeSaved("not-saved"))
        assertTrue(vm.state.value.saved.isEmpty())
    }

    @Test
    fun `everything survives a restart`() = test {
        val store = newStore(folder)
        val first = viewModel(store).also { it.loaded() }
        val saved = TestContent.all[4].id
        first.toggleInterest(Category.Productivity)
        first.confirmInterests()
        first.toggleSaved(saved)
        first.setThemeMode(ThemeMode.Light)
        store.awaitStored { it.themeMode == ThemeMode.Light && saved in it.savedIds }

        val restarted = viewModel(store).loaded()
        assertEquals(setOf(Category.Productivity), restarted.interests)
        assertTrue(restarted.hasChosenInterests)
        assertTrue(restarted.isSaved(saved))
        assertEquals(ThemeMode.Light, restarted.themeMode)
    }

    @Test
    fun `the feed waits for the content as well as the saved state`() = test {
        val state = DanakViewModel(newStore(folder), loadContent = { TestContent.all.take(2) }).loaded()
        assertEquals(TestContent.all.take(2), state.danaks)
    }

    @Test
    fun `a saved id missing from the content is skipped`() = test {
        val store = newStore(folder)
        val vm = viewModel(store).also { it.loaded() }
        vm.toggleSaved("retired-danak")
        vm.toggleSaved(TestContent.all[0].id)
        assertEquals(listOf(TestContent.all[0].id), vm.state.value.saved.map { it.id })
    }

    @Test
    fun `a verified refresh replaces the content once the app is in the foreground`() = test {
        val fresh = TestContent.all.take(5).reversed()
        val vm = DanakViewModel(newStore(folder), { TestContent.all }, { ContentRepository.Refresh.Updated(fresh) })
        vm.loaded()
        vm.onForeground()
        vm.state.first { it.danaks == fresh }
    }

    @Test
    fun `refreshes are spaced out after a success and retried after a failure`() = test {
        var now = 0L
        var calls = 0
        var result: ContentRepository.Refresh = ContentRepository.Refresh.Failed("offline")
        val vm = DanakViewModel(newStore(folder), { TestContent.all }, { calls++; result }, clockMs = { now })
        vm.loaded()

        vm.onForeground()
        vm.onForeground()
        assertEquals("a failure is not retried at once", 1, calls)
        now += DanakViewModel.RETRY_INTERVAL_MS
        result = ContentRepository.Refresh.UpToDate
        vm.onForeground()
        assertEquals("but it is retried on a later foreground", 2, calls)
        now += DanakViewModel.RETRY_INTERVAL_MS
        vm.onForeground()
        assertEquals("a success is not repeated within the interval", 2, calls)
        now += DanakViewModel.REFRESH_INTERVAL_MS
        vm.onForeground()
        assertEquals(3, calls)
    }

    @Test
    fun `a danak taken out by a refresh stays openable but leaves the feed and saved list`() = test {
        val all = TestContent.all
        val removed = all.first()
        val vm = DanakViewModel(newStore(folder), { all }, { ContentRepository.Refresh.Updated(all.drop(1)) })
        vm.loaded()
        vm.toggleSaved(removed.id)
        vm.onForeground()
        val state = vm.state.first { it.danaks.size == all.size - 1 }
        assertEquals(removed, vm.danakById(removed.id))
        assertTrue(state.feed.none { it.id == removed.id })
        assertTrue(state.saved.isEmpty())
        assertTrue("the saved id is kept, in case it comes back", state.isSaved(removed.id))
    }

    @Test
    fun `lookup by id finds known danaks and nothing else`() = test {
        val vm = viewModel(newStore(folder)).also { it.loaded() }
        val known = TestContent.all.first()
        assertEquals(known, vm.danakById(known.id))
        assertNull(vm.danakById("no-such-danak"))
    }
}
