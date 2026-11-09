package app.brano.playback

import app.brano.playback.PlaybackPolicy.ErrorRecovery
import androidx.media3.common.PlaybackException
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackPolicyTest {

    @Test
    fun emptyQueueIsNotSavedBeforeThePlayerEverHeldItems() {
        // Restore skipped (timeout / nothing restorable): must not overwrite the saved queue.
        assertThat(PlaybackPolicy.shouldSaveQueue(itemCount = 0, hadItems = false)).isFalse()
    }

    @Test
    fun emptyQueueIsSavedOnceThePlayerHeldItems() {
        // The user cleared the queue: that is a real state to persist.
        assertThat(PlaybackPolicy.shouldSaveQueue(itemCount = 0, hadItems = true)).isTrue()
    }

    @Test
    fun nonEmptyQueueIsAlwaysSaved() {
        assertThat(PlaybackPolicy.shouldSaveQueue(itemCount = 3, hadItems = false)).isTrue()
    }

    private fun after(
        errorCode: Int = PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        index: Int = 0,
        itemCount: Int = 2,
        wasPlayWhenReady: Boolean = true,
        consecutiveErrors: Int = 1,
    ) = PlaybackPolicy.afterError(errorCode, index, itemCount, wasPlayWhenReady, consecutiveErrors)

    @Test
    fun sourceErrorWhilePlayingWithNextItemRemovesAndResumes() {
        assertThat(after()).isEqualTo(ErrorRecovery.REMOVE_AND_PLAY)
    }

    @Test
    fun sourceErrorWhilePausedWithNextItemRemovesButStaysPaused() {
        // e.g. the restored current file is unreadable: the app must stay paused.
        assertThat(after(wasPlayWhenReady = false)).isEqualTo(ErrorRecovery.REMOVE_AND_PREPARE_PAUSED)
    }

    @Test
    fun sourceErrorOnLastItemRemovesAndStops() {
        assertThat(after(index = 2, itemCount = 3)).isEqualTo(ErrorRecovery.REMOVE_AND_STOP)
    }

    @Test
    fun sourceErrorOnOnlyItemRemovesAndStops() {
        assertThat(after(index = 0, itemCount = 1)).isEqualTo(ErrorRecovery.REMOVE_AND_STOP)
    }

    @Test
    fun ioParsingAndDecodingErrorsAreSourceErrors() {
        listOf(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        ).forEach { assertThat(after(errorCode = it)).isEqualTo(ErrorRecovery.REMOVE_AND_PLAY) }
    }

    @Test
    fun audioOutputAndOtherErrorsPauseAndKeepTheItem() {
        // Not the file's fault (e.g. AudioTrack init failure on a route change): never empty the queue.
        listOf(
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
            PlaybackException.ERROR_CODE_UNSPECIFIED,
            PlaybackException.ERROR_CODE_REMOTE_ERROR,
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
        ).forEach { assertThat(after(errorCode = it)).isEqualTo(ErrorRecovery.PAUSE_AND_KEEP) }
    }

    @Test
    fun thirdConsecutiveErrorStopsTheCascade() {
        assertThat(after(consecutiveErrors = 2)).isEqualTo(ErrorRecovery.REMOVE_AND_PLAY)
        // e.g. the SD card was unmounted: every file fails; stop after three instead of emptying the queue.
        assertThat(after(consecutiveErrors = 3)).isEqualTo(ErrorRecovery.PAUSE_AND_KEEP)
        assertThat(after(consecutiveErrors = 7)).isEqualTo(ErrorRecovery.PAUSE_AND_KEEP)
    }
}
