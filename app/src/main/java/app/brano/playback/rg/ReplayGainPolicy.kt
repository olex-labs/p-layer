package app.brano.playback.rg

import kotlin.math.pow

/** Album or track gain, limits and peak protection. */
object ReplayGainPolicy {
    private const val MIN_DB = -24f
    private const val MAX_DB = 12f

    /** Album gain only when the whole queue is one album (at least two songs) played in order. */
    fun useAlbum(albumIds: List<Long>, shuffle: Boolean): Boolean =
        !shuffle && albumIds.size >= 2 && albumIds.all { it == albumIds.first() }

    /** Linear multiplier for the samples; 1 when the file has no ReplayGain tags. */
    fun linearGain(tags: ReplayGainTags, album: Boolean): Float {
        val db = (if (album) tags.albumGainDb ?: tags.trackGainDb else tags.trackGainDb ?: tags.albumGainDb) ?: return 1f
        val clamped = db.coerceIn(MIN_DB, MAX_DB)
        var gain = 10.0.pow(clamped / 20.0).toFloat()
        val peak = if (album) tags.albumPeak ?: tags.trackPeak else tags.trackPeak ?: tags.albumPeak
        if (peak != null && peak > 0f) gain = minOf(gain, 1f / peak)
        return gain
    }
}