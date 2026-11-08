package app.brano.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.brano.R
import app.brano.data.db.PlaylistSummary
import app.brano.ui.icons.AppIcons

@Composable
fun AddToPlaylistDialog(
    playlists: List<PlaylistSummary>,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    if (creating) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.new_playlist)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.create))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.action_add_to_playlist)) },
            text = {
                LazyColumn {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(AppIcons.Add, contentDescription = null)
                            Text(stringResource(R.string.new_playlist), modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                    items(playlists, key = { it.id }) { p ->
                        Text(
                            p.name,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(p.id) }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
