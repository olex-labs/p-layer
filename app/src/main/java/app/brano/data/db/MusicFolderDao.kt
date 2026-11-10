package app.brano.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class MusicFolderDao {
    @Query("SELECT * FROM music_folders ORDER BY path")
    abstract fun observeAll(): Flow<List<MusicFolderEntity>>

    fun observe(): Flow<Set<String>> = observeAll().map { list -> list.mapTo(HashSet()) { it.path } }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(entity: MusicFolderEntity)

    @Query("DELETE FROM music_folders WHERE path = :path")
    abstract suspend fun remove(path: String)

    suspend fun add(path: String, treeUri: String) = insert(MusicFolderEntity(path, treeUri))
}
