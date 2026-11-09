package app.brano.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.brano.data.library.TrackRef
import kotlinx.coroutines.flow.Flow

data class PlaylistSummary(val id: Long, val name: String, val trackCount: Int)

@Dao
abstract class PlaylistDao {
    @Query(
        "SELECT p.id AS id, p.name AS name, COUNT(i.position) AS trackCount FROM playlists p " +
            "LEFT JOIN playlist_items i ON i.playlistId = p.id GROUP BY p.id ORDER BY p.name COLLATE NOCASE",
    )
    abstract fun observeAll(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY position")
    abstract fun observeItems(id: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY position")
    protected abstract suspend fun items(id: Long): List<PlaylistItemEntity>

    @Insert
    protected abstract suspend fun insertPlaylist(entity: PlaylistEntity): Long

    @Insert
    protected abstract suspend fun insertItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :id")
    protected abstract suspend fun clearItems(id: Long)

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    protected abstract suspend fun updateName(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    protected abstract suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("SELECT DISTINCT playlistId FROM playlist_items WHERE path = :path")
    protected abstract suspend fun playlistsWithPath(path: String): List<Long>

    @Query("UPDATE playlist_items SET mediaStoreId = :newId WHERE path = :path")
    protected abstract suspend fun updateItemId(path: String, newId: Long)

    suspend fun create(name: String): Long {
        val now = System.currentTimeMillis()
        return insertPlaylist(PlaylistEntity(name = name, createdAt = now, updatedAt = now))
    }

    suspend fun rename(id: Long, name: String) = updateName(id, name, System.currentTimeMillis())

    @Transaction
    open suspend fun addTracks(id: Long, refs: List<TrackRef>) {
        if (refs.isEmpty()) return
        val start = items(id).size
        insertItems(refs.mapIndexed { i, r -> PlaylistItemEntity(id, start + i, r.mediaStoreId, r.path) })
        touch(id, System.currentTimeMillis())
    }

    @Transaction
    open suspend fun move(id: Long, from: Int, to: Int) {
        val list = items(id).toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        list.add(to, list.removeAt(from))
        rewrite(id, list)
    }

    @Transaction
    open suspend fun removeAt(id: Long, position: Int) {
        val list = items(id).toMutableList()
        if (position !in list.indices) return
        list.removeAt(position)
        rewrite(id, list)
    }

    @Transaction
    open suspend fun updateIds(changes: Map<String, Long>) {
        val now = System.currentTimeMillis()
        for ((path, newId) in changes) {
            val affected = playlistsWithPath(path)
            updateItemId(path, newId)
            affected.forEach { touch(it, now) }
        }
    }

    private suspend fun rewrite(id: Long, list: List<PlaylistItemEntity>) {
        clearItems(id)
        insertItems(list.mapIndexed { i, e -> e.copy(playlistId = id, position = i) })
        touch(id, System.currentTimeMillis())
    }
}
