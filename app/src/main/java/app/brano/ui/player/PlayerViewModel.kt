package app.brano.ui.player

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import app.brano.lyrics.LyricsRepository
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
import app.brano.data.settings.SettingsRepository

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel internal constructor(
    private val controller: PlayerController,
    private val favorites: FavoritesDao,
    private val playlists: PlaylistDao,
    seeds: ArtworkSeedLoader,
    settings: SettingsRepository,
    private val lyricsRepository: LyricsRepository,
    private val now: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        controller: PlayerController,
        favorites: FavoritesDao,
        playlists: PlaylistDao,
        seeds: ArtworkSeedLoader,
        settings: SettingsRepository,
        lyricsRepository: LyricsRepository,
    ) : this(controller, favorites, playlists, seeds, settings, lyricsRepository, System::currentTimeMillis)

    /** Everything except the position, so the 500 ms position ticks do not recompose the screen. */
    val state: StateFlow<PlayerUiState> = controller.state
        .map { it.copy(positionMs = 0) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), controller.state.value.copy(positionMs = 0))

    val position: StateFlow<Long> = controller.state
        .map { it.positionMs }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val errors: SharedFlow<String> = controller.errors

    /** Whether the vinyl cover turns while playing ("Disco che gira" setting). */
    val spinDisc: StateFlow<Boolean> = settings.settings
        .map { it.spinDisc }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** By id, then by path (the id changes when MediaStore re-indexes the same file). */
    val isFavorite: StateFlow<Boolean> = combine(
        controller.state.map { it.current?.ref() }.distinctUntilChanged(),
        favorites.observe(),
    ) { ref, favs -> ref != null && favs.any { it.mediaStoreId == ref.mediaStoreId || it.path == ref.path } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val seedColor: StateFlow<Color?> = controller.state
        .map { it.current?.albumId }
        .distinctUntilChanged()
        .mapLatest { albumId -> albumId?.let { seeds.seedFor(it) } }
        // Start from the cached seed so reopening the player shows the album colors at once.
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            controller.state.value.current?.albumId?.let(seeds::cachedSeed),
        )

    val onlineLyrics: StateFlow<Boolean> = settings.settings
        .map { it.onlineLyrics }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _lyricsVisible = MutableStateFlow(false)

    /** Lyrics shown in place of the disc. */
    val lyricsVisible: StateFlow<Boolean> = _lyricsVisible.asStateFlow()

    /** Loaded only while visible, for the current track; a track change cancels a pending load. */
    val lyrics: StateFlow<LyricsState> = combine(_lyricsVisible, controller.state.map { it.current }.distinctUntilChanged()) { v, t -> v to t }
        .transformLatest { (visible, track) ->
            when {
                !visible -> emit(LyricsState.Hidden)
                track == null -> emit(LyricsState.None)
                else -> {
                    emit(LyricsState.Loading)
                    emit(lyricsRepository.load(track)?.let { LyricsState.Shown(it) } ?: LyricsState.None)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsState.Hidden)

    fun toggleLyrics() {
        _lyricsVisible.value = !_lyricsVisible.value
        controller.fastPosition = _lyricsVisible.value
    }

    override fun onCleared() {
        controller.fastPosition = false
    }

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
