package app.brano.playback

import androidx.media3.common.Player
import app.brano.data.library.Track

data class PlayerUiState(
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val sleep: SleepTimerState = SleepTimerState.Off,
    /** True while a controller is connected; the other fields keep their last values otherwise. */
    val connected: Boolean = false,
    /** The player holds at least one item (even one that cannot be shown). */
    val hasItems: Boolean = false,
)
