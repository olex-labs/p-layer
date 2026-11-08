package app.brano.ui.library

import app.brano.data.library.LibraryRepository
import app.brano.data.library.Track
import app.brano.data.library.t
import app.brano.data.settings.AppSettings
import app.brano.data.settings.SettingsRepository
import app.brano.data.settings.TrackSort
import app.brano.playback.PlayerController
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val tracks = listOf(
        t(1, title = "banana", artist = "Zed", album = "Beta", dur = 100, dateAddedSec = 10),
        t(2, title = "Apple", artist = null, album = null, dur = 300, dateAddedSec = 30),
        t(3, title = "cherry", artist = "ábel", album = "Alpha", dur = 200, dateAddedSec = 20),
    )
    private val settings = MutableStateFlow(AppSettings())
    private val repo = mockk<LibraryRepository>(relaxed = true)
    private val settingsRepo = mockk<SettingsRepository>(relaxed = true)
    private val player = mockk<PlayerController>(relaxed = true)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        io.mockk.every { repo.tracks } returns MutableStateFlow<List<Track>>(tracks)
        io.mockk.every { repo.albums } returns MutableStateFlow(emptyList())
        io.mockk.every { repo.artists } returns MutableStateFlow(emptyList())
        io.mockk.every { repo.folders } returns MutableStateFlow(null)
        io.mockk.every { repo.isLoading } returns MutableStateFlow(false)
        io.mockk.every { settingsRepo.settings } returns settings
        io.mockk.coEvery { settingsRepo.update(any()) } coAnswers {
            settings.value = firstArg<(AppSettings) -> AppSettings>()(settings.value)
        }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun ids(l: List<Track>) = l.map { it.id }

    @Test fun sort_title_isNormalizedAscending() {
        assertThat(ids(sortTracks(tracks, TrackSort.TITLE))).containsExactly(2L, 1L, 3L).inOrder()
    }

    @Test fun sort_artist_nullLast() {
        assertThat(ids(sortTracks(tracks, TrackSort.ARTIST))).containsExactly(3L, 1L, 2L).inOrder()
    }

    @Test fun sort_album_nullLast() {
        assertThat(ids(sortTracks(tracks, TrackSort.ALBUM))).containsExactly(3L, 1L, 2L).inOrder()
    }

    @Test fun sort_dateAdded_descending() {
        assertThat(ids(sortTracks(tracks, TrackSort.DATE_ADDED))).containsExactly(2L, 3L, 1L).inOrder()
    }

    @Test fun sort_duration_descending() {
        assertThat(ids(sortTracks(tracks, TrackSort.DURATION))).containsExactly(2L, 3L, 1L).inOrder()
    }

    @Test fun setSort_persistsAndReordersTracks() = runTest(UnconfinedTestDispatcher()) {
        val vm = LibraryViewModel(repo, settingsRepo, player, UnconfinedTestDispatcher(testScheduler))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.tracks.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.sort.collect {} }
        vm.setSort(TrackSort.DURATION)
        assertThat(settings.value.trackSort).isEqualTo(TrackSort.DURATION)
        assertThat(vm.sort.value).isEqualTo(TrackSort.DURATION)
        assertThat(ids(vm.tracks.value)).containsExactly(2L, 3L, 1L).inOrder()
    }

    @Test fun playAll_playsSortedListFromIndex_andShuffleAllShuffles() = runTest(UnconfinedTestDispatcher()) {
        val vm = LibraryViewModel(repo, settingsRepo, player, UnconfinedTestDispatcher(testScheduler))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.tracks.collect {} }
        vm.playAll(1)
        verify { player.play(match { ids(it) == listOf(2L, 1L, 3L) }, 1, false) }
        vm.shuffleAll()
        verify { player.play(match { ids(it) == listOf(2L, 1L, 3L) }, 0, true) }
    }

    @Test fun tracks_areSortedOnTheInjectedDispatcher() = runTest {
        val vm = LibraryViewModel(repo, settingsRepo, player, StandardTestDispatcher(testScheduler))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.tracks.collect {} }
        assertThat(vm.tracks.value).isEmpty()
        advanceUntilIdle()
        assertThat(ids(vm.tracks.value)).containsExactly(2L, 1L, 3L).inOrder()
    }
}
