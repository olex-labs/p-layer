package app.brano.ui.settings

import app.brano.data.db.ExcludedFolderDao
import app.brano.data.library.FolderNode
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
    private val excluded = MutableStateFlow<Set<String>>(emptySet())
    private val folders = MutableStateFlow<FolderNode?>(null)
    private val settingsRepo = mockk<SettingsRepository>(relaxed = true)
    private val dao = mockk<ExcludedFolderDao>(relaxed = true)
    private val library = mockk<LibraryRepository>(relaxed = true)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settingsRepo.settings } returns settings
        coEvery { settingsRepo.update(any()) } coAnswers {
            settings.value = firstArg<(AppSettings) -> AppSettings>()(settings.value)
        }
        every { dao.observe() } returns excluded
        coEvery { dao.add(any()) } coAnswers { excluded.value = excluded.value + firstArg<String>() }
        coEvery { dao.remove(any()) } coAnswers { excluded.value = excluded.value - firstArg<String>() }
        every { library.folders } returns folders
        every { library.isLoading } returns MutableStateFlow(false)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SettingsViewModel(settingsRepo, dao, library)

    @Test fun setMinDurationSec_storesMilliseconds() {
        vm().setMinDurationSec(60)
        assertThat(settings.value.minDurationMs).isEqualTo(60_000L)
    }

    @Test fun themeAmoledDynamic_persist() {
        val vm = vm()
        vm.setTheme(ThemeMode.DARK); vm.setAmoled(true); vm.setDynamicColor(false)
        assertThat(settings.value).isEqualTo(AppSettings(theme = ThemeMode.DARK, amoled = true, dynamicColor = false))
    }

    @Test fun addAndRemoveExcluded() {
        val vm = vm()
        val job = kotlinx.coroutines.CoroutineScope(Dispatchers.Main).let { s ->
            s.launch { vm.excluded.collect {} }
        }
        vm.addExcluded("/sdcard/Podcast")
        assertThat(vm.excluded.value).containsExactly("/sdcard/Podcast")
        vm.removeExcluded("/sdcard/Podcast")
        assertThat(vm.excluded.value).isEmpty()
        job.cancel()
    }

    @Test fun rescan_refreshesLibrary() {
        vm().rescan()
        coVerify { library.refresh() }
    }

    @Test fun flattenFolders_treeOrderIndentedAndSkipsExcluded() {
        val b = FolderNode("/sdcard/Music/B", "B", emptyList(), emptyList())
        val a = FolderNode("/sdcard/Music/A", "A", emptyList(), emptyList())
        val old = FolderNode("/sdcard/Music-old", "Music-old", emptyList(), emptyList())
        val music = FolderNode("/sdcard/Music", "Music", listOf(a, b), emptyList())
        val sd = FolderNode("/sdcard", "sdcard", listOf(music, old), emptyList())
        val root = FolderNode("/", "", listOf(sd), emptyList())
        val out = flattenFolders(root, setOf("/sdcard/Music/B"))
        assertThat(out.map { it.path })
            .containsExactly("/sdcard", "/sdcard/Music", "/sdcard/Music/A", "/sdcard/Music-old").inOrder()
        assertThat(out.map { it.depth }).containsExactly(0, 1, 2, 1).inOrder()
    }

    @Test fun flattenFolders_collapsedChainOffersEverySegment() {
        val media = FolderNode("/s/WhatsApp/Media", "WhatsApp/Media", emptyList(), emptyList())
        val root = FolderNode("/", "", listOf(media), emptyList())
        val out = flattenFolders(root, emptySet())
        assertThat(out.map { it.path }).containsExactly("/s", "/s/WhatsApp", "/s/WhatsApp/Media").inOrder()
        assertThat(out.map { it.depth }).containsExactly(0, 1, 2).inOrder()
    }

    @Test fun flattenFolders_collapsedRootNode() {
        val music = FolderNode("/storage/emulated/0/Music", "Music", emptyList(), emptyList())
        val out = flattenFolders(music, emptySet())
        assertThat(out.map { it.path }).containsExactly(
            "/storage", "/storage/emulated", "/storage/emulated/0", "/storage/emulated/0/Music",
        ).inOrder()
    }
}
