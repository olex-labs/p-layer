package app.brano.data.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class TrackSort { TITLE, ARTIST, ALBUM, DATE_ADDED, DURATION }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = true,
    val minDurationMs: Long = 30_000,
    val trackSort: TrackSort = TrackSort.TITLE,
    val spinDisc: Boolean = true,
)
