package app.brano.domain

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import app.brano.data.db.PlaylistDao
import app.brano.data.db.TrackResolver
import app.brano.data.library.LibraryRepository
import app.brano.data.library.TrackRef
import app.brano.data.library.ref
import app.brano.domain.m3u.M3uImportResult
import app.brano.domain.m3u.M3uMatcher
import app.brano.domain.m3u.M3uParser
import app.brano.domain.m3u.M3uWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

const val DEFAULT_IMPORTED_NAME = "Playlist importata"

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
private const val PRIMARY_ROOT = "/storage/emulated/0"

/** File name without extension, or the fallback when the provider gave none. */
internal fun playlistNameFromFile(displayName: String?): String {
    val base = displayName?.trim()?.substringBeforeLast('.', displayName.trim())?.trim().orEmpty()
    return base.ifEmpty { DEFAULT_IMPORTED_NAME }
}

/** Directory of an M3U on primary shared storage (for relative entries), or null when it cannot be derived. */
internal fun m3uDirOf(uri: Uri): String? {
    if (uri.authority != EXTERNAL_STORAGE_AUTHORITY) return null
    val docId = try {
        DocumentsContract.getDocumentId(uri)
    } catch (e: IllegalArgumentException) {
        return null
    }
    if (!docId.startsWith("primary:")) return null
    val rel = docId.removePrefix("primary:")
    val parent = rel.substringBeforeLast('/', "")
    return if (parent.isEmpty()) PRIMARY_ROOT else "$PRIMARY_ROOT/$parent"
}

class PlaylistIo(
    private val contentResolver: ContentResolver,
    private val playlistDao: PlaylistDao,
    private val library: LibraryRepository,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        playlistDao: PlaylistDao,
        library: LibraryRepository,
    ) : this(context.contentResolver, playlistDao, library)

    /** Writes the playlist as M3U to [target]; items no longer in the library are skipped. */
    suspend fun export(playlistId: Long, target: Uri) {
        val refs = playlistDao.observeItems(playlistId).first().map { TrackRef(it.mediaStoreId, it.path) }
        val (resolved, _) = TrackResolver.resolve(refs, library::trackById, library::trackByPath)
        val text = M3uWriter.write(resolved.mapNotNull { it.track })
        withContext(Dispatchers.IO) {
            val out = contentResolver.openOutputStream(target, "wt") ?: throw IOException("Cannot open $target")
            out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }

    /** Creates a playlist named after the file when at least one entry matches; never creates an empty one. */
    suspend fun import(source: Uri): M3uImportResult {
        val text = withContext(Dispatchers.IO) {
            val input = contentResolver.openInputStream(source) ?: throw IOException("Cannot open $source")
            input.use { String(it.readBytes(), Charsets.UTF_8) }
        }
        val result = M3uMatcher.match(M3uParser.parse(text), m3uDirOf(source), library.tracks.value)
        if (result.matched.isNotEmpty()) {
            val id = playlistDao.create(playlistNameFromFile(displayName(source)))
            playlistDao.addTracks(id, result.matched.map { it.ref() })
        }
        return result
    }

    private suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
