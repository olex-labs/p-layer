package app.brano.playback.rg

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReplayGainTagsTest {
    private fun txxx(name: String, value: String) = TextInformationFrame("TXXX", name, ImmutableList.of(value))

    @Test fun txxx_trackAndAlbum() {
        val t = ReplayGainTags.from(
            Metadata(txxx("replaygain_track_gain", "-6.50 dB"), txxx("REPLAYGAIN_ALBUM_GAIN", "-7.1 dB"), txxx("REPLAYGAIN_TRACK_PEAK", "0.98")),
        )
        assertThat(t.trackGainDb).isEqualTo(-6.5f)
        assertThat(t.albumGainDb).isEqualTo(-7.1f)
        assertThat(t.trackPeak).isEqualTo(0.98f)
        assertThat(t.albumPeak).isNull()
    }

    @Test fun vorbis_lowercaseKeys() {
        val t = ReplayGainTags.from(Metadata(VorbisComment("replaygain_album_gain", "+1.20 dB"), VorbisComment("replaygain_album_peak", "1.05")))
        assertThat(t.albumGainDb).isEqualTo(1.2f)
        assertThat(t.albumPeak).isEqualTo(1.05f)
        assertThat(t.trackGainDb).isNull()
    }

    @Test fun parseDb_variants() {
        assertThat(ReplayGainTags.parseDb("-6,50 dB")).isEqualTo(-6.5f)
        assertThat(ReplayGainTags.parseDb("+1.2 dB")).isEqualTo(1.2f)
        assertThat(ReplayGainTags.parseDb("+0 dB")).isEqualTo(0f)
        assertThat(ReplayGainTags.parseDb("loud")).isNull()
        assertThat(ReplayGainTags.parseDb(null)).isNull()
        // A broken tag must never mute the song (NaN gain) or zero it (infinite peak).
        assertThat(ReplayGainTags.parseDb("NaN")).isNull()
        assertThat(ReplayGainTags.parseDb("Infinity")).isNull()
    }

    @Test fun noMetadata_isNone() {
        assertThat(ReplayGainTags.from(null)).isEqualTo(ReplayGainTags.NONE)
    }
}