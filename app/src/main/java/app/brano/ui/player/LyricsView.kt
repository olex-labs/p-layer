package app.brano.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brano.R
import app.brano.lyrics.LyricLine
import app.brano.lyrics.Lyrics
import app.brano.lyrics.LyricsParser

/** What the lyrics view shows; Hidden means the disc is shown instead. */
sealed interface LyricsState {
    data object Hidden : LyricsState
    data object Loading : LyricsState
    data object None : LyricsState
    data class Shown(val lyrics: Lyrics) : LyricsState
}

/**
 * Lyrics in place of the disc. Synced: the sung line is bright, the others dimmed, the list keeps
 * it in view and a tap seeks there. Only a change of line recomposes, not every position tick.
 */
@Composable
fun LyricsView(
    state: LyricsState,
    position: () -> Long,
    onSeek: (Long) -> Unit,
    onlineEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    when (state) {
        LyricsState.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = LocalContentColor.current)
        }
        LyricsState.None, LyricsState.Hidden -> Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.lyrics_none), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                if (!onlineEnabled) {
                    Text(
                        stringResource(R.string.lyrics_enable_online),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }
        is LyricsState.Shown ->
            if (state.lyrics.synced) {
                SyncedLyrics(state.lyrics.lines, position, onSeek, modifier)
            } else {
                PlainLyrics(state.lyrics.lines, modifier)
            }
    }
}

private val lineStyle @Composable get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp)

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, position: () -> Long, onSeek: (Long) -> Unit, modifier: Modifier) {
    val current by remember(lines) { derivedStateOf { LyricsParser.currentLine(lines, position()) } }
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        if (current >= 0) {
            // Keep the sung line about a third from the top.
            listState.animateScrollToItem(current, -listState.layoutInfo.viewportSize.height / 3)
        }
    }
    val color = LocalContentColor.current
    LazyColumn(state = listState, modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 96.dp)) {
        itemsIndexed(lines) { i, line ->
            Text(
                line.text.ifEmpty { "♪" },
                style = lineStyle,
                color = if (i == current) color else color.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = line.timeMs != null) { line.timeMs?.let(onSeek) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PlainLyrics(lines: List<LyricLine>, modifier: Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 24.dp)) {
        lines.forEach { Text(it.text, style = lineStyle.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp)) }
    }
}
