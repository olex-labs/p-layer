package app.brano.widget

import app.brano.data.library.t
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WidgetStateTest {
    @Test fun of_track_copiesTitleArtistAlbum() {
        val track = t(1)
        val s = WidgetState.of(track, isPlaying = true)
        assertThat(s.title).isEqualTo(track.title)
        assertThat(s.artist).isEqualTo(track.artist.orEmpty())
        assertThat(s.albumId).isEqualTo(track.albumId)
        assertThat(s.isPlaying).isTrue()
        assertThat(s.hasTrack).isTrue()
    }

    @Test fun of_null_isEmpty() {
        assertThat(WidgetState.of(null, isPlaying = false)).isEqualTo(WidgetState.EMPTY)
        assertThat(WidgetState.EMPTY.hasTrack).isFalse()
        assertThat(WidgetState.EMPTY.title).isEmpty()
    }
}
