package app.brano.di

import android.content.Context
import androidx.room.Room
import app.brano.data.db.BranoDatabase
import app.brano.data.db.ExcludedFolderDao
import app.brano.data.db.FavoritesDao
import app.brano.data.db.PlayStatsDao
import app.brano.data.db.PlaylistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): BranoDatabase =
        Room.databaseBuilder(context, BranoDatabase::class.java, "brano.db").build()

    @Provides fun playlistDao(db: BranoDatabase): PlaylistDao = db.playlistDao()
    @Provides fun favoritesDao(db: BranoDatabase): FavoritesDao = db.favoritesDao()
    @Provides fun playStatsDao(db: BranoDatabase): PlayStatsDao = db.playStatsDao()
    @Provides fun excludedFolderDao(db: BranoDatabase): ExcludedFolderDao = db.excludedFolderDao()
}
