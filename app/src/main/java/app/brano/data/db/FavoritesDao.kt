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

    @Query("SELECT COUNT(*) FROM favorites WHERE mediaStoreId = :id")
    protected abstract suspend fun count(id: Long): Int

    @Query("DELETE FROM favorites WHERE mediaStoreId = :id")
    protected abstract suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(entity: FavoriteEntity)

    /** Returns true when the track is now a favorite. */
    @Transaction
    open suspend fun toggle(ref: TrackRef, now: Long): Boolean {
        return if (count(ref.mediaStoreId) > 0) {
            deleteById(ref.mediaStoreId)
            false
        } else {
            insert(FavoriteEntity(ref.mediaStoreId, ref.path, now))
            true
        }
    }
}
