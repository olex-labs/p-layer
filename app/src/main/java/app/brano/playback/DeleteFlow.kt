package app.brano.playback

import app.brano.data.library.Track
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Deleting tracks' files, which needs the system's confirmation. Lives above any screen (a singleton
 * scope), so the answer is never lost when the menu that started it closes or the screen rotates.
 * Every confirmation to show is sent on [requests]; the host shows it and reports with [onResult].
 *
 * [trash] (Android 11+): one dialog for all, files go to the system trash. Otherwise (Android 10)
 * files are deleted one by one, asking only for those that need it; a failure skips that file.
 * [S] is the platform's IntentSender (a plain value in tests).
 */
class DeleteFlow<S : Any>(
    private val scope: CoroutineScope,
    private val trash: Boolean,
    private val trashRequest: (List<Track>) -> S,
    private val deleteFile: suspend (Track) -> Outcome<S>,
    private val onDeleted: (List<Track>) -> Unit,
) {
    sealed interface Outcome<out S> {
        data object Deleted : Outcome<Nothing>
        data object Failed : Outcome<Nothing>
        data class NeedsUser<S>(val request: S) : Outcome<S>
        /** Test helper: makes [deleteFile] throw. */
        data object Boom : Outcome<Nothing>
    }

    private val _requests = Channel<S>(Channel.BUFFERED)
    val requests: Flow<S> = _requests.receiveAsFlow()

    private var pending: List<Track> = emptyList()
    private var waiting: Track? = null
    private val done = ArrayList<Track>()

    fun start(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        pending = tracks
        done.clear()
        if (trash) {
            _requests.trySend(trashRequest(tracks))
        } else {
            scope.launch { next() }
        }
    }

    fun onResult(ok: Boolean) {
        if (trash) {
            if (ok && pending.isNotEmpty()) onDeleted(pending)
            pending = emptyList()
            return
        }
        val track = waiting ?: return
        waiting = null
        if (!ok) {
            pending = emptyList()
            finish()
            return
        }
        scope.launch {
            if (safeDelete(track) == Outcome.Deleted) done += track
            next()
        }
    }

    private suspend fun next() {
        while (pending.isNotEmpty()) {
            val track = pending.first()
            pending = pending.drop(1)
            when (val o = safeDelete(track)) {
                Outcome.Deleted -> done += track
                is Outcome.NeedsUser -> {
                    waiting = track
                    _requests.trySend(o.request)
                    return
                }
                else -> Unit
            }
        }
        finish()
    }

    private fun finish() {
        if (done.isNotEmpty()) onDeleted(done.toList())
        done.clear()
    }

    private suspend fun safeDelete(track: Track): Outcome<S> = try {
        deleteFile(track)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failed
    }
}
