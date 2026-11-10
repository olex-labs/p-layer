# Pubblicare una versione di P-layer

## 1. Chiave di firma (una volta sola)

Android accetta un aggiornamento solo se è firmato con la **stessa chiave** della versione installata. Le release pubbliche devono quindi essere firmate sempre con la tua chiave, mai con quella di debug.

1. Apri un terminale nella cartella del progetto e lancia questo comando (`keytool` è incluso in Android Studio):

   ```bash
   "/c/Program Files/Android/Android Studio/jbr/bin/keytool" -genkeypair -v -keystore player-release.jks -alias player -keyalg RSA -keysize 4096 -validity 36500
   ```

   Ti chiede una password robusta e qualche dato facoltativo (nome, città).
2. Crea il file `keystore.properties` nella cartella del progetto:

   ```properties
   storeFile=player-release.jks
   storePassword=LA_TUA_PASSWORD
   keyAlias=player
   keyPassword=LA_TUA_PASSWORD
   ```

3. **Fai una copia di sicurezza** di `player-release.jks` e delle password fuori dal PC, per esempio in un password manager. Se perdi la chiave non potrai più pubblicare aggiornamenti installabili sopra la versione esistente.

`*.jks` e `keystore.properties` sono esclusi da git: non finiscono mai nel repository.

## 2. Segreti su GitHub (una volta sola)

La release automatica firma l'APK sui server di GitHub. Per farlo le servono quattro segreti, da aggiungere in **Settings → Secrets and variables → Actions → New repository secret** del repository `olex-labs/p-layer`:

| Nome | Valore |
|---|---|
| `KEYSTORE_BASE64` | il file `.jks` codificato in base64 (vedi sotto) |
| `KEYSTORE_PASSWORD` | la password del keystore |
| `KEY_ALIAS` | `player` |
| `KEY_PASSWORD` | la password della chiave |

Per ottenere il valore di `KEYSTORE_BASE64` su Windows (PowerShell):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("player-release.jks")) | Set-Clipboard
```

Poi incolla il valore nel segreto.

## 3. Nuova versione

1. In `app/build.gradle.kts` aumenta `versionCode` di 1 e aggiorna `versionName` (per esempio `"1.10.0"`).
2. In `CHANGELOG.md` aggiungi la sezione `## [1.10.0] - AAAA-MM-GG`, con le voci Added / Changed / Fixed.
3. Fai il commit su `main`, poi crea e invia il tag:

   ```bash
   git tag v1.10.0
   git push origin main v1.10.0
   ```

4. Il workflow **Release** esegue i test, crea `P-layer-v1.10.0.apk` firmato e pubblica la release. Le note della release sono prese dalla sezione del CHANGELOG.

## Build locale (senza GitHub)

```bash
./gradlew :app:assembleRelease
```

L'APK è in `app/build/outputs/apk/release/`. Se `keystore.properties` esiste è firmato con la tua chiave, altrimenti con quella di debug, che va bene solo per le prove.
