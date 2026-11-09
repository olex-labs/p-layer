package app.brano.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VinylDiscTest {
    @Test
    fun `quarter period is 90 degrees`() {
        assertThat(discAngleAfter(0f, 5_000)).isWithin(0.01f).of(90f)
    }

    @Test
    fun `resumes from the stopped angle`() {
        assertThat(discAngleAfter(270f, 5_000)).isWithin(0.01f).of(0f)
    }

    @Test
    fun `zero elapsed keeps the angle`() {
        assertThat(discAngleAfter(123f, 0)).isWithin(0.01f).of(123f)
    }
}
