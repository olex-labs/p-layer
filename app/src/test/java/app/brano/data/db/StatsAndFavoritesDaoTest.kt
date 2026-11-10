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
class StatsAndFavoritesDaoTest {
    private lateinit var db: BranoDatabase
    private val a = t(1).ref()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BranoDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun favorites_toggleTwiceRemoves() = runTest {
        val fav = db.favoritesDao()
        assertThat(fav.toggle(a, 1)).isTrue()
        assertThat(fav.observe().first().single().addedAt).isEqualTo(1)
        assertThat(fav.toggle(a, 2)).isFalse()
        assertThat(fav.observe().first()).isEmpty()
    }

    @Test fun favorites_toggleMatchesByPathAfterAnIdChange() = runTest {
        // MediaStore re-indexed the file: same path, new id. Un-favoriting must remove the old row.
        val fav = db.favoritesDao()
        assertThat(fav.toggle(a, 1)).isTrue()
        val moved = a.copy(mediaStoreId = 99)
        assertThat(fav.toggle(moved, 2)).isFalse()
        assertThat(fav.observe().first()).isEmpty()
    }

    @Test fun stats_incrementAccumulates() = runTest {
        val stats = db.playStatsDao()
        stats.increment(a, 10)
        stats.increment(a, 20)
        val s = stats.observeAll().first().single()
        assertThat(s.playCount).isEqualTo(2)
        assertThat(s.lastPlayedAt).isEqualTo(20)
    }

}
