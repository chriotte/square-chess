# Square Chess

Offline chess for square and near-square screens, compact phones and phones with a physical keyboard.

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="A game against the computer, with legal-move markers" width="320">

## Features

- **Play the computer** at ten levels, from a true beginner to full-strength Fairy-Stockfish. The engine runs on your phone.
- **Over the board:** two players on one phone, with an optional chess clock.
- **Record** games played on a real board, then review them move by move.
- **Chess clock** for over-the-board games, with increment and delay.
- **Keyboard entry:** type `e4`, `Nf3` or `e2e4` on a physical keyboard, or tap and drag pieces.
- **Review, undo, resign and draw claims.**
- **PGN export and import** of your whole game library, for Lichess, ChessBase and other chess software.
- Board colours, move sounds, vibration, legal-move markers and coordinates.

## Why Square Chess?

Most chess apps are designed for tall phones and leave a small board on square screens. Square Chess sizes the board first and fits everything else around it. Optional information appears only in space the board cannot use.

## Privacy

Square Chess works fully offline and does not require Google Play Services or a Google account. It has **no Internet permission**, no account, no advertising and no analytics. Your games and settings stay on your device. See the [privacy policy](PRIVACY_POLICY.md).

## Installation

- **Google Play:** [com.dataespresso.squarechess](https://play.google.com/store/apps/details?id=com.dataespresso.squarechess)
- **GitHub Releases:** signed APK with SHA-256 checksum (planned)
- **F-Droid:** planned

Play and GitHub/F-Droid builds are signed with different keys, so one cannot update the other. To move between them, export your games (Game history → Export), install the other build, and import the file (Game history → Import).

## Supported devices

Android 10 (API 29) or newer, ARM64. Designed on the Unihertz Titan 2 Elite and tested with emulator profiles for other square and keyboard phones (`docs/testing/test-plan.md`). Works on ordinary phones too.

## Building from source

JDK 17, the Android SDK (platform 36), NDK 28.2.13676358 and CMake 3.22.1:

```sh
./gradlew check assembleDebug
```

Details: [docs/development/building.md](docs/development/building.md). Architecture: [docs/architecture/overview.md](docs/architecture/overview.md).

## Chess engine

Square Chess uses [Fairy-Stockfish](https://github.com/fairy-stockfish/Fairy-Stockfish) with classical evaluation, from the Lichess mobile app's pinned [multistockfish](https://github.com/lichess-org/dart-multistockfish) package, compiled from source (`native/fairy/`). Difficulty levels set only the engine's own Skill Level and MultiPV options. How the levels were chosen: [docs/engine/](docs/engine/).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).

## Licence

Square Chess is free software: [GNU GPL v3.0 or later](LICENSE). Third-party components and their licences: [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
