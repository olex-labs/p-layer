package app.brano.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val position: Int,
    val mediaStoreId: Long,
    val path: String,
)

@Entity(tableName = "play_stats", primaryKeys = ["mediaStoreId"])
data class PlayStatEntity(
    val mediaStoreId: Long,
    val path: String,
    val playCount: Int,
    val lastPlayedAt: Long,
)

@Entity(tableName = "favorites", primaryKeys = ["mediaStoreId"])
data class FavoriteEntity(
    val mediaStoreId: Long,
    val path: String,
    val addedAt: Long,
)

@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(
    @PrimaryKey val path: String,
)
