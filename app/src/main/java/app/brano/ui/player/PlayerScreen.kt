package app.brano.ui.player

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import app.brano.R
import app.brano.playback.SleepTimerState
import app.brano.ui.components.Artwork
import app.brano.ui.components.formatDuration
import app.brano.ui.icons.AppIcons

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val position = viewModel.position.collectAsStateWithLifecycle()
    val favorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    var sleepOpen by rememberSaveable { mutableStateOf(false) }
    val track = state.current

    // Nothing loaded (e.g. the queue was cleared): the player has nothing to show.
    if (track == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.close)) }
        }
        return
    }

    val density = LocalDensity.current
    val closeThresholdPx = with(density) { 120.dp.toPx() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                var startedLow = false
                detectVerticalDragGestures(
                    onDragStart = {
                        total = 0f
                        startedLow = it.y > size.height / 2f
                    },
                    onDragEnd = {
                        if (total > closeThresholdPx) onBack()
                        else if (total < -closeThresholdPx / 2f && startedLow) queueOpen = true
                    },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            }
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(AppIcons.ChevronDown, contentDescription = stringResource(R.string.cd_close_player))
            }
            Text(
                stringResource(R.string.player_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            IconButton(onClick = { queueOpen = true }) {
                Icon(AppIcons.Queue, contentDescription = stringResource(R.string.cd_queue))
            }
        }

        SwipeableArtwork(
            albumId = track.albumId,
            onNext = viewModel::next,
            onPrevious = viewModel::previous,
            modifier = Modifier.padding(top = 8.dp).weight(1f, fill = false),
        )

        Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    track.artist ?: stringResource(R.string.unknown_artist),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = viewModel::toggleFavorite) {
                Icon(
                    if (favorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                    contentDescription = stringResource(if (favorite) R.string.cd_favorite_remove else R.string.cd_favorite_add),
                    tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SeekBar(
            position = { position.value },
            durationMs = state.durationMs,
            onSeek = viewModel::seekTo,
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::previous, modifier = Modifier.size(56.dp)) {
                Icon(AppIcons.SkipPrevious, contentDescription = stringResource(R.string.cd_previous), modifier = Modifier.size(36.dp))
            }
            IconButton(
                onClick = viewModel::togglePlayPause,
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    if (state.isPlaying) AppIcons.Pause else AppIcons.Play,
                    contentDescription = stringResource(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = viewModel::next, modifier = Modifier.size(56.dp)) {
                Icon(AppIcons.SkipNext, contentDescription = stringResource(R.string.cd_next), modifier = Modifier.size(36.dp))
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.setShuffle(!state.shuffle) }) {
                Icon(
                    if (state.shuffle) AppIcons.ShuffleOn else AppIcons.Shuffle,
                    contentDescription = stringResource(R.string.cd_shuffle),
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::cycleRepeat) {
                val (icon, description) = when (state.repeatMode) {
                    Player.REPEAT_MODE_ALL -> AppIcons.RepeatOn to R.string.cd_repeat_all
                    Player.REPEAT_MODE_ONE -> AppIcons.RepeatOne to R.string.cd_repeat_one
                    else -> AppIcons.Repeat to R.string.cd_repeat_off
                }
                Icon(
                    icon,
                    contentDescription = stringResource(description),
                    tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
            SleepTimerButton(state.sleep) { sleepOpen = true }
        }
    }

    if (queueOpen) {
        QueueSheet(
            queue = state.queue,
            currentIndex = state.index,
            onSkipTo = viewModel::skipToQueueItem,
            onMove = viewModel::moveInQueue,
            onRemove = viewModel::removeFromQueue,
            onSaveAsPlaylist = viewModel::saveQueueAsPlaylist,
            onDismiss = { queueOpen = false },
        )
    }
    if (sleepOpen) {
        SleepTimerDialog(
            sleep = state.sleep,
            onStartMinutes = { viewModel.startSleepTimer(it * 60_000L) },
            onEndOfTrack = viewModel::sleepAtEndOfTrack,
            onCancelTimer = viewModel::cancelSleepTimer,
            onDismiss = { sleepOpen = false },
        )
    }
}

/** Large cover; a horizontal drag beyond 25% of its width skips to the next (left) or previous (right) track. */
@Composable
private fun SwipeableArtwork(
    albumId: Long,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Artwork(
        albumId,
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        when {
                            total < -size.width * 0.25f -> onNext()
                            total > size.width * 0.25f -> onPrevious()
                        }
                    },
                    onHorizontalDrag = { _, dx -> total += dx },
                )
            },
    )
}

/** Follows the player position; while dragging it shows the local value and seeks once on release. */
@Composable
private fun SeekBar(position: () -> Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val enabled = durationMs > 0
    val value = (dragValue ?: position().toFloat()).coerceIn(0f, if (enabled) durationMs.toFloat() else 1f)
    val description = stringResource(R.string.cd_seekbar)
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = value,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let { onSeek(it.toLong()) }
                dragValue = null
            },
            valueRange = 0f..(if (enabled) durationMs.toFloat() else 1f),
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(value.toLong()), style = MaterialTheme.typography.bodySmall)
            Text(formatDuration(durationMs), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Shows mm:ss while counting, a distinct tinted label for end-of-track, nothing extra when off. */
@Composable
private fun SleepTimerButton(sleep: SleepTimerState, onClick: () -> Unit) {
    val active = sleep != SleepTimerState.Off
    TextButton(onClick = onClick) {
        Icon(
            AppIcons.SleepTimer,
            contentDescription = stringResource(R.string.cd_sleep_timer),
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (sleep) {
            is SleepTimerState.Counting -> Text(
                formatDuration(sleep.remainingMs),
                modifier = Modifier.padding(start = 6.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            SleepTimerState.EndOfTrack -> Text(
                stringResource(R.string.sleep_end_of_track_short),
                modifier = Modifier.padding(start = 6.dp),
                color = MaterialTheme.colorScheme.tertiary,
            )
            SleepTimerState.Off -> Unit
        }
    }
}
