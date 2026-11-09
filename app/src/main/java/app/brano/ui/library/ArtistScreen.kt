package app.brano.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brano.R
import app.brano.data.library.Album
import app.brano.data.library.LibraryGrouping
import app.brano.ui.components.Artwork
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
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val currentId by viewModel.currentId.collectAsStateWithLifecycle()
    val artist = artists.firstOrNull { it.id == artistId }
    val artistAlbums = remember(artist, albums) { artist?.let { LibraryGrouping.albumsOf(it, albums) }.orEmpty() }
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
                if (artistAlbums.isNotEmpty()) {
                    item(key = "albums_title") { SectionHeader(stringResource(R.string.tab_albums)) }
                    item(key = "albums") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(artistAlbums, key = { it.id }) { album -> AlbumCard(album) { onOpenAlbum(album.id) } }
                        }
                    }
                    item(key = "tracks_title") { SectionHeader(stringResource(R.string.tab_tracks)) }
                }
                item(key = "header") {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { PlayShuffleRow(artist.tracks, viewModel) }
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

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Column(Modifier.width(128.dp).clickable(onClick = onClick)) {
        Artwork(album.id, Modifier.size(128.dp))
        Text(
            album.title ?: stringResource(R.string.unknown_album),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (album.year > 0) {
            Text(
                album.year.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
