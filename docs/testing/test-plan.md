# Test plan

## Automated

| Suite | Command | Covers |
|---|---|---|
| JVM unit tests (134) | `./gradlew testDebugUnitTest testReleaseUnitTest` | Rules and perft, SAN/notation entry, draws and dead positions, clocks, FEN import, PGN export and import, difficulty levels, captured pieces, move sounds, toolbar layout, feedback reports, evaluation, openings, e-ink detection, puzzles (rules, pack, review, training level, selection) |
| Lint and release policy | `./gradlew check` | Android lint; no proprietary Google/Firebase/Play libraries; release permissions on the allow-list |
| Device tests (dev app only) | see `docs/development/building.md` | Engine (legal moves at all levels, restart, cancellation, terminal positions), keyboard entry and review, clocks and lifecycle, Room migrations, layout, help and feedback, evaluation, puzzles (`PuzzleDeviceTest`; see `docs/puzzles/testing.md`) |
| Engine calibration (opt-in) | `-e beginnerCalibration true [-e levels 1,2] [-e rounds N]` on `BeginnerCalibrationDeviceTest` | Tactical probes and games between levels; fails on any stale engine output |
| Engine benchmark / self-play (opt-in) | `-e engineBenchmark true` / `-e engineSelfPlay true` on `FairyEngineDeviceTest` | Response time, depth, memory; a complete self-play game |
| Process death (host-driven) | `scripts/test-clock-process-recovery.ps1` | Clock recovery after the app process is killed |

## Emulator profiles

Create them with `scripts/setup-emulators.ps1` (add `-InstallMissingComponents` to install the emulator and the system images). They approximate screen size, density and keyboard only — not cutouts, fold transitions or vendor software.

| AVD | Resolution | Density | Keyboard | Android |
|---|---:|---:|---|---|
| `SquareChess_Titan_2_Elite` | 1080 × 1200 | 300 dpi | Yes | 16 (API 36) |
| `SquareChess_Titan_2` | 1440 × 1440 | 480 dpi | Yes | 16 (API 36) |
| `SquareChess_Clicks_Communicator` | 1080 × 1200 | 400 dpi | Yes | 16 (API 36) |
| `SquareChess_Light_Phone_III` | 1080 × 1240 | 420 dpi | No | 16 (API 36) |
| `SquareChess_Fold_Outer` | 1080 × 2092 | 420 dpi | No | 16 (API 36) |
| `SquareChess_Fold_Inner` | 1840 × 2208 | 420 dpi | No | 16 (API 36) |
| `SquareChess_Eink_Tablet` | 1404 × 1872 | 227 dpi | No | 16 (API 36) |
| `SquareChess_BlackBerry_Priv` | 1440 × 2560 | 560 dpi | Yes | 8.0 (API 26) |
| `SquareChess_Eink_Android8` | 1404 × 1872 | 300 dpi | No | 8.0 (API 26), 32-bit x86, 2 GB |
| `SquareChess_Boox_Go_6` | 1072 × 1448 | 300 dpi | No | 13 (API 33), 2 GB |

The e-ink tablet profile has the size and density of a 10.3-inch e-reader such as the BOOX Note3; it shows layout only, not e-paper refresh. Turn on Settings → E-ink mode in the app.

The two Android 8.0 profiles test the oldest supported Android version (minSdk 26):

- `SquareChess_Eink_Android8` is an older 7.8-inch e-ink tablet. Its 32-bit image runs the app's
  32-bit engine (the debug build adds `x86` for it; release builds ship `armeabi-v7a`). An x86 PC
  cannot run 32-bit ARM code, so test a real 32-bit ARM device before a release.
- `SquareChess_BlackBerry_Priv` has the Priv's screen and keyboard. The real Priv stops at Android 6,
  which the app does not support; the profile runs Android 8.0 instead.

The Light Phone III profile matches its 3.92-inch 1080 × 1240 AMOLED screen (about 419 ppi); it is plain Android, not LightOS.

## Manual checks before a release

1. Board size is identical before and after a UI change in every state (untimed, timed, paused, review) on the Titan 2 Elite profile and on the device.
2. A full game against the computer on a physical device; a timed over-the-board game; a recorded game with export and re-import.
3. App in airplane mode, with no Google account: start, play, save, export, import, restart.
4. `apksigner verify --print-certs` shows the expected certificate (`docs/releases/signing.md`).

## Not yet covered

TalkBack and large-font passes on every screen; 16 KB page-size runtime test; fold/unfold transitions; LightOS itself.
