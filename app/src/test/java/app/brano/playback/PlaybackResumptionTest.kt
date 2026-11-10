package app.brano.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.brano.data.library.t
import app.brano.data.settings.onlyExisting
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackResumptionTest {
    private fun item(id: Int) = MediaItem.Builder().setMediaId(id.toString()).build()

    @Test fun restoredQueue_isHandedBackWithIndexAndPosition() {
        val items = listOf(item(3), item(1), item(2))
        val player = mockk<Player> {
            every { mediaItemCount } returns 3
            every { getMediaItemAt(any()) } answers { items[firstArg()] }
            every { currentMediaItemIndex } returns 1
            every { currentPosition } returns 26_000L
        }
        val r = PlaybackResumption.of(player)
        assertThat(r.mediaItems.map { it.mediaId }).containsExactly("3", "1", "2").inOrder()
        assertThat(r.startIndex).isEqualTo(1)
        assertThat(r.startPositionMs).isEqualTo(26_000L)
    }

    @Test fun nothingRestored_isAnEmptyList() {
        val player = mockk<Player> { every { mediaItemCount } returns 0 }
        assertThat(PlaybackResumption.of(player).mediaItems).isEmpty()
    }

    @Test fun deletedFiles_areNotRestored() {
        // The library snapshot can still list files deleted while the app was closed.
        val tracks = listOf(t(1), t(2)).associateBy { it.id }
        val lookup = onlyExisting(tracks::get) { path -> path != t(2).path }
        assertThat(lookup(1)).isEqualTo(t(1))
        assertThat(lookup(2)).isNull()
        assertThat(lookup(9)).isNull()
    }
}
