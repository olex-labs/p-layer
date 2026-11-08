package app.brano.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val store: DataStore<Preferences>) {
    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val new = transform(prefs.toSettings())
            prefs[THEME] = new.theme.name
            prefs[AMOLED] = new.amoled
            prefs[DYNAMIC] = new.dynamicColor
            prefs[MIN_DURATION] = new.minDurationMs
            prefs[SORT] = new.trackSort.name
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = enumOrDefault(this[THEME], d.theme),
            amoled = this[AMOLED] ?: d.amoled,
            dynamicColor = this[DYNAMIC] ?: d.dynamicColor,
            minDurationMs = this[MIN_DURATION] ?: d.minDurationMs,
            trackSort = enumOrDefault(this[SORT], d.trackSort),
        )
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val AMOLED = booleanPreferencesKey("amoled")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val MIN_DURATION = longPreferencesKey("min_duration_ms")
        val SORT = stringPreferencesKey("track_sort")
    }
}
