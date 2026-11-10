package app.brano.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brano.data.db.HiddenTrackDao
import app.brano.data.db.HiddenTrackEntity
import app.brano.data.db.MusicFolderDao
import app.brano.data.db.MusicFolderEntity
import app.brano.data.library.LibraryRepository
import app.brano.data.settings.AppSettings
import app.brano.data.settings.SettingsRepository
import app.brano.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val musicFolderDao: MusicFolderDao,
    private val hiddenDao: HiddenTrackDao,
    private val library: LibraryRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** Folders the library reads from; empty means the whole phone. */
    val musicFolders: StateFlow<List<MusicFolderEntity>> = musicFolderDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hidden: StateFlow<List<HiddenTrackEntity>> = hiddenDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isLoading: StateFlow<Boolean> = library.isLoading

    fun setTheme(m: ThemeMode) = update { it.copy(theme = m) }
    fun setAmoled(b: Boolean) = update { it.copy(amoled = b) }
    fun setSpinDisc(b: Boolean) = update { it.copy(spinDisc = b) }
    fun setOnlineLyrics(b: Boolean) = update { it.copy(onlineLyrics = b) }
    fun setDynamicColor(b: Boolean) = update { it.copy(dynamicColor = b) }
    /** [s] comes from the slider: rounded, since its steps land on values like 29.999998. */
    fun setMinDurationSec(s: Float) = update { it.copy(minDurationMs = s.roundToInt() * 1_000L) }

    /** [path] from treeDocIdToPath; the screen already took the persistable grant on [treeUri]. */
    fun addMusicFolder(path: String, treeUri: String) {
        viewModelScope.launch { musicFolderDao.add(path, treeUri) }
    }

    fun removeMusicFolder(path: String) {
        viewModelScope.launch { musicFolderDao.remove(path) }
    }

    fun unhide(path: String) {
        viewModelScope.launch { hiddenDao.unhide(path) }
    }

    fun unhideAll() {
        viewModelScope.launch { hiddenDao.unhideAll() }
    }

    fun rescan() {
        viewModelScope.launch { library.refresh() }
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }
}
