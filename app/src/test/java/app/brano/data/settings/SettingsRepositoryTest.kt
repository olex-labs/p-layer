package app.brano.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.dataStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "$name.preferences_pb") }

    @Test fun settings_defaults() = runTest {
        assertThat(SettingsRepository(dataStore("a")).settings.first()).isEqualTo(AppSettings())
    }

    @Test fun settings_updatePersists() = runTest {
        val repo = SettingsRepository(dataStore("a"))
        repo.update { it.copy(minDurationMs = 60_000, theme = ThemeMode.DARK, trackSort = TrackSort.DURATION) }
        val s = repo.settings.first()
        assertThat(s.minDurationMs).isEqualTo(60_000)
        assertThat(s.theme).isEqualTo(ThemeMode.DARK)
        assertThat(s.trackSort).isEqualTo(TrackSort.DURATION)
        assertThat(s.dynamicColor).isTrue()
    }

    @Test fun settings_unknownEnumFallsBackToDefault() = runTest {
        val ds = dataStore("a")
        ds.edit { it[stringPreferencesKey("theme")] = "PURPLE" }
        assertThat(SettingsRepository(ds).settings.first().theme).isEqualTo(AppSettings().theme)
    }

    @Test fun spinDisc_defaultsToTrueWhenKeyMissing() = runTest {
        val ds = dataStore("a")
        ds.edit { it[stringPreferencesKey("theme")] = "DARK" }
        assertThat(SettingsRepository(ds).settings.first().spinDisc).isTrue()
    }

    @Test fun spinDisc_roundTripsFalse() = runTest {
        val repo = SettingsRepository(dataStore("a"))
        repo.update { it.copy(spinDisc = false) }
        assertThat(repo.settings.first().spinDisc).isFalse()
    }

    @Test fun onlineLyrics_offByDefault_andRoundTrips() = runTest {
        val repo = SettingsRepository(dataStore("a"))
        assertThat(repo.settings.first().onlineLyrics).isFalse()
        repo.update { it.copy(onlineLyrics = true) }
        assertThat(repo.settings.first().onlineLyrics).isTrue()
    }
}
