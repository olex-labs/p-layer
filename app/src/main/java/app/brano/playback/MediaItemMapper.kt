package app.brano.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.brano.data.library.Track

object MediaItemMapper {
    private const val ALBUM_ART_BASE = "content://media/external/audio/albumart/"

    fun toMediaItem(t: Track): MediaItem = MediaItem.Builder()
        .setMediaId(t.id.toString())
        .setUri(Uri.parse(t.contentUri))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setAlbumTitle(t.album)
                .setArtworkUri(Uri.parse(ALBUM_ART_BASE + t.albumId))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()

    /** The MediaStore id of [item], or -1 if its mediaId is not one. */
    fun idOf(item: MediaItem): Long = item.mediaId.toLongOrNull() ?: -1L
}
