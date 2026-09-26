# Square Chess

An offline native Android chess app for compact displays and physical keyboards.

**Development checkpoint, not a release candidate.** PROJECT.md defines V1; IMPLEMENTATION_PLAN.md and TEST_PLAN.md track the remaining gates.

## Build

1. Clone with `git clone --recurse-submodules https://github.com/chriotte/square-chess.git`.
2. Install JDK 17, Android SDK platform 36/build-tools 36.0.0, NDK 28.2.13676358 and CMake 3.22.1. Set `JAVA_HOME` and `ANDROID_HOME` or `sdk.dir` in untracked `local.properties`.
3. Run `./scripts/fetch-network.ps1` (PowerShell) to download and verify the pinned Stockfish NNUE asset.
4. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
5. Install `app/build/outputs/apk/debug/app-debug.apk` with ADB.

ARM64/API 29+ only at this checkpoint. Runtime has no INTERNET permission. The build needs Internet access for tool/dependency/network downloads.

## Controls

Tap source and destination; choose promotion when prompted. On a hardware keyboard, enter UCI (`e2e4`, `e7e8n`) or SAN (`Nf3`) then Enter. Backspace edits, Escape/Back cancels entry, F flips an otherwise empty move entry. Menu exposes history, takeback, result and home. All confirmed moves are stored locally in Room.

## Status

Initial compact board, three basic untimed modes, persistence, and Stockfish JNI integration are implemented for feasibility testing. Levels are experimental settings, not calibrated ratings. Clocks, richer OTB metadata/correction, import/export, post-game analysis, expanded layout, TalkBack/device matrix and release compliance remain required work. See the phase report for executed tests.

## Source and licences

GPL-3.0-or-later. Stockfish is pinned as a source submodule. Chesslib source is vendored under Apache-2.0. See LICENSES.md. This private development repository is not yet a public corresponding-source distribution endpoint.
