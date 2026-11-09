package app.brano.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.brano.R
import app.brano.ui.icons.AppIcons

/** Multi-select holder shared by the library, search and playlist screens. */
class SelectionState {
    var selected: Set<Long> by mutableStateOf(emptySet())
        private set

    val active: Boolean get() = selected.isNotEmpty()

    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun clear() {
        selected = emptySet()
    }
}

@Composable
fun rememberSelectionState(): SelectionState = remember { SelectionState() }

/** Top bar shown while a selection is active: "N selezionati", close and a "more" action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onMore: () -> Unit,
    height: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
) {
    TopAppBar(
        expandedHeight = height,
        title = { Text(stringResource(R.string.selected_count, count)) },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(AppIcons.Close, contentDescription = stringResource(R.string.close)) }
        },
        actions = {
            IconButton(onClick = onMore) { Icon(AppIcons.MoreVert, contentDescription = stringResource(R.string.cd_more)) }
        },
    )
}
