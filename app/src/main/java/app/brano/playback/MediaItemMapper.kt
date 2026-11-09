package app.brano.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.brano.data.library.Track

object MediaItemMapper {
    private const val ALBUM_ART_BASE = "content://media/external/audio/albumart/"
    private const val EXTRA_ALBUM_ID = "brano.albumId"
    private const val EXTRA_ARTIST_ID = "brano.artistId"
    private const val EXTRA_DURATION_MS = "brano.durationMs"
    private const val EXTRA_PATH = "brano.path"
    private const val EXTRA_CONTENT_URI = "brano.contentUri"

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
                .setExtras(
                    Bundle().apply {
                        putLong(EXTRA_ALBUM_ID, t.albumId)
                        putLong(EXTRA_ARTIST_ID, t.artistId)
                        putLong(EXTRA_DURATION_MS, t.durationMs)
                        putString(EXTRA_PATH, t.path)
                        putString(EXTRA_CONTENT_URI, t.contentUri)
                    },
                )
                .build(),
        )
        .build()

    /** The MediaStore id of [item], or -1 if its mediaId is not one. */
    fun idOf(item: MediaItem): Long = item.mediaId.toLongOrNull() ?: -1L

    /**
     * The track as described by [item]'s own metadata, for items whose track is no longer in the
     * library (deleted, excluded, filtered). Null for items not built by [toMediaItem].
     */
    fun toTrackOrNull(item: MediaItem): Track? {
        val id = idOf(item).takeIf { it >= 0 } ?: return null
        val md = item.mediaMetadata
        val extras = md.extras ?: return null
        val path = extras.getString(EXTRA_PATH) ?: return null
        return Track(
            id = id,
            title = md.title?.toString() ?: path.substringAfterLast('/'),
            artist = md.artist?.toString(),
            album = md.albumTitle?.toString(),
            albumId = extras.getLong(EXTRA_ALBUM_ID),
            artistId = extras.getLong(EXTRA_ARTIST_ID),
            durationMs = extras.getLong(EXTRA_DURATION_MS),
            path = path,
            folder = path.substringBeforeLast('/', ""),
            dateAddedSec = 0,
            trackNumber = 0,
            year = 0,
            contentUri = extras.getString(EXTRA_CONTENT_URI) ?: item.localConfiguration?.uri?.toString().orEmpty(),
        )
    }
}
