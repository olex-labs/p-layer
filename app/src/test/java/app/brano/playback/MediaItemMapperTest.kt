package app.brano.playback

import android.net.Uri
import app.brano.data.library.t
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaItemMapperTest {
    private val track = t(
        id = 42, title = "Volare", artist = "Domenico Modugno", album = "Nel blu", albumId = 7,
        contentUri = "content://media/external/audio/media/42",
    )

    @Test
    fun idRoundTrips() {
        assertThat(MediaItemMapper.idOf(MediaItemMapper.toMediaItem(track))).isEqualTo(42L)
    }

    @Test
    fun mediaIdAndUri() {
        val item = MediaItemMapper.toMediaItem(track)
        assertThat(item.mediaId).isEqualTo("42")
        assertThat(item.localConfiguration!!.uri)
            .isEqualTo(Uri.parse("content://media/external/audio/media/42"))
    }

    @Test
    fun metadata() {
        val md = MediaItemMapper.toMediaItem(track).mediaMetadata
        assertThat(md.title.toString()).isEqualTo("Volare")
        assertThat(md.artist.toString()).isEqualTo("Domenico Modugno")
        assertThat(md.albumTitle.toString()).isEqualTo("Nel blu")
        assertThat(md.artworkUri).isEqualTo(Uri.parse("content://media/external/audio/albumart/7"))
    }

    @Test
    fun missingArtistAndAlbumStayNull() {
        val md = MediaItemMapper.toMediaItem(t(id = 3, artist = null, album = null)).mediaMetadata
        assertThat(md.artist).isNull()
        assertThat(md.albumTitle).isNull()
    }

    @Test
    fun idOfNonNumericMediaIdIsMinusOne() {
        val item = androidx.media3.common.MediaItem.Builder().setMediaId("x").build()
        assertThat(MediaItemMapper.idOf(item)).isEqualTo(-1L)
    }
}
