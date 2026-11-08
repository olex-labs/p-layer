package app.brano.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.brano.data.settings.QueueStateStore
import app.brano.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class QueueStore

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides @Singleton @SettingsStore
    fun settingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides @Singleton @QueueStore
    fun queueDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("queue_state") }

    @Provides @Singleton
    fun settingsRepository(@SettingsStore store: DataStore<Preferences>) = SettingsRepository(store)

    @Provides @Singleton
    fun queueStateStore(@QueueStore store: DataStore<Preferences>) = QueueStateStore(store)
}
