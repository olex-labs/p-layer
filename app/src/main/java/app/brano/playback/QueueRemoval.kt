package app.brano.playback

internal object QueueRemoval {
    /** Queue positions holding any of [removed], highest first so they can be removed one by one. */
    fun indicesToRemove(queueIds: List<Long>, removed: Set<Long>): List<Int> =
        queueIds.indices.filter { queueIds[it] in removed }.sortedDescending()
}
