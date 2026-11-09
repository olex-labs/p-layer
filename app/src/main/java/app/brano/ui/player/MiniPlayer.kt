package app.brano.ui.player

import androidx.compose.foundation.clickable
import app.brano.ui.theme.PillShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.brano.R
import app.brano.playback.PlayerUiState
import app.brano.ui.components.Artwork
import app.brano.ui.icons.AppIcons

/** Hidden when nothing is loaded; tap or swipe up opens the full player. */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.current ?: return
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .pointerInput(Unit) {
                var drag = 0f
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (drag < -40f) onOpen() },
                    onVerticalDrag = { _, dy -> drag += dy },
                )
            }
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(28.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Artwork(track.albumId, Modifier.size(40.dp), shape = CircleShape)
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        track.artist ?: stringResource(R.string.unknown_artist),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        if (state.isPlaying) AppIcons.Pause else AppIcons.Play,
                        contentDescription = stringResource(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(AppIcons.SkipNext, contentDescription = stringResource(R.string.cd_next))
                }
            }
            val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 6.dp).height(3.dp).clip(PillShape),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
    }
}
