# Verifica v1 (Task 15)

Data: 2026-11-09. Build verificata: `app-release.apk` (R8 attivo, firma debug), versione 1.0.
Dispositivo: **emulatore** AVD `Medium_Phone_API_36.1` (x86_64, Android 16 / API 36, rendering software, senza finestra),
nessun telefono fisico collegato. Le voci che richiedono un telefono reale sono marcate "da verificare dall'utente".
Libreria di prova: 6 file MP3 da 60 s (5 con tag, 1 senza artista, 1 con copertina incorporata) in `/sdcard/Music`.

## Suite automatiche

| Comando | Esito |
|---|---|
| `./gradlew testDebugUnitTest` | PASS, 189 test, 0 falliti (dopo le correzioni della revisione finale) |
| `./gradlew connectedDebugAndroidTest` (emulatore) | PASS, 1 test (`PlaybackResumeTest`) |
| `./gradlew lintDebug` | PASS, 0 errori, 43 warning, 2 hint |
| `./gradlew assembleRelease` (`isMinifyEnabled = true`) | PASS |

Correzioni necessarie per portare lint a 0 errori (prima: 9 errori):
- `PlaybackService`: `@OptIn(UnstableApi)` sulla callback di sessione e `SessionError.ERROR_NOT_SUPPORTED` al posto di `RESULT_ERROR_NOT_SUPPORTED` (WrongConstant).
- `M3uParser`: BOM scritto come escape `\uFEFF` invece del carattere letterale (ByteOrderMark). Comportamento invariato.
- `local.properties` (file locale, non versionato): escape del `:` in `sdk.dir` (PropertyEscape).
- `android:allowBackup="false"` (app offline personale: niente ripristino di id MediaStore obsoleti).

Release: `proguard-rules.pro` contiene le regole ufficiali di kotlinx.serialization; la build release e' firmata con la chiave debug
(`signingConfigs.getByName("debug")`) solo per poterla installare. Per una distribuzione serve una chiave propria.

## Checklist su emulatore (build release installata con `adb install -r`)

| # | Controllo | Esito | Note |
|---|---|---|---|
| 1 | Avvio, permesso audio, libreria carica (release/R8) | PASS | Elenco Brani/Album/Artisti popolato, nessun crash (`FATAL EXCEPTION` = 0 in tutta la sessione). |
| 2 | Riproduzione e apertura Player (release/R8) | PASS | Mini-player e schermata "In riproduzione" corretti, seek, pausa/riprendi. |
| 3 | Avvio a freddo con cache | **434 / 414 / 383 ms** (`TotalTime`); "Fully drawn" (libreria visibile, `reportFullyDrawn`) **449 / 451 / 426 ms** | Dopo le correzioni I5 (lavoro della libreria fuori dal Main, una sola scansione, servizio collegato dopo il primo frame). Prima: 1348 / 1363 / 1295 ms. Logcat release: `D Brano: Library snapshot loaded: 14 tracks` a ogni avvio: lo snapshot si decodifica con R8. Da riconfermare sul telefono. |
| 4 | Ricerca reattiva | PASS | "cov" e "ARTISTA b" (maiuscole/minuscole) aggiornano subito Brani/Album/Artisti. |
| 5 | Gapless: due brani consecutivi dello stesso album | PASS (solo transizione) | Salto alla traccia successiva a fine brano senza errori ne' pause di stato (`PLAYING` continuo, nessun errore ExoPlayer). L'assenza di silenzio all'ascolto: **da verificare dall'utente**. |
| 6 | Notifica multimediale | PASS | Notifica `MediaStyle`, categoria transport, visibilita' PUBLIC, 2 azioni, sessione attiva in `dumpsys media_session`. |
| 7 | Aspetto lockscreen | da verificare dall'utente | Visibilita' PUBLIC impostata; l'aspetto reale va visto sul telefono. |
| 8 | Bluetooth (auto, cuffie, tasti) | da verificare dall'utente | Non disponibile sull'emulatore. |
| 9 | Chiamata in arrivo -> pausa e ripresa | PASS (simulata) | `adb emu gsm call 5551234`: stato `PAUSED`; `adb emu gsm cancel`: torna `PLAYING`. Chiamata reale su telefono: da verificare dall'utente. |
| 10 | Kill dell'app -> riapertura con brano e posizione | PASS | In pausa a 27,1 s: `force-stop`, riapertura: stesso brano, 0:27, in pausa. In riproduzione a 39,1 s: `force-stop` -> ripristinato 37,5 s (salvataggio ogni 5 s). Nota: `am kill` non uccide il processo finche' il servizio in primo piano e' attivo (comportamento Android), quindi si e' usato `force-stop`. |
| 11 | File cancellato durante la coda -> saltato | PASS | File illeggibile: snackbar "Impossibile riprodurre: Rotto", brano rimosso, passa al successivo. File del brano in riproduzione cancellato: mini-player e Player continuano a mostrare titolo e artista. |
| 15 | Menu ⋮ di una riga Brani (bordo destro) | PASS | Tap con `adb shell input tap` sul ⋮ della prima riga: si apre il foglio azioni (anche subito dopo uno scorrimento). |
| 16 | Casuale | PASS | Tre pressioni: brani iniziali diversi; la coda mostra l'ordine reale e "Successivo" segue la coda. |
| 17 | Menu del Player, album dell'artista, scheda evidenziata | PASS | ⋮ del Player: Aggiungi a playlist / Vai all'album / Vai all'artista / Info file. Dettaglio artista con sezione Album (scheda -> dettaglio album). La scheda Libreria resta evidenziata nel dettaglio artista. Righe Brani con durata. |
| 12 | Import M3U da PC (CRLF, BOM, percorsi `..\Music\t1.mp3`) | PASS | Via selettore SAF (guidato con adb/uiautomator): playlist "pc" creata, snackbar "2 importati, 1 non trovati" (il terzo percorso era inesistente di proposito). |
| 13 | Copertine incorporate (`content://media/external/audio/albumart/<id>`) | PASS | File con cover rossa 300x300: griglia Album e Player mostrano la copertina rossa (screenshot con `screencap`), il tema del Player prende il colore dalla copertina. I brani senza copertina mostrano il segnaposto. |
| 14 | Fluidita' dello scorrimento | da verificare dall'utente | Non misurabile sull'emulatore software. |

### Dettagli e anomalie osservate nella prima verifica (corrette nella revisione finale: I1, I2, I5)

**Voce 11 - file cancellato.**
- File rimosso con `adb shell rm` prima che il brano fosse precaricato (Cover): al "successivo" il brano viene saltato e rimosso dalla coda, la riproduzione prosegue. MediaProvider rileva subito la cancellazione e la libreria si aggiorna da sola (il brano sparisce dalla lista).
- File corrotto (contenuto sostituito, riga MediaStore ancora presente, simula "file illeggibile senza scansione"): ExoPlayer segnala `Source error`, il servizio salta al brano successivo, nessun crash. **Nessuna snackbar "Impossibile riprodurre: ..." visibile** in 5 screenshot consecutivi, mentre la snackbar dell'import M3U compare regolarmente: l'errore sembra non arrivare a `PlayerController.errors` (il servizio gestisce l'errore in `PlaybackService.onPlayerError`). Da indagare.
- File cancellato mentre il brano e' gia' precaricato/in riproduzione (Quattro): l'audio continua (file descriptor aperto), ma la libreria lo elimina e la schermata Player mostra solo il pulsante "Chiudi" (stato `current == null` perche' il brano non e' piu' nella libreria) finche' suona. Caso limite, nessun crash.

**Avvio a freddo.** Vedi voce 3: ~1,3 s sull'emulatore, sopra l'obiettivo di 1 s.

## Da verificare dall'utente sul telefono

Bluetooth/auto, aspetto lockscreen, chiamata reale, fluidita' di scorrimento, gapless all'ascolto, tempo di avvio a freddo (< 1 s con cache) con
`adb shell am start -W -n app.brano/.MainActivity`.

## Redesign v1.1

- Avvio a freddo (emulatore, release, 9 prove): mediana v1.1 487 ms, v1 615 ms nelle stesse condizioni: nessuna regressione.
- Scorrimento: frame lenti identici tra v1 e v1.1 sull'emulatore (dipendono dall'emulatore); da verificare dall'utente su telefono.
- Disco che gira: fermo in pausa e con l'opzione spenta; da verificare dall'utente su telefono.

## v1.6

- Cartelle musicali con il selettore di sistema, nascondi/ripristina, elimina nel cestino, testi .lrc sincronizzati: verificati sull'emulatore (Android 16).
- Avvio a freddo, installazione pulita, 2 giri alternati: v1.5 media 694 ms, v1.6 media 685 ms: nessuna regressione.
- Da verificare dall'utente: testi incorporati in MP3/FLAC reali, ricerca online LRCLIB, eliminazione su Android 10, scheda SD come cartella musicale.

## v1.8 — widget sulla schermata Home

Verificato sull'emulatore (API 36, `Medium_Phone_API_36.1`):

- [x] Il widget "Brano · Barra" (4x1) compare nella lista dei widget con l'anteprima scura, "Brano" e "Tocca ▶ per iniziare".
- [x] Aggiunto alla Home mostra lo stato vuoto.
- [x] ▶ ad app chiusa (servizio fermato e processo terminato): la coda salvata riparte dal punto in cui era (26 s → 29 s), senza crash.
- [x] Durante la riproduzione mostra titolo, artista, copertina, colore dell'album e l'icona di pausa.
- [x] ⏮ / ⏭ cambiano brano. Media3 scarta questi tasti dal proprio ricevitore, quindi passano da `WidgetActionReceiver`.
- [x] Dopo il riavvio del telefono mostra ancora l'ultimo brano. Copertina e colore tornano al primo play.

Da provare sul telefono:
- [ ] Launcher diversi da Pixel (Samsung One UI, MIUI): dimensioni e angoli.
- [ ] ▶ dal widget dopo molte ore di inattività (il sistema ha chiuso l'app).
- [ ] Ultimo brano della coda: ⏭ non fa nulla, come in Media3.
## v1.9 — P-layer

Verificato sull'emulatore:
- [x] Il nuovo ID \io.github.olexlabs.player\ si installa accanto alla vecchia app e parte; il nome mostrato è \P-layer\.
- [x] Widget aggiunto durante la riproduzione: mostra subito brano, copertina e colore.
- [x] Dopo il riavvio il widget mostra l'ultimo brano con copertina, colore e ▶.

Da provare sul telefono:
- [ ] Disinstallare Brano (vecchio ID) prima di installare P-layer: preferiti e playlist non vengono trasferiti, esportare le playlist in M3U prima.

## v1.11 — equalizzatore e ReplayGain

Verificato sull'emulatore (senza audio):
- [x] MP3 con TXXX REPLAYGAIN_TRACK_GAIN=-6.00 dB: guadagno 0,501 nel log.
- [x] Album intero in ordine: il log mostra album=true.
- [x] Equalizzatore: scelta Rock, resta dopo il riavvio; l'effetto risulta attivo sulla sessione audio senza errori.

Da provare sul telefono (con audio vero):
- [ ] Ogni preset si sente diverso e Spento torna al suono originale.
- [ ] Brani con tag ReplayGain molto diversi suonano allo stesso volume; nessuna distorsione sui brani alzati.
- [ ] Telefoni con equalizzatore proprio (Samsung, Dolby): nessun conflitto.
