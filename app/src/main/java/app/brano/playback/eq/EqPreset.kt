package app.brano.playback.eq

/**
 * Equalizer presets as gains in dB at [EqCurve.REFERENCE_HZ] (60, 230, 910, 3600, 14000 Hz).
 * [OFF] turns the equalizer off. Names shown to the user come from string resources.
 */
enum class EqPreset(val db: FloatArray?) {
    OFF(null),
    BASS(floatArrayOf(6f, 4f, 0f, 0f, 1f)),
    ROCK(floatArrayOf(4f, 2f, -1f, 2f, 4f)),
    POP(floatArrayOf(-1f, 2f, 3f, 2f, -1f)),
    JAZZ(floatArrayOf(3f, 1f, 1f, 2f, 2f)),
    CLASSICAL(floatArrayOf(4f, 2f, 0f, 2f, 3f)),
    VOCAL(floatArrayOf(-2f, 0f, 3f, 3f, 0f)),
    ELECTRONIC(floatArrayOf(5f, 2f, 0f, 2f, 4f)),
    ACOUSTIC(floatArrayOf(2f, 1f, 2f, 3f, 2f)),
    NIGHT(floatArrayOf(-3f, -1f, 0f, -1f, -3f)),
    ;

    companion object {
        fun fromName(name: String?): EqPreset = entries.firstOrNull { it.name == name } ?: OFF
    }
}