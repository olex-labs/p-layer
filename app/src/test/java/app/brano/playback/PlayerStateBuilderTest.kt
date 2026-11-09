package app.brano.playback

import androidx.media3.common.MediaItem
import app.brano.data.library.Track
import app.brano.data.library.t
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerStateBuilderTest {
    private val a = t(1, title = "A")
    private val b = t(2, title = "B")
    private val c = t(3, title = "C")

    private fun lookupOf(vararg tracks: Track): (Long) -> Track? = tracks.associateBy { it.id }::get

    /** An item from outside the app's mapper: no metadata extras, so it cannot be shown. */
    private fun foreign(id: Long) = MediaItem.Builder().setMediaId(id.toString()).build()

    @Test
    fun allKnownItemsMapOneToOne() {
        val s = PlayerStateBuilder.build(listOf(a, b, c).map(MediaItemMapper::toMediaItem), 1, lookupOf(a, b, c))
        assertThat(s.queue).containsExactly(a, b, c).inOrder()
        assertThat(s.timelineIndices).containsExactly(0, 1, 2).inOrder()
        assertThat(s.current).isEqualTo(b)
        assertThat(s.index).isEqualTo(1)
    }

    @Test
    fun libraryDataWinsOverTheItemMetadata() {
        val renamed = b.copy(title = "B (remaster)")
        val s = PlayerStateBuilder.build(listOf(MediaItemMapper.toMediaItem(b)), 0, lookupOf(renamed))
        assertThat(s.current).isEqualTo(renamed)
    }

    @Test
    fun currentTrackMissingFromTheLibraryFallsBackToItsMetadata() {
        // Deleted, excluded or filtered out while playing: still shown as long as the player holds it.
        val s = PlayerStateBuilder.build(listOf(a, b).map(MediaItemMapper::toMediaItem), 1, lookupOf(a))
        assertThat(s.current?.id).isEqualTo(2L)
        assertThat(s.current?.title).isEqualTo("B")
        assertThat(s.queue.map { it.id }).containsExactly(1L, 2L).inOrder()
        assertThat(s.index).isEqualTo(1)
    }

    @Test
    fun unknownItemsAreSkippedAndIndicesMapAroundTheGaps() {
        val items = listOf(MediaItemMapper.toMediaItem(a), foreign(99), MediaItemMapper.toMediaItem(c))
        val s = PlayerStateBuilder.build(items, 2, lookupOf(a, c))
        assertThat(s.queue).containsExactly(a, c).inOrder()
        assertThat(s.timelineIndices).containsExactly(0, 2).inOrder()
        assertThat(s.current).isEqualTo(c)
        assertThat(s.index).isEqualTo(1)
    }

    @Test
    fun unknownCurrentItemHasNoTrackAndNoIndex() {
        val items = listOf(MediaItemMapper.toMediaItem(a), foreign(99))
        val s = PlayerStateBuilder.build(items, 1, lookupOf(a))
        assertThat(s.current).isNull()
        assertThat(s.index).isEqualTo(-1)
    }

    @Test
    fun emptyPlayer() {
        val s = PlayerStateBuilder.build(emptyList(), -1, lookupOf())
        assertThat(s.queue).isEmpty()
        assertThat(s.current).isNull()
        assertThat(s.index).isEqualTo(-1)
    }

    @Test
    fun queueIsInPlayOrderBecauseShuffleReordersTheItems() {
        // Shuffle is applied by physically reordering the player's items (QueueShuffle), so the
        // timeline order is the play order and the queue shows what plays next.
        val order = QueueShuffle.shuffledFrom(listOf(a, b, c), first = 2, random = kotlin.random.Random(7))
        val s = PlayerStateBuilder.build(order.map(MediaItemMapper::toMediaItem), 0, lookupOf(a, b, c))
        assertThat(s.queue).isEqualTo(order)
        assertThat(s.current).isEqualTo(c)
        assertThat(s.index).isEqualTo(0)
    }
}
