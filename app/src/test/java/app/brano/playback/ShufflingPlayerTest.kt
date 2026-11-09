package app.brano.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlin.random.Random
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The shuffle logic lives in the service's player, so every controller (app, headset, lockscreen) gets it. */
@RunWith(RobolectricTestRunner::class)
class ShufflingPlayerTest {
    private var items = mutableListOf<MediaItem>()
    private var current = 0
    private var shuffle = false
    private var newQueues = 0
    private lateinit var inner: Player
    private lateinit var player: ShufflingPlayer

    private fun item(id: Int) = MediaItem.Builder().setMediaId(id.toString()).build()
    private fun ids() = items.map { it.mediaId }
    private fun currentId() = items[current].mediaId

    @Before fun setUp() {
        inner = mockk(relaxed = true) {
            every { mediaItemCount } answers { items.size }
            every { getMediaItemAt(any()) } answers { items[firstArg()] }
            every { currentMediaItemIndex } answers { current }
            every { shuffleModeEnabled } answers { shuffle }
            every { setShuffleModeEnabled(any()) } answers { shuffle = firstArg() }
            every { removeMediaItems(any(), any()) } answers {
                val from = firstArg<Int>()
                val to = secondArg<Int>()
                repeat(to - from) { items.removeAt(from) }
                if (current >= to) current -= to - from
            }
            every { addMediaItems(any<Int>(), any()) } answers {
                val at = firstArg<Int>()
                val added = secondArg<List<MediaItem>>()
                items.addAll(at, added)
                if (current >= at) current += added.size
            }
            every { addMediaItems(any<List<MediaItem>>()) } answers { items.addAll(firstArg<List<MediaItem>>()) }
            every { setMediaItems(any(), any<Int>(), any()) } answers {
                items = firstArg<List<MediaItem>>().toMutableList()
                current = secondArg()
            }
        }
        player = ShufflingPlayer(inner, Random(7)) { newQueues++ }
        items = (1..8).map(::item).toMutableList()
        current = 2
    }

    @Test fun shuffleOn_fromAnyController_reordersAroundTheCurrentTrack() {
        player.setShuffleModeEnabled(true)

        assertThat(shuffle).isTrue()
        assertThat(currentId()).isEqualTo("3")
        assertThat(current).isEqualTo(0)
        assertThat(ids()).containsExactlyElementsIn((1..8).map(Int::toString))
        assertThat(ids()).isNotEqualTo((1..8).map(Int::toString))
        assertThat(player.unshuffledIds).isEqualTo((1..8).map(Int::toString))
    }

    @Test fun shuffleOff_restoresTheOriginalOrderKeepingTheCurrentTrack() {
        player.setShuffleModeEnabled(true)
        current = 4
        val playing = currentId()

        player.setShuffleModeEnabled(false)

        assertThat(ids()).isEqualTo((1..8).map(Int::toString))
        assertThat(currentId()).isEqualTo(playing)
        assertThat(player.unshuffledIds).isNull()
    }

    @Test fun sameMode_isANoOp() {
        player.setShuffleModeEnabled(false)
        assertThat(ids()).isEqualTo((1..8).map(Int::toString))
    }

    @Test fun savedOrder_survivesARestart() {
        // After a process restart the queue comes back shuffled, with the saved original order.
        items = listOf(5, 1, 8, 2, 7, 3, 6, 4).map(::item).toMutableList()
        current = 0
        player.restoreShuffle(on = true, unshuffled = (1..8).map(Int::toString))

        player.setShuffleModeEnabled(false)

        assertThat(ids()).isEqualTo((1..8).map(Int::toString))
        assertThat(currentId()).isEqualTo("5")
    }

    @Test fun newQueueWhileShuffled_isShuffledFromTheChosenTrack() {
        shuffle = true
        player.setMediaItems((11..16).map(::item), 3, 0L)

        assertThat(currentId()).isEqualTo("14")
        assertThat(current).isEqualTo(0)
        assertThat(player.unshuffledIds).isEqualTo((11..16).map(Int::toString))
        assertThat(newQueues).isEqualTo(1)
    }

    @Test fun newQueue_isReported_evenWithoutShuffle() {
        player.setMediaItems((11..13).map(::item), 1, 0L)

        assertThat(ids()).isEqualTo((11..13).map(Int::toString))
        assertThat(newQueues).isEqualTo(1)
    }
}
