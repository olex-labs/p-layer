package app.brano.domain

import app.brano.data.library.Album
import app.brano.data.library.Artist
import app.brano.data.library.LibraryGrouping
import app.brano.data.library.Track

data class SearchResults(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
) {
    companion object {
        val EMPTY = SearchResults(emptyList(), emptyList(), emptyList())
    }
}

/** Precomputes normalized text once; [query] is a plain contains scan. */
class SearchIndex(private val tracks: List<Track>) {
    private val trackKeys: Array<String> = Array(tracks.size) { i ->
        val t = tracks[i]
        Normalizer.normalize("${t.title} ${t.artist.orEmpty()} ${t.album.orEmpty()}")
    }
    private val albums: List<Album> = LibraryGrouping.albums(tracks)
    private val albumKeys: Array<String> = Array(albums.size) {
        Normalizer.normalize("${albums[it].title.orEmpty()} ${albums[it].artist.orEmpty()}")
    }
    private val artists: List<Artist> = LibraryGrouping.artists(tracks)
    private val artistKeys: Array<String> = Array(artists.size) { Normalizer.normalize(artists[it].name.orEmpty()) }

    fun query(q: String): SearchResults {
        val n = Normalizer.normalize(q)
        if (n.isEmpty()) return SearchResults.EMPTY
        return SearchResults(
            tracks = tracks.filterIndexed { i, _ -> trackKeys[i].contains(n) },
            albums = albums.filterIndexed { i, _ -> albumKeys[i].contains(n) },
            artists = artists.filterIndexed { i, _ -> artistKeys[i].contains(n) },
        )
    }
}
