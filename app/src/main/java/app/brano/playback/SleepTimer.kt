package app.brano.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SleepTimerState {
    data object Off : SleepTimerState
    data class Counting(val remainingMs: Long) : SleepTimerState
    data object EndOfTrack : SleepTimerState
}

class SleepTimer(
    private val scope: CoroutineScope,
    private val onVolume: (Float) -> Unit,
    private val onExpire: () -> Unit,
) {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var job: Job? = null

    fun start(durationMs: Long) {
        reset()
        if (durationMs <= 0) {
            expire()
            return
        }
        _state.value = SleepTimerState.Counting(durationMs)
        job = scope.launch {
            var remaining = durationMs
            while (true) {
                _state.value = SleepTimerState.Counting(remaining)
                if (remaining <= 0) break
                if (remaining <= FADE_WINDOW_MS) {
                    onVolume((remaining / FADE_WINDOW_MS.toFloat()).coerceIn(0f, 1f))
                }
                val step = minOf(TICK_MS, remaining)
                delay(step)
                remaining -= step
            }
            expire()
        }
    }

    fun startEndOfTrack() {
        reset()
        _state.value = SleepTimerState.EndOfTrack
    }

    fun onTrackEnded() {
        if (_state.value == SleepTimerState.EndOfTrack) expire()
    }

    fun cancel() {
        reset()
        _state.value = SleepTimerState.Off
    }

    /** Cancels any running countdown and restores full volume. */
    private fun reset() {
        job?.cancel()
        job = null
        onVolume(1f)
    }

    private fun expire() {
        job = null
        _state.value = SleepTimerState.Off
        onExpire()
        onVolume(1f)
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val FADE_WINDOW_MS = 10_000L
    }
}
