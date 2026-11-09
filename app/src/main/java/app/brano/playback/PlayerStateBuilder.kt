package app.brano.playback

import androidx.media3.common.MediaItem
import app.brano.data.library.Track

/** The displayable part of the player's queue. */
internal data class QueueView(
    /** Tracks of the items that can be shown, in timeline (= play) order. */
    val queue: List<Track>,
    /** Timeline index of each [queue] entry. */
    val timelineIndices: List<Int>,
    val current: Track?,
    /** Position of the current item in [queue], or -1. */
    val index: Int,
)

/** Builds [QueueView] from the player's items; pure so it can be unit-tested. */
internal object PlayerStateBuilder {
    /** Library data first ([lookup] by id), else the item's own metadata; items with neither are skipped. */
    fun resolve(item: MediaItem, lookup: (Long) -> Track?): Track? =
        lookup(MediaItemMapper.idOf(item)) ?: MediaItemMapper.toTrackOrNull(item)

    fun build(items: List<MediaItem>, currentIndex: Int, lookup: (Long) -> Track?): QueueView {
        val tracks = ArrayList<Track>(items.size)
        val indices = ArrayList<Int>(items.size)
        var current: Track? = null
        items.forEachIndexed { i, item ->
            val track = resolve(item, lookup) ?: return@forEachIndexed
            tracks += track
            indices += i
            if (i == currentIndex) current = track
        }
        return QueueView(tracks, indices, current, indices.indexOf(currentIndex))
    }
}
