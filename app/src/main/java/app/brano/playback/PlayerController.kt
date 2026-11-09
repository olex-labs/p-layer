package app.brano.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import app.brano.R
import app.brano.data.library.LibraryRepository
import app.brano.data.library.Track
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
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
 * [PlayerUiState.queue], which skips items that cannot be shown (neither in the library nor
 * carrying their own metadata).
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

    /** Media ids in the order before shuffle was turned on in this process; null when unknown. */
    private var unshuffledIds: List<String>? = null
    private val random = Random.Default

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

    /** Plays [tracks] from [startIndex], keeping the current shuffle mode (shuffled after that track if on). */
    fun play(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        withController { c -> start(c, tracks, startIndex.coerceIn(0, tracks.lastIndex), c.shuffleModeEnabled) }
    }

    /** "Casuale": all of [tracks] in random order, starting from a random one, with shuffle on. */
    fun shuffle(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        withController { c -> start(c, tracks, random.nextInt(tracks.size), shuffle = true) }
    }

    private fun start(c: MediaController, tracks: List<Track>, startIndex: Int, shuffle: Boolean) {
        val items = tracks.map(MediaItemMapper::toMediaItem)
        if (shuffle) {
            unshuffledIds = items.map { it.mediaId }
            c.setMediaItems(QueueShuffle.shuffledFrom(items, startIndex, random), 0, 0L)
        } else {
            unshuffledIds = null
            c.setMediaItems(items, startIndex, 0L)
        }
        c.shuffleModeEnabled = shuffle
        c.prepare()
        c.play()
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

    /**
     * Shuffle reorders the queue itself: on, every other item moves after the current one in random
     * order; off, the order from before shuffling comes back (when known in this process), with
     * items added meanwhile at the end. The player's shuffle flag only records the mode, since the
     * service uses an identity shuffle order.
     */
    fun setShuffle(on: Boolean) = withController { c ->
        if (on == c.shuffleModeEnabled) return@withController
        val cur = c.currentMediaItemIndex
        val items = List(c.mediaItemCount) { c.getMediaItemAt(it) }
        if (cur in items.indices) {
            if (on) {
                unshuffledIds = items.map { it.mediaId }
                rearrange(c, QueueShuffle.shuffledFrom(items, cur, random), 0)
            } else {
                unshuffledIds?.let { original ->
                    val (ids, index) = QueueShuffle.restore(items.map { it.mediaId }, cur, original)
                    val byId = items.associateBy { it.mediaId }
                    rearrange(c, ids.map { byId.getValue(it) }, index)
                }
            }
        }
        if (!on) unshuffledIds = null
        c.shuffleModeEnabled = on
    }

    /** Replaces everything around the current item so the queue becomes [order], with the current at [currentAt]. */
    private fun rearrange(c: MediaController, order: List<MediaItem>, currentAt: Int) {
        val cur = c.currentMediaItemIndex
        if (cur < 0 || currentAt !in order.indices) return
        if (cur + 1 < c.mediaItemCount) c.removeMediaItems(cur + 1, c.mediaItemCount)
        if (cur > 0) c.removeMediaItems(0, cur)
        if (currentAt > 0) c.addMediaItems(0, order.subList(0, currentAt))
        if (currentAt < order.lastIndex) c.addMediaItems(order.subList(currentAt + 1, order.size))
    }

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
        // Keep the last track visible until the next connection replaces it (no blank flash on return).
        _state.update { it.copy(connected = false, isPlaying = false) }
    }

    private fun refresh(c: MediaController, rebuildQueue: Boolean) {
        var queue = _state.value.queue
        val current: Track?
        if (rebuildQueue) {
            val view = PlayerStateBuilder.build(
                List(c.mediaItemCount) { c.getMediaItemAt(it) },
                c.currentMediaItemIndex,
                libraryRepository::trackById,
            )
            queue = view.queue
            timelineIndices = view.timelineIndices
            current = view.current
        } else {
            current = c.currentMediaItem?.let { PlayerStateBuilder.resolve(it, libraryRepository::trackById) }
        }
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
                connected = true,
                hasItems = c.mediaItemCount > 0,
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
    }

    private val controllerListener = object : MediaController.Listener {
        /** The service reports failing items explicitly (see [PlaybackService.ACTION_PLAYBACK_ERROR]). */
        @OptIn(UnstableApi::class)
        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (command.customAction != PlaybackService.ACTION_PLAYBACK_ERROR) {
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            _errors.tryEmit(context.getString(R.string.playback_error, PlaybackErrors.titleOf(args)))
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

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
