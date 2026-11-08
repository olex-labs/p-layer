package app.brano.data.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryFilterTest {
    @Test fun filter_dropsShortTracks() {
        val out = LibraryFilter.apply(listOf(t(dur = 29_999), t(id = 2, dur = 30_000)), 30_000, emptySet())
        assertThat(out.map { it.id }).containsExactly(2L)
    }

    @Test fun filter_excludesFolderAndSubfolders() {
        val out = LibraryFilter.apply(
            listOf(t(1, folder = "/sdcard/WhatsApp/Media"), t(2, folder = "/sdcard/WhatsAppX"), t(3, folder = "/sdcard/WhatsApp")),
            0, setOf("/sdcard/WhatsApp"),
        )
        assertThat(out.map { it.id }).containsExactly(2L)
    }

    @Test fun filter_ignoresTrailingSlashOnExcluded() {
        val out = LibraryFilter.apply(listOf(t(1, folder = "/sdcard/A/B"), t(2, folder = "/sdcard/AB")), 0, setOf("/sdcard/A/"))
        assertThat(out.map { it.id }).containsExactly(2L)
    }

    @Test fun ref_usesIdAndPath() =
        assertThat(t(5, path = "/x/y.mp3").ref()).isEqualTo(TrackRef(5, "/x/y.mp3"))
}
