# Square Chess

An offline native Android chess app for compact displays and physical keyboards.

**Development checkpoint, not a release candidate.** PROJECT.md defines V1; IMPLEMENTATION_PLAN.md and TEST_PLAN.md track the remaining gates.

## Build

1. Clone with `git clone --recurse-submodules https://github.com/chriotte/square-chess.git`.
2. Install JDK 17, Android SDK platform 36/build-tools 36.0.0, NDK 28.2.13676358 and CMake 3.22.1. Set `JAVA_HOME` and `ANDROID_HOME` or `sdk.dir` in untracked `local.properties`.
3. Run `./scripts/fetch-network.ps1` (PowerShell) to download and verify the pinned Stockfish NNUE asset.
4. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
5. Install `app/build/outputs/apk/debug/app-debug.apk` with ADB.

Release builds target ARM64/API 29+. Debug builds also include x86_64 for Android emulators. Runtime has no INTERNET permission. The build needs Internet access for tool/dependency/network downloads.

## Emulators

Install the Android Emulator and the `Google APIs x86_64` system image for API 36 in Android Studio's SDK Manager, then run this PowerShell script from the project root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup-emulators.ps1
```

Add `-InstallMissingComponents` to install the Android Emulator and API 36 image using the SDK Manager instead of installing them in Android Studio. It creates five AVDs with screen sizes and host-keyboard input approximating the devices below. Existing AVDs with the same names are left unchanged. A fold's inner and outer screens are separate AVDs; fold/unfold transitions and manufacturer-specific firmware are not emulated.

| AVD | Display resolution | Density | Hardware keyboard |
| --- | ---: | ---: | --- |
| `SquareChess_Fold_Inner` | 1840 x 2208 | 420 dpi | No |
| `SquareChess_Fold_Outer` | 1080 x 2092 | 420 dpi | No |
| `SquareChess_Clicks_Communicator` | 1080 x 1200 | 400 dpi | Yes |
| `SquareChess_Titan_2` | 1440 x 1440 | 480 dpi | Yes |
| `SquareChess_Titan_2_Elite` | 1080 x 1200 | 300 dpi | Yes |

Launch one from Android Studio's Device Manager or with the `emulator.exe` path printed by the setup script. If the SDK's emulator directory is on `PATH`, for example:

```powershell
emulator -avd SquareChess_Clicks_Communicator
.\gradlew.bat :app:installDebug
```

The debug variant includes x86_64 and ARM64; release remains ARM64-only. On a running keyboard AVD, use the desktop keyboard to exercise physical-key input. These profiles reproduce approximate display dimensions/density and keyboard availability, not exact device cutouts, fold posture behavior, keyboard firmware, or vendor-specific software.

## Controls

Tap source and destination; choose promotion when prompted. On a hardware keyboard, enter UCI (`e2e4`, `e7e8n`) or SAN (`Nf3`) then Enter. Backspace edits, Escape/Back cancels entry, F flips an otherwise empty move entry. Menu exposes history, takeback, result and home. All confirmed moves are stored locally in Room.

The home screen also offers a **Chess clock** for games played on a physical board. Choose a preset or set base time, increment and delay; select which side starts, then tap the active side after each move. Pause, resume, reset and rotate the display as needed. It does not create or update game records.

## Status

The compact board, three game modes, persistence, Stockfish JNI integration, integrated game clocks, the standalone over-the-board clock, single-game PGN sharing in all modes, optional position import (.fen), and current-position FEN sharing are implemented for feasibility testing. Levels are experimental settings, not calibrated ratings. Clock lifecycle, flagfall, delay, PGN formatting/sharing, and custom-FEN persistence have automated device coverage; richer OTB metadata/correction, manual FEN picker verification, post-game analysis, expanded layout, TalkBack/device matrix and release compliance remain required work. See USABILITY_REVIEW_WRAP_UP.md for current verification and limitations.

## Source and licences

GPL-3.0-or-later. Stockfish is pinned as a source submodule. Chesslib source is vendored under Apache-2.0. See LICENSES.md. This private development repository is not yet a public corresponding-source distribution endpoint.
