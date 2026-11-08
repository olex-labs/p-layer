package app.brano.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class ExcludedFolderDao {
    @Query("SELECT path FROM excluded_folders")
    protected abstract fun observePaths(): Flow<List<String>>

    fun observe(): Flow<Set<String>> = observePaths().map { it.toSet() }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(entity: ExcludedFolderEntity)

    @Query("DELETE FROM excluded_folders WHERE path = :path")
    abstract suspend fun remove(path: String)

    suspend fun add(path: String) = insert(ExcludedFolderEntity(path))
}
