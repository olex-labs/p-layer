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

internal fun flattenFolders(root: FolderNode?, excluded: Set<String>): List<FolderChoice> {
    if (root == null) return emptyList()
    val out = ArrayList<FolderChoice>()
    fun walk(n: FolderNode, depth: Int) {
        val real = n.path != "/"
        if (real && n.path !in excluded) out += FolderChoice(n.path, n.name.ifEmpty { n.path }, depth)
        n.children.forEach { walk(it, if (real) depth + 1 else depth) }
    }
    walk(root, 0)
    return out.sortedBy { it.path.lowercase() }
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
