package app.brano.ui.theme

import androidx.compose.ui.graphics.Color
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
    return PlayerColors(
        glow = tone(0.50f),
        deep = tone(0.12f),
        text = Color(0xFFFFF7ED),
        accent = tone(0.72f),
    )
}
