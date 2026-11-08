package app.brano.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.brano.data.library.TrackRef
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlayStatsDao {
    @Query("SELECT * FROM play_stats")
    abstract fun observeAll(): Flow<List<PlayStatEntity>>

    @Query("SELECT * FROM play_stats WHERE mediaStoreId = :id")
    protected abstract suspend fun get(id: Long): PlayStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsert(entity: PlayStatEntity)

    @Transaction
    open suspend fun increment(ref: TrackRef, now: Long) {
        val current = get(ref.mediaStoreId)
        upsert(PlayStatEntity(ref.mediaStoreId, ref.path, (current?.playCount ?: 0) + 1, now))
    }
}
