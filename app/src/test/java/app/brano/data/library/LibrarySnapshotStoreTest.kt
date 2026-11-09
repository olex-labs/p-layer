package app.brano.data.library

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LibrarySnapshotStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private class RecordingLog : SnapshotLog {
        val events = ArrayList<String>()
        override fun loaded(count: Int) { events += "loaded $count" }
        override fun failed(error: Throwable) { events += "failed" }
    }

    @Test fun roundTripReportsTheLoad() = runTest {
        val log = RecordingLog()
        val store = LibrarySnapshotStore(tmp.root.resolve("library.json"), Dispatchers.Unconfined, log)
        val tracks = listOf(t(1, title = "Volare", artist = null), t(2, album = null, year = 1958))
        store.save(tracks)
        assertThat(store.load()).isEqualTo(tracks)
        assertThat(log.events).containsExactly("loaded 2")
    }

    @Test fun missingFileIsSilentlyNull() = runTest {
        val log = RecordingLog()
        assertThat(LibrarySnapshotStore(tmp.root.resolve("none.json"), Dispatchers.Unconfined, log).load()).isNull()
        assertThat(log.events).isEmpty()
    }

    @Test fun unreadableFileIsNullAndReported() = runTest {
        val log = RecordingLog()
        val file = tmp.newFile("bad.json").apply { writeText("{not json") }
        assertThat(LibrarySnapshotStore(file, Dispatchers.Unconfined, log).load()).isNull()
        assertThat(log.events).containsExactly("failed")
    }
}
