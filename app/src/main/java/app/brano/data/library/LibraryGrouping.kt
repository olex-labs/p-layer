package app.brano.data.library

import app.brano.domain.Normalizer

data class Album(
    val id: Long,
    val title: String?,
    val artist: String?,
    val year: Int,
    val trackCount: Int,
    val tracks: List<Track>,
)

data class Artist(
    val id: Long,
    val name: String?,
    val albumCount: Int,
    val tracks: List<Track>,
)

data class FolderNode(
    val path: String,
    val name: String,
    val children: List<FolderNode>,
    val tracks: List<Track>,
)

/** Albums, artists and folder tree of one library, computed together by [LibraryGrouping.group]. */
data class Grouped(
    val albums: List<Album>,
    val artists: List<Artist>,
    val folders: FolderNode,
)

object LibraryGrouping {
    /** A track with its sort keys, normalized once. */
    private class Keyed(val track: Track, val title: String, val album: String)

    /**
     * Groups [t] into albums, artists and the folder tree. Every distinct string is normalized at
     * most once (memoized), and all sorting compares those precomputed keys.
     */
    fun group(t: List<Track>, normalize: (String) -> String = Normalizer::normalize): Grouped {
        val memo = HashMap<String, String>()
        fun key(s: String?): String = if (s == null) "" else memo.getOrPut(s) { normalize(s) }
        val keyed = t.map { Keyed(it, key(it.title), key(it.album)) }
        val byTrackNumber = compareBy<Keyed> { it.track.trackNumber }.thenBy { it.title }

        val albums = keyed.groupBy { it.track.albumId }.map { (id, group) ->
            val title = group.firstNotNullOfOrNull { it.track.album }
            Album(
                id = id,
                title = title,
                artist = group.firstNotNullOfOrNull { it.track.artist },
                year = group.maxOf { it.track.year },
                trackCount = group.size,
                tracks = group.sortedWith(byTrackNumber).map { it.track },
            ) to title?.let(::key)
        }.sortedNullLast()

        val byAlbum = compareBy<Keyed> { it.album }.then(byTrackNumber)
        val artists = keyed.groupBy { it.track.artistId }.map { (id, group) ->
            val name = group.firstNotNullOfOrNull { it.track.artist }
            Artist(
                id = id,
                name = name,
                albumCount = group.mapTo(HashSet()) { it.track.albumId }.size,
                tracks = group.sortedWith(byAlbum).map { it.track },
            ) to name?.let(::key)
        }.sortedNullLast()

        return Grouped(albums, artists, folderTree(keyed, ::key))
    }

    /** Sorts by the precomputed key, missing (null) keys last. */
    private fun <T> List<Pair<T, String?>>.sortedNullLast(): List<T> =
        sortedWith(compareBy<Pair<T, String?>> { it.second == null }.thenBy { it.second.orEmpty() }).map { it.first }

    fun albums(t: List<Track>): List<Album> = group(t).albums

    fun artists(t: List<Track>): List<Artist> = group(t).artists

    fun folderTree(t: List<Track>): FolderNode = group(t).folders

    /** The albums [artist] has tracks on, in the order of [albums]. */
    fun albumsOf(artist: Artist, albums: List<Album>): List<Album> {
        val ids = artist.tracks.mapTo(HashSet()) { it.albumId }
        return albums.filter { it.id in ids }
    }

    private class Builder(val segment: String) {
        val children = LinkedHashMap<String, Builder>()
        val tracks = ArrayList<Keyed>()
    }

    private fun folderTree(t: List<Keyed>, key: (String?) -> String): FolderNode {
        val root = Builder("")
        for (k in t) {
            var node = root
            for (seg in k.track.folder.split('/').filter { it.isNotEmpty() }) {
                node = node.children.getOrPut(seg) { Builder(seg) }
            }
            node.tracks += k
        }
        return compress(build(root, emptyList(), key))
    }

    private fun build(b: Builder, parentSegs: List<String>, key: (String?) -> String): FolderNode {
        val segs = if (b.segment.isEmpty() && parentSegs.isEmpty()) emptyList() else parentSegs + b.segment
        return FolderNode(
            path = "/" + segs.joinToString("/"),
            name = b.segment,
            children = b.children.values.map { build(it, segs, key) }.sortedBy { key(it.name) },
            tracks = b.tracks.sortedBy { it.title }.map { it.track },
        )
    }

    private fun compress(n: FolderNode): FolderNode {
        var node = n
        while (node.children.size == 1 && node.tracks.isEmpty()) {
            val child = node.children.single()
            node = child.copy(name = if (node.name.isEmpty()) child.name else "${node.name}/${child.name}")
        }
        return node.copy(children = node.children.map(::compress))
    }
}
