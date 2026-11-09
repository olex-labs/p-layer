package app.brano.domain

import app.brano.data.library.Album
import app.brano.data.library.Artist
import app.brano.data.library.Grouped
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

/**
 * Precomputes normalized text once; [query] is a plain contains scan. Takes the library's already
 * grouped [albums] and [artists] so they are not grouped a second time.
 */
class SearchIndex(
    private val tracks: List<Track>,
    private val albums: List<Album>,
    private val artists: List<Artist>,
) {
    constructor(tracks: List<Track>) : this(tracks, LibraryGrouping.group(tracks))

    private constructor(tracks: List<Track>, g: Grouped) : this(tracks, g.albums, g.artists)

    private val trackKeys: Array<String> = Array(tracks.size) { i ->
        val t = tracks[i]
        Normalizer.normalize("${t.title} ${t.artist.orEmpty()} ${t.album.orEmpty()}")
    }
    private val albumKeys: Array<String> = Array(albums.size) {
        Normalizer.normalize("${albums[it].title.orEmpty()} ${albums[it].artist.orEmpty()}")
    }
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
