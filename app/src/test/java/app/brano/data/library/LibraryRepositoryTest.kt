package app.brano.data.library

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
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

    private fun store() = LibrarySnapshotStore(tmp.newFile("library.json"), Dispatchers.Unconfined)

    private fun TestScope.repo(
        source: TrackSource,
        store: LibrarySnapshotStore,
        excluded: Flow<Set<String>> = MutableStateFlow(emptySet()),
        min: Flow<Long> = MutableStateFlow(30_000L),
    ) = LibraryRepository(source, store, excluded, min, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

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
        val excluded = MutableStateFlow(emptySet<String>())
        val source = FakeSource(listOf(t(1, dur = 40_000), t(2, dur = 100_000, folder = "/sdcard/Skip")))
        val repo = repo(source, store(), excluded, min)
        assertThat(repo.tracks.value.map { it.id }).containsExactly(1L, 2L)
        min.value = 60_000L
        assertThat(repo.tracks.value.map { it.id }).containsExactly(2L)
        excluded.value = setOf("/sdcard/Skip")
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
}
