package app.brano.ui.theme

import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DynamicColorsTest {
    private fun palette(vibrant: Palette.Swatch?, dominant: Palette.Swatch?) = mockk<Palette> {
        every { vibrantSwatch } returns vibrant
        every { dominantSwatch } returns dominant
    }

    @Test
    fun `only dominant swatch yields dominant color`() {
        val seed = seedFromPalette(palette(null, Palette.Swatch(Color.RED, 100)))
        assertThat(seed?.toArgb()).isEqualTo(Color.RED)
    }

    @Test
    fun `vibrant wins over dominant`() {
        val seed = seedFromPalette(palette(Palette.Swatch(Color.BLUE, 10), Palette.Swatch(Color.RED, 100)))
        assertThat(seed?.toArgb()).isEqualTo(Color.BLUE)
    }

    @Test
    fun `empty palette yields null`() {
        assertThat(seedFromPalette(palette(null, null))).isNull()
    }
}
