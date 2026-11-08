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

object LibraryGrouping {
    private fun String?.sortKey(): String = this?.let(Normalizer::normalize).orEmpty()

    private fun <T> List<T>.sortedNullLast(name: (T) -> String?): List<T> =
        sortedWith(compareBy<T> { name(it) == null }.thenBy { name(it).sortKey() })

    fun albums(t: List<Track>): List<Album> =
        t.groupBy { it.albumId }.map { (id, group) ->
            val tracks = group.sortedWith(compareBy<Track> { it.trackNumber }.thenBy { it.title.sortKey() })
            Album(
                id = id,
                title = group.firstNotNullOfOrNull { it.album },
                artist = group.firstNotNullOfOrNull { it.artist },
                year = group.maxOf { it.year },
                trackCount = group.size,
                tracks = tracks,
            )
        }.sortedNullLast { it.title }

    fun artists(t: List<Track>): List<Artist> =
        t.groupBy { it.artistId }.map { (id, group) ->
            Artist(
                id = id,
                name = group.firstNotNullOfOrNull { it.artist },
                albumCount = group.map { it.albumId }.distinct().size,
                tracks = group.sortedWith(
                    compareBy<Track> { it.album.sortKey() }.thenBy { it.trackNumber }.thenBy { it.title.sortKey() },
                ),
            )
        }.sortedNullLast { it.name }

    private class Builder(val segment: String) {
        val children = LinkedHashMap<String, Builder>()
        val tracks = ArrayList<Track>()
    }

    fun folderTree(t: List<Track>): FolderNode {
        val root = Builder("")
        for (track in t) {
            var node = root
            for (seg in track.folder.split('/').filter { it.isNotEmpty() }) {
                node = node.children.getOrPut(seg) { Builder(seg) }
            }
            node.tracks += track
        }
        return compress(build(root, emptyList()))
    }

    private fun build(b: Builder, parentSegs: List<String>): FolderNode {
        val segs = if (b.segment.isEmpty() && parentSegs.isEmpty()) emptyList() else parentSegs + b.segment
        return FolderNode(
            path = "/" + segs.joinToString("/"),
            name = b.segment,
            children = b.children.values.map { build(it, segs) }.sortedBy { it.name.sortKey() },
            tracks = b.tracks.sortedBy { it.title.sortKey() },
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
