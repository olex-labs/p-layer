package app.brano.data.library

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Persists the last known library as JSON so the UI has content before MediaStore answers. */
open class LibrarySnapshotStore(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Load outcome reporting; Logcat by default (a no-op in plain JVM tests). */
    private val log: SnapshotLog = SnapshotLog.Logcat,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Track.serializer())

    open suspend fun load(): List<Track>? = withContext(io) {
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString(serializer, file.readText()) }
            .onSuccess { log.loaded(it.size) }
            .onFailure { log.failed(it) }
            .getOrNull()
    }

    open suspend fun save(tracks: List<Track>): Unit = withContext(io) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(serializer, tracks))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
        Unit
    }
}

/** Reports how a snapshot load went; the failure line stays so a broken decode (e.g. R8) is visible. */
interface SnapshotLog {
    fun loaded(count: Int)
    fun failed(error: Throwable)

    object Logcat : SnapshotLog {
        private const val TAG = "Brano"
        override fun loaded(count: Int) {
            Log.d(TAG, "Library snapshot loaded: $count tracks")
        }
        override fun failed(error: Throwable) {
            Log.w(TAG, "Library snapshot unreadable, falling back to a full scan", error)
        }
    }

    object None : SnapshotLog {
        override fun loaded(count: Int) = Unit
        override fun failed(error: Throwable) = Unit
    }
}
