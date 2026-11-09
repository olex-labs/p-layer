package app.brano.ui.playlists

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brano.data.db.FavoritesDao
import app.brano.data.db.PlayStatsDao
import app.brano.data.db.PlaylistDao
import app.brano.data.db.ResolvedItem
import app.brano.data.db.TrackResolver
import app.brano.data.library.LibraryRepository
import app.brano.data.library.TrackRef
import app.brano.data.library.ref
import app.brano.domain.PlaylistIo
import app.brano.domain.SmartPlaylist
import app.brano.domain.SmartPlaylists
import app.brano.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlaylistDetailEvent {
    data class Removed(val ref: TrackRef, val position: Int) : PlaylistDetailEvent
    data object Exported : PlaylistDetailEvent
    data object ExportFailed : PlaylistDetailEvent
}

/** Detail of a user playlist (`id` argument) or of a smart list (`kind` argument, read-only). */
@HiltViewModel
class PlaylistDetailViewModel internal constructor(
    private val playlistId: Long?,
    val smart: SmartPlaylist?,
    private val library: LibraryRepository,
    private val playlistDao: PlaylistDao,
    favoritesDao: FavoritesDao,
    playStatsDao: PlayStatsDao,
    private val player: PlayerController,
    workDispatcher: CoroutineDispatcher,
    private val playlistIo: PlaylistIo? = null,
    private val nowSec: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        library: LibraryRepository,
        playlistDao: PlaylistDao,
        favoritesDao: FavoritesDao,
        playStatsDao: PlayStatsDao,
        player: PlayerController,
        playlistIo: PlaylistIo,
    ) : this(
        playlistId = savedStateHandle.get<Long>("id"),
        smart = savedStateHandle.get<String>("kind")?.let { k -> SmartPlaylist.entries.firstOrNull { it.name == k } },
        library = library,
        playlistDao = playlistDao,
        favoritesDao = favoritesDao,
        playStatsDao = playStatsDao,
        player = player,
        workDispatcher = Dispatchers.Default,
        playlistIo = playlistIo,
        nowSec = { System.currentTimeMillis() / 1000 },
    )

    val editable: Boolean = playlistId != null && smart == null

    /** Playlist name; empty for smart lists (the screen shows a localized title for those). */
    val title: StateFlow<String> = (
        if (playlistId != null) {
            playlistDao.observeAll().map { all -> all.firstOrNull { it.id == playlistId }?.name.orEmpty() }
        } else {
            flowOf("")
        }
        ).distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val items: StateFlow<List<ResolvedItem>> = itemsFlow(favoritesDao, playStatsDao)
        .flowOn(workDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentId: StateFlow<Long?> = player.state
        .map { it.current?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = MutableSharedFlow<PlaylistDetailEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<PlaylistDetailEvent> = _events

    private fun itemsFlow(favoritesDao: FavoritesDao, playStatsDao: PlayStatsDao): Flow<List<ResolvedItem>> = when {
        smart != null -> combine(library.tracks, favoritesDao.observe(), playStatsDao.observeAll()) { tracks, favs, stats ->
            SmartPlaylists.build(smart, tracks, favs, stats, nowSec()).map { ResolvedItem(it.ref(), it) }
        }
        playlistId != null -> combine(library.tracks, playlistDao.observeItems(playlistId)) { _, rows ->
            val (resolved, changes) = TrackResolver.resolve(
                rows.map { TrackRef(it.mediaStoreId, it.path) },
                library::trackById,
                library::trackByPath,
            )
            if (changes.isNotEmpty()) viewModelScope.launch { playlistDao.updateIds(changes) }
            resolved
        }
        else -> emptyFlow()
    }

    /** [index] is a position in [items]; the queue holds the available items only. */
    fun play(index: Int) {
        val all = items.value
        if (all.getOrNull(index)?.track == null) return
        val queue = all.mapNotNull { it.track }
        val start = all.subList(0, index).count { it.track != null }
        player.play(queue, start)
    }

    fun shuffle() {
        val queue = items.value.mapNotNull { it.track }
        if (queue.isNotEmpty()) player.shuffle(queue)
    }

    fun move(from: Int, to: Int) {
        val id = editableId() ?: return
        viewModelScope.launch { playlistDao.move(id, from, to) }
    }

    fun remove(position: Int) {
        val id = editableId() ?: return
        val ref = items.value.getOrNull(position)?.ref ?: return
        viewModelScope.launch {
            playlistDao.removeAt(id, position)
            _events.emit(PlaylistDetailEvent.Removed(ref, position))
        }
    }

    fun undoRemove(removed: PlaylistDetailEvent.Removed) {
        val id = editableId() ?: return
        viewModelScope.launch {
            playlistDao.addTracks(id, listOf(removed.ref))
            val last = playlistDao.observeItems(id).first().lastIndex
            playlistDao.move(id, last, removed.position)
        }
    }

    fun rename(name: String) {
        val id = editableId() ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { playlistDao.rename(id, trimmed) }
    }

    fun delete() {
        val id = editableId() ?: return
        viewModelScope.launch { playlistDao.delete(id) }
    }

    fun export(target: Uri) {
        val id = editableId() ?: return
        val io = playlistIo ?: return
        viewModelScope.launch {
            val ok = try {
                io.export(id, target)
                true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                false
            }
            _events.emit(if (ok) PlaylistDetailEvent.Exported else PlaylistDetailEvent.ExportFailed)
        }
    }

    private fun editableId(): Long? = if (editable) playlistId else null
}
