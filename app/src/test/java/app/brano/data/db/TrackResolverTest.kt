package app.brano.data.db

import app.brano.data.library.TrackRef
import app.brano.data.library.t
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackResolverTest {
    @Test fun resolver_fallsBackToPath_andReportsIdChange() {
        val moved = t(id = 9, path = "/m/a.mp3")
        val (items, changes) = TrackResolver.resolve(
            listOf(TrackRef(5, "/m/a.mp3")), { null }, { if (it == "/m/a.mp3") moved else null },
        )
        assertThat(items.single().track).isEqualTo(moved)
        assertThat(changes).containsExactly("/m/a.mp3", 9L)
    }

    @Test fun resolver_foundById_noChanges() {
        val tr = t(id = 5)
        val (items, changes) = TrackResolver.resolve(listOf(TrackRef(5, tr.path)), { tr }, { null })
        assertThat(items.single().track).isEqualTo(tr)
        assertThat(changes).isEmpty()
    }

    @Test fun resolver_missingTrack_isUnavailable() {
        val (items, changes) = TrackResolver.resolve(listOf(TrackRef(5, "/x.mp3")), { null }, { null })
        assertThat(items.single().track).isNull()
        assertThat(changes).isEmpty()
    }
}
