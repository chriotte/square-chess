# Square Chess

**Chess built for square screens, compact devices, e-ink and physical keyboards.**

Square Chess is an offline Android chess app built especially for hardware that conventional chess apps often do not use well — square and near-square screens, compact devices, e-ink displays and physical keyboards — while still working on ordinary phones and tablets.

On phones with a physical QWERTY keyboard, you can also play by typing moves such as `e2e4`.

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="A game against the computer, with legal-move markers" width="320"> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" alt="E-ink mode: black-and-white board with hatched dark squares, last-move corner marks and legal-move dots" width="270">

## Features

- **Play against the computer** at ten difficulty levels, from beginner-friendly play up to full-strength Fairy-Stockfish. The engine runs entirely on your phone.
- **Play with a friend** on the same device, with an optional chess clock.
- **Record games played on a real board** and review them afterwards move by move.
- **Standalone chess clock** for over-the-board games, with increment and delay.
- **Physical keyboard input** using moves such as `e4`, `Nf3`, `O-O` or `e2e4`.
- **E-ink mode** with a black-and-white board, shape-based highlights and no animations, for e-ink devices.
- **Touch controls** with both tap-to-move and drag-and-drop.
- **Review your games** with move history, undo, resign and draw handling.
- **Import and export PGN** for Lichess, ChessBase and other chess software.
- **Customise the board** with colours, coordinates, legal-move markers, move sounds and vibration.

## Why Square Chess?

Most Android chess apps are designed around tall, colour phone screens.

Square Chess takes a board-first approach: the chessboard is made as large as possible, while controls and optional information are fitted around the space that remains. The same idea applies to e-ink: the board is drawn in clear black and white, moves are marked by shapes rather than colours, and nothing animates, so e-paper screens stay sharp.

It is particularly suited to square and near-square phones (including 1:1 and 9:10 displays), compact devices, e-ink readers and phones with physical keyboards, while still working well on conventional Android phones and tablets.

## Private and offline

Square Chess works fully offline and does not require Google Play Services or a Google account.

It has:

- **No Internet permission**
- **No account**
- **No advertising**
- **No analytics**

Your games and settings stay on your device.

See the [privacy policy](PRIVACY_POLICY.md).

## Installation

- **Google Play:** [Square Chess on Google Play](https://play.google.com/store/apps/details?id=com.dataespresso.squarechess)
- **GitHub:** [download the latest release](https://github.com/chriotte/square-chess/releases/latest) — signed APK with its SHA-256 checksum
- **F-Droid:** planned

Google Play and GitHub/F-Droid builds are signed with different keys, so they cannot update one another directly.

To move between builds:

1. Export your games from **Game history → Export**
2. Install the other build
3. Import them using **Game history → Import**

## Supported devices

Square Chess requires:

- Android 10 (API 29) or newer
- ARM64

It is designed and tested on the **Unihertz Titan 2 Elite** and an **e-ink tablet**, with additional emulator testing for other square-screen and physical-keyboard phones.

It also works on conventional Android phones and tablets.

See the [device testing plan](docs/testing/test-plan.md).

## Building from source

Requirements:

- JDK 21
- Android SDK, platform 36
- Android NDK 28.2.13676358
- CMake 3.22.1

Build and run the checks with:

```sh
./gradlew check assembleDebug
```

See [Building from source](docs/development/building.md) for full instructions.

For an overview of the project structure, see [Architecture](docs/architecture/overview.md).

## Chess engine

Square Chess uses [Fairy-Stockfish](https://github.com/fairy-stockfish/Fairy-Stockfish) with classical evaluation.

The engine is based on the version pinned by the Lichess mobile project's [multistockfish](https://github.com/lichess-org/dart-multistockfish) package and is compiled from source in [`native/fairy/`](native/fairy/).

Difficulty levels use Fairy-Stockfish's own **Skill Level** and **MultiPV** settings rather than a custom move-weakening algorithm.

See [engine documentation](docs/engine/) for calibration and implementation details.

## Contributing

Contributions, bug reports and suggestions are welcome.

See:

- [CONTRIBUTING.md](CONTRIBUTING.md)
- [SECURITY.md](SECURITY.md)

## Author

Square Chess is designed and built by **Christopher Ottesen** 

## Licence

Square Chess is free software licensed under the [GNU General Public License v3.0 or later](LICENSE).

Third-party components and their licences are documented in [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
