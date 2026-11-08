package app.brano.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import app.brano.ui.components.rememberSelectionState

@Composable
fun ArtistScreen(
    artistId: Long,
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val currentId by viewModel.currentId.collectAsStateWithLifecycle()
    val artist = artists.firstOrNull { it.id == artistId }
    val selection = rememberSelectionState()
    SelectableScaffold(
        title = artist?.name ?: stringResource(R.string.unknown_artist),
        onBack = onBack,
        selection = selection,
        allTracks = artist?.tracks.orEmpty(),
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
    ) { onMore ->
        if (artist == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.not_found)) }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "header") {
                    Box(Modifier.padding(16.dp)) { PlayShuffleRow(artist.tracks, viewModel) }
                }
                trackItems(
                    artist.tracks, currentId, selection,
                    onPlay = { viewModel.play(artist.tracks, it) },
                    onMore = { onMore(listOf(it)) },
                )
            }
        }
    }
}
