package app.brano.data.library

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private class FakeSource(var tracks: List<Track>) : TrackSource {
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        override suspend fun loadAll() = tracks
        override fun changes(): Flow<Unit> = changes
    }

    private fun store() = LibrarySnapshotStore(tmp.newFile("library.json"), Dispatchers.Unconfined, SnapshotLog.None)

    private fun TestScope.repo(
        source: TrackSource,
        store: LibrarySnapshotStore,
        folders: Flow<Set<String>> = MutableStateFlow(emptySet()),
        min: Flow<Long> = MutableStateFlow(30_000L),
        compute: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler),
        hidden: Flow<Set<String>> = MutableStateFlow(emptySet()),
    ) = LibraryRepository(source, store, folders, hidden, min, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), compute)

    @Test fun repository_emitsFreshFilteredAndPersistsSnapshot() = runTest {
        val store = store()
        store.save(listOf(t(1, title = "Old")))
        val source = FakeSource(listOf(t(2, title = "New"), t(3, dur = 1_000)))
        val repo = repo(source, store)
        assertThat(repo.tracks.value.map { it.title }).containsExactly("New")
        assertThat(repo.isLoading.value).isFalse()
        assertThat(repo.trackById(2)?.title).isEqualTo("New")
        assertThat(repo.trackByPath("/sdcard/Music/2.mp3")?.id).isEqualTo(2L)
        assertThat(store.load()!!.map { it.id }).containsExactly(2L)
    }

    @Test fun repository_emitsSnapshotThenFresh() = runTest {
        val store = store()
        store.save(listOf(t(1, title = "Old")))
        val gate = CompletableDeferred<List<Track>>()
        val source = object : TrackSource {
            override suspend fun loadAll() = gate.await()
            override fun changes(): Flow<Unit> = MutableSharedFlow()
        }
        val repo = repo(source, store)
        assertThat(repo.tracks.value.map { it.title }).containsExactly("Old")
        assertThat(repo.isLoading.value).isTrue()
        gate.complete(listOf(t(2, title = "New")))
        assertThat(repo.tracks.value.map { it.title }).containsExactly("New")
        assertThat(repo.isLoading.value).isFalse()
    }

    @Test fun repository_reloadsOnSourceChange() = runTest {
        val source = FakeSource(listOf(t(1)))
        val repo = repo(source, store())
        repo.tracks.test {
            assertThat(awaitItem().map { it.id }).containsExactly(1L)
            source.tracks = listOf(t(1), t(2))
            source.changes.emit(Unit)
            assertThat(awaitItem().map { it.id }).containsExactly(1L, 2L)
        }
    }

    @Test fun repository_reloadsOnSettingsChange() = runTest {
        val min = MutableStateFlow(30_000L)
        val folders = MutableStateFlow(emptySet<String>())
        val hidden = MutableStateFlow(emptySet<String>())
        val two = t(2, dur = 100_000, folder = "/sdcard/Keep")
        val source = FakeSource(listOf(t(1, dur = 40_000, folder = "/sdcard/Other"), two, t(3, dur = 100_000, folder = "/sdcard/Other")))
        val repo = repo(source, store(), folders, min, hidden = hidden)
        assertThat(repo.tracks.value.map { it.id }).containsExactly(1L, 2L, 3L)
        min.value = 60_000L
        assertThat(repo.tracks.value.map { it.id }).containsExactly(2L, 3L)
        folders.value = setOf("/sdcard/Keep")
        assertThat(repo.tracks.value.map { it.id }).containsExactly(2L)
        hidden.value = setOf(two.path)
        assertThat(repo.tracks.value).isEmpty()
    }

    @Test fun repository_derivedStateUpdated() = runTest {
        val source = FakeSource(listOf(t(1, title = "Hello", albumId = 4, artistId = 8)))
        val repo = repo(source, store())
        assertThat(repo.albums.value.single().id).isEqualTo(4L)
        assertThat(repo.artists.value.single().id).isEqualTo(8L)
        assertThat(repo.folders.value).isNotNull()
        assertThat(repo.search.value.query("hello").tracks).hasSize(1)
    }

    @Test fun repository_staleSnapshotNeverOverwritesFresh() = runTest {
        val gate = CompletableDeferred<Unit>()
        val file = tmp.newFile("slow.json")
        val slowStore = object : LibrarySnapshotStore(file, Dispatchers.Unconfined, SnapshotLog.None) {
            override suspend fun load(): List<Track>? {
                gate.await()
                return listOf(t(1, title = "Old"))
            }
        }
        val repo = repo(FakeSource(listOf(t(2, title = "New"))), slowStore)
        assertThat(repo.tracks.value.map { it.title }).containsExactly("New")
        gate.complete(Unit)
        assertThat(repo.tracks.value.map { it.title }).containsExactly("New")
    }

    @Test fun repository_survivesLoadFailureAndRecovers() = runTest {
        var fail = false
        var recovered = false
        val source = object : TrackSource {
            val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
            override suspend fun loadAll(): List<Track> {
                if (fail) throw SecurityException("no permission")
                return listOf(t(if (recovered) 2 else 1))
            }
            override fun changes(): Flow<Unit> = changes
        }
        val repo = repo(source, store())
        assertThat(repo.tracks.value.map { it.id }).containsExactly(1L)
        fail = true
        source.changes.emit(Unit)
        assertThat(repo.tracks.value.map { it.id }).containsExactly(1L)
        assertThat(repo.isLoading.value).isFalse()
        fail = false
        recovered = true
        source.changes.emit(Unit)
        assertThat(repo.tracks.value.map { it.id }).containsExactly(2L)
    }

    @Test fun repository_doesItsWorkOnTheComputeDispatcher() = runTest {
        // Filtering, grouping and indexing must not run on the caller's (Main) dispatcher.
        val compute = StandardTestDispatcher(testScheduler)
        val repo = repo(FakeSource(listOf(t(1))), store(), compute = compute)
        assertThat(repo.tracks.value).isEmpty()
        advanceUntilIdle()
        assertThat(repo.tracks.value.map { it.id }).containsExactly(1L)
    }

    @Test fun repository_unchangedRefreshSkipsPublishAndSave() = runTest {
        var saves = 0
        val counting = object : LibrarySnapshotStore(tmp.newFile("count.json"), Dispatchers.Unconfined, SnapshotLog.None) {
            override suspend fun save(tracks: List<Track>) {
                saves++
                super.save(tracks)
            }
        }
        val repo = repo(FakeSource(listOf(t(1), t(2))), counting)
        val albums = repo.albums.value
        assertThat(saves).isEqualTo(1)
        repo.refresh()
        repo.refresh()
        assertThat(saves).isEqualTo(1)
        assertThat(repo.albums.value).isSameInstanceAs(albums)
    }

    @Test fun repository_publishesTheSnapshotThenSkipsAnIdenticalScan() = runTest {
        var saves = 0
        val store = object : LibrarySnapshotStore(tmp.newFile("same.json"), Dispatchers.Unconfined, SnapshotLog.None) {
            override suspend fun save(tracks: List<Track>) {
                saves++
                super.save(tracks)
            }
        }
        store.save(listOf(t(1)))
        saves = 0
        val repo = repo(FakeSource(listOf(t(1))), store)
        assertThat(repo.tracks.value.map { it.id }).containsExactly(1L)
        assertThat(saves).isEqualTo(0) // the scan matched the snapshot: nothing rewritten
    }
}
