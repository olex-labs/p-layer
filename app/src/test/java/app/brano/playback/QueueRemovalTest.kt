package app.brano.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QueueRemovalTest {
    @Test fun removesThePlayingOneToo() {
        assertThat(QueueRemoval.indicesToRemove(listOf(1, 2, 3), setOf(2))).containsExactly(1)
    }

    @Test fun duplicates_allRemoved_descending() {
        assertThat(QueueRemoval.indicesToRemove(listOf(5, 1, 5, 2, 5), setOf(5)))
            .containsExactly(4, 2, 0).inOrder()
    }

    @Test fun unknownIds_removeNothing() {
        assertThat(QueueRemoval.indicesToRemove(listOf(1, 2), setOf(9))).isEmpty()
    }

    @Test fun severalIds_descendingSoTheyCanBeRemovedOneByOne() {
        assertThat(QueueRemoval.indicesToRemove(listOf(1, 2, 3, 4), setOf(1, 3))).containsExactly(2, 0).inOrder()
    }
}
