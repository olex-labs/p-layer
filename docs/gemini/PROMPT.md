# Brano: grafica da creare con Gemini

Tu crei i file con Gemini, poi li metti nel progetto e me lo dici. Io li controllo, li converto nel formato dell'app (senza librerie aggiuntive, non pesano) e li collego.

## Come lavorare con Gemini

- Usa Gemini in chat (gemini.google.com).
- Gli SVG sono **testo**: chiedi sempre il codice in un blocco di codice, non un'immagine.
  - Se Gemini ti risponde con un'immagine PNG, scrivi: `Rispondi solo con il codice SVG, in un blocco di codice.`
- Per ogni icona, copia il codice in un file di testo e salvalo con il nome esatto, per esempio `Play.svg`.
- Cartelle in cui salvare:
  - `design/icons/` per le icone dell'interfaccia;
  - `design/launcher/` per l'icona dell'app e quella della notifica;
  - `design/empty/` per le illustrazioni.
- Fai **un passo alla volta, nello stesso ordine e nella stessa chat**, così Gemini mantiene lo stile.
- Per controllare un file, aprilo con doppio clic nel browser: si deve vedere l'icona.

---

## Passo 1: prova di stile (4 icone)

Prima di fare tutto il set, fissiamo lo stile su 4 icone. Se non ti piacciono, chiedi modifiche finché non va bene: le altre seguiranno lo stesso stile.

```text
Sei un icon designer. Disegna 4 icone SVG per "Brano", un lettore musicale Android.
Stile: "vinile caldo + forme morbide espressive". Angoli arrotondati, terminali tondi, curve morbide,
niente spigoli vivi, aspetto amichevole ma elegante (non infantile).

Regole tecniche per ogni icona:
- <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
- disegno contenuto nell'area da 2 a 22 (2px di margine per lato)
- un solo colore: fill="currentColor". Niente stroke (converti i tratti in forme piene), niente gradienti, testo, filtri, immagini, clipPath o mask
- spessore visivo dei tratti pari a 2px
- usa solo elementi <path> (al massimo 3 per icona)

Icone: Play, Pause, Album (un disco in vinile visto dall'alto con il foro centrale), Favorite (cuore pieno).
Restituisci 4 blocchi di codice separati, ciascuno preceduto dal nome del file (es. Play.svg).
```

## Passo 2: il set completo (4 messaggi da 7 icone)

Gemini lavora meglio con pochi elementi per volta. Manda questi messaggi **uno dopo l'altro**, nella stessa chat del passo 1.

```text
Perfetto. Con lo stesso identico stile e le stesse regole tecniche, disegna queste icone
(un blocco di codice per file, preceduto dal nome):
SkipNext, SkipPrevious, Shuffle, ShuffleOn, Repeat, RepeatOn, RepeatOne.
- ShuffleOn e RepeatOn: come Shuffle e Repeat, ma con un piccolo puntino pieno sotto che indica "attivo".
- RepeatOne: Repeat con un piccolo "1" disegnato come forma (non testo).
```

```text
Stesso stile e stesse regole. Icone: Back (freccia a sinistra), ChevronDown, Close, MoreVert (tre puntini verticali),
DragHandle (due linee orizzontali), Add (più), Info (cerchio con la "i").
```

```text
Stesso stile e stesse regole. Icone: Queue (elenco con una piccola nota), Playlist (elenco di righe),
PlaylistAdd (elenco con un più), FavoriteBorder (cuore solo contorno), SleepTimer (luna con un piccolo
orologio), Sort (righe di lunghezza decrescente), MusicNote (nota singola).
```

```text
Stesso stile e stesse regole. Icone: Search (lente), Library (pila di dischi o di album),
Artist (busto di persona con una piccola nota), Folder (cartella), Settings (ingranaggio morbido),
Import (freccia che entra in un vassoio), Export (freccia che esce da un vassoio).
```

In totale sono 32 file (le 4 del passo 1 e le 28 del passo 2), tutti in `design/icons/`.

## Passo 3: icona dell'app

Android usa icone "adattive": un primo piano e uno sfondo separati, che il telefono ritaglia in cerchio, goccia, quadrato e così via. Servono anche una versione monocromatica (per le icone a tema di Android 13+) e la piccola icona della notifica.

```text
Ora l'icona dell'app Brano, formato "adaptive icon" di Android. Concetto: un disco in vinile stilizzato
che richiama la lettera "b" minuscola (per esempio, il disco è la pancia della "b" e un'asta morbida sale a sinistra).
Stesso linguaggio morbido delle icone.

Consegna 4 file SVG (un blocco di codice ciascuno, preceduto dal nome):
1. launcher_foreground.svg: viewBox 0 0 108 108, sfondo trasparente. Il disegno sta TUTTO dentro
   il cerchio centrale di diametro 66 (da 21 a 87), perché il resto può essere tagliato. Colori:
   crema #FFF7ED per il disegno, eventuale dettaglio #231F1A.
2. launcher_background.svg: viewBox 0 0 108 108, un rettangolo che riempie tutto con un
   gradiente radiale caldo, dal viola #7C5CFF al centro-alto a un arancio bruciato scuro #7C2D12 ai bordi.
3. launcher_monochrome.svg: come il foreground, ma un solo colore pieno (#000000) e sfondo trasparente.
4. notification.svg: viewBox 0 0 24 24, sagoma piena BIANCA (#FFFFFF) del disco con la "b",
   semplificata per essere leggibile a 16 pixel, sfondo trasparente.
Usa solo <path>, <circle> e <rect>, più <radialGradient> solo nel background.
```

Salva i 4 file in `design/launcher/`.

## Passo 4 (facoltativo): illustrazioni per le schermate vuote

```text
Ora 3 piccole illustrazioni SVG per le schermate vuote di Brano, nello stesso stile morbido.
Regole: viewBox 0 0 160 160, al massimo 2 colori: "currentColor" per le linee e le forme principali,
e "currentColor" con opacity 0.2 per le campiture. Nessun testo, gradiente, filtro o immagine.
Massimo 12 elementi per illustrazione.
1. empty_library.svg: uno scaffale vuoto con un disco appoggiato di lato.
2. permission.svg: un disco in vinile con un piccolo lucchetto morbido sopra.
3. empty_playlist.svg: una cassetta o un elenco vuoto con una piccola nota che fluttua.
```

Salva i 3 file in `design/empty/`.

---

## Prima di dirmelo, controlla

- [ ] Ogni file si apre nel browser e si vede il disegno.
- [ ] I nomi dei file sono esatti (maiuscole comprese): `ShuffleOn.svg`, non `shuffle_on.svg`.
- [ ] Le icone sembrano della stessa famiglia (stesso spessore, stessi angoli).
- [ ] Nell'icona dell'app, il disegno non tocca i bordi.

Quando hai finito scrivimi "icone pronte". Non serve che siano perfette: le misure e il colore unico li controllo e li sistemo io.
