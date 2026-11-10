package app.brano.playback.eq

import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.util.Log

/**
 * The phone's equalizer on the player's audio session. Every call is guarded: a phone without the
 * effect, or one that fails, just plays without it.
 */
class EqualizerController(
    private val create: (Int) -> Equalizer = { Equalizer(0, it) },
) {
    private var eq: Equalizer? = null
    private var preset: EqPreset = EqPreset.OFF

    /** (Re)creates the effect for a new audio session and re-applies the current preset. */
    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId == 0) return
        eq = guarded { create(audioSessionId) }
        if (eq == null) _usable.value = false
        apply(preset)
    }

    fun apply(preset: EqPreset) {
        this.preset = preset
        val e = eq ?: return
        val ok = guarded {
            if (preset == EqPreset.OFF) {
                e.enabled = false
            } else {
                val bands = e.numberOfBands.toInt()
                val centers = IntArray(bands) { e.getCenterFreq(it.toShort()) }
                val range = e.bandLevelRange
                EqCurve.bandLevels(preset, centers, range[0], range[1]).forEachIndexed { band, level ->
                    e.setBandLevel(band.toShort(), level)
                }
                e.enabled = true
            }
        }
        if (ok == null) _usable.value = false
    }

    fun release() {
        eq?.let { guarded { it.release() } }
        eq = null
    }

    private inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "Equalizer unavailable", e)
        null
    }

    companion object {
        private const val TAG = "PlayerEq"

        private val _usable = kotlinx.coroutines.flow.MutableStateFlow(true)

        /** False once the phone refused the effect: Settings then says it is not available. */
        val usable: kotlinx.coroutines.flow.StateFlow<Boolean> = _usable

        internal fun markUsable() {
            _usable.value = true
        }

        /** Whether this phone offers an equalizer effect at all. */
        fun isAvailable(): Boolean = try {
            AudioEffect.queryEffects()?.any { it.type == AudioEffect.EFFECT_TYPE_EQUALIZER } == true
        } catch (e: Exception) {
            false
        }
    }
}
