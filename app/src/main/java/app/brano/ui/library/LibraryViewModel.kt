package app.brano.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brano.data.library.Album
import app.brano.data.library.Artist
import app.brano.data.library.FolderNode
import app.brano.data.library.LibraryRepository
import app.brano.data.library.Track
import app.brano.data.settings.SettingsRepository
import app.brano.data.settings.TrackSort
import app.brano.domain.Normalizer
import app.brano.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** TITLE/ARTIST/ALBUM ascend by normalized text with missing values last; DATE_ADDED and DURATION descend. */
fun sortTracks(tracks: List<Track>, sort: TrackSort): List<Track> = when (sort) {
    TrackSort.TITLE -> byText(tracks) { it.title }
    TrackSort.ARTIST -> byText(tracks) { it.artist }
    TrackSort.ALBUM -> byText(tracks) { it.album }
    TrackSort.DATE_ADDED -> tracks.sortedByDescending { it.dateAddedSec }
    TrackSort.DURATION -> tracks.sortedByDescending { it.durationMs }
}

private fun byText(tracks: List<Track>, text: (Track) -> String?): List<Track> =
    tracks.map { Triple(it, text(it)?.let(Normalizer::normalize), Normalizer.normalize(it.title)) }
        .sortedWith(
            compareBy<Triple<Track, String?, String>> { it.second == null }
                .thenBy { it.second.orEmpty() }
                .thenBy { it.third },
        )
        .map { it.first }

@HiltViewModel
class LibraryViewModel internal constructor(
    library: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val player: PlayerController,
    sortDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @Inject
    constructor(
        library: LibraryRepository,
        settingsRepository: SettingsRepository,
        player: PlayerController,
    ) : this(library, settingsRepository, player, Dispatchers.Default)

    private val sortSetting: Flow<TrackSort> = settingsRepository.settings.map { it.trackSort }.distinctUntilChanged()

    val sort: StateFlow<TrackSort> = sortSetting
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackSort.TITLE)

    val tracks: StateFlow<List<Track>> = combine(library.tracks, sortSetting, ::sortTracks)
        .flowOn(sortDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<Album>> = library.albums
    val artists: StateFlow<List<Artist>> = library.artists
    val folders: StateFlow<FolderNode?> = library.folders
    val isLoading: StateFlow<Boolean> = library.isLoading

    val currentId: StateFlow<Long?> = player.state
        .map { it.current?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSort(s: TrackSort) {
        viewModelScope.launch { settingsRepository.update { it.copy(trackSort = s) } }
    }

    fun playAll(startIndex: Int) = player.play(tracks.value, startIndex)

    fun shuffleAll() = player.play(tracks.value, 0, shuffle = true)

    fun play(tracks: List<Track>, startIndex: Int, shuffle: Boolean = false) = player.play(tracks, startIndex, shuffle)
}
