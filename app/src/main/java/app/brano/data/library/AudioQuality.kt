package app.brano.data.library

private val codecByMime = mapOf(
    "audio/mpeg" to "MP3", "audio/mp3" to "MP3",
    "audio/mp4" to "AAC", "audio/aac" to "AAC", "audio/x-m4a" to "AAC", "audio/mp4a-latm" to "AAC",
    "audio/flac" to "FLAC", "audio/x-flac" to "FLAC",
    "audio/ogg" to "OGG", "audio/vorbis" to "OGG", "application/ogg" to "OGG",
    "audio/opus" to "OPUS",
    "audio/wav" to "WAV", "audio/x-wav" to "WAV", "audio/wave" to "WAV",
    "audio/x-ms-wma" to "WMA",
    "audio/alac" to "ALAC",
)

private val codecByExtension = mapOf(
    "mp3" to "MP3", "m4a" to "AAC", "aac" to "AAC", "flac" to "FLAC", "ogg" to "OGG",
    "opus" to "OPUS", "wav" to "WAV", "wma" to "WMA",
)

private val losslessCodecs = setOf("FLAC", "WAV", "ALAC")

private fun codec(mimeType: String?, path: String): String? =
    mimeType?.lowercase()?.let(codecByMime::get)
        ?: codecByExtension[path.substringAfterLast('.', "").lowercase()]

/**
 * Short file-quality label: "MP3 · 320 kbps", or just the codec for lossless files and unknown
 * bitrates ("FLAC"). Null when the format is not recognised.
 */
fun qualityLabel(mimeType: String?, bitrateKbps: Int, path: String): String? {
    val codec = codec(mimeType, path) ?: return null
    return if (codec in losslessCodecs || bitrateKbps <= 0) codec else "$codec · $bitrateKbps kbps"
}

fun isLossless(mimeType: String?, path: String): Boolean = codec(mimeType, path) in losslessCodecs

val Track.qualityLabel: String? get() = qualityLabel(mimeType, bitrateKbps, path)
val Track.isLossless: Boolean get() = isLossless(mimeType, path)
