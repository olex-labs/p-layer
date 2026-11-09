# Verifica v1 (Task 15)

Data: 2026-11-09. Build verificata: `app-release.apk` (R8 attivo, firma debug), versione 1.0.
Dispositivo: **emulatore** AVD `Medium_Phone_API_36.1` (x86_64, Android 16 / API 36, rendering software, senza finestra),
nessun telefono fisico collegato. Le voci che richiedono un telefono reale sono marcate "da verificare dall'utente".
Libreria di prova: 6 file MP3 da 60 s (5 con tag, 1 senza artista, 1 con copertina incorporata) in `/sdcard/Music`.

## Suite automatiche

| Comando | Esito |
|---|---|
| `./gradlew testDebugUnitTest` | PASS, 150 test, 0 falliti |
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
| 3 | Avvio a freddo con cache | **1348 / 1363 / 1295 ms** (`TotalTime`, 3 prove dopo 1 di riscaldamento da 1673 ms) | Obiettivo spec < 1 s: **non raggiunto sull'emulatore** (x86_64, rendering software, senza baseline profile applicato). Un telefono reale e' tipicamente piu' veloce: **da riverificare sul telefono**. Due avvii dopo force-stop con stato ripristinato: 1104 e 1806 ms. |
| 4 | Ricerca reattiva | PASS | "cov" e "ARTISTA b" (maiuscole/minuscole) aggiornano subito Brani/Album/Artisti. |
| 5 | Gapless: due brani consecutivi dello stesso album | PASS (solo transizione) | Salto alla traccia successiva a fine brano senza errori ne' pause di stato (`PLAYING` continuo, nessun errore ExoPlayer). L'assenza di silenzio all'ascolto: **da verificare dall'utente**. |
| 6 | Notifica multimediale | PASS | Notifica `MediaStyle`, categoria transport, visibilita' PUBLIC, 2 azioni, sessione attiva in `dumpsys media_session`. |
| 7 | Aspetto lockscreen | da verificare dall'utente | Visibilita' PUBLIC impostata; l'aspetto reale va visto sul telefono. |
| 8 | Bluetooth (auto, cuffie, tasti) | da verificare dall'utente | Non disponibile sull'emulatore. |
| 9 | Chiamata in arrivo -> pausa e ripresa | PASS (simulata) | `adb emu gsm call 5551234`: stato `PAUSED`; `adb emu gsm cancel`: torna `PLAYING`. Chiamata reale su telefono: da verificare dall'utente. |
| 10 | Kill dell'app -> riapertura con brano e posizione | PASS | In pausa a 27,1 s: `force-stop`, riapertura: stesso brano, 0:27, in pausa. In riproduzione a 39,1 s: `force-stop` -> ripristinato 37,5 s (salvataggio ogni 5 s). Nota: `am kill` non uccide il processo finche' il servizio in primo piano e' attivo (comportamento Android), quindi si e' usato `force-stop`. |
| 11 | File cancellato durante la coda -> saltato | PASS con riserva | Vedi sotto: salta senza crash e senza fermare la coda; **l'avviso (snackbar) non e' stato osservato**. |
| 12 | Import M3U da PC (CRLF, BOM, percorsi `..\Music\t1.mp3`) | PASS | Via selettore SAF (guidato con adb/uiautomator): playlist "pc" creata, snackbar "2 importati, 1 non trovati" (il terzo percorso era inesistente di proposito). |
| 13 | Copertine incorporate (`content://media/external/audio/albumart/<id>`) | PASS | File con cover rossa 300x300: griglia Album e Player mostrano la copertina rossa (screenshot con `screencap`), il tema del Player prende il colore dalla copertina. I brani senza copertina mostrano il segnaposto. |
| 14 | Fluidita' dello scorrimento | da verificare dall'utente | Non misurabile sull'emulatore software. |

### Dettagli e anomalie osservate (da non correggere in questo task)

**Voce 11 - file cancellato.**
- File rimosso con `adb shell rm` prima che il brano fosse precaricato (Cover): al "successivo" il brano viene saltato e rimosso dalla coda, la riproduzione prosegue. MediaProvider rileva subito la cancellazione e la libreria si aggiorna da sola (il brano sparisce dalla lista).
- File corrotto (contenuto sostituito, riga MediaStore ancora presente, simula "file illeggibile senza scansione"): ExoPlayer segnala `Source error`, il servizio salta al brano successivo, nessun crash. **Nessuna snackbar "Impossibile riprodurre: ..." visibile** in 5 screenshot consecutivi, mentre la snackbar dell'import M3U compare regolarmente: l'errore sembra non arrivare a `PlayerController.errors` (il servizio gestisce l'errore in `PlaybackService.onPlayerError`). Da indagare.
- File cancellato mentre il brano e' gia' precaricato/in riproduzione (Quattro): l'audio continua (file descriptor aperto), ma la libreria lo elimina e la schermata Player mostra solo il pulsante "Chiudi" (stato `current == null` perche' il brano non e' piu' nella libreria) finche' suona. Caso limite, nessun crash.

**Avvio a freddo.** Vedi voce 3: ~1,3 s sull'emulatore, sopra l'obiettivo di 1 s.

## Da verificare dall'utente sul telefono

Bluetooth/auto, aspetto lockscreen, chiamata reale, fluidita' di scorrimento, gapless all'ascolto, tempo di avvio a freddo (< 1 s con cache) con
`adb shell am start -W -n app.brano/.MainActivity`, snackbar di errore per file illeggibile.
