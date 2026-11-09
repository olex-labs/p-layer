package app.brano.ui.library

import androidx.compose.foundation.layout.Arrangement
import app.brano.ui.components.formatTotalDuration
import app.brano.ui.components.CoverHeader
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brano.R
import app.brano.data.library.Track
import app.brano.ui.components.Artwork
import app.brano.ui.components.rememberSelectionState
import app.brano.ui.icons.AppIcons

@Composable
fun AlbumScreen(
    albumId: Long,
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val currentId by viewModel.currentId.collectAsStateWithLifecycle()
    val album = albums.firstOrNull { it.id == albumId }
    val selection = rememberSelectionState()
    SelectableScaffold(
        title = album?.title ?: stringResource(R.string.unknown_album),
        onBack = onBack,
        selection = selection,
        allTracks = album?.tracks.orEmpty(),
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
    ) { onMore ->
        if (album == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.not_found)) }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "header") {
                    CoverHeader(
                        albumId = album.id,
                        subtitle = album.artist ?: stringResource(R.string.unknown_artist),
                        meta = listOfNotNull(
                            album.year.takeIf { it > 0 }?.toString(),
                            stringResource(R.string.tracks_count, album.trackCount),
                            formatTotalDuration(album.tracks.sumOf { it.durationMs }),
                        ).joinToString(" · "),
                        onPlay = { viewModel.play(album.tracks, 0) },
                        onShuffle = { viewModel.shuffle(album.tracks) },
                    )
                }
                trackItems(
                    album.tracks, currentId, selection,
                    onPlay = { viewModel.play(album.tracks, it) },
                    onMore = { onMore(listOf(it)) },
                )
            }
        }
    }
}

@Composable
internal fun PlayShuffleRow(tracks: List<Track>, viewModel: LibraryViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { viewModel.play(tracks, 0) }) {
            Icon(AppIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.play_all))
        }
        FilledTonalButton(onClick = { viewModel.shuffle(tracks) }) {
            Icon(AppIcons.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.shuffle))
        }
    }
}
