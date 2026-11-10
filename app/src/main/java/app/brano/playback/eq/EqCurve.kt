package app.brano.playback.eq

import kotlin.math.ln
import kotlin.math.roundToInt

/** Maps a preset onto whatever bands the phone's equalizer has. */
object EqCurve {
    val REFERENCE_HZ = intArrayOf(60, 230, 910, 3600, 14000)

    /**
     * Band levels in millibels for bands centred at [centersMilliHz] (as Equalizer.getCenterFreq
     * reports them), interpolated on log-frequency between reference points, held at the edges
     * outside 60 Hz - 14 kHz, and clamped to the device range.
     */
    fun bandLevels(preset: EqPreset, centersMilliHz: IntArray, minMb: Short, maxMb: Short): ShortArray {
        val db = preset.db ?: return ShortArray(centersMilliHz.size)
        return ShortArray(centersMilliHz.size) { i ->
            val mb = (dbAt(db, centersMilliHz[i] / 1000.0) * 100).roundToInt()
            mb.coerceIn(minMb.toInt(), maxMb.toInt()).toShort()
        }
    }

    private fun dbAt(db: FloatArray, hz: Double): Double {
        val ref = REFERENCE_HZ
        if (hz <= ref.first()) return db.first().toDouble()
        if (hz >= ref.last()) return db.last().toDouble()
        val i = ref.indexOfLast { it <= hz }
        if (ref[i].toDouble() == hz) return db[i].toDouble()
        val t = (ln(hz) - ln(ref[i].toDouble())) / (ln(ref[i + 1].toDouble()) - ln(ref[i].toDouble()))
        return db[i] + t * (db[i + 1] - db[i])
    }
}