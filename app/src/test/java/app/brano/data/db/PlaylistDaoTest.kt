package app.brano.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.brano.data.library.ref
import app.brano.data.library.t
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistDaoTest {
    private lateinit var db: BranoDatabase
    private lateinit var dao: PlaylistDao
    private val a = t(1).ref()
    private val b = t(2).ref()
    private val c = t(3).ref()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BranoDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.playlistDao()
    }

    @After fun tearDown() = db.close()

    @Test fun move_keepsPositionsContiguous() = runTest {
        val id = dao.create("p")
        dao.addTracks(id, listOf(a, b, c))
        dao.move(id, 0, 2)
        val items = dao.observeItems(id).first()
        assertThat(items.map { it.mediaStoreId }).containsExactly(b.mediaStoreId, c.mediaStoreId, a.mediaStoreId).inOrder()
        assertThat(items.map { it.position }).containsExactly(0, 1, 2).inOrder()
    }

    @Test fun duplicateTrack_removeOneKeepsOther() = runTest {
        val id = dao.create("p")
        dao.addTracks(id, listOf(a, a, b))
        dao.removeAt(id, 0)
        val items = dao.observeItems(id).first()
        assertThat(items.map { it.mediaStoreId }).containsExactly(a.mediaStoreId, b.mediaStoreId).inOrder()
        assertThat(items.map { it.position }).containsExactly(0, 1).inOrder()
    }

    @Test fun addTracks_appendsToTail() = runTest {
        val id = dao.create("p")
        dao.addTracks(id, listOf(a))
        dao.addTracks(id, listOf(b, c))
        assertThat(dao.observeItems(id).first().map { it.mediaStoreId })
            .containsExactly(a.mediaStoreId, b.mediaStoreId, c.mediaStoreId).inOrder()
    }

    @Test fun deletePlaylist_cascadesItems() = runTest {
        val id = dao.create("p")
        dao.addTracks(id, listOf(a, b))
        dao.delete(id)
        assertThat(dao.observeItems(id).first()).isEmpty()
        assertThat(dao.observeAll().first()).isEmpty()
    }

    @Test fun observeAll_summaryCountsAndSortsByName() = runTest {
        val z = dao.create("zeta")
        dao.create("Alpha")
        dao.addTracks(z, listOf(a, b))
        val all = dao.observeAll().first()
        assertThat(all.map { it.name }).containsExactly("Alpha", "zeta").inOrder()
        assertThat(all.map { it.trackCount }).containsExactly(0, 2).inOrder()
    }

    @Test fun rename_updatesName() = runTest {
        val id = dao.create("old")
        dao.rename(id, "new")
        assertThat(dao.observeAll().first().single().name).isEqualTo("new")
    }

    @Test fun updateIds_rewritesIdByPath() = runTest {
        val id = dao.create("p")
        dao.addTracks(id, listOf(a))
        dao.updateIds(mapOf(a.path to 99L))
        assertThat(dao.observeItems(id).first().single().mediaStoreId).isEqualTo(99L)
    }
}
