# Brano — redesign visivo (v1.1)

Data: 2026-11-09 · Stato: bozza da approvare

## Obiettivo

L'app funziona, ma ha l'aspetto Material standard. Vogliamo darle un carattere suo, mescolando gli stili più apprezzati e i colori dell'album, **senza renderla più lenta o pesante**.

Direzione scelta dall'utente (vedi `docs/preview/direzioni.html`):

- **Libreria e navigazione = stile C "Espressivo"**: forme morbide e asimmetriche, titoli molto grandi e pesanti, pulsanti e mini-player a pillola.
- **Player = stile A "Vinile"**: luce radiale con i colori dell'album, copertina tonda come un disco, titoli serif in corsivo.

## Decisioni (prese come default, modificabili in revisione)

1. **Colori della libreria:** una tinta pastello presa dall'album in ascolto, come fa già oggi `seedColorScheme`. Senza musica, o con l'opzione "colori dalla copertina" spenta, si usa un seme **lilla** fisso (`#7C5CFF`) al posto del Material di sistema, così l'app ha sempre la sua identità. Il colore dinamico di sistema (Android 12+) resta disponibile come scelta nelle Impostazioni.
2. **Il disco gira** piano (un giro ogni 20 s) mentre la musica suona e si ferma in pausa. Si può disattivare dalle Impostazioni ("Disco che gira", attivo di default). Con le animazioni di sistema disattivate, il disco resta fermo.
3. **Tema scuro:** stesse forme. La libreria usa lo schema scuro tinto, il player resta sempre "notturno": il suo gradiente scuro è identico nei due temi.
4. **Nessun font scaricato:** il serif è `FontFamily.Serif` di sistema (Noto Serif), il sans pesante è quello di sistema con `FontWeight.Black`. L'APK non cresce.

## Componenti

### 1. Tema (`ui/theme`)

- `Theme.kt`:
  - seme di fallback lilla;
  - `Shapes` personalizzate: small 12dp, medium 20dp, large 28dp, extraLarge 36dp;
  - `Typography` con display/headline in `FontWeight.Black`.
- Nuovo `BranoShapes.kt` con le forme condivise:
  - `CoverShape`: angoli asimmetrici 14/30/14/30 dp;
  - `PillShape`: 50%.
- Nuovo `PlayerColors.kt`, funzione pura e testabile `playerColors(palette)` che restituisce:
  - **luce:** vibrant o light-vibrant;
  - **profondo:** dark-muted, oppure la luce scurita al 15% di luminosità;
  - **testo:** quasi bianco, con contrasto ≥ 4.5:1 sul colore profondo garantito dalla funzione;
  - **accento:** per la barra di avanzamento e il pulsante play.
- `ArtworkSeedLoader`: oltre al seme salva anche i `PlayerColors` dalla stessa `Palette`, già calcolata. Nessun lavoro in più sulle immagini.

### 2. Libreria e navigazione (stile C)

- **Intestazioni delle schermate principali** (Libreria, Cerca, Playlist, Impostazioni): titolo grande (~34sp, Black) come nel mockup.
- **`Artwork`:** prende un parametro `shape`, che di default è `CoverShape`.
- **Schede Brani, Album, Artisti, Cartelle:**
  - i chip delle schede diventano pillole;
  - le copertine della griglia album usano `CoverShape` grande;
  - gli artisti hanno l'avatar tondo.
- **`MiniPlayer`:**
  - pillola flottante con margine 10dp, sfondo `secondaryContainer` e angoli 28dp;
  - copertina tonda, come un piccolo disco che richiama il player;
  - sottile barra di avanzamento dentro la pillola.
- **`NavigationBar`:** indicatore a pillola già M3. Va solo armonizzato nei colori con `secondaryContainer`.
- **Dettaglio album, artista e playlist:**
  - intestazione con copertina grande `CoverShape` e titolo Black;
  - pulsanti "Riproduci" e "Casuale" a pillola.

### 3. Player (stile A)

- **Sfondo:** `Brush.radialGradient` da *luce* (in alto, al 25%) a *profondo* (al 60%) a quasi nero, disegnato in `drawBehind`. Niente blur e niente bitmap a schermo intero. Al cambio album i colori si animano in 600 ms, come già fa il tema.
- **Copertina:**
  - cerchio con un anello scuro di 10dp e un filo luminoso attorno, come un vinile;
  - foro centrale piccolo, disegnato sopra;
  - rotazione con `graphicsLayer { rotationZ }` letta da un'animazione infinita, quindi senza ricomposizioni.
- **Testi:** titolo in serif corsivo 22sp, artista e album sotto con opacità 70%.
- **Controlli:**
  - play/pausa in un cerchio pieno del colore del testo, con l'icona nel colore *profondo*;
  - avanzamento con traccia *accento* e resto bianco al 20%.
- **Coda e timer:** i fogli e i dialoghi restano con le forme M3 nuove, dai `Shapes` del tema.

## Prestazioni (vincoli verificabili)

- **Nessun effetto costoso:** niente `Modifier.blur`, `RenderEffect`, shader custom o video.
- **Rotazione del disco** solo in `graphicsLayer`. Va verificato con Layout Inspector che `PlayerScreen` non si ricomponga a ogni frame.
- **Palette** calcolata una sola volta per album, come oggi, sul thread Default.
- **Avvio a freddo** sull'emulatore entro +50 ms rispetto a oggi (~0.4 s).
- **Scorrimento** della lista Brani senza frame persi visibili; si confronta `gfxinfo` prima e dopo.

## Fuori ambito

- Icone SVG personalizzate e icona del launcher: fase successiva, con Gemini.
- Font personalizzati scaricati.
- Animazioni di transizione condivise tra mini-player e player.
- Funzioni v2: equalizzatore, ReplayGain, testi, crossfade.

## Test

- **Unit:**
  - `playerColors`: contrasto testo/profondo ≥ 4.5 per palette chiare, scure, grigie e assenti;
  - fallback quando la palette è vuota;
  - seme lilla quando non c'è musica.
- **Esistenti:** i 190 test restano verdi. Lint a 0 errori.
- **Emulatore:**
  - screenshot di Libreria, Album, Playlist, mini-player e Player con due album diversi, in tema chiaro e scuro;
  - si aggiorna `docs/preview/index.html` con le nuove immagini e un nuovo APK.
- **Verifica manuale:** disco fermo in pausa e opzione "Disco che gira" spenta.
