# Brano — Piano di implementazione v1

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lettore musicale Android offline, semplice e veloce, che riproduce i file audio del telefono con libreria, ricerca, playlist (utente + automatiche + M3U), coda, sleep timer e tema dinamico.

**Architecture:** App a modulo unico. La libreria si legge da MediaStore e resta in memoria come `StateFlow`; Room salva solo i dati dell'utente (playlist, preferiti, statistiche, cartelle escluse). La riproduzione vive in un `MediaSessionService` Media3; la UI Compose parla con il player solo tramite `PlayerController`.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Media3 (ExoPlayer, Session), Room, DataStore Preferences, Hilt, Coil, androidx.palette, kotlinx.serialization. Test: JUnit4, Google Truth, kotlinx-coroutines-test, Turbine, Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-08-music-player-design.md`

## Global Constraints

- Package / applicationId: `app.brano`; nome visibile "Brano".
- minSdk 26; compileSdk e targetSdk = ultima API stabile disponibile in Android Studio al momento del setup.
- Gradle Kotlin DSL con version catalog `gradle/libs.versions.toml`; nessun modulo Gradle aggiuntivo.
- Nessun permesso di rete (`INTERNET` non dichiarato). Nessun account, nessuna analytics.
- Testi UI in italiano, raccolti in `res/values/strings.xml`.
- Icone solo tramite `app.brano.ui.icons.AppIcons` (mai `Icons.*` diretto nelle schermate).
- Filtro libreria: `IS_MUSIC != 0`, durata minima default **30 000 ms**, cartelle escluse.
- Ripresa: stato salvato **ogni 5 s** in riproduzione e su pausa/stop.
- Ascolto contato a **50% della durata o 240 000 ms**, il minore, una volta per riproduzione.
- Sleep timer: 15/30/45/60 min, personalizzato, "a fine brano"; fade-out **ultimi 10 s**.
- Smart playlist: Aggiunti di recente = **30 giorni**; Più ascoltati = **top 100**; Ascoltati di recente = **ultimi 100**.
- Debounce del `ContentObserver`: **500 ms**.
- Comandi su Windows: eseguire da Git Bash nella root del progetto (`./gradlew ...`).

## Review Focus

1. **Tag mancanti** — MediaStore restituisce `<unknown>` o null per artista/album: la UI mostra "Artista sconosciuto" / "Album sconosciuto", la ricerca e l'ordinamento non crashano. → test in Task 2.
2. **Ripresa con file cancellati** — la coda salvata contiene ID non più presenti: vengono scartati, l'indice si riallinea al brano giusto o al primo disponibile; coda vuota → nessuna riproduzione, nessun crash. → test in Task 4.
3. **M3U da altri lettori/PC** — BOM UTF-8, righe CRLF, separatori `\`, righe vuote, percorsi con spazi: importati correttamente. → test in Task 6.
4. **Stesso brano due volte in una playlist** — consentito; rimuovere/spostare una sola occorrenza non tocca l'altra. → test in Task 3.
5. **Libreria grande (10 000 brani)** — indice di ricerca costruito una volta; una query resta < 10 ms. → test in Task 2.

---

### Task 0: Ambiente e scheletro del progetto

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/app/brano/BranoApp.kt`, `app/src/main/java/app/brano/MainActivity.kt`, `app/src/main/res/values/strings.xml`, `.gitignore`
- Test: `app/src/test/java/app/brano/SanityTest.kt`

**Interfaces:**
- Produces: `@HiltAndroidApp class BranoApp : Application`; `@AndroidEntryPoint class MainActivity : ComponentActivity` con `setContent { }` vuoto (riempito in Task 9).

- [ ] **Step 1:** L'utente installa Android Studio (ultima stabile) con SDK e un emulatore API 34+, oppure abilita il debug USB sul telefono. Verifica: `adb devices` elenca un dispositivo.
- [ ] **Step 2:** `git init` nella root, `.gitignore` standard Android (`.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`).
- [ ] **Step 3:** Crea il progetto (Android Studio → "Empty Activity", Compose, Kotlin DSL) in questa cartella con package `app.brano`, minSdk 26. Aggiungi al version catalog e ad `app/build.gradle.kts` tutte le dipendenze dello stack (Hilt con KSP, Room con KSP, Media3 exoplayer+session, DataStore, Coil compose, palette, navigation-compose, kotlinx-serialization-json, reorderable list `sh.calvin.reorderable`) e quelle di test. `testOptions.unitTests.isIncludeAndroidResources = true` per Robolectric.
- [ ] **Step 4:** Manifest: permessi `READ_MEDIA_AUDIO` (API 33+), `READ_EXTERNAL_STORAGE` con `maxSdkVersion="32"`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. Nessun `INTERNET`.
- [ ] **Step 5:** `SanityTest` con `assertThat(1 + 1).isEqualTo(2)`. Run: `./gradlew testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 6:** Installa e avvia: `./gradlew installDebug` → l'app si apre su schermo vuoto senza crash.
- [ ] **Step 7:** Commit `chore: scaffold Android project`.

---

### Task 1: Modello Track, normalizzazione e filtro libreria

**Files:**
- Create: `app/src/main/java/app/brano/data/library/Track.kt`, `.../data/library/LibraryFilter.kt`, `.../domain/Normalizer.kt`
- Test: `app/src/test/java/app/brano/data/library/LibraryFilterTest.kt`, `.../domain/NormalizerTest.kt`

**Interfaces:**
- Produces:
  - `data class Track(val id: Long, val title: String, val artist: String?, val album: String?, val albumId: Long, val artistId: Long, val durationMs: Long, val path: String, val folder: String, val dateAddedSec: Long, val trackNumber: Int, val year: Int, val contentUri: String)` — `@Serializable`. `artist`/`album` null = sconosciuto.
  - `data class TrackRef(val mediaStoreId: Long, val path: String)`; `fun Track.ref(): TrackRef`.
  - `object Normalizer { fun normalize(s: String): String }` — minuscolo, rimuove diacritici (`java.text.Normalizer` NFD + rimozione `\p{Mn}`), spazi compressi, trim.
  - `object LibraryFilter { fun apply(tracks: List<Track>, minDurationMs: Long, excludedFolders: Set<String>): List<Track> }` — una cartella esclusa esclude anche le sottocartelle.

- [ ] **Step 1: Test che falliscono**
```kotlin
@Test fun normalize_removesAccentsAndCase() =
    assertThat(Normalizer.normalize("  Perché   NO ")).isEqualTo("perche no")
@Test fun filter_dropsShortTracks() =
    assertThat(LibraryFilter.apply(listOf(t(dur = 29_999), t(id = 2, dur = 30_000)), 30_000, emptySet()).map { it.id }).containsExactly(2L)
@Test fun filter_excludesFolderAndSubfolders() {
    val out = LibraryFilter.apply(listOf(t(1, folder = "/sdcard/WhatsApp/Media"), t(2, folder = "/sdcard/WhatsAppX"), t(3, folder = "/sdcard/WhatsApp")), 0, setOf("/sdcard/WhatsApp"))
    assertThat(out.map { it.id }).containsExactly(2L)
}
```
(`t(...)` = helper di test che costruisce un `Track` con valori di default.)
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests "app.brano.*"` → FAIL (classi mancanti).
- [ ] **Step 3:** Implementa i tre file secondo le firme.
- [ ] **Step 4:** Run stesso comando → PASS.
- [ ] **Step 5:** Commit `feat: track model, normalizer, library filter`.

---

### Task 2: Libreria da MediaStore, raggruppamenti e indice di ricerca

**Files:**
- Create: `.../data/library/TrackSource.kt`, `.../data/library/MediaStoreTrackSource.kt`, `.../data/library/LibraryGrouping.kt`, `.../data/library/LibrarySnapshotStore.kt`, `.../data/library/LibraryRepository.kt`, `.../domain/SearchIndex.kt`, `.../di/LibraryModule.kt`
- Test: `.../data/library/LibraryGroupingTest.kt`, `.../domain/SearchIndexTest.kt`, `.../data/library/LibraryRepositoryTest.kt`

**Interfaces:**
- Consumes: `Track`, `LibraryFilter`, `Normalizer` (Task 1); `ExcludedFolderDao.observe()` (Task 3) e `SettingsRepository.settings` (Task 4) — nel test si usano fake; il modulo Hilt li collega quando esistono (implementa Task 3 e 4 prima del collegamento DI, oppure lascia `LibraryModule` per la fine del Task 4).
- Produces:
  - `interface TrackSource { suspend fun loadAll(): List<Track>; fun changes(): Flow<Unit> }` — `MediaStoreTrackSource` mappa `<unknown>`/vuoto a `null`, `changes()` = `ContentObserver` su `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` con `debounce(500)`.
  - `data class Album(val id: Long, val title: String?, val artist: String?, val year: Int, val trackCount: Int, val tracks: List<Track>)`
  - `data class Artist(val id: Long, val name: String?, val albumCount: Int, val tracks: List<Track>)`
  - `data class FolderNode(val path: String, val name: String, val children: List<FolderNode>, val tracks: List<Track>)`
  - `object LibraryGrouping { fun albums(t: List<Track>): List<Album>; fun artists(t: List<Track>): List<Artist>; fun folderTree(t: List<Track>): FolderNode }` — tracce di album ordinate per `trackNumber`; nodi cartella con un solo figlio e nessun brano vengono compressi.
  - `class SearchIndex(tracks: List<Track>) { fun query(q: String): SearchResults }`; `data class SearchResults(val tracks: List<Track>, val albums: List<Album>, val artists: List<Artist>)` — match "contiene" su testo normalizzato precalcolato (titolo+artista+album); query vuota → risultati vuoti.
  - `class LibraryRepository { val tracks: StateFlow<List<Track>>; val albums: StateFlow<List<Album>>; val artists: StateFlow<List<Artist>>; val folders: StateFlow<FolderNode?>; val search: StateFlow<SearchIndex>; val isLoading: StateFlow<Boolean>; fun trackById(id: Long): Track?; fun trackByPath(path: String): Track?; suspend fun refresh() }` — all'avvio emette lo snapshot di `LibrarySnapshotStore` (JSON in `filesDir/library.json`), poi `refresh()`; si ricarica su `changes()` e su cambio di durata minima / cartelle escluse.

- [ ] **Step 1: Test che falliscono**
```kotlin
@Test fun albums_sortedByTrackNumber() { /* due brani stesso albumId, trackNumber 2 e 1 */ assertThat(albums.single().tracks.map { it.trackNumber }).containsExactly(1, 2).inOrder() }
@Test fun unknownArtist_groupedAndSearchable() { /* artist = null */ assertThat(LibraryGrouping.artists(list).single().name).isNull(); assertThat(SearchIndex(list).query("titolo").tracks).hasSize(1) }
@Test fun search_accentInsensitive() = assertThat(SearchIndex(listOf(t(title = "Perché"))).query("perche").tracks).hasSize(1)
@Test fun search_10kTracks_under10ms() {
    val idx = SearchIndex((1..10_000L).map { t(it, title = "Song $it") })
    idx.query("song 5") // warm-up
    val ms = measureTimeMillis { idx.query("song 99") }
    assertThat(ms).isLessThan(10)
}
@Test fun repository_emitsSnapshotThenFresh() = runTest { /* fake source + snapshot: prima emissione = snapshot, dopo refresh = sorgente filtrata */ }
@Test fun repository_reloadsOnSourceChange() = runTest { /* fake changes() emette → tracks aggiornate (Turbine) */ }
```
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests "app.brano.*"` → FAIL.
- [ ] **Step 3:** Implementa i file secondo le firme. Proiezione MediaStore: `_ID, TITLE, ARTIST, ALBUM, ALBUM_ID, ARTIST_ID, DURATION, DATA, DATE_ADDED, TRACK, YEAR`; `selection = IS_MUSIC != 0`. `trackNumber` = `TRACK % 1000`.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat: MediaStore library, grouping, search index`.

---

### Task 3: Database Room

**Files:**
- Create: `.../data/db/Entities.kt`, `.../data/db/PlaylistDao.kt`, `.../data/db/FavoritesDao.kt`, `.../data/db/PlayStatsDao.kt`, `.../data/db/ExcludedFolderDao.kt`, `.../data/db/BranoDatabase.kt`, `.../data/db/TrackResolver.kt`, `.../di/DatabaseModule.kt`
- Test: `.../data/db/PlaylistDaoTest.kt`, `.../data/db/StatsAndFavoritesDaoTest.kt`, `.../data/db/TrackResolverTest.kt` (Robolectric, `Room.inMemoryDatabaseBuilder`)

**Interfaces:**
- Consumes: `Track`, `TrackRef` (Task 1).
- Produces:
  - Entità come da spec: `PlaylistEntity(id, name, createdAt, updatedAt)`, `PlaylistItemEntity(playlistId, position, mediaStoreId, path)` PK `(playlistId, position)` con FK cascade, `PlayStatEntity(mediaStoreId, path, playCount, lastPlayedAt)`, `FavoriteEntity(mediaStoreId, path, addedAt)`, `ExcludedFolderEntity(path)`.
  - `PlaylistDao`: `fun observeAll(): Flow<List<PlaylistSummary>>` (`PlaylistSummary(id, name, trackCount)`), `suspend fun create(name: String): Long`, `suspend fun rename(id: Long, name: String)`, `suspend fun delete(id: Long)`, `fun observeItems(id: Long): Flow<List<PlaylistItemEntity>>`, `suspend fun addTracks(id: Long, refs: List<TrackRef>)` (in coda), `suspend fun move(id: Long, from: Int, to: Int)`, `suspend fun removeAt(id: Long, position: Int)`, `suspend fun updateIds(changes: Map<String, Long>)` (path → nuovo id). Ogni modifica aggiorna `updatedAt`; le posizioni restano contigue 0..n-1 (`@Transaction`).
  - `FavoritesDao`: `fun observe(): Flow<List<FavoriteEntity>>`, `suspend fun toggle(ref: TrackRef, now: Long): Boolean` (true = ora preferito).
  - `PlayStatsDao`: `suspend fun increment(ref: TrackRef, now: Long)`, `fun observeAll(): Flow<List<PlayStatEntity>>`.
  - `ExcludedFolderDao`: `fun observe(): Flow<Set<String>>`, `suspend fun add(path: String)`, `suspend fun remove(path: String)`.
  - `data class ResolvedItem(val ref: TrackRef, val track: Track?)`; `object TrackResolver { fun resolve(refs: List<TrackRef>, byId: (Long) -> Track?, byPath: (String) -> Track?): Pair<List<ResolvedItem>, Map<String, Long>> }` — id trovato → ok; altrimenti per path → ok + id da aggiornare; altrimenti `track = null` ("non disponibile").

- [ ] **Step 1: Test che falliscono**
```kotlin
@Test fun move_keepsPositionsContiguous() = runTest { /* 3 brani, move(0→2): ordine b,c,a; posizioni 0,1,2 */ }
@Test fun duplicateTrack_removeOneKeepsOther() = runTest {
    dao.addTracks(id, listOf(a, a, b)); dao.removeAt(id, 0)
    assertThat(dao.observeItems(id).first().map { it.mediaStoreId }).containsExactly(a.mediaStoreId, b.mediaStoreId).inOrder()
}
@Test fun deletePlaylist_cascadesItems() = runTest { }
@Test fun favorites_toggleTwiceRemoves() = runTest { assertThat(fav.toggle(a, 1)).isTrue(); assertThat(fav.toggle(a, 2)).isFalse() }
@Test fun stats_incrementAccumulates() = runTest { /* 2 increment → playCount 2, lastPlayedAt = secondo now */ }
@Test fun resolver_fallsBackToPath_andReportsIdChange() { /* id 5 sparito, path trovato con id 9 → track != null, changes = {path: 9} */ }
@Test fun resolver_missingTrack_isUnavailable() { /* né id né path → track == null */ }
```
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests "app.brano.data.db.*"` → FAIL.
- [ ] **Step 3:** Implementa entità, DAO, database (`version = 1`, `exportSchema = true`, schema in `app/schemas`), resolver, modulo Hilt.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat: Room database for playlists, favorites, stats`.

---

### Task 4: Impostazioni e stato di ripresa (DataStore)

**Files:**
- Create: `.../data/settings/AppSettings.kt`, `.../data/settings/SettingsRepository.kt`, `.../data/settings/QueueStateStore.kt`, `.../di/SettingsModule.kt`; completa `.../di/LibraryModule.kt`
- Test: `.../data/settings/SettingsRepositoryTest.kt`, `.../data/settings/QueueStateStoreTest.kt`

**Interfaces:**
- Produces:
  - `enum class ThemeMode { SYSTEM, LIGHT, DARK }`; `enum class TrackSort { TITLE, ARTIST, ALBUM, DATE_ADDED, DURATION }`
  - `data class AppSettings(val theme: ThemeMode = SYSTEM, val amoled: Boolean = false, val dynamicColor: Boolean = true, val minDurationMs: Long = 30_000, val trackSort: TrackSort = TITLE)`
  - `class SettingsRepository { val settings: Flow<AppSettings>; suspend fun update(transform: (AppSettings) -> AppSettings) }`
  - `data class QueueState(val ids: List<Long>, val index: Int, val positionMs: Long, val shuffle: Boolean, val repeatMode: Int)` (`repeatMode` = costanti `Player.REPEAT_MODE_*`)
  - `class QueueStateStore { suspend fun save(s: QueueState); suspend fun load(): QueueState? }`
  - `fun QueueState.restore(lookup: (Long) -> Track?): RestoredQueue?`; `data class RestoredQueue(val tracks: List<Track>, val index: Int, val positionMs: Long)` — scarta gli ID mancanti; se il brano corrente manca → indice sul successivo disponibile e posizione 0; nessun brano → `null`.

- [ ] **Step 1: Test che falliscono**
```kotlin
@Test fun settings_defaults() = runTest { assertThat(repo.settings.first()).isEqualTo(AppSettings()) }
@Test fun settings_updatePersists() = runTest { repo.update { it.copy(minDurationMs = 60_000) }; assertThat(newRepoSameFile().settings.first().minDurationMs).isEqualTo(60_000) }
@Test fun queue_saveLoadRoundTrip() = runTest { }
@Test fun restore_dropsMissing_keepsCurrent() { /* ids [1,2,3], index 2, 1 mancante → tracks [2,3], index 1, pos invariata */ }
@Test fun restore_currentMissing_movesToNext_positionZero() { /* ids [1,2,3], index 1, 2 mancante → index punta a 3, pos 0 */ }
@Test fun restore_allMissing_returnsNull() { }
```
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests "app.brano.data.settings.*"` → FAIL.
- [ ] **Step 3:** Implementa (`PreferenceDataStoreFactory` iniettabile con file temporaneo nei test; `ids` salvati come stringa CSV).
- [ ] **Step 4:** Run → PASS. Collega in `LibraryModule` `LibraryRepository` a `ExcludedFolderDao` e `SettingsRepository`; `./gradlew assembleDebug` → OK.
- [ ] **Step 5:** Commit `feat: settings and resume state`.

---

### Task 5: Playlist automatiche

**Files:**
- Create: `.../domain/SmartPlaylists.kt`
- Test: `.../domain/SmartPlaylistsTest.kt`

**Interfaces:**
- Consumes: `Track` (T1), `FavoriteEntity`, `PlayStatEntity` (T3).
- Produces: `enum class SmartPlaylist { FAVORITES, RECENTLY_ADDED, MOST_PLAYED, RECENTLY_PLAYED }`; `object SmartPlaylists { fun build(kind: SmartPlaylist, tracks: List<Track>, favorites: List<FavoriteEntity>, stats: List<PlayStatEntity>, nowSec: Long): List<Track> }`
  - FAVORITES: per `addedAt` decrescente. RECENTLY_ADDED: `dateAddedSec >= nowSec - 30*86400`, decrescente. MOST_PLAYED: `playCount` desc, max 100. RECENTLY_PLAYED: `lastPlayedAt` desc, max 100. Voci il cui brano non è in `tracks` vengono saltate.

- [ ] **Step 1:** Test: uno per tipo con i limiti esatti (31 giorni escluso / 30 incluso; 150 stat → 100 risultati in ordine; preferito di brano assente saltato).
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests "app.brano.domain.SmartPlaylistsTest"` → FAIL.
- [ ] **Step 3:** Implementa.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat: smart playlists`.

---

### Task 6: Import/export M3U

**Files:**
- Create: `.../domain/m3u/M3uParser.kt`, `.../domain/m3u/M3uWriter.kt`, `.../domain/m3u/M3uMatcher.kt`
- Test: `.../domain/m3u/M3uParserTest.kt`, `.../domain/m3u/M3uWriterTest.kt`, `.../domain/m3u/M3uMatcherTest.kt`

**Interfaces:**
- Consumes: `Track` (T1).
- Produces:
  - `data class M3uEntry(val path: String, val title: String?, val durationSec: Int?)`
  - `object M3uParser { fun parse(text: String): List<M3uEntry> }` — rimuove BOM, accetta `\r\n`/`\n`, ignora righe vuote e commenti `#` tranne `#EXTINF:<sec>,<titolo>`, converte `\` in `/`, decodifica `file://` URI.
  - `object M3uWriter { fun write(tracks: List<Track>): String }` — `#EXTM3U\n` poi per brano `#EXTINF:<sec>,<artista> - <titolo>\n<path>\n` (senza artista: solo titolo).
  - `data class M3uImportResult(val matched: List<Track>, val notFound: Int)`
  - `object M3uMatcher { fun match(entries: List<M3uEntry>, m3uDir: String?, library: List<Track>): M3uImportResult }` — ordine: path assoluto esatto → path relativo risolto su `m3uDir` (normalizzando `..`) → nome file (case-insensitive, primo match).

- [ ] **Step 1: Test che falliscono**
```kotlin
@Test fun parse_bomCrlfBackslashes() {
    val e = M3uParser.parse("﻿#EXTM3U\r\n#EXTINF:210,A - B\r\nMusic\\Rock\\b c.mp3\r\n\r\n")
    assertThat(e).containsExactly(M3uEntry("Music/Rock/b c.mp3", "A - B", 210))
}
@Test fun parse_fileUri() = assertThat(M3uParser.parse("file:///sdcard/Music/a%20b.mp3").single().path).isEqualTo("/sdcard/Music/a b.mp3")
@Test fun writeThenParse_roundTrip() { }
@Test fun match_relativeToPlaylistDir() { /* m3uDir=/sdcard/Music/pl, entry ../Rock/x.mp3 → /sdcard/Music/Rock/x.mp3 */ }
@Test fun match_byFilenameFallback_andCountsNotFound() { }
```
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests "app.brano.domain.m3u.*"` → FAIL.
- [ ] **Step 3:** Implementa.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat: M3U import/export`.

---

### Task 7: Logica sleep timer e conteggio ascolti

**Files:**
- Create: `.../playback/SleepTimer.kt`, `.../playback/PlayCountTracker.kt`
- Test: `.../playback/SleepTimerTest.kt`, `.../playback/PlayCountTrackerTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface SleepTimerState { data object Off; data class Counting(val remainingMs: Long); data object EndOfTrack }`
  - `class SleepTimer(scope: CoroutineScope, onVolume: (Float) -> Unit, onExpire: () -> Unit) { val state: StateFlow<SleepTimerState>; fun start(durationMs: Long); fun startEndOfTrack(); fun onTrackEnded(); fun cancel() }` — tick ogni 1 s con `delay`; negli ultimi 10 000 ms chiama `onVolume(remaining / 10_000f)`; a 0 chiama `onExpire()` poi `onVolume(1f)` e torna `Off`. `cancel()` ripristina volume 1f. `onTrackEnded()` in modalità EndOfTrack → `onExpire()`.
  - `class PlayCountTracker(private val onCounted: (Long) -> Unit) { fun onTrackStarted(id: Long); fun onProgress(positionMs: Long, durationMs: Long) }` — soglia `min(durationMs / 2, 240_000)`; una sola chiamata per `onTrackStarted`.

- [ ] **Step 1: Test che falliscono** (con `runTest` + `advanceTimeBy`)
```kotlin
@Test fun timer_fadesInLast10s_thenExpires() = runTest { t.start(15_000); advanceTimeBy(5_000); assertThat(volumes.last()).isEqualTo(1f); advanceTimeBy(5_000); assertThat(volumes.last()).isWithin(0.11f).of(0.5f); advanceTimeBy(5_100); assertThat(expired).isTrue(); assertThat(volumes.last()).isEqualTo(1f) }
@Test fun timer_cancelRestoresVolume() = runTest { }
@Test fun timer_endOfTrack_expiresOnTrackEnd() = runTest { }
@Test fun count_atHalf_once() { tr.onTrackStarted(1); tr.onProgress(99_999, 200_000); tr.onProgress(100_000, 200_000); tr.onProgress(150_000, 200_000); assertThat(counted).containsExactly(1L) }
@Test fun count_longTrack_at4min() { /* durata 20 min → conta a 240_000 */ }
```
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests "app.brano.playback.*"` → FAIL.
- [ ] **Step 3:** Implementa.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat: sleep timer and play count logic`.

---

### Task 8: Servizio di riproduzione e PlayerController

**Files:**
- Create: `.../playback/PlaybackService.kt`, `.../playback/MediaItemMapper.kt`, `.../playback/PlayerController.kt`, `.../playback/PlayerUiState.kt`; modifica `AndroidManifest.xml` (service `foregroundServiceType="mediaPlayback"` con intent-filter `androidx.media3.session.MediaSessionService`)
- Test: `.../playback/MediaItemMapperTest.kt` (Robolectric); `app/src/androidTest/java/app/brano/playback/PlaybackResumeTest.kt`

**Interfaces:**
- Consumes: `LibraryRepository.trackById` (T2), `QueueStateStore`, `QueueState.restore` (T4), `PlayStatsDao.increment` (T3), `SleepTimer`, `PlayCountTracker` (T7).
- Produces:
  - `object MediaItemMapper { fun toMediaItem(t: Track): MediaItem; fun idOf(item: MediaItem): Long }` — `mediaId = id.toString()`, URI = `contentUri`, metadati titolo/artista/album/artwork (`content://media/external/audio/albumart/<albumId>`).
  - `PlaybackService : MediaSessionService` — ExoPlayer con `setAudioAttributes(AUDIO_CONTENT_TYPE_MUSIC, handleAudioFocus = true)`, `setHandleAudioBecomingNoisy(true)`, `setWakeMode(C.WAKE_MODE_LOCAL)`. Salva `QueueState` ogni 5 s se `isPlaying` e in `onIsPlayingChanged(false)`/`onTaskRemoved`/`onDestroy`. All'avvio ripristina con `restore()` in pausa. `onPlayerError` per file illeggibile → rimuove l'elemento, `prepare()` e `play()` sul successivo, emette evento d'errore. Inoltra progresso a `PlayCountTracker` (che chiama `PlayStatsDao.increment`) e fine brano al `SleepTimer`.
  - `data class PlayerUiState(val current: Track? = null, val isPlaying: Boolean = false, val positionMs: Long = 0, val durationMs: Long = 0, val queue: List<Track> = emptyList(), val index: Int = -1, val shuffle: Boolean = false, val repeatMode: Int = Player.REPEAT_MODE_OFF, val sleep: SleepTimerState = SleepTimerState.Off)`
  - `@Singleton class PlayerController { val state: StateFlow<PlayerUiState>; val errors: SharedFlow<String>; fun play(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false); fun playNext(tracks: List<Track>); fun addToQueue(tracks: List<Track>); fun moveInQueue(from: Int, to: Int); fun removeFromQueue(index: Int); fun skipToQueueItem(index: Int); fun togglePlayPause(); fun next(); fun previous(); fun seekTo(ms: Long); fun setShuffle(on: Boolean); fun cycleRepeat(); fun startSleepTimer(ms: Long); fun sleepAtEndOfTrack(); fun cancelSleepTimer() }` — wrapper di `MediaController` (`SessionToken` del servizio); posizione aggiornata ogni 500 ms solo mentre è in riproduzione. Il sleep timer vive nel servizio e i comandi passano come `SessionCommand` custom.

- [ ] **Step 1:** Test mapper: `idOf(toMediaItem(t)) == t.id`, metadati corretti. Run `./gradlew testDebugUnitTest --tests "app.brano.playback.MediaItemMapperTest"` → FAIL.
- [ ] **Step 2:** Implementa mapper → PASS.
- [ ] **Step 3:** Implementa `PlaybackService`, `PlayerUiState`, `PlayerController`, registrazione nel manifest.
- [ ] **Step 4:** Test strumentale `PlaybackResumeTest`: copia 2 file audio brevi da `androidTest/assets` in `MediaStore`, `play(...)`, `seekTo(3000)`, ferma il servizio, ricollega il controller → `state.current` = stesso brano, `positionMs` ≈ 3000 (±1000), `isPlaying == false`. Run `./gradlew connectedDebugAndroidTest` → PASS.
- [ ] **Step 5:** Commit `feat: playback service with resume, focus, noisy handling`.

---

### Task 9: Shell UI — tema, icone, navigazione, permessi, mini-player

**Files:**
- Create: `.../ui/theme/Theme.kt`, `.../ui/theme/DynamicColors.kt`, `.../ui/icons/AppIcons.kt`, `.../ui/navigation/BranoNavHost.kt`, `.../ui/navigation/Destinations.kt`, `.../ui/onboarding/PermissionScreen.kt`, `.../ui/player/MiniPlayer.kt`, `.../ui/components/TrackRow.kt`, `.../ui/components/Artwork.kt`, `.../ui/components/TrackActionsSheet.kt`, `.../ui/components/AddToPlaylistDialog.kt`, `.../ui/AppViewModel.kt`; modifica `MainActivity.kt`
- Test: `.../ui/theme/DynamicColorsTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository` (T4), `PlayerController` (T8), `PlaylistDao` (T3), `FavoritesDao` (T3).
- Produces:
  - `object AppIcons` — una `val` `ImageVector` per ogni icona dell'elenco nella spec (nomi camelCase: `Play`, `Pause`, `SkipNext`, `SkipPrevious`, `Shuffle`, `ShuffleOn`, `Repeat`, `RepeatOn`, `RepeatOne`, `Queue`, `Playlist`, `PlaylistAdd`, `Favorite`, `FavoriteBorder`, `SleepTimer`, `Search`, `Library`, `Album`, `Artist`, `Folder`, `Settings`, `MoreVert`, `DragHandle`, `Close`, `ChevronDown`, `Sort`, `Add`, `Import`, `Export`, `Info`, `MusicNote`) mappate su Material Symbols (`material-icons-extended`).
  - `@Composable fun BranoTheme(settings: AppSettings, seedColor: Color?, content: @Composable () -> Unit)` — seed da copertina se `dynamicColor`, altrimenti Material You (API 31+) o palette base; `amoled` → `background`/`surface` = `Color.Black` in tema scuro.
  - `fun seedFromPalette(p: Palette): Color?` in `DynamicColors.kt` — vibrant → dominant → null.
  - Destinazioni: `library`, `search`, `playlists`, `settings`, `album/{id}`, `artist/{id}`, `folder?path=`, `playlist/{id}`, `smart/{kind}`, `player`.
  - `@Composable fun TrackRow(track: Track, isCurrent: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onMore: () -> Unit)` — artista null → `stringResource(R.string.unknown_artist)`.
  - `@Composable fun TrackActionsSheet(tracks: List<Track>, onDismiss: () -> Unit, onGoToAlbum: (Long) -> Unit, onGoToArtist: (Long) -> Unit)` — Riproduci dopo, Aggiungi in coda, Aggiungi a playlist (apre `AddToPlaylistDialog` con "Nuova playlist"), Vai all'album / artista (solo con un brano), Info file (dialog: percorso, durata, formato).
- [ ] **Step 1:** Test `seedFromPalette`: palette con solo dominant → dominant; vuota → null. Run `./gradlew testDebugUnitTest --tests "app.brano.ui.theme.*"` → FAIL, implementa, → PASS.
- [ ] **Step 2:** Implementa il resto. `PermissionScreen` usa `rememberLauncherForActivityResult(RequestPermission)`; negato in modo permanente → pulsante `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`. `POST_NOTIFICATIONS` richiesto alla prima `play()`. Il permesso audio viene ricontrollato a ogni `ON_RESUME`: se revocato si torna a `PermissionScreen`. Scaffold con `NavigationBar` (4 voci) e `MiniPlayer` sopra; il mini-player si nasconde se `current == null`; tap o swipe su → `player`.
- [ ] **Step 3:** Verifica manuale su dispositivo: primo avvio → schermata permesso → concesso → schermata Libreria (vuota per ora); negare → messaggio + pulsante impostazioni. `./gradlew installDebug`.
- [ ] **Step 4:** Commit `feat: app shell, theme, icons, permission flow`.

---

### Task 10: Schermate Libreria (Brani, Album, Artisti, Cartelle)

**Files:**
- Create: `.../ui/library/LibraryScreen.kt`, `.../ui/library/LibraryViewModel.kt`, `.../ui/library/TracksTab.kt`, `.../ui/library/AlbumsTab.kt`, `.../ui/library/ArtistsTab.kt`, `.../ui/library/FoldersTab.kt`, `.../ui/library/AlbumScreen.kt`, `.../ui/library/ArtistScreen.kt`, `.../ui/components/FastScroller.kt`, `.../ui/components/SelectionState.kt`
- Test: `.../ui/library/LibraryViewModelTest.kt`

**Interfaces:**
- Consumes: `LibraryRepository` (T2), `SettingsRepository` (T4), `PlayerController` (T8), componenti di T9.
- Produces:
  - `class LibraryViewModel { val tracks: StateFlow<List<Track>>; val albums; val artists; val folders; val sort: StateFlow<TrackSort>; fun setSort(s: TrackSort); fun playAll(startIndex: Int); fun shuffleAll() }` — ordinamento: TITLE/ARTIST/ALBUM con `Normalizer` (null in fondo), DATE_ADDED e DURATION decrescenti.
  - `class SelectionState { val selected: Set<Long>; fun toggle(id: Long); fun clear(); val active: Boolean }` — riusata da Ricerca e Playlist.
- [ ] **Step 1:** Test VM: ordinamento per ogni `TrackSort` con artista null in fondo; `setSort` persiste in `SettingsRepository`. Run `./gradlew testDebugUnitTest --tests "app.brano.ui.library.*"` → FAIL, implementa VM → PASS.
- [ ] **Step 2:** Implementa le schermate: `HorizontalPager` + `TabRow`; `LazyColumn`/`LazyVerticalGrid` con `key = id`; pulsante "Casuale" in testa a Brani; `FastScroller` con lettera iniziale; long-press → modalità selezione con barra azioni (`TrackActionsSheet` sui selezionati); stato vuoto con link a Impostazioni; Cartelle navigabili con back.
- [ ] **Step 3:** Verifica manuale su telefono con musica reale: le 4 schede si popolano, lo scroll è fluido, tocco → riproduce, notifica e lockscreen funzionano, cuffie staccate → pausa.
- [ ] **Step 4:** Commit `feat: library screens`.

---

### Task 11: Ricerca

**Files:**
- Create: `.../ui/search/SearchScreen.kt`, `.../ui/search/SearchViewModel.kt`
- Test: `.../ui/search/SearchViewModelTest.kt`

**Interfaces:**
- Consumes: `LibraryRepository.search` (T2), `PlayerController` (T8).
- Produces: `class SearchViewModel { val query: StateFlow<String>; val results: StateFlow<SearchResults>; fun onQuery(q: String) }` — nessun debounce (l'indice è veloce), calcolo su `Dispatchers.Default`.
- [ ] **Step 1:** Test: `onQuery("perche")` → risultati con "Perché"; query vuota → vuoti. FAIL → implementa → PASS (`./gradlew testDebugUnitTest --tests "app.brano.ui.search.*"`).
- [ ] **Step 2:** UI: campo con focus automatico e tastiera aperta, sezioni Brani/Album/Artisti, tocco su un brano → `play(results.tracks, index)`.
- [ ] **Step 3:** Commit `feat: search`.

---

### Task 12: Playlist (utente, automatiche, M3U)

**Files:**
- Create: `.../ui/playlists/PlaylistsScreen.kt`, `.../ui/playlists/PlaylistsViewModel.kt`, `.../ui/playlists/PlaylistDetailScreen.kt`, `.../ui/playlists/PlaylistDetailViewModel.kt`, `.../domain/PlaylistIo.kt`
- Test: `.../ui/playlists/PlaylistDetailViewModelTest.kt`, `.../domain/PlaylistIoTest.kt` (Robolectric)

**Interfaces:**
- Consumes: `PlaylistDao`, `FavoritesDao`, `PlayStatsDao`, `TrackResolver` (T3), `SmartPlaylists` (T5), `M3uParser`/`M3uWriter`/`M3uMatcher` (T6), `LibraryRepository` (T2), `PlayerController` (T8).
- Produces:
  - `class PlaylistIo(contentResolver, playlistDao, library) { suspend fun export(playlistId: Long, target: Uri); suspend fun import(source: Uri): M3uImportResult }` — import crea una playlist con il nome del file senza estensione; `m3uDir` ricavato da `DocumentsContract` quando possibile, altrimenti null.
  - `class PlaylistDetailViewModel { val title: StateFlow<String>; val items: StateFlow<List<ResolvedItem>>; val editable: Boolean; fun play(index: Int); fun shuffle(); fun move(from: Int, to: Int); fun remove(position: Int); fun rename(name: String); fun delete() }` — per `playlist/{id}` usa `TrackResolver` e chiama `updateIds` con gli ID cambiati; per `smart/{kind}` usa `SmartPlaylists` e `editable = false`. `play` salta gli elementi non disponibili.
- [ ] **Step 1:** Test VM: `play(2)` con elemento 1 non disponibile → coda senza quello, indice corretto; smart non editabile. Test `PlaylistIo`: export di una playlist → file contiene `#EXTM3U` e i path; import di quel file → stessa sequenza di ID. FAIL → implementa → PASS (`./gradlew testDebugUnitTest --tests "app.brano.*playlists*" --tests "app.brano.domain.PlaylistIoTest"`).
- [ ] **Step 2:** UI: lista con 4 automatiche in cima + playlist utente + FAB "Nuova" + menu "Importa M3U" (`OpenDocument` con `audio/x-mpegurl`, `audio/mpegurl`, `*/*`). Dettaglio: drag & drop (`sh.calvin.reorderable`), swipe per rimuovere con "Annulla" nello snackbar, elementi non disponibili in grigio con etichetta "Non disponibile", menu Rinomina/Elimina/Esporta (`CreateDocument("audio/x-mpegurl")`). Snackbar riepilogo import "X importati, Y non trovati".
- [ ] **Step 3:** Verifica manuale: crea playlist, aggiungi da Libreria (anche selezione multipla), riordina, esporta, reimporta.
- [ ] **Step 4:** Commit `feat: playlists with smart lists and M3U`.

---

### Task 13: Player completo e coda

**Files:**
- Create: `.../ui/player/PlayerScreen.kt`, `.../ui/player/QueueSheet.kt`, `.../ui/player/SleepTimerDialog.kt`, `.../ui/player/PlayerViewModel.kt`
- Test: `.../ui/player/PlayerViewModelTest.kt`

**Interfaces:**
- Consumes: `PlayerController` (T8), `FavoritesDao` (T3), `PlaylistDao` (T3), `seedFromPalette`/`BranoTheme` (T9).
- Produces: `class PlayerViewModel { val state: StateFlow<PlayerUiState>; val isFavorite: StateFlow<Boolean>; val seedColor: StateFlow<Color?>; fun toggleFavorite(); fun saveQueueAsPlaylist(name: String) }` — `seedColor` estratto con Coil + Palette su `Dispatchers.Default` al cambio di `current.albumId`, con cache per albumId.
- [ ] **Step 1:** Test VM: `toggleFavorite` aggiorna `isFavorite`; `saveQueueAsPlaylist("X")` crea una playlist con la coda in ordine. FAIL → implementa → PASS (`./gradlew testDebugUnitTest --tests "app.brano.ui.player.*"`).
- [ ] **Step 2:** UI come da spec: copertina grande con swipe orizzontale prec./succ., cuore, seekbar trascinabile (aggiorna `seekTo` al rilascio), controlli, shuffle/repeat (off/tutti/uno con icone `Repeat`/`RepeatOn`/`RepeatOne`), icona sleep timer (con tempo residuo quando attivo), swipe giù chiude. `QueueSheet`: riordinabile, swipe per rimuovere, tocco per saltare, "Salva come playlist". `SleepTimerDialog`: 15/30/45/60, personalizzato (minuti), "A fine brano", "Annulla timer". Transizione colori animata con `animateColorAsState`. Errori di `PlayerController.errors` → snackbar.
- [ ] **Step 3:** Verifica manuale: colori cambiano tra album; il timer di 1 minuto personalizzato sfuma e mette in pausa; riordino della coda rispettato in riproduzione.
- [ ] **Step 4:** Commit `feat: now playing screen and queue`.

---

### Task 14: Impostazioni

**Files:**
- Create: `.../ui/settings/SettingsScreen.kt`, `.../ui/settings/SettingsViewModel.kt`
- Test: `.../ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository` (T4), `ExcludedFolderDao` (T3), `LibraryRepository.refresh` (T2).
- Produces: `class SettingsViewModel { val settings: StateFlow<AppSettings>; val excluded: StateFlow<Set<String>>; fun setTheme(m: ThemeMode); fun setAmoled(b: Boolean); fun setDynamicColor(b: Boolean); fun setMinDurationSec(s: Int); fun addExcluded(path: String); fun removeExcluded(path: String); fun rescan() }`
- [ ] **Step 1:** Test VM: `setMinDurationSec(60)` → `minDurationMs == 60_000`; add/remove cartella. FAIL → implementa → PASS (`./gradlew testDebugUnitTest --tests "app.brano.ui.settings.*"`).
- [ ] **Step 2:** UI: Tema (Sistema/Chiaro/Scuro), Nero AMOLED, Colori da copertina, Durata minima (slider 0–120 s, step 5), Cartelle escluse (lista + aggiungi scegliendo tra le cartelle della libreria + rimuovi), "Riscansiona libreria", versione app da `BuildConfig.VERSION_NAME`.
- [ ] **Step 3:** Verifica manuale: escludere una cartella la toglie subito dalla Libreria; cambiare tema si applica senza riavvio.
- [ ] **Step 4:** Commit `feat: settings screen`.

---

### Task 15: Verifica finale su dispositivo

**Files:**
- Create: `docs/TESTING.md` (checklist manuale eseguita, con esito)

- [ ] **Step 1:** Run `./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug` → tutto PASS, nessun errore lint.
- [ ] **Step 2:** `./gradlew assembleRelease` con R8 attivo (`isMinifyEnabled = true`, regole keep per kotlinx.serialization) e firma debug → installa con `adb install -r` e ripeti i controlli chiave (sotto) sulla build release.
- [ ] **Step 3:** Checklist su telefono reale: avvio a freddo < 1 s con cache (misura con `adb shell am start -W app.brano/.MainActivity`, `TotalTime`); ricerca reattiva; gapless su un album continuo; notifica/lockscreen/Bluetooth; chiamata in arrivo → pausa e ripresa; kill dell'app (`adb shell am kill app.brano`) → riapertura con brano e posizione ripristinati; file cancellato durante la coda → saltato con avviso; import di un M3U creato su PC.
- [ ] **Step 4:** Annota gli esiti in `docs/TESTING.md`. Commit `docs: v1 verification checklist`.
