package app.brano.domain.m3u

import app.brano.data.library.t
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class M3uWriterTest {
    @Test fun write_format() {
        val out = M3uWriter.write(
            listOf(
                t(1, title = "B", artist = "A", dur = 210_999, path = "/m/1.mp3"),
                t(2, title = "Solo", artist = null, dur = 5_000, path = "/m/2.mp3"),
            ),
        )
        assertThat(out).isEqualTo("#EXTM3U\n#EXTINF:210,A - B\n/m/1.mp3\n#EXTINF:5,Solo\n/m/2.mp3\n")
    }

    @Test fun write_empty() = assertThat(M3uWriter.write(emptyList())).isEqualTo("#EXTM3U\n")

    @Test fun writeThenParse_roundTrip() {
        val lib = listOf(t(1, path = "/sdcard/Music/a.mp3"), t(2, path = "/sdcard/Music/Rock/b c.mp3"), t(3, artist = null))
        val parsed = M3uParser.parse(M3uWriter.write(lib))
        val res = M3uMatcher.match(parsed, null, lib)
        assertThat(res.matched.map { it.id }).containsExactly(1L, 2L, 3L).inOrder()
        assertThat(res.notFound).isEqualTo(0)
    }
}
