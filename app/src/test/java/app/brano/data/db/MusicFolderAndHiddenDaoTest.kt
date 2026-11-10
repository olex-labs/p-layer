package app.brano.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
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
class MusicFolderAndHiddenDaoTest {
    private lateinit var db: BranoDatabase
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, BranoDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun musicFolders_addRemoveObserve() = runTest {
        val dao = db.musicFolderDao()
        dao.add("/sdcard/Music", "content://tree/a")
        dao.add("/sdcard/Music", "content://tree/a") // twice: still one
        dao.add("/sdcard/Podcast", "content://tree/b")
        assertThat(dao.observe().first()).containsExactly("/sdcard/Music", "/sdcard/Podcast")
        assertThat(dao.observeAll().first().map { it.treeUri }).containsExactly("content://tree/a", "content://tree/b")
        dao.remove("/sdcard/Podcast")
        assertThat(dao.observe().first()).containsExactly("/sdcard/Music")
    }

    @Test fun hidden_hideUnhideNewestFirst() = runTest {
        val dao = db.hiddenTrackDao()
        dao.hide(listOf("/m/a.mp3"), now = 1)
        dao.hide(listOf("/m/b.mp3", "/m/c.mp3"), now = 2)
        assertThat(dao.observe().first()).containsExactly("/m/a.mp3", "/m/b.mp3", "/m/c.mp3")
        assertThat(dao.observeAll().first().first().hiddenAt).isEqualTo(2)
        dao.unhide("/m/b.mp3")
        assertThat(dao.observe().first()).containsExactly("/m/a.mp3", "/m/c.mp3")
        dao.unhideAll()
        assertThat(dao.observe().first()).isEmpty()
    }

    @Test fun migration1To2_replacesExcludedFolders() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null).callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE excluded_folders (path TEXT NOT NULL, PRIMARY KEY(path))")
                        db.execSQL("INSERT INTO excluded_folders VALUES ('/sdcard/WhatsApp')")
                    }

                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        val sql = helper.writableDatabase
        MIGRATION_1_2.migrate(sql)
        val tables = sql.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        assertThat(tables).containsAtLeast("music_folders", "hidden_tracks")
        assertThat(tables).doesNotContain("excluded_folders")
        sql.execSQL("INSERT INTO music_folders VALUES ('/sdcard/Music', 'content://x')")
        sql.execSQL("INSERT INTO hidden_tracks VALUES ('/m/a.mp3', 5)")
        helper.close()
    }
}
