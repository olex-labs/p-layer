package app.brano.ui.settings

import androidx.annotation.StringRes
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
    val excluded by viewModel.excluded.collectAsStateWithLifecycle()
    val choices by viewModel.folderChoices.collectAsStateWithLifecycle()
    val loading by viewModel.isLoading.collectAsStateWithLifecycle()
    var choosing by rememberSaveable { mutableStateOf(false) }
    // Local while dragging; persisted on release.
    var sliderSec by remember(settings.minDurationMs) { mutableFloatStateOf(settings.minDurationMs / 1000f) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
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
            SectionTitle(R.string.settings_excluded)
            if (excluded.isEmpty()) {
                ListItem(headlineContent = { Text(stringResource(R.string.settings_excluded_empty)) })
            }
            excluded.sorted().forEach { path ->
                ListItem(
                    headlineContent = { Text(path, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeExcluded(path) }) {
                            Icon(AppIcons.Close, contentDescription = stringResource(R.string.settings_remove_excluded))
                        }
                    },
                )
            }
            ListItem(
                modifier = Modifier.clickable { choosing = true },
                headlineContent = { Text(stringResource(R.string.settings_add_excluded)) },
                leadingContent = { Icon(AppIcons.Add, contentDescription = null) },
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

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.settings_choose_folder)) },
            text = {
                if (choices.isEmpty()) {
                    Text(stringResource(R.string.settings_no_folders))
                } else {
                    LazyColumn {
                        items(choices, key = { it.path }) { c ->
                            Text(
                                c.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addExcluded(c.path)
                                        choosing = false
                                    }
                                    .padding(start = (c.depth * 16).dp, top = 12.dp, bottom = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.cancel)) } },
        )
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
