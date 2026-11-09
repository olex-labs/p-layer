package app.brano.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brano.R
import app.brano.data.library.Track
import app.brano.ui.AppViewModel
import app.brano.ui.icons.AppIcons

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(
    tracks: List<Track>,
    onDismiss: () -> Unit,
    onGoToAlbum: (Long) -> Unit,
    onGoToArtist: (Long) -> Unit,
    showQueueActions: Boolean = true,
    viewModel: AppViewModel = hiltViewModel(),
) {
    if (tracks.isEmpty()) return
    val single = tracks.singleOrNull()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var showPlaylists by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    if (!showPlaylists && !showInfo) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(Modifier.navigationBarsPadding()) {
                if (showQueueActions) {
                    SheetAction(AppIcons.Queue, R.string.action_play_next) {
                        viewModel.playNext(tracks)
                        onDismiss()
                    }
                    SheetAction(AppIcons.Add, R.string.action_add_to_queue) {
                        viewModel.addToQueue(tracks)
                        onDismiss()
                    }
                }
                SheetAction(AppIcons.PlaylistAdd, R.string.action_add_to_playlist) { showPlaylists = true }
                if (single != null && viewModel.isInLibrary(single)) {
                    SheetAction(AppIcons.Album, R.string.action_go_to_album) {
                        onGoToAlbum(single.albumId)
                        onDismiss()
                    }
                    SheetAction(AppIcons.Artist, R.string.action_go_to_artist) {
                        onGoToArtist(single.artistId)
                        onDismiss()
                    }
                }
                if (single != null) {
                    SheetAction(AppIcons.Info, R.string.action_file_info) { showInfo = true }
                }
            }
        }
    }
    if (showPlaylists) {
        AddToPlaylistDialog(
            playlists = playlists,
            onPick = {
                viewModel.addToPlaylist(it, tracks)
                onDismiss()
            },
            onCreate = {
                viewModel.createPlaylist(it, tracks)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
    }
    if (showInfo && single != null) {
        val format = single.path.substringAfterLast('.', "").uppercase()
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(single.title) },
            text = {
                Column {
                    Text("${stringResource(R.string.info_path)}: ${single.path}")
                    Text("${stringResource(R.string.info_duration)}: ${formatDuration(single.durationMs)}")
                    Text("${stringResource(R.string.info_format)}: $format")
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
