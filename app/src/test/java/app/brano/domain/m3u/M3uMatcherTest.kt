package app.brano.domain.m3u

import app.brano.data.library.t
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class M3uMatcherTest {
    private fun e(p: String) = M3uEntry(p, null, null)

    @Test fun match_absoluteExact() {
        val lib = listOf(t(1, path = "/sdcard/Other/x.mp3"), t(2, path = "/sdcard/Music/x.mp3"))
        val r = M3uMatcher.match(listOf(e("/sdcard/Music/x.mp3")), null, lib)
        assertThat(r.matched.map { it.id }).containsExactly(2L)
    }

    @Test fun match_relativeToPlaylistDir() {
        val lib = listOf(t(1, path = "/sdcard/Other/x.mp3"), t(2, path = "/sdcard/Music/Rock/x.mp3"))
        val r = M3uMatcher.match(listOf(e("../Rock/x.mp3")), "/sdcard/Music/pl", lib)
        assertThat(r.matched.map { it.id }).containsExactly(2L)
        assertThat(r.notFound).isEqualTo(0)
    }

    @Test fun match_relativeWithDotSegments() {
        val lib = listOf(t(1, path = "/sdcard/Other/a.mp3"), t(2, path = "/sdcard/Music/pl/a.mp3"))
        val r = M3uMatcher.match(listOf(e("./sub/../a.mp3")), "/sdcard/Music/pl", lib)
        assertThat(r.matched.map { it.id }).containsExactly(2L)
    }

    @Test fun match_byFilenameFallback_andCountsNotFound() {
        val lib = listOf(t(1, path = "/sdcard/A/Song.MP3"), t(2, path = "/sdcard/B/song.mp3"))
        val r = M3uMatcher.match(listOf(e("/elsewhere/song.mp3"), e("missing.mp3")), null, lib)
        assertThat(r.matched.map { it.id }).containsExactly(1L)
        assertThat(r.notFound).isEqualTo(1)
    }

    @Test fun match_duplicatesPreservedInOrder() {
        val lib = listOf(t(1, path = "/o/a.mp3"), t(2, path = "/o/b.mp3"), t(3, path = "/m/a.mp3"), t(4, path = "/m/b.mp3"))
        val r = M3uMatcher.match(listOf(e("/m/b.mp3"), e("/m/a.mp3"), e("/m/b.mp3")), null, lib)
        assertThat(r.matched.map { it.id }).containsExactly(4L, 3L, 4L).inOrder()
    }

    @Test fun match_dotDotBeyondRoot_fallsBackToFilename() {
        val lib = listOf(t(1, path = "/sdcard/Music/x.mp3"))
        val r = M3uMatcher.match(listOf(e("../../../x.mp3")), "/sd", lib)
        assertThat(r.matched.map { it.id }).containsExactly(1L)
    }

    @Test fun match_driveLetterPath_matchesByFilename() {
        val lib = listOf(t(1, path = "/sdcard/Music/a.mp3"))
        val entries = M3uParser.parse("C:\\Music\\a.mp3")
        assertThat(M3uMatcher.match(entries, null, lib).matched.map { it.id }).containsExactly(1L)
        assertThat(M3uMatcher.match(entries, "/sdcard/pl", lib).matched.map { it.id }).containsExactly(1L)
    }
}
