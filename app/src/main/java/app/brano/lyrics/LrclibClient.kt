package app.brano.lyrics

import app.brano.data.library.Track
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * LRCLIB (lrclib.net), a free and open lyrics archive: one GET per track, no account. Used only when
 * the user turned on "Cerca testi online". [fetch] returns (HTTP status, body) and may throw.
 */
class LrclibClient(private val fetch: (URL) -> Pair<Int, String> = ::httpGet) {
    sealed interface Result {
        data class Found(val text: String) : Result
        data object NotFound : Result
        data object Failed : Result
    }

    fun buildUrl(track: Track): URL {
        val params = buildList {
            add("track_name" to track.title)
            track.artist?.let { add("artist_name" to it) }
            track.album?.let { add("album_name" to it) }
            add("duration" to (track.durationMs / 1000.0).roundToInt().toString())
        }
        val query = params.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }
        return URL("$BASE?$query")
    }

    suspend fun find(track: Track): Result = withContext(Dispatchers.IO) {
        try {
            val (code, body) = fetch(buildUrl(track))
            when {
                code == 404 -> Result.NotFound
                code !in 200..299 -> Result.Failed
                else -> {
                    val r = json.decodeFromString(Response.serializer(), body)
                    val text = r.syncedLyrics?.takeIf { it.isNotBlank() } ?: r.plainLyrics?.takeIf { it.isNotBlank() }
                    if (text == null) Result.NotFound else Result.Found(text)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failed
        }
    }

    @Serializable
    private data class Response(val syncedLyrics: String? = null, val plainLyrics: String? = null)

    private companion object {
        const val BASE = "https://lrclib.net/api/get"
        val json = Json { ignoreUnknownKeys = true }
    }
}

private const val TIMEOUT_MS = 8_000

private fun httpGet(url: URL): Pair<Int, String> {
    val c = url.openConnection() as HttpURLConnection
    c.connectTimeout = TIMEOUT_MS
    c.readTimeout = TIMEOUT_MS
    c.setRequestProperty("User-Agent", "Brano/1.6 (Android music player)")
    try {
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        return code to body
    } finally {
        c.disconnect()
    }
}
