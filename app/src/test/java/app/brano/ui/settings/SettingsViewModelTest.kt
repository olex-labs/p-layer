package app.brano.ui.settings

import app.brano.data.db.HiddenTrackDao
import app.brano.data.db.MusicFolderDao
import app.brano.data.db.MusicFolderEntity
import app.brano.data.library.LibraryRepository
import app.brano.data.settings.AppSettings
import app.brano.data.settings.SettingsRepository
import app.brano.data.settings.ThemeMode
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val settings = MutableStateFlow(AppSettings())
    private val musicFolders = MutableStateFlow<List<MusicFolderEntity>>(emptyList())
    private val settingsRepo = mockk<SettingsRepository>(relaxed = true)
    private val dao = mockk<MusicFolderDao>(relaxed = true)
    private val hiddenDao = mockk<HiddenTrackDao>(relaxed = true)
    private val library = mockk<LibraryRepository>(relaxed = true)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settingsRepo.settings } returns settings
        coEvery { settingsRepo.update(any()) } coAnswers {
            settings.value = firstArg<(AppSettings) -> AppSettings>()(settings.value)
        }
        every { dao.observeAll() } returns musicFolders
        coEvery { dao.add(any(), any()) } coAnswers {
            musicFolders.value = musicFolders.value + MusicFolderEntity(firstArg(), secondArg())
        }
        coEvery { dao.remove(any()) } coAnswers { musicFolders.value = musicFolders.value.filter { it.path != firstArg<String>() } }
        every { hiddenDao.observeAll() } returns MutableStateFlow(emptyList())
        every { library.isLoading } returns MutableStateFlow(false)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SettingsViewModel(settingsRepo, dao, hiddenDao, library)

    @Test fun setMinDurationSec_storesMilliseconds() {
        vm().setMinDurationSec(60f)
        assertThat(settings.value.minDurationMs).isEqualTo(60_000L)
    }

    @Test fun setMinDurationSec_roundsSliderValues() {
        // Slider steps land on floats like 29.999998f: must persist 30 s, not 29 s.
        vm().setMinDurationSec(29.999998f)
        assertThat(settings.value.minDurationMs).isEqualTo(30_000L)
        vm().setMinDurationSec(35.2f)
        assertThat(settings.value.minDurationMs).isEqualTo(35_000L)
    }

    @Test fun themeAmoledDynamic_persist() {
        val vm = vm()
        vm.setTheme(ThemeMode.DARK); vm.setAmoled(true); vm.setDynamicColor(false)
        assertThat(settings.value).isEqualTo(AppSettings(theme = ThemeMode.DARK, amoled = true, dynamicColor = false))
    }

    @Test fun addAndRemoveMusicFolder() {
        val vm = vm()
        kotlinx.coroutines.test.runTest(UnconfinedTestDispatcher()) {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.musicFolders.collect {} }
            vm.addMusicFolder("/storage/emulated/0/Music", "content://tree/music")
            assertThat(vm.musicFolders.value.map { it.path }).containsExactly("/storage/emulated/0/Music")
            vm.removeMusicFolder("/storage/emulated/0/Music")
            assertThat(vm.musicFolders.value).isEmpty()
        }
    }

    @Test fun unhide_andUnhideAll() {
        val vm = vm()
        vm.unhide("/m/a.mp3")
        vm.unhideAll()
        coVerify { hiddenDao.unhide("/m/a.mp3") }
        coVerify { hiddenDao.unhideAll() }
    }
}
