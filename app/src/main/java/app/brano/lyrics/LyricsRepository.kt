package app.brano.lyrics

import app.brano.data.library.Track
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds a track's lyrics, first hit wins: the .lrc file next to the track ([sidecar]), lyrics
 * inside the file ([embedded]), downloaded copy in [cacheDir], then LRCLIB ([online]) only when
 * [onlineEnabled]. A download is kept; "not found" online is remembered for a week, a network
 * failure is not.
 */
class LyricsRepository(
    private val cacheDir: File,
    private val sidecar: suspend (Track) -> String?,
    private val embedded: suspend (Track) -> String?,
    private val online: suspend (Track) -> LrclibClient.Result,
    private val onlineEnabled: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun load(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        // The user's own files come first, so an .lrc added after a download replaces it.
        quietly { sidecar(track) }
            ?.let { LyricsParser.parse(it, LyricsSource.FILE) }?.let { return@withContext it }
        quietly { embedded(track) }
            ?.let { LyricsParser.parse(it, LyricsSource.EMBEDDED) }?.let { return@withContext it }
        val cached = File(cacheDir, "${track.id}.lrc")
        if (cached.exists()) LyricsParser.parse(cached.readText(), LyricsSource.CACHE)?.let { return@withContext it }
        if (!onlineEnabled()) return@withContext null

        val miss = File(cacheDir, "${track.id}.miss")
        val missedAt = if (miss.exists()) miss.readText().trim().toLongOrNull() else null
        if (missedAt != null && now() - missedAt < MISS_TTL_MS) return@withContext null
        when (val r = online(track)) {
            is LrclibClient.Result.Found -> {
                cacheDir.mkdirs()
                cached.writeText(r.text)
                miss.delete()
                LyricsParser.parse(r.text, LyricsSource.ONLINE)
            }
            LrclibClient.Result.NotFound -> {
                cacheDir.mkdirs()
                miss.writeText(now().toString())
                null
            }
            LrclibClient.Result.Failed -> null
        }
    }

    /** A failing source counts as "nothing there"; cancellation (track changed) still stops the load. */
    private suspend fun quietly(block: suspend () -> String?): String? = try {
        block()
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val MISS_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
