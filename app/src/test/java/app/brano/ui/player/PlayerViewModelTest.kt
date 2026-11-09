package app.brano.ui.player

import androidx.compose.ui.graphics.Color
import app.brano.data.db.FavoriteEntity
import app.brano.data.db.FavoritesDao
import app.brano.data.db.PlaylistDao
import app.brano.data.library.TrackRef
import app.brano.data.library.ref
import app.brano.data.library.t
import app.brano.playback.PlayerController
import app.brano.playback.PlayerUiState
import app.brano.ui.theme.ArtworkSeedLoader
import app.brano.data.settings.AppSettings
import app.brano.data.settings.SettingsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
class PlayerViewModelTest {
    private val queue = (1L..3L).map { t(it, albumId = it) }
    private val playerState = MutableStateFlow(PlayerUiState(current = queue[0], queue = queue, index = 0))
    private val player = mockk<PlayerController>(relaxed = true)
    private val favorites = mockk<FavoritesDao>()
    private val playlists = mockk<PlaylistDao>(relaxed = true)
    private val seeds = mockk<ArtworkSeedLoader>()
    private val appSettings = MutableStateFlow(AppSettings())
    private val settingsRepo = mockk<SettingsRepository> { every { settings } returns appSettings }
    private val favs = MutableStateFlow<List<FavoriteEntity>>(emptyList())

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { player.state } returns playerState
        every { favorites.observe() } returns favs
        coEvery { favorites.toggle(any(), any()) } answers {
            val ref = firstArg<TrackRef>()
            val on = favs.value.none { it.mediaStoreId == ref.mediaStoreId }
            favs.value = if (on) favs.value + FavoriteEntity(ref.mediaStoreId, ref.path, 5L) else favs.value.filter { it.mediaStoreId != ref.mediaStoreId }
            on
        }
        coEvery { seeds.seedFor(1L) } returns Color.Red
        coEvery { seeds.seedFor(2L) } returns null
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = PlayerViewModel(player, favorites, playlists, seeds, settingsRepo) { 5L }

    @Test fun toggleFavorite_updatesIsFavoriteForCurrentTrack() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.isFavorite.collect {} }
        assertThat(vm.isFavorite.value).isFalse()

        vm.toggleFavorite()
        assertThat(vm.isFavorite.value).isTrue()

        playerState.value = playerState.value.copy(current = queue[1], index = 1)
        assertThat(vm.isFavorite.value).isFalse()

        vm.toggleFavorite()
        playerState.value = playerState.value.copy(current = queue[0], index = 0)
        assertThat(vm.isFavorite.value).isTrue()
        vm.toggleFavorite()
        assertThat(vm.isFavorite.value).isFalse()
    }

    @Test fun isFavorite_matchesByPathWhenTheIdChanged() = runTest(UnconfinedTestDispatcher()) {
        favs.value = listOf(FavoriteEntity(mediaStoreId = 77, path = queue[0].path, addedAt = 1))
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.isFavorite.collect {} }
        assertThat(vm.isFavorite.value).isTrue()
    }

    @Test fun saveQueueAsPlaylist_createsPlaylistWithQueueInOrder() = runTest(UnconfinedTestDispatcher()) {
        coEvery { playlists.create("X") } returns 9L
        val refs = slot<List<TrackRef>>()
        coEvery { playlists.addTracks(9L, capture(refs)) } returns Unit
        val vm = vm()

        vm.saveQueueAsPlaylist("X")

        coVerify { playlists.create("X") }
        assertThat(refs.captured).isEqualTo(queue.map { it.ref() })
    }

    @Test fun saveQueueAsPlaylist_blankNameOrEmptyQueue_doesNothing() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm()
        vm.saveQueueAsPlaylist("  ")
        playerState.value = PlayerUiState()
        vm.saveQueueAsPlaylist("Y")
        coVerify(exactly = 0) { playlists.create(any()) }
    }

    @Test fun seedColor_followsCurrentAlbum() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.seedColor.collect {} }
        assertThat(vm.seedColor.value).isEqualTo(Color.Red)
        playerState.value = playerState.value.copy(current = queue[1])
        assertThat(vm.seedColor.value).isNull()
        playerState.value = PlayerUiState()
        assertThat(vm.seedColor.value).isNull()
    }

    @Test fun spinDisc_followsSettings() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.spinDisc.collect {} }
        assertThat(vm.spinDisc.value).isTrue()
        appSettings.value = AppSettings(spinDisc = false)
        assertThat(vm.spinDisc.value).isFalse()
    }
}
