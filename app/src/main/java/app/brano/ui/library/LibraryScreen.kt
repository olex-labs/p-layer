package app.brano.ui.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brano.R
import app.brano.data.library.Track
import app.brano.ui.components.SelectionState
import app.brano.ui.components.SelectionTopBar
import app.brano.ui.components.TrackActionsSheet
import app.brano.ui.components.rememberSelectionState
import app.brano.ui.icons.AppIcons
import kotlinx.coroutines.launch

/**
 * Top bar (title, or selection bar while selecting), the sheet for track actions and the system-back
 * handling for selection mode. [content] gets a callback that opens the actions sheet for given tracks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectableScaffold(
    title: String,
    onBack: (() -> Unit)?,
    selection: SelectionState,
    allTracks: List<Track>,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    content: @Composable (onMore: (List<Track>) -> Unit) -> Unit,
) {
    var sheetTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var sheetFromSelection by remember { mutableStateOf(false) }
    BackHandler(enabled = selection.active) { selection.clear() }

    Column(Modifier.fillMaxSize()) {
        if (selection.active) {
            SelectionTopBar(
                count = selection.selected.size,
                onClose = selection::clear,
                onMore = {
                    sheetTracks = allTracks.filter { it.id in selection.selected }
                    sheetFromSelection = true
                },
            )
        } else {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(AppIcons.Back, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                },
            )
        }
        Box(Modifier.weight(1f)) {
            content { tracks ->
                sheetTracks = tracks
                sheetFromSelection = false
            }
        }
    }
    TrackActionsSheet(
        tracks = sheetTracks,
        onDismiss = {
            sheetTracks = emptyList()
            if (sheetFromSelection) selection.clear()
        },
        onGoToAlbum = onOpenAlbum,
        onGoToArtist = onOpenArtist,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val loading by viewModel.isLoading.collectAsStateWithLifecycle()
    val currentId by viewModel.currentId.collectAsStateWithLifecycle()
    val selection = rememberSelectionState()
    val pager = rememberPagerState { 4 }
    // "Library visible" for startup measurements (am start -W / Play vitals report time to full display).
    ReportDrawnWhen { tracks.isNotEmpty() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { selection.clear() }
    // The first read may not have started yet: do not flash the empty state during cold start.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800)
        settled = true
    }

    SelectableScaffold(
        title = stringResource(R.string.tab_library),
        onBack = null,
        selection = selection,
        allTracks = tracks,
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
    ) { onMore ->
        when {
            tracks.isEmpty() && (loading || !settled) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            tracks.isEmpty() -> EmptyLibrary(onOpenSettings)
            else -> Column(Modifier.fillMaxSize()) {
                val labels = listOf(R.string.tab_tracks, R.string.tab_albums, R.string.tab_artists, R.string.tab_folders)
                PrimaryTabRow(selectedTabIndex = pager.currentPage) {
                    labels.forEachIndexed { i, label ->
                        Tab(
                            selected = pager.currentPage == i,
                            onClick = { scope.launch { pager.animateScrollToPage(i) } },
                            text = { Text(stringResource(label)) },
                        )
                    }
                }
                HorizontalPager(pager, modifier = Modifier.fillMaxSize(), key = { it }) { page ->
                    when (page) {
                        0 -> TracksTab(viewModel, tracks, sort, currentId, selection, onMore)
                        1 -> AlbumsTab(albums, onOpenAlbum)
                        2 -> ArtistsTab(artists, onOpenArtist)
                        else -> FoldersTab(folders, viewModel, currentId, selection, onOpenFolder, onMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.empty_library_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.empty_library_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings_button)) }
    }
}

@Composable
fun FolderScreen(
    path: String?,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val root by viewModel.folders.collectAsStateWithLifecycle()
    val currentId by viewModel.currentId.collectAsStateWithLifecycle()
    val selection = rememberSelectionState()
    val node = root?.let { r -> if (path == null) r else r.find(path) }
    SelectableScaffold(
        title = node?.name?.ifEmpty { null } ?: stringResource(R.string.tab_folders),
        onBack = onBack,
        selection = selection,
        allTracks = node?.tracks.orEmpty(),
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
    ) { onMore ->
        FoldersTab(node, viewModel, currentId, selection, onOpenFolder, onMore)
    }
}
