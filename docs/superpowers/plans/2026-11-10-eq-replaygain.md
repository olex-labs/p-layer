# P-layer v1.11 Equalizer and ReplayGain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A preset-only equalizer and automatic (album or track) ReplayGain. Both are applied by the playback service and set from the Settings screen.

**Architecture:**
- **Pure logic, unit-tested:**
  - `EqPreset` and `EqCurve`: the presets and how they map onto the device's bands;
  - `ReplayGainTags`: reads the tags from Media3 metadata;
  - `ReplayGainPolicy`: picks album or track gain and applies the limits;
  - `ReplayGainProcessor`: scales PCM samples.
- **Thin Android glue:** `EqualizerController` wraps `android.media.audiofx.Equalizer`. `PlaybackService` connects everything to player events and to the settings.

**Tech Stack:** Kotlin, Media3 1.8 (ExoPlayer, `BaseAudioProcessor`, `DefaultAudioSink`), DataStore, Compose M3, JUnit4 + Truth + Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-10-eq-replaygain-design.md`

## Global Constraints

- No new dependencies.
- Reference frequencies in Hz: 60, 230, 910, 3600, 14000.
- The preset table in spec §1 is the source of the dB values.
- Final ReplayGain is clamped to **−24 dB … +12 dB**, with a peak limit of linear gain ≤ `1 / peak`.
- Album gain is used only when the queue has ≥ 2 items, all with the same `albumId`, and shuffle is off.
- Settings defaults: `eqPreset = OFF`, `replayGain = true`. Preference keys are `eq_preset` and `replay_gain`. An unknown stored value means `OFF`.
- Every new string goes in both `values/` (English) and `values-it/` (Italian). `StringsParityTest` must stay green.
- Any equalizer failure is logged and never stops playback.
- Version 1.11.0, versionCode 11. LRCLIB User-Agent: "P-layer/1.11 (Android music player)".
- Gradle runs from PowerShell: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat ...`.

## Review Focus

1. **Device bands below 60 Hz or above 14 kHz, or only 3 bands:** values are clamped to the edges and interpolated, never out of the device range. Task 1 has the edge tests.
2. **A ReplayGain tag in an odd format:** `"-6,50 dB"`, `"+0 dB"` or garbage gives a number or null, never a crash. Task 2 has the tests.
3. **Shuffle toggled or queue replaced while a track plays:** the gain is recomputed (album to track). Task 4 hooks `onShuffleModeEnabledChanged` and `onTimelineChanged`, checked by the emulator log in Task 5.
4. **PCM float input with values already near ±1 and a positive gain:** the output is clamped to [−1, 1]. Task 3 has the test.
5. **`Equalizer` constructor throws (an emulator or device without the effect):** the service keeps playing and the Settings row shows "not available". Task 4 has a `runCatching` and the emulator check in Task 5.

---

### Task 1: Equalizer presets and band mapping

**Files:**
- Create: `app/src/main/java/app/brano/playback/eq/EqPreset.kt`
- Create: `app/src/main/java/app/brano/playback/eq/EqCurve.kt`
- Test: `app/src/test/java/app/brano/playback/eq/EqCurveTest.kt`

**Interfaces:**
- Produces:
  - `enum class EqPreset(val db: FloatArray?) { OFF(null), BASS(…), ROCK, POP, JAZZ, CLASSICAL, VOCAL, ELECTRONIC, ACOUSTIC, NIGHT }`, with the values from the spec table and in that order.
  - `companion fun fromName(name: String?): EqPreset` returns `OFF` for null or an unknown name.
  - `object EqCurve { val REFERENCE_HZ = intArrayOf(60, 230, 910, 3600, 14000); fun bandLevels(preset: EqPreset, centersMilliHz: IntArray, minMb: Short, maxMb: Short): ShortArray }`. The levels are in millibels.
  - `bandLevels` interpolates linearly in `ln(frequency)` between the two neighbouring reference points, holds the edge values outside 60…14000 Hz, multiplies by 100, rounds, and clamps to `[minMb, maxMb]`. For `OFF` it returns zeros.

- [ ] **Step 1: Failing tests**
  - `fiveReferenceBands_giveTheTableValues`: centres `[60000, 230000, 910000, 3600000, 14000000]` mHz, ROCK, range ±1500 → `[400, 200, -100, 200, 400]`.
  - `threeBands_interpolate`: centres `[120000, 1500000, 9000000]`, BASS → each value lies between its two neighbouring reference values (assert the middle band is between 0 and 0, i.e. `0`; the first is between 400 and 600).
  - `outsideRange_holdsEdges`: a centre at `20000` with BASS → `600`; a centre at `20000000` with BASS → `100`.
  - `clampedToDeviceRange`: BASS with range ±300 → the first band is `300`.
  - `off_isZero`.
  - `fromName_unknownIsOff`: `fromName("X") == OFF` and `fromName(null) == OFF`.
- [ ] **Step 2: Run** `.\gradlew.bat :app:testDebugUnitTest --tests 'app.brano.playback.eq.*'`. Expected: FAIL (unresolved).
- [ ] **Step 3: Implement both files.**
- [ ] **Step 4: Run again.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(eq): presets and mapping onto device bands`.

### Task 2: ReplayGain tags and policy

**Files:**
- Create: `app/src/main/java/app/brano/playback/rg/ReplayGainTags.kt`
- Create: `app/src/main/java/app/brano/playback/rg/ReplayGainPolicy.kt`
- Test: `app/src/test/java/app/brano/playback/rg/ReplayGainTagsTest.kt`, `ReplayGainPolicyTest.kt`

**Interfaces:**
- Produces:
  - `data class ReplayGainTags(val trackGainDb: Float?, val albumGainDb: Float?, val trackPeak: Float?, val albumPeak: Float?) { companion object { val NONE: ReplayGainTags; fun from(metadata: androidx.media3.common.Metadata?): ReplayGainTags; fun parseDb(text: String?): Float? } }`.
    - `from` reads `TextInformationFrame` with `id == "TXXX"` (the tag name is `description`; the value is `values.first()`) and `VorbisComment` (the tag name is `key`).
    - Tag names are compared without regard to case.
    - `parseDb` trims the text, drops a trailing `dB`, turns `,` into `.`, and returns `toFloatOrNull()`.
  - `object ReplayGainPolicy { fun useAlbum(albumIds: List<Long>, shuffle: Boolean): Boolean; fun linearGain(tags: ReplayGainTags, album: Boolean): Float }`.
    - `useAlbum` is true only when there are ≥ 2 items, all ids are equal, and shuffle is off.
    - `linearGain` takes the preferred dB value (or the other one if it is missing, or 0), clamps it to [−24, 12], converts with `10^(dB/20)`, and, if the matching peak (or the other one) is > 0, takes `min(gain, 1/peak)`.

- [ ] **Step 1: Failing tests**
  - Tags:
    - `txxx_trackAndAlbum`: frames `TXXX(replaygain_track_gain="-6.50 dB")`, `TXXX(REPLAYGAIN_ALBUM_GAIN="-7.1 dB")` and `TXXX(REPLAYGAIN_TRACK_PEAK="0.98")` → −6.5, −7.1, 0.98, null.
    - `vorbis_lowercaseKeys`.
    - `parseDb_variants`: `"-6,50 dB" → -6.5`, `"+1.2 dB" → 1.2`, `"+0 dB" → 0`, `"loud" → null`, `null → null`.
    - `noMetadata_isNone`.
  - Policy:
    - `useAlbum`: `[5, 5, 5]` without shuffle → true; with shuffle → false; `[5, 6]` → false; `[5]` → false.
    - `linearGain_minus6dB_isAbout0_501`: `isWithin(0.001f).of(0.501f)`.
    - `missingPreferred_usesOther`.
    - `peakLimitsBoost`: track +6 dB with peak 0.9 → `1/0.9`.
    - `clampedTo12dB`: +20 dB with no peak → `10^(12/20)`.
    - `noTags_isOne`.
- [ ] **Step 2: Run** `--tests 'app.brano.playback.rg.*'`. Expected: FAIL.
- [ ] **Step 3: Implement both files.**
- [ ] **Step 4: Run again.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(replaygain): read tags from Media3 metadata, album/track policy`.

### Task 3: ReplayGain audio processor

**Files:**
- Create: `app/src/main/java/app/brano/playback/rg/ReplayGainProcessor.kt`
- Test: `app/src/test/java/app/brano/playback/rg/ReplayGainProcessorTest.kt`

**Interfaces:**
- Produces: `@UnstableApi class ReplayGainProcessor : BaseAudioProcessor() { @Volatile var gain: Float = 1f }`.
  - `onConfigure` accepts `C.ENCODING_PCM_16BIT` and `C.ENCODING_PCM_FLOAT` and returns the same format.
  - Any other encoding throws `UnhandledAudioFormatException`.
  - `queueInput` writes every sample scaled by `gain`, clamped to [−32768, 32767] for 16-bit and [−1, 1] for float.
  - At `gain == 1f` the bytes are copied unchanged.

- [ ] **Step 1: Failing tests** (configure with `AudioFormat(44100, 2, encoding)`, `flush()`, `queueInput(buffer)`, read `getOutput()`):
  - `pcm16_halfGain`: `[1000, -2000]` with gain 0.5 → `[500, -1000]`.
  - `pcm16_clamps`: `[30000]` with gain 2 → `[32767]`.
  - `float_clamps`: `[0.9f, -0.9f]` with gain 2 → `[1f, -1f]`.
  - `unityGain_isIdentity`.
- [ ] **Step 2: Run** `--tests 'app.brano.playback.rg.ReplayGainProcessorTest'`. Expected: FAIL.
- [ ] **Step 3: Implement it.**
- [ ] **Step 4: Run again.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(replaygain): PCM gain processor with clamping`.

### Task 4: Settings, equalizer controller, service wiring, Settings UI

**Files:**
- Modify: `app/src/main/java/app/brano/data/settings/AppSettings.kt`. Add `val eqPreset: EqPreset = EqPreset.OFF` and `val replayGain: Boolean = true`.
- Modify: `app/src/main/java/app/brano/data/settings/SettingsRepository.kt`. Add keys `EQ_PRESET = stringPreferencesKey("eq_preset")` and `REPLAY_GAIN = booleanPreferencesKey("replay_gain")`, with read and write. Read the preset through `EqPreset.fromName`.
- Create: `app/src/main/java/app/brano/playback/eq/EqualizerController.kt`. It has `fun attach(audioSessionId: Int)`, `fun apply(preset: EqPreset)` and `fun release()`.
  - It holds `Equalizer(0, sessionId)`.
  - `apply` maps the preset with `EqCurve.bandLevels` (using `getCenterFreq` and `bandLevelRange`), sets the bands, and turns the equalizer on. For `OFF` it turns it off.
  - Every call is wrapped in `runCatching` with `Log.w`.
  - It also has `companion fun isAvailable(): Boolean`, which checks `AudioEffect.queryEffects()` for `EFFECT_TYPE_EQUALIZER`.
- Modify: `app/src/main/java/app/brano/playback/PlaybackService.kt`.
  - Inject `SettingsRepository`. Build the player with `setRenderersFactory(object : DefaultRenderersFactory(this) { override fun buildAudioSink(...) = DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(rgProcessor)).build() })`.
  - Collect the settings in `scope`: call `eq.apply(eqPreset)`, store `replayGainOn`, then call `updateGain()`.
  - Add `onTracksChanged`, `onShuffleModeEnabledChanged` and `onTimelineChanged` (alongside the existing code). Each of them calls `updateGain()`.
  - Add `onAudioSessionIdChanged`, which calls `eq.attach(id)` and then `eq.apply(current)`.
  - Release the controller in `onDestroy`.
  - `updateGain()` reads the selected audio format's `metadata` from `player.currentTracks`, uses the queue `albumId`s (through `MediaItemMapper.toTrackOrNull`) and `shufflingPlayer.shuffleModeEnabled`, and sets `rgProcessor.gain` (1f when off). It logs `ReplayGain gain=%.3f album=%b` at debug level.
- Modify: `ui/settings/SettingsViewModel.kt`. Add `setEqPreset(p)` and `setReplayGain(b)`.
- Modify: `ui/settings/SettingsScreen.kt`.
  - Add the "Equalizer" row: its supporting text is the preset label, or `eq_unavailable` when `!EqualizerController.isAvailable()` (then the row is disabled). Tapping it opens an `AlertDialog` with radio rows for the presets.
  - Add the "Volume normalisation" switch, placed after the online lyrics row.
- Modify: `res/values/strings.xml` and `res/values-it/strings.xml`. Add:
  - `settings_eq` — "Equalizer" / "Equalizzatore";
  - `eq_unavailable` — "Not available on this phone" / "Non disponibile su questo telefono";
  - `eq_off`, `eq_bass`, `eq_rock`, `eq_pop`, `eq_jazz`, `eq_classical`, `eq_vocal`, `eq_electronic`, `eq_acoustic`, `eq_night` — Off/Spento, Bass/Bassi, Rock, Pop, Jazz, Classical/Classica, Vocal/Voce, Electronic/Elettronica, Acoustic/Acustica, Night/Notte;
  - `settings_replaygain` — "Volume normalisation" / "Volume uniforme";
  - `settings_replaygain_hint` — "Uses the files' ReplayGain tags to play songs at the same loudness" / "Usa i tag ReplayGain dei file per suonare i brani allo stesso volume".
- Test: extend `app/src/test/java/app/brano/data/settings/SettingsRepositoryTest.kt` (or create it if missing).

**Interfaces:**
- Consumes: `EqPreset`, `EqCurve` (Task 1); `ReplayGainTags`, `ReplayGainPolicy` (Task 2); `ReplayGainProcessor` (Task 3).

- [ ] **Step 1: Failing tests**
  - `defaults_eqOffAndReplayGainOn`.
  - `eqPreset_roundTrip`: ROCK.
  - `unknownStoredPreset_isOff`: write the raw string "LOUD" to `eq_preset`, read it back → `OFF`.
- [ ] **Step 2: Run** `--tests '*SettingsRepositoryTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** the settings, the controller, the service wiring, the view model, the screen and the strings.
- [ ] **Step 4: Run** `:app:testDebugUnitTest :app:assembleRelease :app:lintDebug`. Expected: all pass, `StringsParityTest` green.
- [ ] **Step 5: Commit** `feat: equalizer presets and ReplayGain wired into playback and settings`.

### Task 5: Emulator verification, version, docs

**Files:**
- Modify: `app/build.gradle.kts` (versionCode 11, versionName "1.11.0").
- Modify: `LrclibClient.kt` (User-Agent 1.11).
- Modify: `CHANGELOG.md` (add a `[1.11.0]` Added section and the compare link), `README.md` and `README.it.md` (feature bullets; remove "Equalizer and ReplayGain" from the roadmap), `docs/TESTING.md` (a v1.11 section that notes the emulator has no audio).

- [ ] **Step 1: Make an MP3 copy with a ReplayGain tag.** Write a Python script in the scratchpad that prepends an ID3v2.3 tag with `TXXX REPLAYGAIN_TRACK_GAIN=-6.00 dB` to a copy of `/sdcard/Music/a1.mp3`, then push it as `/sdcard/Music/rg-test.mp3` and rescan.
- [ ] **Step 2: Play rg-test from the library.** Expected: `adb logcat -d | Select-String 'ReplayGain gain'` shows `gain=0.501 album=false`.
- [ ] **Step 3: Play an album from its album page** (shuffle off). Expected: the log shows `album=true`.
- [ ] **Step 4: Settings → Equalizer → Rock, then restart the app.** Expected: Rock is still selected, and there are no `AudioEffect`/`Equalizer` exceptions in logcat (or the row shows "not available" if the emulator lacks the effect). Take a screenshot.
- [ ] **Step 5: Set the version, update the docs, build the release, copy `docs/preview/P-layer-v1.11.0.apk`, and commit** `chore: version 1.11.0, docs`.
