package app.brano.playback

/**
 * Counts a play once per playback, at 50% of the duration or 240 s, whichever comes first.
 *
 * Not thread-safe: use from a single thread only.
 */
class PlayCountTracker(private val onCounted: (Long) -> Unit) {
    private var currentId: Long? = null
    private var counted = false

    fun onTrackStarted(id: Long) {
        currentId = id
        counted = false
    }

    fun onProgress(positionMs: Long, durationMs: Long) {
        val id = currentId ?: return
        if (counted || durationMs <= 0) return
        val threshold = minOf(durationMs / 2, MAX_THRESHOLD_MS)
        if (positionMs >= threshold) {
            counted = true
            onCounted(id)
        }
    }

    private companion object {
        const val MAX_THRESHOLD_MS = 240_000L
    }
}
