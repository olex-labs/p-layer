package app.brano.lyrics

import app.brano.data.library.t
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LrclibClientTest {
    private val track = t(1, title = "Caffè & co", artist = "Marco Vela", album = "Luci d'Autunno", dur = 242_600)

    private fun client(reply: (URL) -> Pair<Int, String>) = LrclibClient(fetch = reply)

    @Test fun url_encodesEveryField() {
        val url = client { 200 to "{}" }.buildUrl(track).toString()
        assertThat(url).startsWith("https://lrclib.net/api/get?")
        assertThat(url).contains("track_name=Caff%C3%A8+%26+co")
        assertThat(url).contains("artist_name=Marco+Vela")
        assertThat(url).contains("album_name=Luci+d%27Autunno")
        assertThat(url).contains("duration=243")
    }

    @Test fun syncedLyrics_found() = runTest {
        val r = client { 200 to """{"id":1,"syncedLyrics":"[00:01.00]Ciao","plainLyrics":"Ciao","extra":true}""" }.find(track)
        assertThat(r).isEqualTo(LrclibClient.Result.Found("[00:01.00]Ciao"))
    }

    @Test fun onlyPlainLyrics_found() = runTest {
        val r = client { 200 to """{"syncedLyrics":null,"plainLyrics":"Ciao"}""" }.find(track)
        assertThat(r).isEqualTo(LrclibClient.Result.Found("Ciao"))
    }

    @Test fun notFound() = runTest {
        assertThat(client { 404 to "" }.find(track)).isEqualTo(LrclibClient.Result.NotFound)
        assertThat(client { 200 to """{"instrumental":true}""" }.find(track)).isEqualTo(LrclibClient.Result.NotFound)
    }

    @Test fun networkError_failed() = runTest {
        assertThat(client { throw IOException("offline") }.find(track)).isEqualTo(LrclibClient.Result.Failed)
        assertThat(client { 500 to "" }.find(track)).isEqualTo(LrclibClient.Result.Failed)
    }
}
