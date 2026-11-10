package app.brano.playback.rg

import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/**
 * Sets the ReplayGain multiplier when the renderer configures the sink for a new song. That
 * happens on the playback thread, in order with the samples, so the gain changes at the boundary
 * between songs instead of when the UI-side track change arrives (half a second later, gapless).
 */
@UnstableApi
class ReplayGainSink(
    sink: AudioSink,
    private val processor: ReplayGainProcessor,
    private val gainFor: (Metadata?) -> Float,
) : ForwardingAudioSink(sink) {
    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        processor.gain = gainFor(inputFormat.metadata)
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }
}