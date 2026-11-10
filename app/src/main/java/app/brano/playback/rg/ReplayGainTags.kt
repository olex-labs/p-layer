@file:Suppress("DEPRECATION") // flac.VorbisComment: older extractors still emit it

package app.brano.playback.rg

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.TextInformationFrame

/** ReplayGain values of one file, read from the metadata Media3 already parsed (ID3 TXXX, Vorbis comments). */
data class ReplayGainTags(
    val trackGainDb: Float?,
    val albumGainDb: Float?,
    val trackPeak: Float?,
    val albumPeak: Float?,
) {
    companion object {
        val NONE = ReplayGainTags(null, null, null, null)

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        fun from(metadata: Metadata?): ReplayGainTags {
            if (metadata == null) return NONE
            val tags = HashMap<String, String>()
            for (i in 0 until metadata.length()) {
                when (val e = metadata.get(i)) {
                    is TextInformationFrame -> if (e.id == "TXXX" && e.description != null) {
                        e.values.firstOrNull()?.let { tags[e.description!!.uppercase()] = it }
                    }
                    is androidx.media3.extractor.metadata.vorbis.VorbisComment -> tags[e.key.uppercase()] = e.value
                    is androidx.media3.extractor.metadata.flac.VorbisComment -> tags[e.key.uppercase()] = e.value
                }
            }
            if (tags.isEmpty()) return NONE
            return ReplayGainTags(
                trackGainDb = parseDb(tags["REPLAYGAIN_TRACK_GAIN"]),
                albumGainDb = parseDb(tags["REPLAYGAIN_ALBUM_GAIN"]),
                trackPeak = parseDb(tags["REPLAYGAIN_TRACK_PEAK"]),
                albumPeak = parseDb(tags["REPLAYGAIN_ALBUM_PEAK"]),
            )
        }

        /** "-6.50 dB", "+1,2 dB", "0.98" to a number; anything else is null. */
        fun parseDb(text: String?): Float? = text?.trim()
            ?.removeSuffix("dB")?.removeSuffix("DB")?.removeSuffix("db")?.trim()
            ?.replace(',', '.')
            ?.removePrefix("+")
            ?.toFloatOrNull()
            ?.takeIf { it.isFinite() } // NaN would mute the song, Infinity zero it
    }
}