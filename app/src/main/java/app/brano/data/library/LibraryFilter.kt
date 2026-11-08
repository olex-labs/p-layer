package app.brano.data.library

object LibraryFilter {
    fun apply(tracks: List<Track>, minDurationMs: Long, excludedFolders: Set<String>): List<Track> {
        val excluded = excludedFolders.map { it.trimEnd('/') }
        return tracks.filter { track ->
            track.durationMs >= minDurationMs &&
                excluded.none { track.folder == it || track.folder.startsWith("$it/") }
        }
    }
}
