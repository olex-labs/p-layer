package app.brano.data.library

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class MediaStoreTrackSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : TrackSource {

    private val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    @Suppress("DEPRECATION")
    override suspend fun loadAll(): List<Track> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE,
        ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) arrayOf(MediaStore.Audio.Media.BITRATE) else emptyArray()
        val result = ArrayList<Track>()
        context.contentResolver.query(
            uri, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val path = c.getString(7)
                if (path.isNullOrEmpty()) continue
                val id = c.getLong(0)
                result += Track(
                    id = id,
                    title = c.getString(1).cleanOrNull() ?: File(path).nameWithoutExtension,
                    artist = c.getString(2).cleanOrNull(),
                    album = c.getString(3).cleanOrNull(),
                    albumId = c.getLong(4),
                    artistId = c.getLong(5),
                    durationMs = c.getLong(6),
                    path = path,
                    folder = File(path).parent ?: "",
                    dateAddedSec = c.getLong(8),
                    trackNumber = c.getInt(9) % 1000,
                    year = c.getInt(10),
                    contentUri = ContentUris.withAppendedId(uri, id).toString(),
                    mimeType = c.getString(11),
                    // BITRATE is in bits per second, Android 11+ only.
                    bitrateKbps = if (c.columnCount > 12) (c.getLong(12) / 1000).toInt() else 0,
                )
            }
        }
        result
    }

    @OptIn(FlowPreview::class)
    override fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(uri, true, observer)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.debounce(500)
}

internal fun String?.cleanOrNull(): String? =
    this?.trim()?.takeUnless { it.isEmpty() || it == "<unknown>" }
