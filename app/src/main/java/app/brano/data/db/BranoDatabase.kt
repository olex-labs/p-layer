package app.brano.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        PlayStatEntity::class,
        FavoriteEntity::class,
        ExcludedFolderEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class BranoDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun playStatsDao(): PlayStatsDao
    abstract fun excludedFolderDao(): ExcludedFolderDao
}
