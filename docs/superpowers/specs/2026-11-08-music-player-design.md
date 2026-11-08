# Lettore musicale Android — Design v1

Data: 2026-11-08
Nome di lavoro: **Brano** (package `app.brano`) — modificabile in qualsiasi momento.

## Obiettivo

Lettore musicale offline per Android che riproduce i file audio presenti sul telefono.
Prende il meglio dei lettori esistenti (Musicolet, Poweramp, Retro/Phonograph) ma resta
**semplice, curato e veloce**. Uso personale, nessun account, nessuno streaming, nessuna rete.

**Criteri di successo**
- Avvio a freddo → libreria visibile in < 1 s (con cache), anche con 5.000+ brani.
- Ricerca con risultati a ogni tasto (< 10 ms di filtro su 5.000 brani).
- Scroll fluido a 60 fps sulle liste.
- Riproduzione in background affidabile, gapless, ripresa esatta dopo riavvio dell'app.

## Fuori scope (v1)

Equalizzatore, ReplayGain, testi, crossfade, tag editor, widget, Android Auto, streaming,
sincronizzazione cloud. Candidati per la v2.

## Stack

| Area | Scelta |
|---|---|
| Linguaggio / UI | Kotlin, Jetpack Compose, Material 3 |
| SDK | minSdk 26 (Android 8.0), target/compileSdk ultima stabile |
| Riproduzione | AndroidX Media3: ExoPlayer + MediaSessionService + MediaController |
| Persistenza | Room (dati utente), DataStore Preferences (impostazioni, stato ripresa) |
| Immagini | Coil (copertine da MediaStore / album art incorporata) |
| DI | Hilt |
| Colori dinamici | androidx.palette |
| Build | Gradle Kotlin DSL, version catalog, modulo unico `app` |

## Architettura

```
app/src/main/java/app/brano/
  data/library/   LibraryRepository: query MediaStore, filtri, ContentObserver, cache in memoria
  data/db/        Room: entità, DAO, database
  data/settings/  SettingsRepository (DataStore)
  playback/       PlaybackService, QueueStateStore (ripresa), SleepTimer, PlayCountTracker
  domain/         SmartPlaylists, M3uImporter, M3uExporter, Normalizer (ricerca)
  ui/             theme/, icons/, navigation/, library/, search/, playlists/, player/, settings/, onboarding/
```

**Flusso dati**: Compose UI → ViewModel (StateFlow) → Repository. Ogni comando di riproduzione
passa da un unico `PlayerController` (wrapper di `MediaController`): il servizio è l'unica
fonte di verità su coda, brano corrente e stato.

**Prestazioni**
- La libreria **non** viene copiata in Room: si interroga MediaStore (già indicizzato da Android).
  Il risultato è tenuto in memoria come `StateFlow<List<Track>>` e ricaricato su notifica del
  `ContentObserver` (con debounce 500 ms).
- Snapshot della libreria serializzato su disco per mostrare subito la UI al cold start, poi
  aggiornato in background.
- Indice di ricerca precalcolato (stringhe normalizzate: minuscole, senza accenti).
- Liste con `LazyColumn`/`LazyVerticalGrid` e chiavi stabili (`mediaStoreId`).

## Modello dati

**Track** (in memoria, da MediaStore): `id, title, artist, album, albumId, artistId, durationMs,
path, folder, dateAdded, trackNumber, year, contentUri`.

**Filtri libreria**: `IS_MUSIC != 0`, durata ≥ minimo configurabile (default 30 s),
cartella non presente tra le escluse.

**Room**
- `playlists(id PK, name, createdAt, updatedAt)`
- `playlist_items(playlistId FK, position, mediaStoreId, path)` — PK (playlistId, position)
- `play_stats(mediaStoreId PK, path, playCount, lastPlayedAt)`
- `favorites(mediaStoreId PK, path, addedAt)`
- `excluded_folders(path PK)`

I brani si identificano con `mediaStoreId`; `path` è salvato come fallback: se l'ID non esiste
più, si cerca per percorso e si aggiorna l'ID.

**DataStore**: tema (chiaro/scuro/sistema), nero AMOLED, colori dinamici on/off, durata minima,
ordinamento per scheda, stato ripresa (lista ID coda, indice, posizione ms, shuffle, repeat).

## Funzionalità

### Libreria
Schede scorrevoli **Brani · Album · Artisti · Cartelle**.
- Brani: copertina, titolo, artista, durata; ordinamento per titolo/artista/album/data aggiunta/durata;
  pulsante "Casuale" in testa; fast-scroll con lettera.
- Album: griglia di copertine → dettaglio con tracce ordinate per numero traccia.
- Artisti: lista → dettaglio con album e brani.
- Cartelle: navigazione gerarchica stile file manager.

### Ricerca
Campo con focus automatico, risultati live raggruppati (Brani, Album, Artisti), confronto
insensibile a maiuscole e accenti.

### Playlist
- Automatiche (non modificabili): **Preferiti, Aggiunti di recente** (ultimi 30 giorni),
  **Più ascoltati** (top 100), **Ascoltati di recente** (ultimi 100).
- Utente: crea, rinomina, elimina, riordina (drag & drop), rimuovi con swipe, Riproduci/Casuale.
- **M3U**: export tramite Storage Access Framework (`#EXTM3U`, `#EXTINF`, percorsi assoluti);
  import con abbinamento per percorso assoluto, poi relativo al file, poi per nome file;
  riepilogo "X importati, Y non trovati".

### Azioni sul brano
Menu ⋮ / long-press: Riproduci dopo, Aggiungi in coda, Aggiungi a playlist, Vai all'album,
Vai all'artista, Info file. Selezione multipla con long-press, azioni applicate a tutti.

### Player
- Mini-player sempre visibile sopra la bottom bar; tap o swipe su → player completo.
- Player completo: copertina grande (swipe orizzontale = brano prec./succ.), titolo, artista,
  cuore Preferiti, seekbar, prec./play-pausa/succ., shuffle, repeat (off/tutti/uno),
  icona sleep timer, menu. Swipe giù chiude.
- Coda: swipe su dal player; riordinabile, swipe per rimuovere, "Salva come playlist".

### Riproduzione
- `PlaybackService` con ExoPlayer + MediaSession: notifica, lockscreen, pulsanti cuffie/Bluetooth.
- Gapless (nativo ExoPlayer).
- Audio focus: pausa su perdita, ducking su perdita temporanea "can duck".
- Pausa su `ACTION_AUDIO_BECOMING_NOISY` (cuffie staccate).
- **Ripresa**: stato salvato ogni 5 s durante la riproduzione e su pausa/stop; al riavvio la coda
  è ripristinata in pausa alla posizione salvata.
- **Sleep timer**: 15/30/45/60 min, valore personalizzato, "a fine brano"; fade-out del volume
  negli ultimi 10 s, poi pausa.
- **Conteggio ascolti**: +1 quando si supera il 50% della durata o 4 minuti, una volta per riproduzione.

### Impostazioni
Tema, nero AMOLED, colori dinamici da copertina, durata minima brani, cartelle escluse
(aggiungi/rimuovi), rescan libreria, versione app.

### Tema
Material 3; nel player e nel mini-player i colori sono estratti dalla copertina (Palette) con
transizione animata; fallback ai colori Material You di sistema (Android 12+) o a una palette base.

### Primo avvio
Schermata unica che spiega il permesso, poi richiesta di `READ_MEDIA_AUDIO` (Android 13+) o
`READ_EXTERNAL_STORAGE` (≤ 12). Permesso negato → messaggio + pulsante alle impostazioni di sistema.
`POST_NOTIFICATIONS` richiesto su Android 13+ alla prima riproduzione.

## Icone

Fase v1: Material Symbols come segnaposto, centralizzati in `ui/icons/AppIcons.kt`.
Fase successiva: icone SVG personalizzate disegnate con Gemini, convertite in VectorDrawable
e sostituite solo in `AppIcons.kt`. Set necessario:

play, pause, skip_next, skip_previous, shuffle, shuffle_on, repeat, repeat_on, repeat_one,
queue, playlist, playlist_add, favorite, favorite_border, sleep_timer, search, library,
album, artist, folder, settings, more_vert, drag_handle, close, chevron_down, sort, add,
import, export, info, music_note (placeholder copertina), launcher icon (adaptive).

## Gestione errori

| Caso | Comportamento |
|---|---|
| File cancellato/corrotto in riproduzione | Snackbar, salta al successivo, rimosso dalla coda; nelle playlist resta marcato "non disponibile" |
| Libreria vuota | Stato vuoto con spiegazione e link a cartelle escluse / durata minima |
| Permesso revocato | Ritorno alla schermata permessi |
| Import M3U con voci non trovate | Riepilogo, voci ignorate |
| ID MediaStore cambiato | Recupero tramite `path`, aggiornamento ID |

## Test

- Unit test: filtri libreria, normalizzazione ricerca, playlist automatiche, parser/writer M3U,
  logica sleep timer, conteggio ascolti, ViewModel principali.
- Room: test DAO con database in memoria.
- Strumentale: coda e ripresa della riproduzione su emulatore o telefono reale via adb.

## Ambiente di sviluppo

Serve Android Studio (include JDK e Android SDK). Il PC al momento ha `adb` ma non Java/SDK nel
PATH: l'installazione è il primo passo del piano.
