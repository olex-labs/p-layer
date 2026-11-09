package app.brano.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import app.brano.MainActivity
import app.brano.data.db.PlayStatsDao
import app.brano.data.library.LibraryRepository
import app.brano.data.library.ref
import app.brano.data.settings.QueueState
import app.brano.data.settings.QueueStateStore
import app.brano.data.settings.restore
import app.brano.di.ApplicationScope
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var libraryRepository: LibraryRepository
    @Inject lateinit var queueStateStore: QueueStateStore
    @Inject lateinit var playStatsDao: PlayStatsDao
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e -> Log.e(TAG, "Playback service coroutine failed", e) },
    )

    private lateinit var player: ExoPlayer

    /** What the session exposes: shuffles for every controller and reports new lists. */
    private lateinit var shufflingPlayer: ShufflingPlayer
    private lateinit var session: MediaSession
    private lateinit var sleepTimer: SleepTimer
    private lateinit var playCountTracker: PlayCountTracker

    /** True once the player held items in this instance; until then an empty queue is never saved. */
    private var hadItems = false

    /** Player errors since something last played or the user last picked a track. */
    private var consecutiveErrors = 0

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        // Shuffle physically reorders the items (ShufflingPlayer), so the shuffle flag must not
        // reorder playback a second time: keep the timeline order in both modes.
        player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(0))
        // A new list is a fresh start: earlier failures must not count towards pausing this one.
        shufflingPlayer = ShufflingPlayer(player) { consecutiveErrors = 0 }
        sleepTimer = SleepTimer(
            scope,
            onVolume = { player.volume = it },
            onExpire = { player.pause() },
        )
        playCountTracker = PlayCountTracker(::recordPlay)
        player.addListener(playerListener)

        val activityIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, shufflingPlayer)
            .setCallback(sessionCallback)
            .setSessionActivity(activityIntent)
            .build()

        scope.launch { restoreQueue() }
        scope.launch { sleepTimer.state.collect { session.setSessionExtras(it.toBundle()) } }
        scope.launch { progressLoop() }
        scope.launch { periodicSaveLoop() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveState()
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveState()
        player.removeListener(playerListener)
        session.release()
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun restoreQueue() {
        val saved = queueStateStore.load() ?: return
        withTimeoutOrNull(RESTORE_TIMEOUT_MS) { libraryRepository.tracks.first { it.isNotEmpty() } }
            ?: return
        if (player.mediaItemCount > 0) return // a controller already started something
        val restored = saved.restore(libraryRepository::trackById) ?: return
        player.repeatMode = saved.repeatMode
        player.setMediaItems(
            restored.tracks.map(MediaItemMapper::toMediaItem),
            restored.index,
            restored.positionMs,
        )
        shufflingPlayer.restoreShuffle(saved.shuffle, saved.unshuffledIds?.map(Long::toString))
        player.playWhenReady = false
        player.prepare()
    }

    private suspend fun progressLoop() {
        while (true) {
            delay(PROGRESS_INTERVAL_MS)
            if (player.isPlaying) playCountTracker.onProgress(player.currentPosition, player.duration)
        }
    }

    private suspend fun periodicSaveLoop() {
        while (true) {
            delay(SAVE_INTERVAL_MS)
            if (player.isPlaying) saveState()
        }
    }

    /** Snapshots the queue on the main thread, then writes it off the main thread in call order. */
    private fun saveState() {
        if (!PlaybackPolicy.shouldSaveQueue(player.mediaItemCount, hadItems)) return
        val state = QueueState(
            ids = List(player.mediaItemCount) { MediaItemMapper.idOf(player.getMediaItemAt(it)) },
            index = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            unshuffledIds = shufflingPlayer.unshuffledIds?.mapNotNull(String::toLongOrNull),
        )
        appScope.launch(saveDispatcher) {
            runCatching { queueStateStore.save(state) }
                .onFailure { Log.e(TAG, "Saving queue state failed", it) }
        }
    }

    private fun recordPlay(id: Long) {
        val track = libraryRepository.trackById(id) ?: return
        val now = System.currentTimeMillis()
        appScope.launch(Dispatchers.IO) {
            runCatching { playStatsDao.increment(track.ref(), now) }
                .onFailure { Log.e(TAG, "Recording play failed", it) }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            ) {
                sleepTimer.onTrackEnded()
            }
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) consecutiveErrors = 0 // the user picked a track
            mediaItem?.let { playCountTracker.onTrackStarted(MediaItemMapper.idOf(it)) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) sleepTimer.onTrackEnded()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) consecutiveErrors = 0 else saveState()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (!timeline.isEmpty) hadItems = true
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Playback error ${error.errorCodeName}", error)
            consecutiveErrors++
            val resume = player.playWhenReady
            val index = player.currentMediaItemIndex
            // Tell the UI explicitly: controllers never see playerError once prepare() below clears it.
            val title = player.currentMediaItem?.mediaMetadata?.title?.toString()
            session.broadcastCustomCommand(
                SessionCommand(ACTION_PLAYBACK_ERROR, Bundle.EMPTY),
                PlaybackErrors.args(title ?: error.errorCodeName),
            )
            val count = player.mediaItemCount
            if (index !in 0 until count) return
            when (PlaybackPolicy.afterError(error.errorCode, index, count, resume, consecutiveErrors)) {
                PlaybackPolicy.ErrorRecovery.PAUSE_AND_KEEP -> player.playWhenReady = false
                PlaybackPolicy.ErrorRecovery.REMOVE_AND_STOP -> {
                    player.removeMediaItem(index)
                    player.playWhenReady = false
                }
                PlaybackPolicy.ErrorRecovery.REMOVE_AND_PREPARE_PAUSED -> {
                    player.removeMediaItem(index)
                    player.playWhenReady = false
                    player.prepare()
                }
                PlaybackPolicy.ErrorRecovery.REMOVE_AND_PLAY -> {
                    player.removeMediaItem(index)
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            if (controller.packageName != packageName) {
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
            }
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_SLEEP_START, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SLEEP_END_OF_TRACK, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SLEEP_CANCEL, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_SLEEP_START -> sleepTimer.start(args.getLong(EXTRA_MS))
                ACTION_SLEEP_END_OF_TRACK -> sleepTimer.startEndOfTrack()
                ACTION_SLEEP_CANCEL -> sleepTimer.cancel()
                else -> return Futures.immediateFuture(
                    SessionResult(androidx.media3.session.SessionError.ERROR_NOT_SUPPORTED),
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /** Items may arrive without a URI (e.g. from external controllers); resolve them by id. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.mapNotNull { item ->
                if (item.localConfiguration != null) {
                    item
                } else {
                    libraryRepository.trackById(MediaItemMapper.idOf(item))
                        ?.let(MediaItemMapper::toMediaItem)
                }
            }
            return Futures.immediateFuture(resolved.toMutableList())
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val RESTORE_TIMEOUT_MS = 5_000L
        private const val SAVE_INTERVAL_MS = 5_000L
        private const val PROGRESS_INTERVAL_MS = 1_000L

        const val ACTION_SLEEP_START = "app.brano.SLEEP_START"
        const val ACTION_SLEEP_END_OF_TRACK = "app.brano.SLEEP_END_OF_TRACK"
        const val ACTION_SLEEP_CANCEL = "app.brano.SLEEP_CANCEL"
        const val EXTRA_MS = "ms"

        /** Sent by the service to its controllers when an item fails; args via [PlaybackErrors]. */
        const val ACTION_PLAYBACK_ERROR = "app.brano.PLAYBACK_ERROR"

        private const val EXTRA_SLEEP_KIND = "sleep_kind"
        private const val EXTRA_SLEEP_REMAINING_MS = "sleep_remaining_ms"
        private const val KIND_COUNTING = "counting"
        private const val KIND_END_OF_TRACK = "end_of_track"

        @OptIn(ExperimentalCoroutinesApi::class)
        private val saveDispatcher = Dispatchers.IO.limitedParallelism(1)

        internal fun SleepTimerState.toBundle(): Bundle = Bundle().also { b ->
            when (this) {
                SleepTimerState.Off -> Unit
                SleepTimerState.EndOfTrack -> b.putString(EXTRA_SLEEP_KIND, KIND_END_OF_TRACK)
                is SleepTimerState.Counting -> {
                    b.putString(EXTRA_SLEEP_KIND, KIND_COUNTING)
                    b.putLong(EXTRA_SLEEP_REMAINING_MS, remainingMs)
                }
            }
        }

        internal fun sleepStateOf(extras: Bundle): SleepTimerState =
            when (extras.getString(EXTRA_SLEEP_KIND)) {
                KIND_COUNTING -> SleepTimerState.Counting(extras.getLong(EXTRA_SLEEP_REMAINING_MS))
                KIND_END_OF_TRACK -> SleepTimerState.EndOfTrack
                else -> SleepTimerState.Off
            }
    }
}

/** Pure decisions of [PlaybackService], kept separate so they can be unit-tested. */
internal object PlaybackPolicy {
    enum class ErrorRecovery { REMOVE_AND_PLAY, REMOVE_AND_PREPARE_PAUSED, REMOVE_AND_STOP, PAUSE_AND_KEEP }

    /** After this many errors in a row without anything playing, stop skipping. */
    const val MAX_CONSECUTIVE_ERRORS = 3

    /** An empty queue is only persisted once this service instance actually held items. */
    fun shouldSaveQueue(itemCount: Int, hadItems: Boolean): Boolean = itemCount > 0 || hadItems

    /** IO (2xxx), parsing (3xxx) and decoding (4xxx) failures: the file itself cannot be played. */
    fun isSourceError(errorCode: Int): Boolean = errorCode in 2000..4999

    /**
     * What to do when the item at [index] of [itemCount] fails with [errorCode]. Only a source error
     * removes the item, and only while fewer than [MAX_CONSECUTIVE_ERRORS] errors happened in a row
     * ([consecutiveErrors] includes this one); anything else pauses and keeps the queue intact.
     * After a removal playback continues only if a next item exists and playback was already wanted.
     */
    fun afterError(
        errorCode: Int,
        index: Int,
        itemCount: Int,
        wasPlayWhenReady: Boolean,
        consecutiveErrors: Int,
    ): ErrorRecovery = when {
        !isSourceError(errorCode) || consecutiveErrors >= MAX_CONSECUTIVE_ERRORS -> ErrorRecovery.PAUSE_AND_KEEP
        index >= itemCount - 1 -> ErrorRecovery.REMOVE_AND_STOP
        wasPlayWhenReady -> ErrorRecovery.REMOVE_AND_PLAY
        else -> ErrorRecovery.REMOVE_AND_PREPARE_PAUSED
    }
}

/** Arguments of the [PlaybackService.ACTION_PLAYBACK_ERROR] command the service sends to its controllers. */
internal object PlaybackErrors {
    private const val EXTRA_TITLE = "title"

    fun args(title: String?): Bundle = Bundle().apply { putString(EXTRA_TITLE, title) }

    fun titleOf(args: Bundle, fallback: String = ""): String = args.getString(EXTRA_TITLE) ?: fallback
}
