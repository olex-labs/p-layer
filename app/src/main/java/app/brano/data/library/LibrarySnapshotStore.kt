package app.brano.data.library

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
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Track.serializer())

    open suspend fun load(): List<Track>? = withContext(io) {
        runCatching {
            if (file.exists()) json.decodeFromString(serializer, file.readText()) else null
        }.getOrNull()
    }

    suspend fun save(tracks: List<Track>): Unit = withContext(io) {
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
