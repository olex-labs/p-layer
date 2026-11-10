package app.brano.playback

import androidx.media3.common.Player
import androidx.media3.session.MediaSession

/** What a cold "play" resumes: the queue already restored in [player], unchanged; empty if none. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object PlaybackResumption {
    fun of(player: Player): MediaSession.MediaItemsWithStartPosition =
        if (player.mediaItemCount == 0) {
            MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
        } else {
            MediaSession.MediaItemsWithStartPosition(
                List(player.mediaItemCount) { player.getMediaItemAt(it) },
                player.currentMediaItemIndex,
                player.currentPosition,
            )
        }
}
