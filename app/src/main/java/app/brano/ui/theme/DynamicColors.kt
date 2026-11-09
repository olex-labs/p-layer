package app.brano.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

/** Seed color for the theme from album art: vibrant, else dominant, else null. */
fun seedFromPalette(p: Palette): Color? {
    val rgb = p.vibrantSwatch?.rgb ?: p.dominantSwatch?.rgb ?: return null
    return Color(rgb)
}
