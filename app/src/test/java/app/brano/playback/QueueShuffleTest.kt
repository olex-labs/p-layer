package app.brano.playback

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Test

class QueueShuffleTest {
    private val items = (1..20).map { "t$it" }

    @Test
    fun shuffledFromPutsTheChosenItemFirstAndKeepsEveryItem() {
        val out = QueueShuffle.shuffledFrom(items, first = 5, random = Random(1))
        assertThat(out.first()).isEqualTo("t6")
        assertThat(out).containsExactlyElementsIn(items)
        assertThat(out.drop(1)).isNotEqualTo(items - "t6") // actually shuffled
    }

    @Test
    fun randomStartDiffersBetweenRuns() {
        // "Casuale" picks the first track at random: different seeds give different openers.
        val firsts = (1..10).map { seed ->
            val r = Random(seed)
            QueueShuffle.shuffledFrom(items, r.nextInt(items.size), r).first()
        }.toSet()
        assertThat(firsts.size).isGreaterThan(1)
    }

    @Test
    fun restoreReturnsTheOriginalOrderAndTheCurrentItemsPlaceInIt() {
        val shuffled = QueueShuffle.shuffledFrom(items, first = 7, random = Random(3))
        val (order, index) = QueueShuffle.restore(shuffled, currentIndex = 4, original = items)
        assertThat(order).isEqualTo(items)
        assertThat(order[index]).isEqualTo(shuffled[4])
    }

    @Test
    fun restoreDropsRemovedItemsAndAppendsAddedOnes() {
        val current = listOf("c", "x", "a", "y") // "b" removed, "x" and "y" added while shuffled
        val (order, index) = QueueShuffle.restore(current, currentIndex = 0, original = listOf("a", "b", "c"))
        assertThat(order).containsExactly("a", "c", "x", "y").inOrder()
        assertThat(index).isEqualTo(1)
    }

    @Test
    fun restoreKeepsDuplicatesAndTracksTheRightOccurrence() {
        val current = listOf("a", "b", "a")
        val (order, index) = QueueShuffle.restore(current, currentIndex = 2, original = listOf("a", "a", "b"))
        assertThat(order).containsExactly("a", "a", "b").inOrder()
        assertThat(index).isEqualTo(1) // the second "a"
    }
}
