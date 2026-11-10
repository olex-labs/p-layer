package app.brano.widget

import android.content.Context

/** Last widget state, so the widget can redraw after a reboot while the playback service is off. */
class WidgetStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("widget", Context.MODE_PRIVATE)

    fun load(): WidgetState {
        if (!prefs.getBoolean(HAS_TRACK, false)) return WidgetState.EMPTY
        return WidgetState(
            title = prefs.getString(TITLE, "").orEmpty(),
            artist = prefs.getString(ARTIST, "").orEmpty(),
            albumId = prefs.getLong(ALBUM_ID, NO_ALBUM).takeIf { it != NO_ALBUM },
            isPlaying = prefs.getBoolean(PLAYING, false),
            hasTrack = true,
        )
    }

    fun save(s: WidgetState) {
        prefs.edit()
            .putBoolean(HAS_TRACK, s.hasTrack)
            .putString(TITLE, s.title)
            .putString(ARTIST, s.artist)
            .putLong(ALBUM_ID, s.albumId ?: NO_ALBUM)
            .putBoolean(PLAYING, s.isPlaying)
            .apply()
    }

    private companion object {
        const val HAS_TRACK = "has_track"
        const val TITLE = "title"
        const val ARTIST = "artist"
        const val ALBUM_ID = "album_id"
        const val PLAYING = "playing"
        const val NO_ALBUM = -1L
    }
}
