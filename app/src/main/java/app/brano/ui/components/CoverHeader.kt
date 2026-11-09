package app.brano.ui.components

import android.content.Context
import java.util.Locale
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.brano.R
import app.brano.ui.icons.AppIcons
import app.brano.ui.theme.ArtworkSeedLoader
import app.brano.ui.theme.CoverShape
import app.brano.ui.theme.PillShape
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.roundToLong

/** Total length of a list for a header line: "42 min", or "1 h 05 min" from an hour up. */
fun formatTotalDuration(ms: Long): String {
    val minutes = if (ms in 1 until 60_000) 1 else (ms / 60_000.0).roundToLong()
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${"%02d".format(Locale.ROOT, minutes % 60)} min"
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SeedLoaderEntryPoint {
    fun seedLoader(): ArtworkSeedLoader
}

/** The album's theme seed (cached after the first extraction); null while loading or without art. */
@Composable
fun rememberAlbumSeed(albumId: Long?): Color? {
    val context = LocalContext.current
    val loader = remember(context) { seedLoader(context) }
    var seed by remember(albumId) { mutableStateOf(albumId?.let(loader::cachedSeed)) }
    LaunchedEffect(albumId) { if (albumId != null) seed = loader.seedFor(albumId) }
    return seed
}

private fun seedLoader(context: Context): ArtworkSeedLoader =
    EntryPointAccessors.fromApplication(context.applicationContext, SeedLoaderEntryPoint::class.java).seedLoader()

/**
 * Album / playlist header: a large cover lit by its own color (a static radial glow), a heavy
 * [subtitle], a [meta] line and the Play / Shuffle pills side by side. [albumId] null shows a
 * placeholder cover (empty or smart playlists).
 */
@Composable
fun CoverHeader(
    albumId: Long?,
    subtitle: String?,
    meta: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    val seed = rememberAlbumSeed(albumId)
    val target = (seed ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.45f)
    val glow by animateColorAsState(target, tween(600), label = "headerGlow")
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(glow, Color.Transparent),
                        center = Offset(size.width / 2f, 116.dp.toPx()),
                        radius = size.width * 0.75f,
                    ),
                )
            }
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (albumId != null) {
            Artwork(albumId, Modifier.size(200.dp))
        } else {
            Box(
                Modifier.size(200.dp).background(MaterialTheme.colorScheme.surfaceVariant, CoverShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Playlist, contentDescription = null, modifier = Modifier.size(64.dp))
            }
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        Text(
            meta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onPlay, shape = PillShape) {
                Icon(AppIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.play_all), modifier = Modifier.padding(start = 6.dp))
            }
            FilledTonalButton(onClick = onShuffle, shape = PillShape) {
                Icon(AppIcons.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.shuffle), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
