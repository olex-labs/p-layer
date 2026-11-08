package app.brano.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.brano.R
import app.brano.data.library.Track
import app.brano.data.settings.TrackSort
import app.brano.ui.components.FastScroller
import app.brano.ui.components.SelectionState
import app.brano.ui.components.TrackRow
import app.brano.ui.components.indexLetter
import app.brano.ui.icons.AppIcons

/** Rows for [tracks]: tap plays from that index (or toggles while selecting), long-press selects. */
fun LazyListScope.trackItems(
    tracks: List<Track>,
    currentId: Long?,
    selection: SelectionState,
    onPlay: (Int) -> Unit,
    onMore: (Track) -> Unit,
) {
    items(tracks.size, key = { tracks[it].id }) { i ->
        val track = tracks[i]
        TrackRow(
            track = track,
            isCurrent = track.id == currentId,
            selected = track.id in selection.selected,
            onClick = { if (selection.active) selection.toggle(track.id) else onPlay(i) },
            onLongClick = { selection.toggle(track.id) },
            onMore = { onMore(track) },
        )
    }
}

@Composable
fun TracksTab(
    viewModel: LibraryViewModel,
    tracks: List<Track>,
    sort: TrackSort,
    currentId: Long?,
    selection: SelectionState,
    onMore: (List<Track>) -> Unit,
) {
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FilledTonalButton(onClick = viewModel::shuffleAll) {
                        Icon(AppIcons.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.shuffle), modifier = Modifier.padding(start = 8.dp))
                    }
                    SortMenu(sort, viewModel::setSort)
                }
            }
            trackItems(
                tracks, currentId, selection,
                onPlay = viewModel::playAll,
                onMore = { onMore(listOf(it)) },
            )
        }
        val letterSource: ((Track) -> String?)? = when (sort) {
            TrackSort.TITLE -> { t -> t.title }
            TrackSort.ARTIST -> { t -> t.artist }
            TrackSort.ALBUM -> { t -> t.album }
            else -> null
        }
        if (letterSource != null) {
            FastScroller(
                state = listState,
                itemCount = tracks.size,
                indexOffset = 1,
                letterAt = { i -> indexLetter(tracks.getOrNull(i)?.let(letterSource)) },
            )
        }
    }
}

@Composable
private fun SortMenu(current: TrackSort, onPick: (TrackSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(AppIcons.Sort, contentDescription = stringResource(R.string.cd_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TrackSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(sortLabel(s)),
                            color = if (s == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        open = false
                        onPick(s)
                    },
                )
            }
        }
    }
}

private fun sortLabel(s: TrackSort): Int = when (s) {
    TrackSort.TITLE -> R.string.sort_title
    TrackSort.ARTIST -> R.string.sort_artist
    TrackSort.ALBUM -> R.string.sort_album
    TrackSort.DATE_ADDED -> R.string.sort_date_added
    TrackSort.DURATION -> R.string.sort_duration
}
