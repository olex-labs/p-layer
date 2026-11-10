package app.brano.playback.eq

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EqCurveTest {
    private fun levels(p: EqPreset, centersHz: IntArray, range: Int = 1500) =
        EqCurve.bandLevels(p, centersHz.map { it * 1000 }.toIntArray(), (-range).toShort(), range.toShort()).toList()

    @Test fun fiveReferenceBands_giveTheTableValues() {
        assertThat(levels(EqPreset.ROCK, intArrayOf(60, 230, 910, 3600, 14000)))
            .containsExactly(400.toShort(), 200.toShort(), (-100).toShort(), 200.toShort(), 400.toShort()).inOrder()
    }

    @Test fun threeBands_interpolate() {
        val l = levels(EqPreset.BASS, intArrayOf(120, 1500, 9000))
        assertThat(l[0].toInt()).isIn(com.google.common.collect.Range.open(400, 600))
        assertThat(l[1].toInt()).isEqualTo(0)
        assertThat(l[2].toInt()).isIn(com.google.common.collect.Range.open(0, 100))
    }

    @Test fun outsideRange_holdsEdges() {
        assertThat(levels(EqPreset.BASS, intArrayOf(20, 20000))).containsExactly(600.toShort(), 100.toShort()).inOrder()
    }

    @Test fun clampedToDeviceRange() {
        assertThat(levels(EqPreset.BASS, intArrayOf(60), range = 300)).containsExactly(300.toShort())
    }

    @Test fun off_isZero() {
        assertThat(levels(EqPreset.OFF, intArrayOf(60, 14000)).all { it == 0.toShort() }).isTrue()
    }

    @Test fun fromName_unknownIsOff() {
        assertThat(EqPreset.fromName("X")).isEqualTo(EqPreset.OFF)
        assertThat(EqPreset.fromName(null)).isEqualTo(EqPreset.OFF)
        assertThat(EqPreset.fromName("ROCK")).isEqualTo(EqPreset.ROCK)
    }
}