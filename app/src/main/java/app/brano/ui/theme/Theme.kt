package app.brano.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import app.brano.data.settings.AppSettings
import app.brano.data.settings.ThemeMode

@Composable
fun BranoTheme(settings: AppSettings, seedColor: Color?, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    var scheme: ColorScheme = when {
        settings.dynamicColor && seedColor != null -> seedColorScheme(seedColor, dark)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    if (settings.amoled && dark) {
        scheme = scheme.copy(background = Color.Black, surface = Color.Black)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun tone(hue: Float, saturation: Float, lightness: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf((hue + 360f) % 360f, saturation.coerceIn(0f, 1f), lightness)))

/** Small in-house tonal scheme: hue from the seed, lightness/saturation shifted per role. */
internal fun seedColorScheme(seed: Color, dark: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    val hue = hsl[0]
    val sat = hsl[1].coerceIn(0.3f, 0.85f)
    val neutral = 0.08f
    return if (dark) {
        darkColorScheme(
            primary = tone(hue, sat, 0.80f),
            onPrimary = tone(hue, sat, 0.20f),
            primaryContainer = tone(hue, sat, 0.30f),
            onPrimaryContainer = tone(hue, sat, 0.90f),
            secondary = tone(hue + 20, sat * 0.5f, 0.78f),
            onSecondary = tone(hue + 20, sat * 0.5f, 0.20f),
            secondaryContainer = tone(hue + 20, sat * 0.5f, 0.30f),
            onSecondaryContainer = tone(hue + 20, sat * 0.5f, 0.90f),
            tertiary = tone(hue + 60, sat * 0.6f, 0.80f),
            onTertiary = tone(hue + 60, sat * 0.6f, 0.20f),
            tertiaryContainer = tone(hue + 60, sat * 0.6f, 0.30f),
            onTertiaryContainer = tone(hue + 60, sat * 0.6f, 0.90f),
            background = tone(hue, neutral, 0.08f),
            onBackground = tone(hue, neutral, 0.90f),
            surface = tone(hue, neutral, 0.08f),
            onSurface = tone(hue, neutral, 0.90f),
            surfaceVariant = tone(hue, neutral * 1.5f, 0.25f),
            onSurfaceVariant = tone(hue, neutral * 1.5f, 0.78f),
        )
    } else {
        lightColorScheme(
            primary = tone(hue, sat, 0.40f),
            onPrimary = Color.White,
            primaryContainer = tone(hue, sat, 0.90f),
            onPrimaryContainer = tone(hue, sat, 0.10f),
            secondary = tone(hue + 20, sat * 0.5f, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = tone(hue + 20, sat * 0.5f, 0.90f),
            onSecondaryContainer = tone(hue + 20, sat * 0.5f, 0.10f),
            tertiary = tone(hue + 60, sat * 0.6f, 0.40f),
            onTertiary = Color.White,
            tertiaryContainer = tone(hue + 60, sat * 0.6f, 0.90f),
            onTertiaryContainer = tone(hue + 60, sat * 0.6f, 0.10f),
            background = tone(hue, neutral, 0.98f),
            onBackground = tone(hue, neutral, 0.10f),
            surface = tone(hue, neutral, 0.98f),
            onSurface = tone(hue, neutral, 0.10f),
            surfaceVariant = tone(hue, neutral * 1.5f, 0.90f),
            onSurfaceVariant = tone(hue, neutral * 1.5f, 0.30f),
        )
    }
}
