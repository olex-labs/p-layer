package app.brano.domain.m3u

import app.brano.data.library.Track

object M3uWriter {
    fun write(tracks: List<Track>): String = buildString {
        append("#EXTM3U\n")
        for (t in tracks) {
            val label = if (t.artist.isNullOrBlank()) t.title else "${t.artist} - ${t.title}"
            append("#EXTINF:").append(t.durationMs / 1000).append(',').append(label).append('\n')
            append(t.path).append('\n')
        }
    }
}
