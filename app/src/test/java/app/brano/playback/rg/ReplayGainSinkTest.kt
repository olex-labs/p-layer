package app.brano.playback.rg

import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class ReplayGainSinkTest {
    private fun pcm(metadata: Metadata?) = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW).setChannelCount(2).setSampleRate(44_100)
        .setMetadata(metadata).build()

    @Test fun eachNewSong_setsItsGainAtTheStreamBoundary() {
        // The sink is configured on the playback thread when the next song's first samples arrive,
        // so the gain changes exactly there instead of half a second later.
        val inner = mockk<AudioSink>(relaxed = true)
        val processor = ReplayGainProcessor()
        val sink = ReplayGainSink(inner, processor) { meta -> ReplayGainPolicy.linearGain(ReplayGainTags.from(meta), album = false) }

        sink.configure(pcm(Metadata(TextInformationFrame("TXXX", "REPLAYGAIN_TRACK_GAIN", ImmutableList.of("-6 dB")))), 0, null)
        assertThat(processor.gain).isWithin(0.001f).of(0.501f)

        sink.configure(pcm(null), 0, null)
        assertThat(processor.gain).isEqualTo(1f)
        verify(exactly = 2) { inner.configure(any(), 0, null) }
    }
}