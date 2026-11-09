package app.brano.playback

import kotlin.random.Random

/**
 * Shuffle is applied by physically reordering the queue (as Musicolet does), so the queue view,
 * drag and drop, "save as playlist" and the saved resume state all match what is actually heard.
 */
internal object QueueShuffle {
    /** [items] with the one at [first] in front and all the others after it in random order. */
    fun <T> shuffledFrom(items: List<T>, first: Int, random: Random): List<T> {
        if (first !in items.indices) return items.shuffled(random)
        val rest = items.toMutableList().apply { removeAt(first) }.shuffled(random)
        return listOf(items[first]) + rest
    }

    /**
     * Un-shuffle: [original] restricted to what is still in [current] (duplicates counted), followed by
     * the items added since, in their current order. Returns that order and the position in it of the
     * item at [currentIndex] (the same occurrence, for duplicates).
     */
    fun <T> restore(current: List<T>, currentIndex: Int, original: List<T>): Pair<List<T>, Int> {
        val left = HashMap<T, Int>()
        current.forEach { left.merge(it, 1, Int::plus) }
        val order = ArrayList<T>(current.size)
        for (x in original) {
            val n = left[x] ?: 0
            if (n > 0) {
                order += x
                left[x] = n - 1
            }
        }
        for (x in current) {
            val n = left[x] ?: 0
            if (n > 0) {
                order += x
                left[x] = n - 1
            }
        }
        val target = current.getOrNull(currentIndex) ?: return order to -1
        val occurrence = current.subList(0, currentIndex).count { it == target }
        val positions = order.indices.filter { order[it] == target }
        return order to (positions.getOrNull(occurrence) ?: positions.lastOrNull() ?: -1)
    }
}
