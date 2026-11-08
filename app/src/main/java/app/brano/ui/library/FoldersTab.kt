package app.brano.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.brano.R
import app.brano.data.library.FolderNode
import app.brano.data.library.Track
import app.brano.ui.components.SelectionState
import app.brano.ui.icons.AppIcons

fun FolderNode.find(path: String): FolderNode? =
    if (this.path == path) this else children.firstNotNullOfOrNull { it.find(path) }

private fun FolderNode.trackTotal(): Int = tracks.size + children.sumOf { it.trackTotal() }

/** Contents of one folder: subfolders first (tap to enter), then its tracks (tap plays this folder from there). */
@Composable
fun FoldersTab(
    node: FolderNode?,
    viewModel: LibraryViewModel,
    currentId: Long?,
    selection: SelectionState,
    onOpenFolder: (String) -> Unit,
    onMore: (List<Track>) -> Unit,
) {
    if (node == null || (node.children.isEmpty() && node.tracks.isEmpty())) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.folder_empty)) }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(node.children, key = { it.path }) { child ->
            ListItem(
                headlineContent = { Text(child.name) },
                supportingContent = { Text(stringResource(R.string.tracks_count, child.trackTotal())) },
                leadingContent = { Icon(AppIcons.Folder, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(enabled = !selection.active) { onOpenFolder(child.path) },
            )
        }
        trackItems(
            node.tracks, currentId, selection,
            onPlay = { viewModel.play(node.tracks, it) },
            onMore = { onMore(listOf(it)) },
        )
    }
}
