package app.brano.ui.playlists

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brano.R
import app.brano.data.db.ResolvedItem
import app.brano.data.library.Track
import app.brano.ui.components.TrackActionsSheet
import app.brano.ui.components.TrackRow
import app.brano.ui.icons.AppIcons
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** One list row; [key] stays stable across moves and removals so the swipe and drag state follow the item. */
private data class ListRow(val key: String, val item: ResolvedItem)

private fun toRows(items: List<ResolvedItem>): List<ListRow> {
    val seen = HashMap<String, Int>()
    return items.map {
        val n = seen.merge(it.ref.path, 1, Int::plus)!!
        ListRow("${it.ref.path}#$n", it)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val name by viewModel.title.collectAsStateWithLifecycle()
    val currentId by viewModel.currentId.collectAsStateWithLifecycle()
    val title = viewModel.smart?.let { stringResource(smartTitle(it)) } ?: name
    val editable = viewModel.editable
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var sheetTracks by remember { mutableStateOf<List<Track>>(emptyList()) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) viewModel.export(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaylistDetailEvent.Removed -> {
                    val result = snackbar.showSnackbar(
                        message = context.getString(R.string.track_removed),
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove(event)
                }
                PlaylistDetailEvent.Exported -> snackbar.showSnackbar(context.getString(R.string.export_done))
                PlaylistDetailEvent.ExportFailed -> snackbar.showSnackbar(context.getString(R.string.export_failed))
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (editable) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(AppIcons.MoreVert, contentDescription = stringResource(R.string.cd_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rename)) },
                                    onClick = {
                                        menuOpen = false
                                        renaming = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_m3u)) },
                                    leadingIcon = { Icon(AppIcons.Export, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        exportLauncher.launch("$name.m3u")
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    onClick = {
                                        menuOpen = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            DetailList(
                items = items,
                editable = editable,
                currentId = currentId,
                viewModel = viewModel,
                onMore = { sheetTracks = listOf(it) },
            )
        }
    }

    TrackActionsSheet(
        tracks = sheetTracks,
        onDismiss = { sheetTracks = emptyList() },
        onGoToAlbum = onOpenAlbum,
        onGoToArtist = onOpenArtist,
    )

    if (renaming) {
        NameDialog(
            title = R.string.rename_playlist,
            confirm = R.string.save,
            initial = name,
            onConfirm = {
                viewModel.rename(it)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_playlist_title)) },
            text = { Text(stringResource(R.string.delete_playlist_body, name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                    onBack()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailList(
    items: List<ResolvedItem>,
    editable: Boolean,
    currentId: Long?,
    viewModel: PlaylistDetailViewModel,
    onMore: (Track) -> Unit,
) {
    var rows by remember { mutableStateOf(toRows(items)) }
    var dragging by remember { mutableStateOf(false) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    LaunchedEffect(items) { if (!dragging) rows = toRows(items) }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val f = rows.indexOfFirst { it.key == from.key }
        val t = rows.indexOfFirst { it.key == to.key }
        if (f >= 0 && t >= 0) rows = rows.toMutableList().apply { add(t, removeAt(f)) }
    }

    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.playlist_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item(key = "actions") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { viewModel.play(items.indexOfFirst { it.track != null }.coerceAtLeast(0)) }) {
                    Icon(AppIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.play_all))
                }
                FilledTonalButton(onClick = viewModel::shuffle) {
                    Icon(AppIcons.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.shuffle))
                }
            }
        }
        items(rows.size, key = { rows[it].key }) { index ->
            val row = rows[index]
            ReorderableItem(reorderState, key = row.key, enabled = editable) {
                val handle: @Composable () -> Unit = {
                    if (editable) {
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
                                    if (from >= 0 && to >= 0 && from != to) viewModel.move(from, to)
                                },
                            ),
                            onClick = {},
                        ) {
                            Icon(AppIcons.DragHandle, contentDescription = stringResource(R.string.cd_drag))
                        }
                    }
                }
                ItemRow(
                    item = row.item,
                    editable = editable,
                    isCurrent = row.item.track?.id == currentId && currentId != null,
                    onPlay = { viewModel.play(rows.indexOfFirst { it.key == row.key }) },
                    onMore = onMore,
                    onRemove = { viewModel.remove(rows.indexOfFirst { it.key == row.key }) },
                    handle = handle,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemRow(
    item: ResolvedItem,
    editable: Boolean,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onMore: (Track) -> Unit,
    onRemove: () -> Unit,
    handle: @Composable () -> Unit,
) {
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val track = item.track
            Box(Modifier.weight(1f)) {
                if (track != null) {
                    TrackRow(
                        track = track,
                        isCurrent = isCurrent,
                        selected = false,
                        onClick = onPlay,
                        onLongClick = {},
                        onMore = { onMore(track) },
                    )
                } else {
                    UnavailableRow(item)
                }
            }
            handle()
        }
    }
    if (!editable) {
        content()
        return
    }
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
        content = { content() },
    )
}

@Composable
private fun UnavailableRow(item: ResolvedItem) {
    Row(
        Modifier.fillMaxWidth().alpha(0.5f).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(AppIcons.MusicNote, contentDescription = null)
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.ref.path.substringAfterLast('/').substringBeforeLast('.'),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.track_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
