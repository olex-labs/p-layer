package app.brano.di

import android.content.Context
import app.brano.data.library.LibrarySnapshotStore
import app.brano.data.library.MediaStoreTrackSource
import app.brano.data.library.TrackSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

// LibraryRepository is wired in Task 4, once the settings and excluded-folder flows exist.
@Module
@InstallIn(SingletonComponent::class)
object LibraryModule {
    @Provides @Singleton
    fun trackSource(impl: MediaStoreTrackSource): TrackSource = impl

    @Provides @Singleton
    fun snapshotStore(@ApplicationContext context: Context): LibrarySnapshotStore =
        LibrarySnapshotStore(File(context.filesDir, "library.json"))
}
