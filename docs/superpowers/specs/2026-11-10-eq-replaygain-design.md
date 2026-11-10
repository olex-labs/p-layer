# P-layer v1.11: equalizzatore a preset e ReplayGain automatico

Data: 2026-11-10 · Stato: approvato in chat, da rivedere per iscritto

## Obiettivo

Due richieste della roadmap:
1. un **equalizzatore a preset**, senza bande da regolare (scelta dell'utente);
2. **ReplayGain**, cioè volume uniforme tra i brani. Funziona in modo automatico, traccia o album, ed è attivo di default (scelta dell'utente).

Vincoli di sempre:
- nessuna libreria nuova;
- costo minimo di batteria e CPU;
- se qualcosa non funziona, la musica continua a suonare come oggi.

## 1. Equalizzatore a preset

**Comportamento**
- In Impostazioni c'è una riga **"Equalizzatore"** che mostra il preset attivo.
- Toccandola si apre un dialogo a scelta singola con 11 voci: **Spento** (predefinito), Bassi, Rock, Pop, Jazz, Classica, Voce, Elettronica, Acustica, Notte.
- Il preset scelto vale subito, anche a musica in corso, e viene ricordato.
- Se il telefono non ha l'equalizzatore di sistema, la riga è disattivata con il sottotitolo "Non disponibile su questo telefono".
  - Per saperlo si cerca `AudioEffect.EFFECT_TYPE_EQUALIZER` in `AudioEffect.queryEffects()`.

**Preset.** Valori in dB alle frequenze di riferimento 60 Hz, 230 Hz, 910 Hz, 3,6 kHz e 14 kHz.

| Preset | 60 | 230 | 910 | 3600 | 14000 |
|---|---|---|---|---|---|
| Bassi | +6 | +4 | 0 | 0 | +1 |
| Rock | +4 | +2 | −1 | +2 | +4 |
| Pop | −1 | +2 | +3 | +2 | −1 |
| Jazz | +3 | +1 | +1 | +2 | +2 |
| Classica | +4 | +2 | 0 | +2 | +3 |
| Voce | −2 | 0 | +3 | +3 | 0 |
| Elettronica | +5 | +2 | 0 | +2 | +4 |
| Acustica | +2 | +1 | +2 | +3 | +2 |
| Notte | −3 | −1 | 0 | −1 | −3 |

**Adattamento alle bande del telefono (`EqCurve`)**
- Per ogni banda del telefono si prende la sua frequenza centrale e si calcola il guadagno per **interpolazione lineare sul logaritmo della frequenza** tra i due punti di riferimento vicini.
- Fuori dall'intervallo 60 Hz – 14 kHz vale il punto di riferimento più vicino.
- Il risultato si converte in millibel (dB × 100) e si limita all'intervallo `getBandLevelRange()` del telefono.

**Componente (`playback/EqualizerController`)**
- Usa `android.media.audiofx.Equalizer(priority 0, audioSessionId)`, con la sessione audio di ExoPlayer.
- Viene ricreato quando `onAudioSessionIdChanged` segnala una sessione nuova.
- Con "Spento" fa `enabled = false`. Con un preset applica le bande e fa `enabled = true`.
- Osserva `AppSettings.eqPreset` e viene rilasciato in `onDestroy` del servizio.
- **Errori:** un'eccezione alla creazione o nell'uso dell'equalizzatore viene scritta nel log e l'equalizzatore non viene usato. La riproduzione non si interrompe mai.

## 2. ReplayGain automatico

**Comportamento**
- In Impostazioni c'è l'interruttore **"Volume uniforme"** (ReplayGain), attivo di default, con il sottotitolo "Usa i tag ReplayGain dei file per suonare i brani allo stesso volume".
- Il valore usato si decide così:
  - **valore dell'album** quando la coda è un solo album, cioè tutti i brani hanno lo stesso `albumId`, ce ne sono almeno 2 e lo shuffle è spento;
  - **valore del brano** in tutti gli altri casi;
  - se manca il valore scelto si usa l'altro;
  - se non c'è nessun valore il guadagno è 0 dB.
- **Protezione dalla distorsione:** se il tag ha il picco (`*_PEAK`, campione da 0 a 1 o oltre), il guadagno lineare si limita a `1 / picco`.
- **Limite di sicurezza:** il guadagno finale resta tra −24 dB e +12 dB.

**Lettura dei tag (`playback/ReplayGainTags`)**
- I tag vengono dai metadati che Media3 ha già letto: `Format.metadata` della traccia audio selezionata, in `onTracksChanged`.
- Si leggono due tipi di voce:
  - `TextInformationFrame` con id `TXXX` (MP3, ID3v2), dove la descrizione è il nome del tag;
  - `VorbisComment` (FLAC, OGG, Opus), dove la chiave è il nome del tag.
- I nomi riconosciuti, senza differenze tra maiuscole e minuscole, sono `REPLAYGAIN_TRACK_GAIN`, `REPLAYGAIN_ALBUM_GAIN`, `REPLAYGAIN_TRACK_PEAK` e `REPLAYGAIN_ALBUM_PEAK`.
- I valori come `-6.50 dB`, `+1.2 dB` o `-6,5 dB` si leggono come numeri; un valore non leggibile vale null.
- Restano fuori dallo scopo i tag iTunes degli M4A e `R128_*_GAIN` degli Opus.

**Applicazione (`playback/ReplayGainProcessor`)**
- È un `BaseAudioProcessor` aggiunto all'`AudioSink` di ExoPlayer tramite `DefaultRenderersFactory.buildAudioSink` e `DefaultAudioSink.Builder.setAudioProcessors`.
- Accetta `ENCODING_PCM_16BIT` e `ENCODING_PCM_FLOAT`; con altri formati non è attivo.
- `@Volatile var gain: Float` è il moltiplicatore lineare.
  - A 1 i campioni si copiano senza modifiche.
  - Altrimenti ogni campione si moltiplica e si limita: a [−32768, 32767] in 16 bit, a [−1, 1] in float.
- Resta sempre attivo mentre ReplayGain è acceso, così cambiare guadagno non richiede di riconfigurare il player.
- **Limite noto:** il guadagno cambia quando arrivano i tag del nuovo brano (`onTracksChanged`). Nei passaggi senza pausa la correzione può arrivare una frazione di secondo prima o dopo l'inizio del brano.

**Collegamento al servizio**
- In `onTracksChanged` si leggono i tag, si sceglie album o brano con la coda attuale e lo stato dello shuffle, e si imposta `processor.gain`.
- Se l'interruttore è spento, il guadagno è 1.
- Il guadagno si ricalcola anche quando cambia lo shuffle o la coda (`onTimelineChanged`, `onShuffleModeEnabledChanged`).

## 3. Impostazioni

- `AppSettings` riceve due campi:
  - `eqPreset: EqPreset = EqPreset.OFF`, salvato con chiave `eq_preset`;
  - `replayGain: Boolean = true`, salvato con chiave `replay_gain`.
- Un valore salvato sconosciuto vale `OFF`.
- I testi nuovi vanno in inglese e in italiano; `StringsParityTest` controlla che ci siano in entrambi.

## 4. Test

**Test automatici (JVM)**
- `EqCurve`:
  - 5 bande con le stesse frequenze del riferimento danno esattamente i valori della tabella;
  - 3 bande danno valori interpolati tra i punti di riferimento;
  - 10 bande con frequenze fuori intervallo prendono il valore del bordo;
  - i valori restano entro i limiti del telefono.
- `ReplayGainTags`:
  - legge TXXX e VorbisComment, senza badare a maiuscole e minuscole;
  - accetta i formati `-6.50 dB`, `+1.2 dB`, `-6,5`;
  - un testo non leggibile dà null.
- Scelta del guadagno:
  - una coda di un solo album con lo shuffle spento usa l'album;
  - con lo shuffle acceso usa il brano;
  - con album misti usa il brano;
  - se un valore manca usa l'altro;
  - il picco limita il guadagno;
  - il guadagno resta tra −24 e +12 dB.
- `ReplayGainProcessor`:
  - in 16 bit e in float i campioni vengono scalati e limitati;
  - con guadagno 1 l'uscita è identica all'ingresso.
- `EqPreset`: nomi per entrambe le lingue (parità); un valore salvato sconosciuto dà `OFF`.

**Emulatore**
1. In Impostazioni la riga dell'equalizzatore apre i preset; la scelta resta dopo il riavvio dell'app; i log non mostrano errori dell'effetto.
2. Si crea un MP3 di prova con TXXX `REPLAYGAIN_TRACK_GAIN = -6.00 dB`; mentre suona, il log del servizio deve mostrare il guadagno 0,501.
3. Una coda di un album intero usa il valore dell'album (log).

L'emulatore gira senza audio, quindi l'effetto sonoro si giudica sul telefono. Questo va scritto in TESTING.md.

## Fuori dallo scopo
- Bande regolabili a mano, rinforzo bassi, virtualizzatore.
- Pre-amplificazione regolabile.
- Analisi dei file che non hanno tag ReplayGain.
- Tag ReplayGain di M4A/iTunes e R128 degli Opus.
