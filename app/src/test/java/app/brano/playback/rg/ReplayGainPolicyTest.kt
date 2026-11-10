package app.brano.playback.rg

import com.google.common.truth.Truth.assertThat
import kotlin.math.pow
import org.junit.Test

class ReplayGainPolicyTest {
    @Test fun useAlbum_onlyForAWholeAlbumInOrder() {
        assertThat(ReplayGainPolicy.useAlbum(listOf(5L, 5L, 5L), shuffle = false)).isTrue()
        assertThat(ReplayGainPolicy.useAlbum(listOf(5L, 5L, 5L), shuffle = true)).isFalse()
        assertThat(ReplayGainPolicy.useAlbum(listOf(5L, 6L), shuffle = false)).isFalse()
        assertThat(ReplayGainPolicy.useAlbum(listOf(5L), shuffle = false)).isFalse()
    }

    @Test fun linearGain_minus6dB_isAbout0_501() {
        val g = ReplayGainPolicy.linearGain(ReplayGainTags(-6f, null, null, null), album = false)
        assertThat(g).isWithin(0.001f).of(0.501f)
    }

    @Test fun missingPreferred_usesOther() {
        assertThat(ReplayGainPolicy.linearGain(ReplayGainTags(null, -6f, null, null), album = false)).isWithin(0.001f).of(0.501f)
        assertThat(ReplayGainPolicy.linearGain(ReplayGainTags(-6f, null, null, null), album = true)).isWithin(0.001f).of(0.501f)
    }

    @Test fun albumPreferred_whenAlbum() {
        assertThat(ReplayGainPolicy.linearGain(ReplayGainTags(0f, -6f, null, null), album = true)).isWithin(0.001f).of(0.501f)
    }

    @Test fun peakLimitsBoost() {
        assertThat(ReplayGainPolicy.linearGain(ReplayGainTags(6f, null, 0.9f, null), album = false)).isWithin(0.0001f).of(1 / 0.9f)
    }

    @Test fun clampedTo12dB() {
        assertThat(ReplayGainPolicy.linearGain(ReplayGainTags(20f, null, null, null), album = false))
            .isWithin(0.001f).of(10.0.pow(12 / 20.0).toFloat())
        assertThat(ReplayGainPolicy.linearGain(ReplayGainTags(-40f, null, null, null), album = false))
            .isWithin(0.0001f).of(10.0.pow(-24 / 20.0).toFloat())
    }

    @Test fun noTags_isOne() {
        assertThat(ReplayGainPolicy.linearGain(ReplayGainTags.NONE, album = false)).isEqualTo(1f)
    }
}