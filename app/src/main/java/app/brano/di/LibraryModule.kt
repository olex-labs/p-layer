package app.brano.di

import android.content.Context
import app.brano.data.db.ExcludedFolderDao
import app.brano.data.library.LibraryRepository
import app.brano.data.library.LibrarySnapshotStore
import app.brano.data.library.MediaStoreTrackSource
import app.brano.data.library.TrackSource
import app.brano.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Module
@InstallIn(SingletonComponent::class)
object LibraryModule {
    @Provides @Singleton
    fun trackSource(impl: MediaStoreTrackSource): TrackSource = impl

    @Provides @Singleton
    fun snapshotStore(@ApplicationContext context: Context): LibrarySnapshotStore =
        LibrarySnapshotStore(File(context.filesDir, "library.json"))

    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun libraryRepository(
        source: TrackSource,
        snapshotStore: LibrarySnapshotStore,
        excludedFolderDao: ExcludedFolderDao,
        settingsRepository: SettingsRepository,
        @ApplicationScope scope: CoroutineScope,
    ) = LibraryRepository(
        source,
        snapshotStore,
        excludedFolderDao.observe(),
        settingsRepository.settings.map { it.minDurationMs }.distinctUntilChanged(),
        scope,
    )
}
