# Contributing to P-layer

Thanks for helping! Bug reports, ideas, translations and code are all welcome.

## Reporting a bug

Open an [issue](https://github.com/olex-labs/p-layer/issues/new/choose) using the bug template. Include:
- your phone model and Android version;
- the P-layer version (Settings → bottom of the page);
- the steps that cause the bug, what you expected, and what happened;
- the file format, if the bug only happens with some songs.

For a security problem, follow [SECURITY.md](SECURITY.md) instead.

## Suggesting a feature

Open an issue with the feature template and say what you want to do, not only how. P-layer aims to stay small and fast, so a feature that adds a heavy library or background work needs a strong reason.

## Working on the code

1. Fork the repository and create a branch from `main`, for example `fix/widget-cover`.
2. Build and test:
   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ```
3. Add a test for each behaviour you change. Unit tests run on the JVM with Robolectric and need no device.
4. Keep commits focused and use [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`.
5. Open a pull request. Fill in the template and add screenshots for UI changes.

### Code style

- Kotlin with the official code style. Prefer small files with one clear job.
- Screens use Jetpack Compose and Material 3, and the colours come from `ui/theme`.
- Don't do work on the main thread that can block it (disk, network, image decoding).
- Strings that users see belong in `res/values/strings.xml`. Translations go in `values-xx/`.
- Icons are generated: edit the SVGs in `design/icons`, then run `python -I design/to_android.py`.

### Project layout

```
app/src/main/java/app/brano/
  data/       library (MediaStore), database (Room), settings (DataStore)
  playback/   MediaSessionService, shuffle, queue, sleep timer
  lyrics/     LRC parser, embedded tags, LRCLIB client
  widget/     home screen widget
  ui/         Compose screens and theme
design/       icon sources and generators
docs/         design notes, release and test guides
```

The Kotlin package is still `app.brano`, the app's original name. The application ID is `io.github.olexlabs.player`.

## License

By contributing you agree that your contribution is licensed under the [GPL-3.0](LICENSE), like the rest of the project.
