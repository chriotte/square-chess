# Changelog

## 1.0.7 — unreleased
- Release builds use JDK 21, the same as the F-Droid build server, so F-Droid's build of the source matches the signed APK on GitHub Releases. No changes to the app itself.

## 1.0.6 — 29 September 2026
- Reproducible on any day and computer: the engine no longer embeds its compile date, and packaged assets keep LF line endings on every OS. No changes to the app itself.

## 1.0.5 — 29 September 2026
- Reproducible build: the native engine no longer embeds the build folder paths, so two builds of the same tag are byte-identical. This lets F-Droid publish the same signed APK as GitHub Releases. No changes to the app itself.
- F-Droid recipe (`.fdroid.yml`) and documentation (`docs/releases/fdroid.md`).
- CI uses the runner's Android SDK and current action versions; the release workflow can rebuild an existing tag.

## 1.0.4 — 29 September 2026
- Import games from a PGN file (Game history → Import): all games in the file, comments and variations ignored, duplicates skipped, unreadable games reported. Together with Export, this moves a game library between phones or between Play and other sources.
- New "How to play" guide in Help & About: game types, moving pieces, keyboard moves, levels, clocks, review, draws, game history and settings.
- About Square Chess and App information name the author, Christopher Ottesen.
- First standalone APK on GitHub Releases, signed with the standalone release key.
- Removed the Stockfish 19 build and the unused DataStore library; `verifyReleasePolicy` guards against proprietary Google libraries and new permissions.
- Emulator profile for the Light Phone III (1080 × 1240, 420 dpi).
- Repository prepared for open source: README, CONTRIBUTING, SECURITY, THIRD_PARTY_LICENSES, `docs/`, `fastlane/` store metadata, CI and tag-driven release workflows. Lint errors fixed.

## 1.0.3 — 29 September 2026 (Play internal testing)
- Drag-and-drop stays accurate after screen size changes.
- Game export works with more storage providers.

## 1.0.2 — 28 September 2026 (first Play release)
- Fairy-Stockfish (classical evaluation, no NNUE) replaces Stockfish 19; the app is about 80 MB smaller. Ten difficulty levels set the engine's own Skill Level and MultiPV.
- Fixed a race in the engine output pipe that could return the previous move.
- Full PGN export of the game library (filter by mode and period; save or share); game delete; dates and move counts in history.
- Resign against the computer; grouped game menu; last move in the header; review arrows no longer cover the board.
- Larger legal-move dots and capture rings, drag-and-drop, promotion with piece images, move sounds and vibration.
- Settings: sounds, vibration, legal moves, coordinates, board colours.
- Captured pieces and a move line appear only where the board cannot use the space.
- Privacy policy linked in Help & About.

## Before 1.0.2 — development
- Compact square board and physical-keyboard UCI/SAN entry.
- Computer, two-player and recording game types; integrated and standalone chess clocks.
- Room persistence with migrations, SAN history, promotion chooser, FEN import, PGN and FEN sharing.
