package app.brano.playback

import app.brano.playback.PlaybackPolicy.ErrorRecovery
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

    @Test
    fun errorWhilePlayingWithNextItemResumesPlayback() {
        assertThat(PlaybackPolicy.afterErrorRemoval(removedIndex = 0, remaining = 1, wasPlayWhenReady = true))
            .isEqualTo(ErrorRecovery.PREPARE_AND_PLAY)
    }

    @Test
    fun errorWhilePausedWithNextItemPreparesButStaysPaused() {
        // e.g. the restored current file is unreadable: the app must stay paused.
        assertThat(PlaybackPolicy.afterErrorRemoval(removedIndex = 0, remaining = 1, wasPlayWhenReady = false))
            .isEqualTo(ErrorRecovery.PREPARE_PAUSED)
    }

    @Test
    fun errorOnLastItemStops() {
        assertThat(PlaybackPolicy.afterErrorRemoval(removedIndex = 2, remaining = 2, wasPlayWhenReady = true))
            .isEqualTo(ErrorRecovery.STOP)
    }

    @Test
    fun errorOnOnlyItemStops() {
        assertThat(PlaybackPolicy.afterErrorRemoval(removedIndex = 0, remaining = 0, wasPlayWhenReady = true))
            .isEqualTo(ErrorRecovery.STOP)
    }
}
