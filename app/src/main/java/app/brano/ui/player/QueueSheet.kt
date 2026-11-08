package app.brano.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.brano.R
import app.brano.data.library.Track
import app.brano.ui.components.Artwork
import app.brano.ui.icons.AppIcons
import app.brano.ui.playlists.NameDialog
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Stable key per queue entry, also across moves and removals (duplicates get a counter). */
private data class QueueRow(val key: String, val track: Track)

private fun toRows(queue: List<Track>): List<QueueRow> {
    val seen = HashMap<Long, Int>()
    return queue.map {
        val n = seen.merge(it.id, 1, Int::plus)!!
        QueueRow("${it.id}#$n", it)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<Track>,
    currentIndex: Int,
    onSkipTo: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
    onSaveAsPlaylist: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var saving by rememberSaveable { mutableStateOf(false) }
    var rows by remember { mutableStateOf(toRows(queue)) }
    var dragging by remember { mutableStateOf(false) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    var currentKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(queue) { if (!dragging) rows = toRows(queue) }
    // The highlight follows the entry itself while it is being dragged.
    LaunchedEffect(rows, currentIndex, dragging) { if (!dragging) currentKey = rows.getOrNull(currentIndex)?.key }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val f = rows.indexOfFirst { it.key == from.key }
        val t = rows.indexOfFirst { it.key == to.key }
        if (f >= 0 && t >= 0) rows = rows.toMutableList().apply { add(t, removeAt(f)) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.queue_title), style = MaterialTheme.typography.titleLarge)
                FilledTonalButton(onClick = { saving = true }, enabled = queue.isNotEmpty()) {
                    Icon(AppIcons.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.save_queue_as_playlist), modifier = Modifier.padding(start = 6.dp))
                }
            }
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.queue_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(rows.size, key = { rows[it].key }) { index ->
                    val row = rows[index]
                    ReorderableItem(reorderState, key = row.key) {
                        QueueItem(
                            track = row.track,
                            isCurrent = row.key == currentKey,
                            onClick = { onSkipTo(rows.indexOfFirst { it.key == row.key }) },
                            onRemove = { onRemove(rows.indexOfFirst { it.key == row.key }) },
                            handle = {
                                IconButton(
                                    modifier = Modifier.draggableHandle(
                                        onDragStarted = {
                                            dragging = true
                                            dragStartIndex = rows.indexOfFirst { it.key == row.key }
                                        },
                                        onDragStopped = {
                                            val to = rows.indexOfFirst { it.key == row.key }
                                            val from = dragStartIndex
                                            dragging = false
                                            dragStartIndex = -1
                                            if (from >= 0 && to >= 0 && from != to) onMove(from, to)
                                        },
                                    ),
                                    onClick = {},
                                ) { Icon(AppIcons.DragHandle, contentDescription = stringResource(R.string.cd_drag)) }
                            },
                        )
                    }
                }
            }
        }
    }

    if (saving) {
        NameDialog(
            title = R.string.save_queue_as_playlist,
            confirm = R.string.save,
            initial = "",
            onConfirm = {
                onSaveAsPlaylist(it)
                saving = false
                onDismiss()
            },
            onDismiss = { saving = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueItem(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    handle: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) onRemove()
            value != SwipeToDismissBoxValue.Settled
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp),
                contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(AppIcons.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        val bg = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        Row(
            Modifier.fillMaxWidth().background(bg).clickable(onClick = onClick)
                .padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Artwork(track.albumId, Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist ?: stringResource(R.string.unknown_artist),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            handle()
        }
    }
}
