package app.brano.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.brano.data.library.Track
import kotlinx.coroutines.flow.first

/** [repeatMode] holds Player.REPEAT_MODE_* constants. */
data class QueueState(
    val ids: List<Long>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

data class RestoredQueue(val tracks: List<Track>, val index: Int, val positionMs: Long)

class QueueStateStore(private val store: DataStore<Preferences>) {
    suspend fun save(s: QueueState) {
        store.edit {
            it[IDS] = s.ids.joinToString(",")
            it[INDEX] = s.index
            it[POSITION] = s.positionMs
            it[SHUFFLE] = s.shuffle
            it[REPEAT] = s.repeatMode
        }
    }

    suspend fun load(): QueueState? {
        val p = store.data.first()
        val csv = p[IDS] ?: return null
        return QueueState(
            ids = csv.split(',').mapNotNull { it.trim().toLongOrNull() },
            index = p[INDEX] ?: 0,
            positionMs = p[POSITION] ?: 0L,
            shuffle = p[SHUFFLE] ?: false,
            repeatMode = p[REPEAT] ?: 0,
        )
    }

    private companion object {
        val IDS = stringPreferencesKey("ids")
        val INDEX = intPreferencesKey("index")
        val POSITION = longPreferencesKey("position_ms")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val REPEAT = intPreferencesKey("repeat_mode")
    }
}

/**
 * Drops ids no longer in the library. If the current track is gone, the index moves to the next
 * available track after it in the original order (else the last one before it) with position 0.
 */
fun QueueState.restore(lookup: (Long) -> Track?): RestoredQueue? {
    val resolved = ids.map { lookup(it) }
    val tracks = resolved.filterNotNull()
    if (tracks.isEmpty()) return null
    val cur = index.coerceIn(0, ids.lastIndex)
    val availableBefore = resolved.take(cur).count { it != null }
    if (resolved[cur] != null) return RestoredQueue(tracks, availableBefore, positionMs)
    val hasAfter = resolved.drop(cur + 1).any { it != null }
    return RestoredQueue(tracks, if (hasAfter) availableBefore else availableBefore - 1, 0L)
}
