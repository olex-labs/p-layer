package app.brano.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.brano.data.library.t
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class QueueStateStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.store() = QueueStateStore(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "q.preferences_pb") },
    )

    private fun state(ids: List<Long>, index: Int, pos: Long = 5_000) =
        QueueState(ids, index, pos, shuffle = true, repeatMode = 2)

    private val lib = (1L..3L).associateWith { t(id = it) }

    @Test fun queue_loadEmpty_returnsNull() = runTest {
        assertThat(store().load()).isNull()
    }

    @Test fun queue_saveLoadRoundTrip() = runTest {
        val store = store()
        val s = state(listOf(10, 20, 30), 1, 12_345)
        store.save(s)
        assertThat(store.load()).isEqualTo(s)
    }

    @Test fun queue_saveEmptyIds_loadsEmptyList() = runTest {
        val store = store()
        store.save(state(emptyList(), 0))
        assertThat(store.load()?.ids).isEmpty()
    }

    @Test fun restore_dropsMissing_keepsCurrent() {
        val r = state(listOf(1, 2, 3), 2).restore { id -> lib[id].takeIf { id != 1L } }!!
        assertThat(r.tracks.map { it.id }).containsExactly(2L, 3L).inOrder()
        assertThat(r.index).isEqualTo(1)
        assertThat(r.positionMs).isEqualTo(5_000)
    }

    @Test fun restore_currentMissing_movesToNext_positionZero() {
        val r = state(listOf(1, 2, 3), 1).restore { id -> lib[id].takeIf { id != 2L } }!!
        assertThat(r.tracks.map { it.id }).containsExactly(1L, 3L).inOrder()
        assertThat(r.tracks[r.index].id).isEqualTo(3L)
        assertThat(r.positionMs).isEqualTo(0)
    }

    @Test fun restore_currentMissing_noneAfter_usesLastBefore() {
        val r = state(listOf(1, 2, 3), 2).restore { id -> lib[id].takeIf { id != 3L } }!!
        assertThat(r.tracks[r.index].id).isEqualTo(2L)
        assertThat(r.positionMs).isEqualTo(0)
    }

    @Test fun restore_allMissing_returnsNull() {
        assertThat(state(listOf(1, 2), 0).restore { null }).isNull()
        assertThat(state(emptyList(), 0).restore { lib[it] }).isNull()
    }

    @Test fun queue_unshuffledOrder_roundTrips() = runTest {
        val store = store()
        store.save(state(listOf(3, 1, 2), 0).copy(unshuffledIds = listOf(1, 2, 3)))
        assertThat(store.load()!!.unshuffledIds).containsExactly(1L, 2L, 3L).inOrder()
    }

    @Test fun queue_withoutShuffleOrder_loadsNull() = runTest {
        val store = store()
        store.save(state(listOf(1, 2), 0).copy(unshuffledIds = null))
        assertThat(store.load()!!.unshuffledIds).isNull()
    }
}
