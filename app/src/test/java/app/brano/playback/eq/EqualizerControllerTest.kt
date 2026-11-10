package app.brano.playback.eq

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EqualizerControllerTest {
    @After fun reset() { EqualizerController.markUsable() }

    @Test fun constructorThrows_marksTheEqualizerUnusable_andDoesNotCrash() {
        val c = EqualizerController(create = { throw UnsupportedOperationException("effect busy") })
        c.attach(42)
        c.apply(EqPreset.ROCK)
        assertThat(EqualizerController.usable.value).isFalse()
    }
}