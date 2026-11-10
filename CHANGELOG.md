# Changelog

All notable changes to P-layer are listed here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/).

## [1.11.0] - 2026-11-10

### Added
- Equalizer with presets: Bass, Rock, Pop, Jazz, Classical, Vocal, Electronic, Acoustic and Night. It is off by default and uses the phone's own equalizer, adapted to its bands.
- Volume normalisation (ReplayGain), on by default. It uses the album value when you play a whole album in order and the song value otherwise. Tags are read from MP3 (ID3 TXXX), FLAC and OGG files, with protection against clipping.

## [1.10.0] - 2026-11-10

### Added
- English interface. English is now the default language and Italian is used when the phone is set to Italian.
- On Android 13+ you can set P-layer's language separately from the phone's (Settings → Apps → P-layer → Language).

### Changed
- Counts use real plurals ("1 song", "3 songs").
- The playlist import summary reads "Imported: 3 · not found: 1".

## [1.9.0] - 2026-11-10

First public release, under the new name **P-layer**.

### Changed
- The app is renamed from Brano to P-layer, and its application ID is now `io.github.olexlabs.player`. A phone with the old build installed has to uninstall it once before installing this one.

### Fixed
- Widget: one added while music is playing shows the live track straight away, with cover and colour.
- Widget: after a reboot it shows the last track as paused, with its cover.
- Widget: a cover or colour that fails to load no longer leaves the previous track on screen.
- Widget: ⏮ / ⏭ return at once when nothing is playing.
- Widget: a slow library scan at start-up no longer empties it.
- Resuming with shuffle on keeps the restored order, even if ▶ is pressed twice quickly.
- Lyrics: embedded in MP3 files that use ID3 "unsynchronisation" are now read.
- Music folders: when Android refuses read access to a folder, the app now says so.

## [1.8.0] - 2026-11-10

### Added
- A 4×1 home screen widget: cover, title, artist and previous / play-pause / next, in the album colour.
- ▶ on the widget or on headphones resumes the saved queue even when the app is closed.

### Fixed
- Songs whose files were deleted while the app was closed are skipped when the queue is restored.

## [1.7.0] - 2026-11-10

### Added
- A new app icon (lilac vinyl), a themed monochrome icon and a notification icon.

### Fixed
- A `.lrc` file added after lyrics were downloaded now takes priority over the downloaded copy.
- "No lyrics" no longer flashes before lyrics load.
- Fast lyric position updates stop when the player is off screen.
- Picking the same music folder twice no longer leaves the old access grant behind.

## [1.6.0] - 2026-11-10

### Added
- Music folders: choose which folders the library reads.
- Hide a song from the app, or move its file to the system trash.
- Synced lyrics from `.lrc` files, from tags inside MP3 and FLAC files, and optionally from LRCLIB. Tap a line to seek to it.

### Changed
- Covers are rounded squares instead of circles.

## [1.5.0] - 2026-11-10

### Added
- A landscape player layout.

### Changed
- Shuffle reorders the queue in the playback service, so the app, headphones and the lock screen all share it.

## [1.4.0] - 2026-11-09

### Added
- An icon set drawn for the app, plus app and notification icons.

## [1.3.0] - 2026-11-09

### Added
- File quality label (format and bitrate).

### Changed
- A more compact library layout.

## [1.2.0] - 2026-11-09

### Changed
- Dark-first theme, album headers with the cover colour, and player controls that are easier to reach with a thumb.

## [1.1.0] - 2026-11-09

### Changed
- Redesign: a player tinted by the album cover, a spinning vinyl disc, and a collapsing large top bar.

## [1.0.0] - 2026-11-09

### Added
- Library of songs, albums, artists and folders, with instant search.
- Background playback with Media3: notification and lock screen controls, headphone buttons.
- Queue, shuffle, repeat, sleep timer, favourites and playlists (M3U import and export).
- The queue and position are saved and restored.

[1.11.0]: https://github.com/olex-labs/p-layer/compare/v1.10.0...v1.11.0
[1.10.0]: https://github.com/olex-labs/p-layer/compare/v1.9.0...v1.10.0
[1.9.0]: https://github.com/olex-labs/p-layer/releases/tag/v1.9.0
[1.8.0]: https://github.com/olex-labs/p-layer/compare/v1.7.0...v1.8.0
[1.7.0]: https://github.com/olex-labs/p-layer/compare/v1.6.0...v1.7.0
[1.6.0]: https://github.com/olex-labs/p-layer/compare/v1.5.0...v1.6.0
[1.5.0]: https://github.com/olex-labs/p-layer/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/olex-labs/p-layer/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/olex-labs/p-layer/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/olex-labs/p-layer/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/olex-labs/p-layer/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/olex-labs/p-layer/commits/v1.0.0
