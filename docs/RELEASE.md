# Pubblicare una versione di Brano

## 1. Chiave di firma (una volta sola)

Android accetta un aggiornamento solo se è firmato con la **stessa chiave** della versione installata.
Oggi gli APK di prova sono firmati con la chiave di debug di questo PC. Per l'uso vero serve una chiave tua.

1. Apri un terminale nella cartella del progetto e lancia (il programma `keytool` è incluso in Android Studio):

   ```bash
   "/c/Program Files/Android/Android Studio/jbr/bin/keytool" -genkeypair -v -keystore brano-release.jks -alias brano -keyalg RSA -keysize 4096 -validity 36500
   ```

   Ti chiede una password (scegline una robusta) e qualche dato facoltativo (nome, città).
2. Crea il file `keystore.properties` nella cartella del progetto, con le tue password:

   ```properties
   storeFile=brano-release.jks
   storePassword=LA_TUA_PASSWORD
   keyAlias=brano
   keyPassword=LA_TUA_PASSWORD
   ```

3. **Fai una copia di sicurezza** di `brano-release.jks` e delle password, fuori dal PC (chiavetta, password manager).
   Se perdi la chiave non potrai più aggiornare l'app sui telefoni dove è installata.

`brano-release.jks` e `keystore.properties` sono esclusi da git: non finiscono mai nel repository.
Senza questi file la build release usa la chiave di debug, come adesso.

Nota: se sul telefono hai installato un APK di prova (chiave di debug), la prima volta che installi un APK
firmato con la tua chiave devi disinstallare quello vecchio. Gli aggiornamenti successivi andranno lisci.

## 2. Nuova versione

1. In `app/build.gradle.kts` aumenta `versionCode` di 1 e aggiorna `versionName` (es. `"1.6"`).
2. Crea l'APK:

   ```bash
   ./gradlew :app:assembleRelease
   ```

3. Il file è in `app/build/outputs/apk/release/app-release.apk`.
