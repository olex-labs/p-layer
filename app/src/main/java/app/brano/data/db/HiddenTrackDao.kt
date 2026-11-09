package app.brano.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class HiddenTrackDao {
    @Query("SELECT * FROM hidden_tracks ORDER BY hiddenAt DESC, path")
    abstract fun observeAll(): Flow<List<HiddenTrackEntity>>

    fun observe(): Flow<Set<String>> = observeAll().map { list -> list.mapTo(HashSet()) { it.path } }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAll(entities: List<HiddenTrackEntity>)

    suspend fun hide(paths: List<String>, now: Long) = insertAll(paths.map { HiddenTrackEntity(it, now) })

    @Query("DELETE FROM hidden_tracks WHERE path = :path")
    abstract suspend fun unhide(path: String)

    @Query("DELETE FROM hidden_tracks")
    abstract suspend fun unhideAll()
}
