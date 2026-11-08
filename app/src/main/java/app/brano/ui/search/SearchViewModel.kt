package app.brano.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brano.data.library.LibraryRepository
import app.brano.data.library.Track
import app.brano.domain.SearchResults
import app.brano.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel internal constructor(
    library: LibraryRepository,
    private val player: PlayerController,
    private val savedState: SavedStateHandle,
    searchDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @Inject
    constructor(
        library: LibraryRepository,
        player: PlayerController,
        savedState: SavedStateHandle,
    ) : this(library, player, savedState, Dispatchers.Default)

    /** Kept in [SavedStateHandle] so it survives rotation and process death. */
    val query: StateFlow<String> = savedState.getStateFlow(KEY_QUERY, "")

    val results: StateFlow<SearchResults> = combine(query, library.search) { q, index -> q to index }
        .mapLatest { (q, index) -> index.query(q) }
        .flowOn(searchDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults.EMPTY)

    val currentId: StateFlow<Long?> = player.state
        .map { it.current?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQuery(q: String) {
        savedState[KEY_QUERY] = q
    }

    fun play(tracks: List<Track>, startIndex: Int, shuffle: Boolean = false) = player.play(tracks, startIndex, shuffle)

    private companion object {
        const val KEY_QUERY = "query"
    }
}
