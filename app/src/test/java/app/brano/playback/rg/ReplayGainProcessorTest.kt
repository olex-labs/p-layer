package app.brano.playback.rg

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test

class ReplayGainProcessorTest {
    private fun processor(encoding: Int, gain: Float) = ReplayGainProcessor().apply {
        configure(AudioFormat(44_100, 2, encoding))
        flush()
        this.gain = gain
    }

    private fun shorts(vararg v: Short): ByteBuffer =
        ByteBuffer.allocateDirect(v.size * 2).order(ByteOrder.nativeOrder()).apply { v.forEach { putShort(it) }; flip() }

    private fun floats(vararg v: Float): ByteBuffer =
        ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).apply { v.forEach { putFloat(it) }; flip() }

    private fun ByteBuffer.readShorts() = List(remaining() / 2) { getShort() }
    private fun ByteBuffer.readFloats() = List(remaining() / 4) { getFloat() }

    @Test fun pcm16_halfGain() {
        val p = processor(C.ENCODING_PCM_16BIT, 0.5f)
        p.queueInput(shorts(1000, -2000))
        assertThat(p.output.readShorts()).containsExactly(500.toShort(), (-1000).toShort()).inOrder()
    }

    @Test fun pcm16_clamps() {
        val p = processor(C.ENCODING_PCM_16BIT, 2f)
        p.queueInput(shorts(30000, -30000))
        assertThat(p.output.readShorts()).containsExactly(32767.toShort(), (-32768).toShort()).inOrder()
    }

    @Test fun float_clamps() {
        val p = processor(C.ENCODING_PCM_FLOAT, 2f)
        p.queueInput(floats(0.9f, -0.9f, 0.25f))
        assertThat(p.output.readFloats()).containsExactly(1f, -1f, 0.5f).inOrder()
    }

    @Test fun unityGain_isIdentity() {
        val p = processor(C.ENCODING_PCM_16BIT, 1f)
        p.queueInput(shorts(123, -456, 32767))
        assertThat(p.output.readShorts()).containsExactly(123.toShort(), (-456).toShort(), 32767.toShort()).inOrder()
    }
}