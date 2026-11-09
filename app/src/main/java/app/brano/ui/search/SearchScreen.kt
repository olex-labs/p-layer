package app.brano.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brano.R
import app.brano.ui.components.Artwork
import app.brano.ui.components.rememberSelectionState
import app.brano.ui.icons.AppIcons
import app.brano.ui.library.SelectableScaffold
import app.brano.ui.library.trackItems

@Composable
fun SearchScreen(
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val currentId by viewModel.currentId.collectAsStateWithLifecycle()
    val selection = rememberSelectionState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    SelectableScaffold(
        title = stringResource(R.string.tab_search),
        onBack = null,
        selection = selection,
        allTracks = results.tracks,
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
    ) { onMore ->
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQuery("") }) {
                            Icon(AppIcons.Close, contentDescription = stringResource(R.string.cd_clear_search))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
            )
            val none = results.tracks.isEmpty() && results.albums.isEmpty() && results.artists.isEmpty()
            if (none && query.isNotBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.search_no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (results.tracks.isNotEmpty()) {
                        item(key = "h-tracks") { SectionHeader(R.string.tab_tracks) }
                        trackItems(
                            results.tracks, currentId, selection,
                            onPlay = { viewModel.play(results.tracks, it) },
                            onMore = { onMore(listOf(it)) },
                        )
                    }
                    if (results.albums.isNotEmpty()) {
                        item(key = "h-albums") { SectionHeader(R.string.tab_albums) }
                        items(results.albums, key = { "a${it.id}" }) { album ->
                            ListItem(
                                headlineContent = { Text(album.title ?: stringResource(R.string.unknown_album)) },
                                supportingContent = { Text(album.artist ?: stringResource(R.string.unknown_artist)) },
                                leadingContent = { Artwork(album.id, Modifier.size(48.dp)) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { onOpenAlbum(album.id) },
                            )
                        }
                    }
                    if (results.artists.isNotEmpty()) {
                        item(key = "h-artists") { SectionHeader(R.string.tab_artists) }
                        items(results.artists, key = { "r${it.id}" }) { artist ->
                            ListItem(
                                headlineContent = { Text(artist.name ?: stringResource(R.string.unknown_artist)) },
                                leadingContent = { Icon(AppIcons.Artist, contentDescription = null) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { onOpenArtist(artist.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: Int) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
