# Brano v1.8: widget sulla schermata Home

Data: 2026-11-10 · Stato: approvato in chat, da rivedere per iscritto

## Obiettivo

Controllare la musica dalla schermata Home senza aprire Brano. Un solo formato, scelto dall'utente: **barra 4x1**.

Vincoli di sempre:
- nessuna libreria nuova (niente Jetpack Glance): si usano i widget classici di Android (`AppWidgetProvider` + `RemoteViews`);
- nessun aggiornamento periodico: il widget cambia solo quando cambia qualcosa;
- funziona offline.

## 1. Aspetto

```
┌──────────────────────────────────────────┐
│ [cover]  Titolo brano          ⏮  ⏯  ⏭  │
│          Artista                         │
└──────────────────────────────────────────┘
```

- **Sfondo:** angoli arrotondati (`@android:dimen/system_app_widget_background_radius` su API 31+, 16dp prima). Il colore è il "deep" dell'album corrente, lo stesso di `playerColors(seed).deep` nel player. Senza copertina o senza seed si usa il lilla scuro dell'icona `#14101F`.
- **Copertina:** a sinistra, 48dp, quadrato morbido (angoli al 10%, come `CoverShape`). Senza copertina compare l'icona `MusicNote` su un fondo leggermente più chiaro.
- **Testi:** titolo e artista su una riga ciascuno, troncati con "…"; testo bianco, artista al 70%.
- **Tasti:** precedente, play/pausa e successivo a destra, con le icone dell'app (`SkipPrevious`, `Play`/`Pause`, `SkipNext`) in vector drawable bianchi. L'icona del tasto centrale segue lo stato.
- **Tocco su copertina o testi:** apre Brano, come la notifica (`MainActivity`).
- **Stato vuoto:** se non si è mai ascoltato niente, il widget mostra "Brano" e "Tocca ▶ per iniziare".
- **Anteprima:** nella lista dei widget del launcher c'è `previewLayout` su API 31+ e un'immagine statica prima. Il widget si chiama "Brano · Barra" e misura `targetCellWidth=4`, `targetCellHeight=1`. Si può allargare solo in orizzontale.

## 2. Architettura

**Componenti nuovi (`app/brano/widget/`)**

- **`WidgetState`:** data class pura con `title`, `artist`, `albumId?`, `isPlaying`, `hasTrack`. Non dipende da Android, quindi si testa facilmente.
- **`WidgetStateStore`:** salva l'ultimo `WidgetState` in `SharedPreferences` (pochi campi, scrittura asincrona con `apply()`). Così il widget mostra l'ultimo brano anche dopo un riavvio, quando il servizio non è attivo.
- **`WidgetRenderer`:** funzione `(Context, WidgetState, Bitmap?, colorDeep: Int) -> RemoteViews`. Costruisce il layout e assegna le `PendingIntent` dei tasti.
- **`BranoWidgetProvider`:** `AppWidgetProvider`. In `onUpdate` (widget aggiunto, riavvio del launcher) disegna dall'ultimo stato salvato, senza copertina se non è già pronta.
- **`WidgetUpdater`:** usato dal servizio. Riceve lo stato e fa tre cose:
  1. confronta lo stato col precedente e ignora i doppioni;
  2. carica la miniatura con Coil (ImageLoader già presente): 144px, `allowHardware(false)` perché `RemoteViews` non accetta bitmap hardware, angoli arrotondati applicati alla bitmap;
  3. calcola il colore con la cache di `ArtworkSeedLoader`, poi chiama `AppWidgetManager.updateAppWidget` per tutti gli id del provider.

  Se non ci sono widget sulla Home (`getAppWidgetIds` vuoto) non fa nulla: nessun costo per chi non usa il widget.

**Collegamento al servizio (`PlaybackService`)**
- Il `playerListener` esistente chiama `widgetUpdater.update(...)` in tre casi: `onMediaItemTransition`, `onIsPlayingChanged` e dopo `restoreQueue`.
- La miniatura si carica in una coroutine dello scope del servizio. Se il brano cambia, il caricamento precedente viene annullato.
- **Limite di dimensione:** la bitmap passata ai `RemoteViews` resta sotto il limite IPC (144×144 ARGB ≈ 81 KB).

**Tasti**
- Ogni tasto è una `PendingIntent` che manda un `ACTION_MEDIA_BUTTON` con il `KeyEvent` giusto (`KEYCODE_MEDIA_PREVIOUS`, `KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_MEDIA_NEXT`) a `androidx.media3.session.MediaButtonReceiver`, dichiarato nel manifest. È la stessa strada delle cuffie Bluetooth.
- Media3 avvia il servizio in primo piano e passa il comando alla sessione. Il tocco su un widget è un'interazione dell'utente, quindi Android permette di avviare il servizio in primo piano.
- **Ad app chiusa:** se il player è vuoto, Media3 chiama `MediaSession.Callback.onPlaybackResumption`. Brano lo implementa restituendo la coda salvata in `QueueStateStore` (brani, indice, posizione) e la riproduzione riparte dal punto salvato.
  - Oggi `restoreQueue` gira in `onCreate`. Ripresa e ripristino usano la stessa funzione di lettura della coda; se il ripristino ha già caricato la coda, la ripresa non la carica una seconda volta.

**Manifest**
- `<receiver android:name=".widget.BranoWidgetProvider" android:exported="false">` con `APPWIDGET_UPDATE` e `@xml/brano_widget_info`.
- `<receiver android:name="androidx.media3.session.MediaButtonReceiver" android:exported="true">` con `android.intent.action.MEDIA_BUTTON`.

## 3. Errori

- **Copertina mancante o illeggibile:** icona `MusicNote`, colore di default. Il widget non resta mai vuoto.
- **Coda salvata vuota o con brani spariti:** `onPlaybackResumption` restituisce una lista vuota e Media3 non avvia la riproduzione. Il widget passa allo stato "Tocca ▶ per iniziare", mentre il tocco sui testi apre l'app.
- **Errori in `WidgetUpdater`:** vengono catturati e scritti nel log, senza mai bloccare o far chiudere il servizio di riproduzione.

## 4. Test

**Test automatici (JVM/Robolectric)**
- `WidgetState` dal brano corrente, dalla pausa e da nessun brano: titoli, stato vuoto e icona play/pausa corretti.
- `WidgetStateStore`: salvataggio e lettura, e valori di default quando non c'è nulla.
- `WidgetUpdater`: stato uguale significa nessun aggiornamento; nessun widget significa nessun lavoro.
- `onPlaybackResumption`: restituisce la coda salvata con indice e posizione; coda vuota dà lista vuota.
- `WidgetRenderer` con Robolectric: le tre `PendingIntent` portano i keycode giusti.

**Emulatore**
1. Aggiungere il widget e verificare l'anteprima e lo stato iniziale.
2. Riprodurre un brano e verificare titolo, copertina, colore e icona pausa.
3. Usare i tasti precedente, play/pausa e successivo.
4. Chiudere l'app dai recenti, aspettare che il servizio si fermi, premere ▶: deve ripartire dall'ultimo punto.
5. Riavviare l'emulatore: il widget mostra ancora l'ultimo brano.

**Misura:** la riproduzione non deve rallentare (avvio a freddo come v1.7 entro il rumore).

## Fuori dallo scopo

- Formati 4x2 e 2x2.
- Barra di avanzamento nel widget, perché richiederebbe aggiornamenti periodici.
- Tasto preferito o shuffle nel widget.
- Temi chiari del widget.
