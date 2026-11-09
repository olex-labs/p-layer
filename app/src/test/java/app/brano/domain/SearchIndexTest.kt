package app.brano.domain

import app.brano.data.library.t
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.system.measureTimeMillis

class SearchIndexTest {
    @Test fun unknownArtist_searchable() {
        val list = listOf(t(1, title = "Il titolo", artist = null))
        assertThat(SearchIndex(list).query("titolo").tracks).hasSize(1)
    }

    @Test fun search_accentInsensitive() =
        assertThat(SearchIndex(listOf(t(title = "Perché"))).query("perche").tracks).hasSize(1)

    @Test fun emptyQuery_emptyResults() {
        val r = SearchIndex(listOf(t(1))).query("  ")
        assertThat(r.tracks).isEmpty()
        assertThat(r.albums).isEmpty()
        assertThat(r.artists).isEmpty()
    }

    @Test fun matchesAlbumsAndArtists() {
        val idx = SearchIndex(
            listOf(t(1, title = "x", artist = "Måneskin", album = "Teatro d ira", albumId = 7, artistId = 3)),
        )
        assertThat(idx.query("teatro").albums.single().id).isEqualTo(7L)
        assertThat(idx.query("maneskin").artists.single().id).isEqualTo(3L)
        assertThat(idx.query("maneskin").albums).hasSize(1) // album matches via its artist
    }

    @Test fun search_10kTracks_under10ms() {
        val idx = SearchIndex((1..10_000L).map { t(it, title = "Song $it") })
        idx.query("song 5") // warm-up
        val ms = measureTimeMillis { idx.query("song 99") }
        assertThat(ms).isLessThan(10)
    }

    @Test fun usesTheAlbumsAndArtistsItIsGiven() {
        val tracks = listOf(t(1, title = "Volare", album = "Nel blu", artist = "Modugno"))
        val albums = app.brano.data.library.LibraryGrouping.albums(tracks)
        val artists = app.brano.data.library.LibraryGrouping.artists(tracks)
        val r = SearchIndex(tracks, albums, artists).query("modugno")
        assertThat(r.albums.single()).isSameInstanceAs(albums.single())
        assertThat(r.artists.single()).isSameInstanceAs(artists.single())
    }
}
