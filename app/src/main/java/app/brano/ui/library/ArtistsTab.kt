package app.brano.ui.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.brano.R
import app.brano.data.library.Artist
import app.brano.ui.icons.AppIcons

@Composable
fun ArtistsTab(artists: List<Artist>, onOpenArtist: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists, key = { it.id }) { artist ->
            ListItem(
                headlineContent = { Text(artist.name ?: stringResource(R.string.unknown_artist)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.albums_count, artist.albumCount) + " · " +
                            stringResource(R.string.tracks_count, artist.tracks.size),
                    )
                },
                leadingContent = {
                    Box(
                        Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Artist, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onOpenArtist(artist.id) },
            )
        }
    }
}
