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
import androidx.compose.runtime.LaunchedEffect
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
import android.app.Activity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.brano.ui.theme.PlayerColors
import app.brano.data.library.qualityLabel
import app.brano.ui.components.QualityBadge
import app.brano.ui.theme.playerColors
import app.brano.ui.components.TrackActionsSheet
import app.brano.ui.components.formatDuration
import app.brano.ui.icons.AppIcons

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val position = viewModel.position.collectAsStateWithLifecycle()
    val favorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val seed by viewModel.seedColor.collectAsStateWithLifecycle()
    val spinDisc by viewModel.spinDisc.collectAsStateWithLifecycle()
    val lyricsVisible by viewModel.lyricsVisible.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val onlineLyrics by viewModel.onlineLyrics.collectAsStateWithLifecycle()
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    var sleepOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val track = state.current

    if (track == null) {
        // The player holds nothing (e.g. the queue was cleared): leave. Not while (re)connecting.
        val empty = state.connected && !state.hasItems
        LaunchedEffect(empty) { if (empty) onBack() }
        if (state.connected && state.hasItems) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.close)) }
            }
        }
        return
    }

    val colors = animatedPlayerColors(playerColors(seed))
    LightStatusBarIcons()
    val density = LocalDensity.current
    val closeThresholdPx = with(density) { 120.dp.toPx() }
    PlayerContentTheme(colors) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to colors.glow,
                        0.6f to colors.deep,
                        1f to PlayerNight,
                        center = Offset(size.width / 2f, size.height * 0.25f),
                        radius = size.width * 1.2f,
                    ),
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
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
            IconButton(onClick = { menuOpen = true }) {
                Icon(AppIcons.MoreVert, contentDescription = stringResource(R.string.cd_more))
            }
        }

        // Portrait: the disc takes the free space and everything else sits low, near the thumb.
        // Landscape: disc on the left, details and controls on the right, so the disc stays big.
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val disc: @Composable (Modifier) -> Unit = { modifier ->
            if (lyricsVisible) {
                LyricsView(lyrics, position = { position.value }, onSeek = viewModel::seekTo, onlineEnabled = onlineLyrics, modifier = modifier)
            } else Box(modifier, contentAlignment = Alignment.Center) {
                SwipeableArtwork(
                    albumId = track.albumId,
                    spinning = state.isPlaying && spinDisc,
                    colors = colors,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous,
                )
            }
        }
        val details: @Composable ColumnScope.() -> Unit = {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 22.sp,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.artist ?: stringResource(R.string.unknown_artist),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    track.qualityLabel?.let { QualityBadge(it, Modifier.padding(top = 6.dp)) }
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
                FilledIconButton(
                    onClick = viewModel::togglePlayPause,
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.text, contentColor = colors.deep),
                ) {
                    Icon(
                        if (state.isPlaying) AppIcons.Pause else AppIcons.Play,
                        contentDescription = stringResource(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = viewModel::next, modifier = Modifier.size(56.dp)) {
                    Icon(AppIcons.SkipNext, contentDescription = stringResource(R.string.cd_next), modifier = Modifier.size(36.dp))
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
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
                IconButton(onClick = viewModel::toggleLyrics) {
                    Icon(
                        AppIcons.Lyrics,
                        contentDescription = stringResource(R.string.cd_lyrics),
                        tint = if (lyricsVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { queueOpen = true }) {
                    Icon(AppIcons.Queue, contentDescription = stringResource(R.string.cd_queue))
                }
            }
        }
        if (landscape) {
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                disc(Modifier.weight(1f).fillMaxHeight().padding(12.dp))
                Column(Modifier.weight(1f), content = details)
            }
        } else {
            disc(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp))
            details()
        }
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
    if (menuOpen) {
        // Add to playlist, go to album / artist, file info for the current track.
        TrackActionsSheet(
            tracks = listOf(track),
            onDismiss = { menuOpen = false },
            onGoToAlbum = onOpenAlbum,
            onGoToArtist = onOpenArtist,
            showQueueActions = false,
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
    spinning: Boolean,
    colors: PlayerColors,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VinylDisc(
        albumId = albumId,
        spinning = spinning,
        colors = colors,
        modifier = modifier
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
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = LocalContentColor.current.copy(alpha = 0.2f),
            ),
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

/** The darkest stop of the player gradient, the same in light and dark theme. */
private val PlayerNight = Color(0xFF0B0806)

/** Each player color eases over 600 ms when the album changes, like the app theme. */
@Composable
private fun animatedPlayerColors(target: PlayerColors): PlayerColors {
    val spec = tween<Color>(durationMillis = 600)
    @Composable fun a(c: Color) = animateColorAsState(c, spec, label = "player").value
    return PlayerColors(glow = a(target.glow), deep = a(target.deep), text = a(target.text), accent = a(target.accent))
}

/**
 * The player content sits on its own dark gradient: text and icons use [PlayerColors.text], highlights
 * (shuffle, repeat, timer, slider) use the accent. Sheets and dialogs opened from here keep the app theme.
 */
@Composable
private fun PlayerContentTheme(colors: PlayerColors, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme.copy(
        primary = colors.accent,
        onPrimary = colors.deep,
        tertiary = colors.accent,
        onSurface = colors.text,
        onBackground = colors.text,
        onSurfaceVariant = colors.text.copy(alpha = 0.7f),
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides colors.text, content = content)
    }
}

/** The player is always dark: light system bar icons while it is shown, the previous look restored after. */
@Composable
private fun LightStatusBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        if (window == null) {
            onDispose {}
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousStatus = controller.isAppearanceLightStatusBars
            val previousNav = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            // No light scrim over the gradient with 3-button navigation.
            val previousEnforced = if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced else false
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            onDispose {
                controller.isAppearanceLightStatusBars = previousStatus
                controller.isAppearanceLightNavigationBars = previousNav
                if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = previousEnforced
            }
        }
    }
}
