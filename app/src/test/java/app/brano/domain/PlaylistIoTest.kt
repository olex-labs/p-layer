package app.brano.domain

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.brano.data.db.BranoDatabase
import app.brano.data.db.PlaylistDao
import app.brano.data.library.LibraryRepository
import app.brano.data.library.Track
import app.brano.data.library.ref
import app.brano.data.library.t
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
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
class PlaylistIoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: BranoDatabase
    private lateinit var dao: PlaylistDao
    private val tracks = listOf(t(1), t(2), t(3))
    private val library = mockk<LibraryRepository>()
    private lateinit var io: PlaylistIo
    private lateinit var file: File

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, BranoDatabase::class.java).allowMainThreadQueries().build()
        dao = db.playlistDao()
        every { library.tracks } returns MutableStateFlow<List<Track>>(tracks)
        every { library.trackById(any()) } answers { tracks.firstOrNull { it.id == firstArg<Long>() } }
        every { library.trackByPath(any()) } answers { tracks.firstOrNull { it.path == firstArg<String>() } }
        io = PlaylistIo(context.contentResolver, dao, library)
        file = File.createTempFile("playlist", ".m3u")
    }

    @After fun tearDown() {
        db.close()
        file.delete()
    }

    @Test fun export_thenImport_keepsSequence() = runTest {
        val id = dao.create("src")
        dao.addTracks(id, listOf(tracks[2].ref(), tracks[0].ref(), tracks[1].ref()))

        io.export(id, Uri.fromFile(file))

        val text = file.readText()
        assertThat(text).startsWith("#EXTM3U")
        tracks.forEach { assertThat(text).contains(it.path) }

        val result = io.import(Uri.fromFile(file))
        assertThat(result.notFound).isEqualTo(0)
        val created = dao.observeAll().first().single { it.id != id }
        val ids = dao.observeItems(created.id).first().map { it.mediaStoreId }
        assertThat(ids).containsExactly(3L, 1L, 2L).inOrder()
    }

    @Test fun export_skipsItemsMissingFromLibrary() = runTest {
        val id = dao.create("src")
        dao.addTracks(id, listOf(tracks[0].ref(), t(99).ref()))
        io.export(id, Uri.fromFile(file))
        assertThat(file.readText()).doesNotContain(t(99).path)
        assertThat(file.readText()).contains(tracks[0].path)
    }

    @Test fun import_withNoMatches_createsNothing() = runTest {
        file.writeText("#EXTM3U\n/nowhere/a.mp3\n/nowhere/b.mp3\n")
        val result = io.import(Uri.fromFile(file))
        assertThat(result.matched).isEmpty()
        assertThat(result.notFound).isEqualTo(2)
        assertThat(dao.observeAll().first()).isEmpty()
    }

    @Test fun playlistName_stripsExtensionWithFallback() {
        assertThat(playlistNameFromFile("Mix estate.m3u")).isEqualTo("Mix estate")
        assertThat(playlistNameFromFile("a.b.m3u8")).isEqualTo("a.b")
        assertThat(playlistNameFromFile("senza")).isEqualTo("senza")
        assertThat(playlistNameFromFile(null)).isEqualTo("Playlist importata")
        assertThat(playlistNameFromFile(".m3u")).isEqualTo("Playlist importata")
    }

    @Test fun m3uDir_fromPrimaryDocumentUri() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3AMusic%2FMixes%2Fa.m3u")
        assertThat(m3uDirOf(uri)).isEqualTo("/storage/emulated/0/Music/Mixes")
        val root = Uri.parse("content://com.android.externalstorage.documents/document/primary%3Aa.m3u")
        assertThat(m3uDirOf(root)).isEqualTo("/storage/emulated/0")
    }

    @Test fun m3uDir_isNullForOtherProviders() {
        assertThat(m3uDirOf(Uri.parse("content://com.android.providers.downloads.documents/document/msf%3A12"))).isNull()
        assertThat(m3uDirOf(Uri.parse("content://com.android.externalstorage.documents/document/1234-ABCD%3Aa.m3u"))).isNull()
        assertThat(m3uDirOf(Uri.fromFile(file))).isNull()
    }
}
