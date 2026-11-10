package app.brano.di

import android.content.Context
import android.net.Uri
import app.brano.data.db.MusicFolderDao
import app.brano.data.settings.SettingsRepository
import app.brano.lyrics.EmbeddedLyrics
import app.brano.lyrics.LrclibClient
import app.brano.lyrics.LyricsRepository
import app.brano.lyrics.readSidecarLyrics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Module
@InstallIn(SingletonComponent::class)
object LyricsModule {
    @Provides @Singleton
    fun lyricsRepository(
        @ApplicationContext context: Context,
        musicFolderDao: MusicFolderDao,
        settings: SettingsRepository,
        @ApplicationScope scope: CoroutineScope,
    ): LyricsRepository {
        val onlineEnabled = settings.settings.map { it.onlineLyrics }.stateIn(scope, SharingStarted.Eagerly, false)
        val client = LrclibClient()
        return LyricsRepository(
            cacheDir = File(context.filesDir, "lyrics"),
            sidecar = { track -> readSidecarLyrics(context, musicFolderDao.observeAll().first(), track) },
            embedded = { track ->
                context.contentResolver.openInputStream(Uri.parse(track.contentUri))?.use(EmbeddedLyrics::read)
            },
            online = client::find,
            onlineEnabled = { onlineEnabled.value },
        )
    }
}
