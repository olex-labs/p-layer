package app.brano.ui.player

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brano.data.db.FavoritesDao
import app.brano.data.db.PlaylistDao
import app.brano.data.library.ref
import app.brano.playback.PlayerController
import app.brano.playback.PlayerUiState
import app.brano.ui.theme.ArtworkSeedLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel internal constructor(
    private val controller: PlayerController,
    private val favorites: FavoritesDao,
    private val playlists: PlaylistDao,
    seeds: ArtworkSeedLoader,
    private val now: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        controller: PlayerController,
        favorites: FavoritesDao,
        playlists: PlaylistDao,
        seeds: ArtworkSeedLoader,
    ) : this(controller, favorites, playlists, seeds, System::currentTimeMillis)

    val state: StateFlow<PlayerUiState> = controller.state

    val errors: SharedFlow<String> = controller.errors

    val isFavorite: StateFlow<Boolean> = combine(
        controller.state.map { it.current?.id }.distinctUntilChanged(),
        favorites.observe(),
    ) { id, favs -> id != null && favs.any { it.mediaStoreId == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val seedColor: StateFlow<Color?> = controller.state
        .map { it.current?.albumId }
        .distinctUntilChanged()
        .mapLatest { albumId -> albumId?.let { seeds.seedFor(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleFavorite() {
        val track = controller.state.value.current ?: return
        viewModelScope.launch { favorites.toggle(track.ref(), now()) }
    }

    fun saveQueueAsPlaylist(name: String) {
        val trimmed = name.trim()
        val queue = controller.state.value.queue
        if (trimmed.isEmpty() || queue.isEmpty()) return
        viewModelScope.launch {
            val id = playlists.create(trimmed)
            playlists.addTracks(id, queue.map { it.ref() })
        }
    }

    fun togglePlayPause() = controller.togglePlayPause()
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun seekTo(ms: Long) = controller.seekTo(ms)
    fun setShuffle(on: Boolean) = controller.setShuffle(on)
    fun cycleRepeat() = controller.cycleRepeat()
    fun startSleepTimer(ms: Long) = controller.startSleepTimer(ms)
    fun sleepAtEndOfTrack() = controller.sleepAtEndOfTrack()
    fun cancelSleepTimer() = controller.cancelSleepTimer()
    fun moveInQueue(from: Int, to: Int) = controller.moveInQueue(from, to)
    fun removeFromQueue(index: Int) = controller.removeFromQueue(index)
    fun skipToQueueItem(index: Int) = controller.skipToQueueItem(index)
}
