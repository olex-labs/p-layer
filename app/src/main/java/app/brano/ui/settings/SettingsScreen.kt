package app.brano.ui.settings

import androidx.annotation.StringRes
import android.os.Environment
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ListItemDefaults
import app.brano.data.library.treeDocIdToPath
import app.brano.data.db.HiddenTrackEntity
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.widget.Toast
import android.provider.DocumentsContract
import android.net.Uri
import android.content.Intent
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.brano.ui.components.BranoTopBar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brano.BuildConfig
import app.brano.R
import app.brano.data.settings.ThemeMode
import app.brano.ui.icons.AppIcons
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val folders by viewModel.musicFolders.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    val loading by viewModel.isLoading.collectAsStateWithLifecycle()
    var showHidden by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val unsupported = stringResource(R.string.settings_folder_unsupported)
    // The system folder picker: the grant it gives is kept, so Brano can later read .lrc files there.
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?.let { treeDocIdToPath(it, Environment.getExternalStorageDirectory().path) }
        if (path == null) {
            Toast.makeText(context, unsupported, Toast.LENGTH_LONG).show()
        } else {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Same folder picked again through another URI: drop the old grant, the new one replaces it.
            folders.firstOrNull { it.path == path && it.treeUri != uri.toString() }?.let { old ->
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(Uri.parse(old.treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            viewModel.addMusicFolder(path, uri.toString())
        }
    }
    // Local while dragging; persisted on release.
    var sliderSec by remember(settings.minDurationMs) { mutableFloatStateOf(settings.minDurationMs / 1000f) }

    val collapse = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(collapse.nestedScrollConnection),
        topBar = { BranoTopBar(large = true, title = { Text(stringResource(R.string.settings_title)) }, scrollBehavior = collapse) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle(R.string.settings_theme)
            Column(Modifier.selectableGroup()) {
                ThemeOption(R.string.theme_system, settings.theme == ThemeMode.SYSTEM) { viewModel.setTheme(ThemeMode.SYSTEM) }
                ThemeOption(R.string.theme_light, settings.theme == ThemeMode.LIGHT) { viewModel.setTheme(ThemeMode.LIGHT) }
                ThemeOption(R.string.theme_dark, settings.theme == ThemeMode.DARK) { viewModel.setTheme(ThemeMode.DARK) }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_amoled)) },
                supportingContent = { Text(stringResource(R.string.settings_amoled_hint)) },
                trailingContent = { Switch(checked = settings.amoled, onCheckedChange = viewModel::setAmoled) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dynamic)) },
                trailingContent = { Switch(checked = settings.dynamicColor, onCheckedChange = viewModel::setDynamicColor) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_spin_disc)) },
                supportingContent = { Text(stringResource(R.string.settings_spin_disc_hint)) },
                trailingContent = { Switch(checked = settings.spinDisc, onCheckedChange = viewModel::setSpinDisc) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_online_lyrics)) },
                supportingContent = { Text(stringResource(R.string.settings_online_lyrics_hint)) },
                trailingContent = { Switch(checked = settings.onlineLyrics, onCheckedChange = viewModel::setOnlineLyrics) },
            )
            HorizontalDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.settings_min_duration, sliderSec.roundToInt()), style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = sliderSec,
                    onValueChange = { sliderSec = it },
                    onValueChangeFinished = { viewModel.setMinDurationSec(sliderSec) },
                    valueRange = 0f..120f,
                    steps = 23,
                )
            }
            HorizontalDivider()
            SectionTitle(R.string.settings_music_folders)
            Text(
                stringResource(R.string.settings_music_folders_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            folders.forEach { folder ->
                ListItem(
                    headlineContent = { Text(folder.path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(folder.path, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(AppIcons.Folder, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = {
                            runCatching {
                                context.contentResolver.releasePersistableUriPermission(
                                    Uri.parse(folder.treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                )
                            }
                            viewModel.removeMusicFolder(folder.path)
                        }) {
                            Icon(AppIcons.Close, contentDescription = stringResource(R.string.settings_remove_music_folder))
                        }
                    },
                )
            }
            ListItem(
                modifier = Modifier.clickable { pickFolder.launch(null) },
                headlineContent = { Text(stringResource(R.string.settings_add_music_folder)) },
                leadingContent = { Icon(AppIcons.Add, contentDescription = null) },
            )
            ListItem(
                modifier = Modifier.clickable { showHidden = true },
                headlineContent = { Text(stringResource(R.string.settings_hidden, hidden.size)) },
            )
            HorizontalDivider()
            ListItem(
                modifier = Modifier.clickable(enabled = !loading) { viewModel.rescan() },
                headlineContent = { Text(stringResource(R.string.settings_rescan)) },
                supportingContent = { if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp)) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                trailingContent = { Text(BuildConfig.VERSION_NAME) },
            )
        }
    }

    if (showHidden) {
        HiddenTracksDialog(hidden, onUnhide = viewModel::unhide, onUnhideAll = viewModel::unhideAll) { showHidden = false }
    }
}

@Composable
private fun SectionTitle(@StringRes res: Int) {
    Text(
        stringResource(res),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ThemeOption(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(label))
    }
}

/** Hidden tracks, newest first: file name and folder, each with "Mostra"; "Mostra tutti" at the bottom. */
@Composable
private fun HiddenTracksDialog(
    hidden: List<HiddenTrackEntity>,
    onUnhide: (String) -> Unit,
    onUnhideAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_hidden, hidden.size)) },
        text = {
            if (hidden.isEmpty()) {
                Text(stringResource(R.string.settings_hidden_empty))
            } else {
                LazyColumn {
                    items(hidden, key = { it.path }) { h ->
                        ListItem(
                            headlineContent = { Text(h.path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(h.path.substringBeforeLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = { TextButton(onClick = { onUnhide(h.path) }) { Text(stringResource(R.string.action_unhide)) } },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            if (hidden.isNotEmpty()) TextButton(onClick = onUnhideAll) { Text(stringResource(R.string.action_unhide_all)) }
        },
    )
}
