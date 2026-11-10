package app.brano.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        PlayStatEntity::class,
        FavoriteEntity::class,
        MusicFolderEntity::class,
        HiddenTrackEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class BranoDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun playStatsDao(): PlayStatsDao
    abstract fun musicFolderDao(): MusicFolderDao
    abstract fun hiddenTrackDao(): HiddenTrackDao
}

/**
 * v1.6: "excluded folders" become "music folders" (the opposite logic, so old rows are not converted)
 * and tracks can be hidden.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS excluded_folders")
        db.execSQL("CREATE TABLE IF NOT EXISTS music_folders (path TEXT NOT NULL, treeUri TEXT NOT NULL, PRIMARY KEY(path))")
        db.execSQL("CREATE TABLE IF NOT EXISTS hidden_tracks (path TEXT NOT NULL, hiddenAt INTEGER NOT NULL, PRIMARY KEY(path))")
    }
}
