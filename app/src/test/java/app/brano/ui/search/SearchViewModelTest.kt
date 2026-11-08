package app.brano.ui.search

import androidx.lifecycle.SavedStateHandle
import app.brano.data.library.LibraryRepository
import app.brano.data.library.t
import app.brano.domain.SearchIndex
import app.brano.playback.PlayerController
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val tracks = listOf(t(1, title = "Perché"), t(2, title = "Altro", artist = "Zed", album = "Beta"))
    private val index = MutableStateFlow(SearchIndex(tracks))
    private val repo = mockk<LibraryRepository>(relaxed = true)
    private val player = mockk<PlayerController>(relaxed = true)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repo.search } returns index
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun onQuery_findsAccentedTitle_andEmptyQueryClears() = runTest(UnconfinedTestDispatcher()) {
        val vm = SearchViewModel(repo, player, SavedStateHandle(), UnconfinedTestDispatcher(testScheduler))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.results.collect {} }
        vm.onQuery("perche")
        assertThat(vm.query.value).isEqualTo("perche")
        assertThat(vm.results.value.tracks.map { it.id }).containsExactly(1L)
        vm.onQuery("")
        assertThat(vm.results.value.tracks).isEmpty()
        assertThat(vm.results.value.albums).isEmpty()
    }

    @Test fun results_updateWhenLibraryChanges() = runTest(UnconfinedTestDispatcher()) {
        val vm = SearchViewModel(repo, player, SavedStateHandle(), UnconfinedTestDispatcher(testScheduler))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.results.collect {} }
        vm.onQuery("nuovo")
        assertThat(vm.results.value.tracks).isEmpty()
        index.value = SearchIndex(tracks + t(3, title = "Nuovo brano"))
        assertThat(vm.results.value.tracks.map { it.id }).containsExactly(3L)
    }

    @Test fun query_restoredFromSavedState() {
        val vm = SearchViewModel(repo, player, SavedStateHandle(mapOf("query" to "abc")), UnconfinedTestDispatcher())
        assertThat(vm.query.value).isEqualTo("abc")
    }

    @Test fun play_delegatesToPlayer() {
        val vm = SearchViewModel(repo, player, SavedStateHandle(), UnconfinedTestDispatcher())
        vm.play(tracks, 1)
        verify { player.play(tracks, 1, false) }
    }
}
