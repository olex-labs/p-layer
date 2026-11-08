package app.brano.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import app.brano.data.db.PlaylistDao
import app.brano.data.db.PlaylistSummary
import app.brano.data.library.LibraryRepository
import app.brano.data.library.Track
import app.brano.data.library.ref
import app.brano.data.settings.AppSettings
import app.brano.data.settings.SettingsRepository
import app.brano.playback.PlayerController
import app.brano.playback.PlayerUiState
import app.brano.ui.components.albumArtUri
import app.brano.ui.theme.seedFromPalette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class AppViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    settingsRepository: SettingsRepository,
    private val playerController: PlayerController,
    private val libraryRepository: LibraryRepository,
    private val playlistDao: PlaylistDao,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val player: StateFlow<PlayerUiState> = playerController.state

    val playlists: StateFlow<List<PlaylistSummary>> =
        playlistDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Theme seed from the current track's artwork; null when nothing plays or art has no usable color. */
    val seedColor: StateFlow<Color?> = playerController.state
        .map { it.current?.albumId }
        .distinctUntilChanged()
        .mapLatest { albumId -> albumId?.let { loadSeed(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun connect() = playerController.connect()

    fun disconnect() = playerController.disconnect()

    fun togglePlayPause() = playerController.togglePlayPause()

    fun next() = playerController.next()

    fun playNext(tracks: List<Track>) = playerController.playNext(tracks)

    fun addToQueue(tracks: List<Track>) = playerController.addToQueue(tracks)

    fun addToPlaylist(id: Long, tracks: List<Track>) {
        viewModelScope.launch { playlistDao.addTracks(id, tracks.map { it.ref() }) }
    }

    fun createPlaylist(name: String, tracks: List<Track>) {
        viewModelScope.launch {
            val id = playlistDao.create(name)
            playlistDao.addTracks(id, tracks.map { it.ref() })
        }
    }

    /** Called once the audio permission is available so the library is (re)read. */
    fun onAudioPermissionGranted() {
        viewModelScope.launch { libraryRepository.refresh() }
    }

    private suspend fun loadSeed(albumId: Long): Color? = withContext(Dispatchers.Default) {
        val request = ImageRequest.Builder(context)
            .data(albumArtUri(albumId))
            .allowHardware(false)
            .size(128)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@withContext null
        seedFromPalette(Palette.from(result.drawable.toBitmap()).generate())
    }
}
