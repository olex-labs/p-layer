package app.brano.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayCountTrackerTest {
    private val counted = mutableListOf<Long>()
    private val tr = PlayCountTracker { counted += it }

    @Test fun count_atHalf_once() {
        tr.onTrackStarted(1)
        tr.onProgress(99_999, 200_000)
        assertThat(counted).isEmpty()
        tr.onProgress(100_000, 200_000)
        tr.onProgress(150_000, 200_000)
        assertThat(counted).containsExactly(1L)
    }

    @Test fun count_longTrack_at4min() {
        tr.onTrackStarted(2)
        tr.onProgress(239_999, 1_200_000)
        assertThat(counted).isEmpty()
        tr.onProgress(240_000, 1_200_000)
        assertThat(counted).containsExactly(2L)
    }

    @Test fun count_againAfterNewTrackStarted() {
        tr.onTrackStarted(1)
        tr.onProgress(100_000, 200_000)
        tr.onTrackStarted(1)
        tr.onProgress(100_000, 200_000)
        assertThat(counted).containsExactly(1L, 1L)
    }

    @Test fun progressBeforeStart_isNoOp() {
        tr.onProgress(500_000, 200_000)
        assertThat(counted).isEmpty()
    }

    @Test fun nonPositiveDuration_neverCounts() {
        tr.onTrackStarted(3)
        tr.onProgress(0, 0)
        tr.onProgress(10_000, -1)
        assertThat(counted).isEmpty()
    }
}
