package app.brano.data.settings

import app.brano.playback.eq.EqPreset

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class TrackSort { TITLE, ARTIST, ALBUM, DATE_ADDED, DURATION }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = true,
    val minDurationMs: Long = 30_000,
    val trackSort: TrackSort = TrackSort.TITLE,
    val spinDisc: Boolean = true,
    /** LRCLIB lookup for tracks without local lyrics; the only use of the network. */
    val onlineLyrics: Boolean = false,
    /** Equalizer preset; OFF turns the effect off. */
    val eqPreset: EqPreset = EqPreset.OFF,
    /** Same loudness across songs from their ReplayGain tags. */
    val replayGain: Boolean = true,
)
