package app.brano.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerColorsTest {
    private fun contrast(a: Color, b: Color) = ColorUtils.calculateContrast(a.toArgb(), b.toArgb())

    @Test
    fun `text is readable on deep for many seeds`() {
        listOf(Color.Red, Color.Yellow, Color.Blue, Color.White, Color.Black, Color.Gray, Color(0xFF7CFC00), null)
            .forEach { seed ->
                val p = playerColors(seed)
                assertThat(contrast(p.text, p.deep)).isAtLeast(4.5)
            }
    }

    @Test
    fun `text is readable where the glow meets the header`() {
        // The top bar sits roughly half way between glow and deep in the radial gradient.
        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color(0xFF7CFC00), Color(0xFFFFC107), Color.Blue, null)
            .forEach { seed ->
                val p = playerColors(seed)
                assertThat(contrast(p.text, lerp(p.deep, p.glow, 0.5f))).isAtLeast(4.5)
            }
    }

    @Test
    fun `null seed equals lilac`() {
        assertThat(playerColors(null)).isEqualTo(playerColors(LilacSeed))
    }

    @Test
    fun `grey seed gives a neutral deep`() {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(playerColors(Color.Gray).deep.toArgb(), hsl)
        assertThat(hsl[1]).isAtMost(0.06f)
    }

    @Test
    fun `accent is lighter than glow`() {
        val p = playerColors(Color.Blue)
        assertThat(p.accent.luminance()).isGreaterThan(p.glow.luminance())
    }
}
