package app.brano.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brano's own color: used when no album is playing, or when album colors are off without system colors. */
val LilacSeed = Color(0xFF7C5CFF)

/**
 * Album art: a soft square with equal rounded corners, Apple Music style. Proportional (10% of the
 * side), so small list covers and big header covers look alike. Equal corners also clip faster
 * than mixed ones.
 */
val CoverShape: Shape = RoundedCornerShape(percent = 10)

val PillShape: Shape = RoundedCornerShape(50)

val BranoShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

val BranoTypography: Typography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Black),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 40.sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Black),
    )
}

/**
 * The seed the color scheme is built from; null means "use the system dynamic scheme".
 * Album colors on: the album seed, or lilac when nothing plays. Off: system colors where available, else lilac.
 */
fun resolveSeed(dynamicColor: Boolean, albumSeed: Color?, systemDynamicAvailable: Boolean): Color? = when {
    dynamicColor -> albumSeed ?: LilacSeed
    systemDynamicAvailable -> null
    else -> LilacSeed
}
