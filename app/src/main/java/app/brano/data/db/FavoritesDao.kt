package app.brano.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.brano.data.library.TrackRef
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FavoritesDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    abstract fun observe(): Flow<List<FavoriteEntity>>

    @Query("SELECT COUNT(*) FROM favorites WHERE mediaStoreId = :id OR path = :path")
    protected abstract suspend fun count(id: Long, path: String): Int

    @Query("DELETE FROM favorites WHERE mediaStoreId = :id OR path = :path")
    protected abstract suspend fun delete(id: Long, path: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(entity: FavoriteEntity)

    /**
     * Returns true when the track is now a favorite. Matches by id or path, so a favorite whose
     * MediaStore id changed (same file re-indexed) is still found and removed.
     */
    @Transaction
    open suspend fun toggle(ref: TrackRef, now: Long): Boolean {
        return if (count(ref.mediaStoreId, ref.path) > 0) {
            delete(ref.mediaStoreId, ref.path)
            false
        } else {
            insert(FavoriteEntity(ref.mediaStoreId, ref.path, now))
            true
        }
    }
}
