# Test plan

## Automated

| Suite | Command | Covers |
|---|---|---|
| JVM unit tests (75) | `./gradlew testDebugUnitTest testReleaseUnitTest` | Rules and perft, SAN/notation entry, draws and dead positions, clocks, FEN import, PGN export and import, difficulty levels, captured pieces, move sounds, toolbar layout, feedback reports |
| Lint and release policy | `./gradlew check` | Android lint; no proprietary Google/Firebase/Play libraries; release permissions on the allow-list |
| Device tests (29, dev app only) | see `docs/development/building.md` | Engine (legal moves at all levels, restart, cancellation, terminal positions), keyboard entry and review, clocks and lifecycle, Room migrations, layout, help and feedback |
| Engine calibration (opt-in) | `-e beginnerCalibration true [-e levels 1,2] [-e rounds N]` on `BeginnerCalibrationDeviceTest` | Tactical probes and games between levels; fails on any stale engine output |
| Engine benchmark / self-play (opt-in) | `-e engineBenchmark true` / `-e engineSelfPlay true` on `FairyEngineDeviceTest` | Response time, depth, memory; a complete self-play game |
| Process death (host-driven) | `scripts/test-clock-process-recovery.ps1` | Clock recovery after the app process is killed |

## Emulator profiles

Create them with `scripts/setup-emulators.ps1` (add `-InstallMissingComponents` to install the emulator and the API 36 image). They approximate screen size, density and keyboard only — not cutouts, fold transitions or vendor software.

| AVD | Resolution | Density | Keyboard |
|---|---:|---:|---|
| `SquareChess_Titan_2_Elite` | 1080 × 1200 | 300 dpi | Yes |
| `SquareChess_Titan_2` | 1440 × 1440 | 480 dpi | Yes |
| `SquareChess_Clicks_Communicator` | 1080 × 1200 | 400 dpi | Yes |
| `SquareChess_Light_Phone_III` | 1080 × 1240 | 420 dpi | No |
| `SquareChess_Fold_Outer` | 1080 × 2092 | 420 dpi | No |
| `SquareChess_Fold_Inner` | 1840 × 2208 | 420 dpi | No |

The Light Phone III profile matches its 3.92-inch 1080 × 1240 AMOLED screen (about 419 ppi); it is plain Android, not LightOS.

## Manual checks before a release

1. Board size is identical before and after a UI change in every state (untimed, timed, paused, review) on the Titan 2 Elite profile and on the device.
2. A full game against the computer on a physical device; a timed over-the-board game; a recorded game with export and re-import.
3. App in airplane mode, with no Google account: start, play, save, export, import, restart.
4. `apksigner verify --print-certs` shows the expected certificate (`docs/releases/signing.md`).

## Not yet covered

TalkBack and large-font passes on every screen; 16 KB page-size runtime test; fold/unfold transitions; LightOS itself.
