package app.brano.ui.playlists

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brano.data.db.PlaylistDao
import app.brano.data.db.PlaylistSummary
import app.brano.domain.PlaylistIo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlaylistsEvent {
    data class Imported(val matched: Int, val notFound: Int) : PlaylistsEvent
    data object ImportFailed : PlaylistsEvent
}

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val playlistIo: PlaylistIo,
) : ViewModel() {
    val playlists: StateFlow<List<PlaylistSummary>> =
        playlistDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<PlaylistsEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<PlaylistsEvent> = _events

    fun create(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { playlistDao.create(trimmed) }
    }

    fun import(source: Uri) {
        viewModelScope.launch {
            val event = try {
                val result = playlistIo.import(source)
                PlaylistsEvent.Imported(result.matched.size, result.notFound)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                PlaylistsEvent.ImportFailed
            }
            _events.emit(event)
        }
    }
}
