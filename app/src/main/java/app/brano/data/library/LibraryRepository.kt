package app.brano.data.library

import app.brano.domain.SearchIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRepository(
    private val source: TrackSource,
    private val snapshotStore: LibrarySnapshotStore,
    excludedFolders: Flow<Set<String>>,
    minDurationMs: Flow<Long>,
    scope: CoroutineScope,
    /** Filtering, grouping and indexing run here, never on the caller's (often Main) dispatcher. */
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) {
    private class Settings(val excluded: Set<String>, val minDurationMs: Long)
    private class Lookup(val byId: Map<Long, Track>, val byPath: Map<String, Track>)

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    private val _folders = MutableStateFlow<FolderNode?>(null)
    private val _search = MutableStateFlow(SearchIndex(emptyList()))
    private val _isLoading = MutableStateFlow(false)

    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()
    val folders: StateFlow<FolderNode?> = _folders.asStateFlow()
    val search: StateFlow<SearchIndex> = _search.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    @Volatile private var lookup = Lookup(emptyMap(), emptyMap())
    private val settings = MutableStateFlow<Settings?>(null)
    private val mutex = Mutex()
    @Volatile private var freshPublished = false

    /** True once anything (snapshot or scan) was published; until then an equal list is still published. */
    @Volatile private var published = false

    init {
        scope.launch {
            // Snapshot first, but never overwrite data already published by a faster refresh.
            snapshotStore.load()?.let { snapshot ->
                withContext(compute) { mutex.withLock { if (!freshPublished) publish(snapshot) } }
            }
        }
        scope.launch {
            combine(excludedFolders, minDurationMs) { e, m -> Settings(e, m) }
                .flatMapLatest { s ->
                    settings.value = s
                    source.changes().map { }.onStart { emit(Unit) }
                }
                .collectLatest { refresh() }
        }
    }

    fun trackById(id: Long): Track? = lookup.byId[id]

    fun trackByPath(path: String): Track? = lookup.byPath[path]

    suspend fun refresh() = withContext(compute) {
        settings.filterNotNull().first()
        mutex.withLock {
            val current = settings.value ?: return@withLock
            _isLoading.value = true
            try {
                val filtered = LibraryFilter.apply(source.loadAll(), current.minDurationMs, current.excluded)
                freshPublished = true
                // Unchanged (e.g. the scan matches the snapshot, or a burst of MediaStore changes):
                // nothing to regroup, re-index or rewrite.
                if (!published || filtered != _tracks.value) {
                    publish(filtered)
                    snapshotStore.save(filtered)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep previous data; a later change or settings emission retries.
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Tracks (and the id/path lookup) first so the song list shows at once; then the derived views. */
    private fun publish(list: List<Track>) {
        lookup = Lookup(list.associateBy { it.id }, list.associateBy { it.path })
        _tracks.value = list
        published = true
        val g = LibraryGrouping.group(list)
        _albums.value = g.albums
        _artists.value = g.artists
        _folders.value = g.folders
        _search.value = SearchIndex(list, g.albums, g.artists)
    }
}
