package app.brano.ui.playlists

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.brano.ui.components.BranoTopBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brano.R
import app.brano.domain.SmartPlaylist
import app.brano.ui.icons.AppIcons

@StringRes
internal fun smartTitle(kind: SmartPlaylist): Int = when (kind) {
    SmartPlaylist.FAVORITES -> R.string.smart_favorites
    SmartPlaylist.RECENTLY_ADDED -> R.string.smart_recently_added
    SmartPlaylist.MOST_PLAYED -> R.string.smart_most_played
    SmartPlaylist.RECENTLY_PLAYED -> R.string.smart_recently_played
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onOpenPlaylist: (Long) -> Unit,
    onOpenSmart: (String) -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }

    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is PlaylistsEvent.Imported -> context.getString(R.string.import_summary, event.matched, event.notFound)
                PlaylistsEvent.ImportFailed -> context.getString(R.string.import_failed)
            }
            snackbar.showSnackbar(message)
        }
    }

    val collapse = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(collapse.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            BranoTopBar(
                large = true,
                scrollBehavior = collapse,
                title = { Text(stringResource(R.string.tab_playlists)) },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(AppIcons.MoreVert, contentDescription = stringResource(R.string.cd_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_m3u)) },
                                leadingIcon = { Icon(AppIcons.Import, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    importLauncher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "*/*"))
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(AppIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.fab_new)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(SmartPlaylist.entries, key = { "smart-${it.name}" }) { kind ->
                ListItem(
                    headlineContent = { Text(stringResource(smartTitle(kind))) },
                    leadingContent = { Icon(smartIcon(kind), contentDescription = null) },
                    modifier = Modifier.clickable { onOpenSmart(kind.name) },
                )
            }
            item(key = "mine-header") {
                Text(
                    stringResource(R.string.playlists_mine),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            if (playlists.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.playlists_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(playlists, key = { "pl-${it.id}" }) { p ->
                ListItem(
                    headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(stringResource(R.string.tracks_count, p.trackCount)) },
                    leadingContent = { Icon(AppIcons.Playlist, contentDescription = null) },
                    modifier = Modifier.clickable { onOpenPlaylist(p.id) },
                )
            }
        }
    }

    if (creating) {
        NameDialog(
            title = R.string.new_playlist,
            confirm = R.string.create,
            initial = "",
            onConfirm = {
                viewModel.create(it)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

private fun smartIcon(kind: SmartPlaylist) = when (kind) {
    SmartPlaylist.FAVORITES -> AppIcons.Favorite
    SmartPlaylist.RECENTLY_ADDED -> AppIcons.Add
    SmartPlaylist.MOST_PLAYED -> AppIcons.Play
    SmartPlaylist.RECENTLY_PLAYED -> AppIcons.Playlist
}

/** Text prompt that rejects blank names. */
@Composable
internal fun NameDialog(
    @StringRes title: Int,
    @StringRes confirm: Int,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
