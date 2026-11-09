package app.brano.data.library

import app.brano.domain.Normalizer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryGroupingTest {
    @Test fun albums_sortedByTrackNumber() {
        val albums = LibraryGrouping.albums(
            listOf(t(1, albumId = 5, trackNumber = 2), t(2, albumId = 5, trackNumber = 1)),
        )
        assertThat(albums.single().tracks.map { it.trackNumber }).containsExactly(1, 2).inOrder()
        assertThat(albums.single().trackCount).isEqualTo(2)
        assertThat(albums.single().id).isEqualTo(5L)
    }

    @Test fun albums_sortedByNormalizedTitle_nullLast() {
        val albums = LibraryGrouping.albums(
            listOf(
                t(1, album = null, albumId = 1),
                t(2, album = "Zeta", albumId = 2),
                t(3, album = "épsilon", albumId = 3),
            ),
        )
        assertThat(albums.map { it.title }).containsExactly("épsilon", "Zeta", null).inOrder()
    }

    @Test fun unknownArtist_grouped() {
        val artists = LibraryGrouping.artists(listOf(t(1, artist = null, artistId = 9), t(2, artist = null, artistId = 9)))
        assertThat(artists.single().name).isNull()
        assertThat(artists.single().tracks).hasSize(2)
    }

    @Test fun artists_countAlbums_sortedNullLast() {
        val artists = LibraryGrouping.artists(
            listOf(
                t(1, artist = null, artistId = 3),
                t(2, artist = "Beta", artistId = 2, albumId = 1),
                t(3, artist = "Beta", artistId = 2, albumId = 2),
                t(4, artist = "alfa", artistId = 1),
            ),
        )
        assertThat(artists.map { it.name }).containsExactly("alfa", "Beta", null).inOrder()
        assertThat(artists[1].albumCount).isEqualTo(2)
    }

    @Test fun folderTree_compressesSingleChildChains() {
        val tree = LibraryGrouping.folderTree(
            listOf(
                t(1, folder = "/sdcard/Music/Rock"),
                t(2, folder = "/sdcard/Music/Pop"),
                t(3, folder = "/sdcard/Music/Pop/Live"),
            ),
        )
        assertThat(tree.path).isEqualTo("/sdcard/Music")
        assertThat(tree.children.map { it.name }).containsExactly("Pop", "Rock").inOrder()
        val pop = tree.children.first()
        assertThat(pop.tracks.map { it.id }).containsExactly(2L)
        assertThat(pop.children.single().path).isEqualTo("/sdcard/Music/Pop/Live")
    }

    @Test fun folderTree_empty() {
        val tree = LibraryGrouping.folderTree(emptyList())
        assertThat(tree.children).isEmpty()
        assertThat(tree.tracks).isEmpty()
    }

    @Test fun albumsOf_artist_keepsOnlyAlbumsWithTheirTracks() {
        val tracks = listOf(
            t(1, artistId = 1, albumId = 10, album = "B"),
            t(2, artistId = 1, albumId = 11, album = "A"),
            t(3, artistId = 2, albumId = 12, album = "C"),
            t(4, artistId = 2, albumId = 10, album = "B"), // compilation shared with artist 2
        )
        val albums = LibraryGrouping.albums(tracks)
        val artist = LibraryGrouping.artists(tracks).first { it.id == 1L }
        assertThat(LibraryGrouping.albumsOf(artist, albums).map { it.id }).containsExactly(11L, 10L).inOrder()
    }

    @Test fun group_normalizesEachDistinctStringOnce() {
        val tracks = (1..60).map {
            t(
                it.toLong(), title = "Brano ${it % 50}", album = "Album ${it % 5}", albumId = (it % 5).toLong(),
                artist = "Artista ${it % 3}", artistId = (it % 3).toLong(), folder = "/sdcard/Music/D${it % 4}",
            )
        }
        val calls = ArrayList<String>()
        val g = LibraryGrouping.group(tracks) { s -> calls += s; Normalizer.normalize(s) }
        assertThat(calls).containsNoDuplicates()
        assertThat(g.albums).isEqualTo(LibraryGrouping.albums(tracks))
        assertThat(g.artists).isEqualTo(LibraryGrouping.artists(tracks))
        assertThat(g.folders).isEqualTo(LibraryGrouping.folderTree(tracks))
    }
}
