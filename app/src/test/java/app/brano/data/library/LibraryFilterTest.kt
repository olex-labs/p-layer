package app.brano.data.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryFilterTest {
    private val none = emptySet<String>()

    @Test fun filter_dropsShortTracks() {
        val out = LibraryFilter.apply(listOf(t(dur = 29_999), t(id = 2, dur = 30_000)), 30_000, none, none)
        assertThat(out.map { it.id }).containsExactly(2L)
    }

    @Test fun emptyMusicFolders_keepEverything() {
        val out = LibraryFilter.apply(listOf(t(1, folder = "/sdcard/A"), t(2, folder = "/sdcard/B")), 0, none, none)
        assertThat(out.map { it.id }).containsExactly(1L, 2L)
    }

    @Test fun onlyTracksInsideChosenFolders_subfoldersIncluded() {
        val out = LibraryFilter.apply(
            listOf(t(1, folder = "/sdcard/Music"), t(2, folder = "/sdcard/Music/Album"), t(3, folder = "/sdcard/WhatsApp")),
            0, setOf("/sdcard/Music"), none,
        )
        assertThat(out.map { it.id }).containsExactly(1L, 2L)
    }

    @Test fun prefixFolder_doesNotMatch() {
        val out = LibraryFilter.apply(listOf(t(1, folder = "/sdcard/Music2"), t(2, folder = "/sdcard/Music")), 0, setOf("/sdcard/Music/"), none)
        assertThat(out.map { it.id }).containsExactly(2L)
    }

    @Test fun hiddenPaths_areRemoved() {
        val keep = t(1, path = "/m/a.mp3")
        val hide = t(2, path = "/m/b.mp3")
        val out = LibraryFilter.apply(listOf(keep, hide), 0, none, setOf("/m/b.mp3"))
        assertThat(out.map { it.id }).containsExactly(1L)
    }

    @Test fun ref_usesIdAndPath() =
        assertThat(t(5, path = "/x/y.mp3").ref()).isEqualTo(TrackRef(5, "/x/y.mp3"))
}
