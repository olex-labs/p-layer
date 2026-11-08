package app.brano.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import app.brano.R
import app.brano.data.library.LibraryRepository
import app.brano.data.library.Track
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * App-wide wrapper around a [MediaController] connected to [PlaybackService].
 *
 * Connects lazily ([connect] or the first command); commands issued before the connection is
 * ready are queued and run once it is. Queue indices taken and exposed here refer to
 * [PlayerUiState.queue], which skips items no longer in the library.
 */
@Singleton
class PlayerController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()
    private var positionJob: Job? = null

    /** Timeline index in the controller for each entry of [PlayerUiState.queue]. */
    private var timelineIndices: List<Int> = emptyList()

    init {
        scope.launch {
            libraryRepository.tracks.collect { controller?.let { refresh(it, rebuildQueue = true) } }
        }
    }

    fun connect() {
        scope.launch {
            if (future != null) return@launch
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val f = MediaController.Builder(context, token)
                .setListener(controllerListener)
                .buildAsync()
            future = f
            f.addListener({
                if (future !== f) return@addListener // disconnected meanwhile
                val c = runCatching { f.get() }.getOrNull()
                if (c == null) {
                    future = null
                    return@addListener
                }
                controller = c
                c.addListener(playerListener)
                _state.update { it.copy(sleep = PlaybackService.sleepStateOf(c.sessionExtras)) }
                refresh(c, rebuildQueue = true)
                val queued = pending.toList()
                pending.clear()
                queued.forEach { it(c) }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    /** Releases the controller; the service stops once nothing else holds it and it is not playing. */
    fun disconnect() {
        scope.launch {
            val f = future ?: return@launch
            controller?.removeListener(playerListener)
            MediaController.releaseFuture(f)
            reset()
        }
    }

    fun play(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(
                tracks.map(MediaItemMapper::toMediaItem),
                startIndex.coerceIn(0, tracks.lastIndex),
                0L,
            )
            c.prepare()
            c.play()
        }
    }

    fun playNext(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) {
                play(tracks)
            } else {
                c.addMediaItems(c.currentMediaItemIndex + 1, tracks.map(MediaItemMapper::toMediaItem))
            }
        }
    }

    fun addToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) play(tracks) else c.addMediaItems(tracks.map(MediaItemMapper::toMediaItem))
        }
    }

    fun moveInQueue(from: Int, to: Int) = withController { c ->
        val f = timelineIndices.getOrNull(from) ?: return@withController
        val t = timelineIndices.getOrNull(to) ?: return@withController
        c.moveMediaItem(f, t)
    }

    fun removeFromQueue(index: Int) = withController { c ->
        timelineIndices.getOrNull(index)?.let(c::removeMediaItem)
    }

    fun skipToQueueItem(index: Int) = withController { c ->
        val i = timelineIndices.getOrNull(index) ?: return@withController
        c.seekToDefaultPosition(i)
        c.play()
    }

    @OptIn(UnstableApi::class)
    fun togglePlayPause() = withController { c -> Util.handlePlayPauseButtonAction(c) }

    fun next() = withController { it.seekToNext() }

    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(ms: Long) = withController { c ->
        c.seekTo(ms)
        _state.update { it.copy(positionMs = ms) }
    }

    fun setShuffle(on: Boolean) = withController { it.shuffleModeEnabled = on }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun startSleepTimer(ms: Long) =
        sendCustom(PlaybackService.ACTION_SLEEP_START, Bundle().apply { putLong(PlaybackService.EXTRA_MS, ms) })

    fun sleepAtEndOfTrack() = sendCustom(PlaybackService.ACTION_SLEEP_END_OF_TRACK)

    fun cancelSleepTimer() = sendCustom(PlaybackService.ACTION_SLEEP_CANCEL)

    private fun sendCustom(action: String, args: Bundle = Bundle.EMPTY) = withController { c ->
        c.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
    }

    private fun withController(block: (MediaController) -> Unit) {
        scope.launch {
            val c = controller
            if (c != null) {
                block(c)
            } else {
                pending += block
                connect()
            }
        }
    }

    private fun reset() {
        future = null
        controller = null
        positionJob?.cancel()
        positionJob = null
        timelineIndices = emptyList()
        _state.value = PlayerUiState()
    }

    private fun refresh(c: MediaController, rebuildQueue: Boolean) {
        var queue = _state.value.queue
        if (rebuildQueue) {
            val tracks = ArrayList<Track>(c.mediaItemCount)
            val indices = ArrayList<Int>(c.mediaItemCount)
            for (i in 0 until c.mediaItemCount) {
                libraryRepository.trackById(MediaItemMapper.idOf(c.getMediaItemAt(i)))?.let {
                    tracks += it
                    indices += i
                }
            }
            queue = tracks
            timelineIndices = indices
        }
        val current = c.currentMediaItem?.let { libraryRepository.trackById(MediaItemMapper.idOf(it)) }
        val duration = c.duration.takeIf { it != C.TIME_UNSET } ?: current?.durationMs ?: 0L
        _state.update {
            it.copy(
                current = current,
                isPlaying = c.isPlaying,
                positionMs = c.currentPosition,
                durationMs = duration,
                queue = queue,
                index = timelineIndices.indexOf(c.currentMediaItemIndex),
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
            )
        }
        updatePositionPolling(c)
    }

    private fun updatePositionPolling(c: MediaController) {
        if (c.isPlaying) {
            if (positionJob?.isActive == true) return
            positionJob = scope.launch {
                while (isActive) {
                    _state.update { it.copy(positionMs = c.currentPosition) }
                    delay(POSITION_INTERVAL_MS)
                }
            }
        } else {
            positionJob?.cancel()
            positionJob = null
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val c = controller ?: return
            refresh(c, rebuildQueue = events.contains(Player.EVENT_TIMELINE_CHANGED))
        }

        override fun onPlayerError(error: PlaybackException) {
            val name = _state.value.current?.title
                ?: controller?.currentMediaItem?.mediaMetadata?.title?.toString()
                ?: error.errorCodeName
            _errors.tryEmit(context.getString(R.string.playback_error, name))
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            _state.update { it.copy(sleep = PlaybackService.sleepStateOf(extras)) }
        }

        override fun onDisconnected(controller: MediaController) {
            scope.launch {
                if (this@PlayerController.controller === controller) reset()
            }
        }
    }

    private companion object {
        const val POSITION_INTERVAL_MS = 500L
    }
}
