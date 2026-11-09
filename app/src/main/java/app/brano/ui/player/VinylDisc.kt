package app.brano.ui.player

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.brano.ui.components.Artwork
import app.brano.ui.theme.PlayerColors

/** Disc angle after [elapsedMs] of turning from [startDeg], one full turn per [periodMs]. */
fun discAngleAfter(startDeg: Float, elapsedMs: Long, periodMs: Long = 20_000): Float =
    (startDeg + 360f * elapsedMs / periodMs) % 360f

/**
 * Round cover drawn as a vinyl record. While [spinning] it turns slowly; the angle is only read in the
 * graphics layer, so turning never recomposes. Pausing keeps the angle, resuming continues from it.
 */
@Composable
fun VinylDisc(albumId: Long, spinning: Boolean, colors: PlayerColors, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animationsOn = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(spinning, animationsOn) {
        if (!spinning || !animationsOn) return@LaunchedEffect
        val startAngle = angle
        val start = withFrameMillis { it }
        while (true) {
            withFrameMillis { now -> angle = discAngleAfter(startAngle, now - start) }
        }
    }
    Box(
        modifier
            .aspectRatio(1f)
            .border(1.dp, colors.glow.copy(alpha = 0.35f), CircleShape)
            .padding(1.dp)
            .background(colors.deep, CircleShape)
            .padding(10.dp)
            .graphicsLayer { rotationZ = angle },
    ) {
        Artwork(
            albumId,
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    // Centre hole of the record.
                    drawCircle(colors.deep, radius = 6.dp.toPx())
                },
            shape = CircleShape,
        )
    }
}
