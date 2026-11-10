package app.brano.widget

import androidx.test.core.app.ApplicationProvider
import app.brano.data.library.t
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetStateStoreTest {
    private val store = WidgetStateStore(ApplicationProvider.getApplicationContext())

    @Test fun store_roundTrip() {
        val s = WidgetState.of(t(3), isPlaying = false)
        store.save(s)
        assertThat(store.load()).isEqualTo(s)
    }

    @Test fun store_nothingSaved_returnsEmpty() {
        assertThat(store.load()).isEqualTo(WidgetState.EMPTY)
    }

    @Test fun store_albumIdNull_roundTrips() {
        val s = WidgetState("Titolo", "Artista", albumId = null, isPlaying = true, hasTrack = true)
        store.save(s)
        assertThat(store.load()).isEqualTo(s)
    }
}
