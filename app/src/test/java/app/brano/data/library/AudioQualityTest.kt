package app.brano.data.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioQualityTest {
    @Test
    fun `lossy formats show codec and bitrate`() {
        assertThat(qualityLabel("audio/mpeg", 320, "/m/a.mp3")).isEqualTo("MP3 · 320 kbps")
        assertThat(qualityLabel("audio/mp4", 256, "/m/a.m4a")).isEqualTo("AAC · 256 kbps")
        assertThat(qualityLabel("audio/ogg", 160, "/m/a.ogg")).isEqualTo("OGG · 160 kbps")
    }

    @Test
    fun `lossless formats show only the codec`() {
        assertThat(qualityLabel("audio/flac", 900, "/m/a.flac")).isEqualTo("FLAC")
        assertThat(qualityLabel("audio/x-wav", 1411, "/m/a.wav")).isEqualTo("WAV")
    }

    @Test
    fun `unknown bitrate shows only the codec`() {
        assertThat(qualityLabel("audio/mpeg", 0, "/m/a.mp3")).isEqualTo("MP3")
    }

    @Test
    fun `missing mime type falls back to the file extension`() {
        assertThat(qualityLabel(null, 0, "/m/a.FLAC")).isEqualTo("FLAC")
        assertThat(qualityLabel(null, 192, "/m/a.opus")).isEqualTo("OPUS · 192 kbps")
    }

    @Test
    fun `unknown formats give no label`() {
        assertThat(qualityLabel("application/octet-stream", 0, "/m/a.xyz")).isNull()
    }

    @Test
    fun `lossless detection`() {
        assertThat(isLossless("audio/flac", "/m/a.flac")).isTrue()
        assertThat(isLossless("audio/mpeg", "/m/a.mp3")).isFalse()
    }
}
