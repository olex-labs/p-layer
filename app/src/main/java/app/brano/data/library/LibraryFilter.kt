package app.brano.data.library

object LibraryFilter {
    /**
     * Drops tracks shorter than [minDurationMs], tracks outside [musicFolders] (when any are chosen;
     * subfolders count, "/Music2" is not inside "/Music") and tracks whose path is in [hiddenPaths].
     */
    fun apply(
        tracks: List<Track>,
        minDurationMs: Long,
        musicFolders: Set<String>,
        hiddenPaths: Set<String>,
    ): List<Track> {
        val folders = musicFolders.map { it.trimEnd('/') }
        return tracks.filter { track ->
            track.durationMs >= minDurationMs &&
                (folders.isEmpty() || folders.any { track.folder == it || track.folder.startsWith("$it/") }) &&
                track.path !in hiddenPaths
        }
    }
}
