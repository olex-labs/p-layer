package app.brano.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/** The always-dark "Vinile" player palette: a radial [glow] fading into [deep], with [text] and [accent] on top. */
data class PlayerColors(val glow: Color, val deep: Color, val text: Color, val accent: Color)

/** Player colors from the album seed (lilac when null). Grey covers give a neutral player. */
fun playerColors(seed: Color?): PlayerColors {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL((seed ?: LilacSeed).toArgb(), hsl)
    val hue = hsl[0]
    val sat = if (hsl[1] < 0.15f) 0.15f else hsl[1].coerceIn(0.35f, 0.85f)
    fun tone(lightness: Float) = Color(ColorUtils.HSLToColor(floatArrayOf(hue, sat, lightness)))
    val text = Color(0xFFFFF7ED)
    val deep = tone(0.12f)
    // Bright hues (yellow, green, cyan) are dimmed until the header text, which sits about half way
    // between glow and deep, keeps a 4.5:1 contrast.
    var glowLightness = 0.50f
    var glow = tone(glowLightness)
    while (glowLightness > 0.20f &&
        ColorUtils.calculateContrast(text.toArgb(), lerp(deep, glow, 0.5f).toArgb()) < 4.6
    ) {
        glowLightness -= 0.02f
        glow = tone(glowLightness)
    }
    return PlayerColors(glow = glow, deep = deep, text = text, accent = tone(0.72f))
}
