package app.brano.widget

import app.brano.data.library.Track

/** What the home screen widget shows. [title] is empty when nothing was ever played. */
data class WidgetState(
    val title: String,
    val artist: String,
    val albumId: Long?,
    val isPlaying: Boolean,
    val hasTrack: Boolean,
) {
    companion object {
        val EMPTY = WidgetState(title = "", artist = "", albumId = null, isPlaying = false, hasTrack = false)

        fun of(track: Track?, isPlaying: Boolean): WidgetState =
            if (track == null) EMPTY
            else WidgetState(track.title, track.artist.orEmpty(), track.albumId, isPlaying, hasTrack = true)
    }
}
