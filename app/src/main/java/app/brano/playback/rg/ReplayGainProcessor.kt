package app.brano.playback.rg

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Multiplies PCM samples by [gain] (ReplayGain), clamping instead of wrapping. Stays active so a
 * gain change never needs the player to reconfigure; at gain 1 the bytes are copied unchanged.
 */
@UnstableApi
class ReplayGainProcessor : BaseAudioProcessor() {
    @Volatile var gain: Float = 1f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val g = gain
        when {
            g == 1f -> out.put(inputBuffer)
            inputAudioFormat.encoding == C.ENCODING_PCM_16BIT -> while (inputBuffer.remaining() >= 2) {
                val v = (inputBuffer.getShort() * g).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                out.putShort(v.toShort())
            }
            else -> while (inputBuffer.remaining() >= 4) {
                out.putFloat((inputBuffer.getFloat() * g).coerceIn(-1f, 1f))
            }
        }
        out.flip()
    }
}