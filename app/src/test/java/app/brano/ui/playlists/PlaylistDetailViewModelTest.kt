package app.brano.ui.playlists

import app.brano.data.db.FavoriteEntity
import app.brano.data.db.FavoritesDao
import app.brano.data.db.PlayStatEntity
import app.brano.data.db.PlayStatsDao
import app.brano.data.db.PlaylistDao
import app.brano.data.db.PlaylistItemEntity
import app.brano.data.db.PlaylistSummary
import app.brano.data.library.LibraryRepository
import app.brano.data.library.Track
import app.brano.data.library.ref
import app.brano.data.library.t
import app.brano.domain.SmartPlaylist
import app.brano.playback.PlayerController
import app.brano.playback.PlayerUiState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
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
class PlaylistDetailViewModelTest {
    private val tracks = (1L..4L).map { t(it, dateAddedSec = 1_000_000L + it) }
    private val library = mockk<LibraryRepository>()
    private val dao = mockk<PlaylistDao>(relaxed = true)
    private val favorites = mockk<FavoritesDao>()
    private val stats = mockk<PlayStatsDao>()
    private val player = mockk<PlayerController>(relaxed = true)
    private val libraryTracks = MutableStateFlow<List<Track>>(tracks)
    private val dbItems = MutableStateFlow<List<PlaylistItemEntity>>(emptyList())
    private val favs = MutableStateFlow<List<FavoriteEntity>>(emptyList())

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { library.tracks } returns libraryTracks
        every { library.trackById(any()) } answers { libraryTracks.value.firstOrNull { it.id == firstArg<Long>() } }
        every { library.trackByPath(any()) } answers { libraryTracks.value.firstOrNull { it.path == firstArg<String>() } }
        every { dao.observeItems(7) } returns dbItems
        every { dao.observeAll() } returns MutableStateFlow(listOf(PlaylistSummary(7, "Mia", 0)))
        every { favorites.observe() } returns favs
        every { stats.observeAll() } returns MutableStateFlow(emptyList<PlayStatEntity>())
        every { player.state } returns MutableStateFlow(PlayerUiState())
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun items(vararg ts: Track) {
        dbItems.value = ts.mapIndexed { i, tr -> PlaylistItemEntity(7, i, tr.id, tr.path) }
    }

    private fun userVm() =
        PlaylistDetailViewModel(7, null, library, dao, favorites, stats, player, UnconfinedTestDispatcher()) { 1_000_100L }

    private fun smartVm(kind: SmartPlaylist) =
        PlaylistDetailViewModel(null, kind, library, dao, favorites, stats, player, UnconfinedTestDispatcher()) { 1_000_100L }

    @Test fun play_skipsUnavailableItemsAndKeepsStartPosition() = runTest(UnconfinedTestDispatcher()) {
        items(tracks[0], t(50), tracks[2], tracks[3]) // second item is not in the library
        val vm = userVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.items.collect {} }

        vm.play(2)

        verify { player.play(listOf(tracks[0], tracks[2], tracks[3]), 1) }
    }

    @Test fun play_onUnavailableItemDoesNothing() = runTest(UnconfinedTestDispatcher()) {
        items(tracks[0], t(50))
        val vm = userVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.items.collect {} }

        vm.play(1)

        verify(exactly = 0) { player.play(any(), any()) }
    }

    @Test fun shuffle_usesAvailableTracksOnly() = runTest(UnconfinedTestDispatcher()) {
        items(tracks[0], t(50), tracks[1])
        val vm = userVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.items.collect {} }

        vm.shuffle()

        verify { player.shuffle(listOf(tracks[0], tracks[1])) }
    }

    @Test fun userPlaylist_isEditableAndExposesTitleAndItems() = runTest(UnconfinedTestDispatcher()) {
        items(tracks[0], t(50))
        val vm = userVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.items.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.title.collect {} }

        assertThat(vm.editable).isTrue()
        assertThat(vm.title.value).isEqualTo("Mia")
        assertThat(vm.items.value.map { it.track?.id }).containsExactly(1L, null).inOrder()
    }

    @Test fun staleIds_areRepairedByPath() = runTest(UnconfinedTestDispatcher()) {
        dbItems.value = listOf(PlaylistItemEntity(7, 0, 999, tracks[1].path))
        val vm = userVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.items.collect {} }

        assertThat(vm.items.value.single().track).isEqualTo(tracks[1])
        coVerify { dao.updateIds(mapOf(tracks[1].path to 2L)) }
    }

    @Test fun smartPlaylist_isNotEditable_andBuiltFromLibrary() = runTest(UnconfinedTestDispatcher()) {
        val vm = smartVm(SmartPlaylist.RECENTLY_ADDED)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.items.collect {} }

        assertThat(vm.editable).isFalse()
        assertThat(vm.items.value.map { it.track!!.id }).containsExactly(4L, 3L, 2L, 1L).inOrder()
    }

    @Test fun smartPlaylist_recomputesWhenFavoritesChange() = runTest(UnconfinedTestDispatcher()) {
        val vm = smartVm(SmartPlaylist.FAVORITES)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.items.collect {} }
        assertThat(vm.items.value).isEmpty()

        favs.value = listOf(FavoriteEntity(3, tracks[2].path, 10))

        assertThat(vm.items.value.map { it.track!!.id }).containsExactly(3L)
    }

    @Test fun move_rename_delete_delegateToDao() = runTest(UnconfinedTestDispatcher()) {
        val vm = userVm()
        vm.move(0, 2)
        vm.rename("Nuovo")
        vm.delete()
        coVerify { dao.move(7, 0, 2) }
        coVerify { dao.rename(7, "Nuovo") }
        coVerify { dao.delete(7) }
    }

    @Test fun rename_rejectsBlank() = runTest(UnconfinedTestDispatcher()) {
        userVm().rename("   ")
        coVerify(exactly = 0) { dao.rename(any(), any()) }
    }

    @Test fun remove_thenUndo_restoresPosition() = runTest(UnconfinedTestDispatcher()) {
        items(tracks[0], tracks[1], tracks[2])
        val vm = userVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.items.collect {} }
        val events = mutableListOf<PlaylistDetailEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        // After the undo re-adds the track the playlist holds 3 items again.
        coEvery { dao.addTracks(7, any()) } answers { items(tracks[0], tracks[2], tracks[1]) }

        vm.remove(1)

        coVerify { dao.removeAt(7, 1) }
        val removed = events.single() as PlaylistDetailEvent.Removed
        assertThat(removed.position).isEqualTo(1)
        assertThat(removed.ref).isEqualTo(tracks[1].ref())

        vm.undoRemove(removed)

        coVerify { dao.addTracks(7, listOf(tracks[1].ref())) }
        coVerify { dao.move(7, 2, 1) }
    }
}
