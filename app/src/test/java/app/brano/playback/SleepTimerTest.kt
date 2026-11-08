package app.brano.playback

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {
    private val volumes = mutableListOf<Float>()
    private var expired = 0

    private fun TestScope.timer() =
        SleepTimer(backgroundScope, { volumes += it }, { expired++ })

    @Test fun timer_fadesInLast10s_thenExpires() = runTest {
        val t = timer()
        t.start(15_000)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertThat(volumes.last()).isEqualTo(1f)
        assertThat(t.state.value).isEqualTo(SleepTimerState.Counting(10_000))
        advanceTimeBy(5_000)
        runCurrent()
        assertThat(volumes.last()).isWithin(0.11f).of(0.5f)
        advanceTimeBy(5_100)
        runCurrent()
        assertThat(expired).isEqualTo(1)
        assertThat(volumes.last()).isEqualTo(1f)
        assertThat(t.state.value).isEqualTo(SleepTimerState.Off)
    }

    @Test fun timer_doesNotFadeBeforeWindow() = runTest {
        val t = timer()
        t.start(30_000)
        advanceTimeBy(15_000)
        runCurrent()
        assertThat(volumes.all { it == 1f }).isTrue()
        assertThat(t.state.value).isEqualTo(SleepTimerState.Counting(15_000))
    }

    @Test fun timer_cancelRestoresVolume() = runTest {
        val t = timer()
        t.start(30_000)
        advanceTimeBy(25_000)
        runCurrent()
        assertThat(volumes.last()).isLessThan(1f)
        t.cancel()
        assertThat(volumes.last()).isEqualTo(1f)
        assertThat(t.state.value).isEqualTo(SleepTimerState.Off)
        advanceTimeBy(60_000)
        runCurrent()
        assertThat(expired).isEqualTo(0)
    }

    @Test fun timer_endOfTrack_expiresOnTrackEnd() = runTest {
        val t = timer()
        t.startEndOfTrack()
        assertThat(t.state.value).isEqualTo(SleepTimerState.EndOfTrack)
        t.onTrackEnded()
        assertThat(expired).isEqualTo(1)
        assertThat(t.state.value).isEqualTo(SleepTimerState.Off)
    }

    @Test fun onTrackEnded_whenNotEndOfTrack_isNoOp() = runTest {
        val t = timer()
        t.onTrackEnded()
        t.start(30_000)
        t.onTrackEnded()
        assertThat(expired).isEqualTo(0)
        assertThat(t.state.value).isInstanceOf(SleepTimerState.Counting::class.java)
    }

    @Test fun secondStart_replacesFirst() = runTest {
        val t = timer()
        t.start(12_000)
        advanceTimeBy(5_000)
        runCurrent()
        t.start(60_000)
        assertThat(volumes.last()).isEqualTo(1f)
        advanceTimeBy(20_000)
        runCurrent()
        assertThat(expired).isEqualTo(0)
        assertThat(t.state.value).isEqualTo(SleepTimerState.Counting(40_000))
        advanceTimeBy(40_100)
        runCurrent()
        assertThat(expired).isEqualTo(1)
    }

    @Test fun startEndOfTrack_cancelsCountdown() = runTest {
        val t = timer()
        t.start(12_000)
        advanceTimeBy(3_000)
        runCurrent()
        t.startEndOfTrack()
        advanceTimeBy(30_000)
        runCurrent()
        assertThat(expired).isEqualTo(0)
        assertThat(volumes.last()).isEqualTo(1f)
        assertThat(t.state.value).isEqualTo(SleepTimerState.EndOfTrack)
    }

    @Test fun start_nonPositiveDuration_expiresImmediately() = runTest {
        val t = timer()
        t.start(0)
        runCurrent()
        assertThat(expired).isEqualTo(1)
        assertThat(volumes.last()).isEqualTo(1f)
        assertThat(t.state.value).isEqualTo(SleepTimerState.Off)
    }
}
