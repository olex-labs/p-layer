package app.brano.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brano.data.db.ExcludedFolderDao
import app.brano.data.library.FolderNode
import app.brano.data.library.LibraryRepository
import app.brano.data.settings.AppSettings
import app.brano.data.settings.SettingsRepository
import app.brano.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A library folder the user can exclude; [depth] drives the indentation in the chooser. */
data class FolderChoice(val path: String, val name: String, val depth: Int)

/**
 * Tree order (parents before children). The library tree collapses track-less single-child chains into one
 * node, so every path segment between a node and its parent gets its own choice.
 */
internal fun flattenFolders(root: FolderNode?, excluded: Set<String>): List<FolderChoice> {
    if (root == null) return emptyList()
    val out = ArrayList<FolderChoice>()
    fun walk(n: FolderNode, parentPath: String, depth: Int) {
        var d = depth
        var childParent = parentPath
        if (n.path != "/") {
            val segs = n.path.removePrefix(parentPath).split('/').filter { it.isNotEmpty() }
            segs.forEachIndexed { i, seg ->
                val path = parentPath.trimEnd('/') + "/" + segs.take(i + 1).joinToString("/")
                if (path !in excluded) out += FolderChoice(path, seg, depth + i)
            }
            d = depth + segs.size
            childParent = n.path
        }
        n.children.forEach { walk(it, childParent, d) }
    }
    walk(root, "/", 0)
    return out
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val excludedDao: ExcludedFolderDao,
    private val library: LibraryRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val excluded: StateFlow<Set<String>> = excludedDao.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val folderChoices: StateFlow<List<FolderChoice>> = combine(library.folders, excludedDao.observe(), ::flattenFolders)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isLoading: StateFlow<Boolean> = library.isLoading

    fun setTheme(m: ThemeMode) = update { it.copy(theme = m) }
    fun setAmoled(b: Boolean) = update { it.copy(amoled = b) }
    fun setDynamicColor(b: Boolean) = update { it.copy(dynamicColor = b) }
    fun setMinDurationSec(s: Int) = update { it.copy(minDurationMs = s * 1_000L) }

    fun addExcluded(path: String) {
        viewModelScope.launch { excludedDao.add(path) }
    }

    fun removeExcluded(path: String) {
        viewModelScope.launch { excludedDao.remove(path) }
    }

    fun rescan() {
        viewModelScope.launch { library.refresh() }
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }
}
