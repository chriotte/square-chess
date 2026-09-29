# Stockfish 19 baseline: findings and how to switch back

Square Chess shipped its first versions with **Fairy-Stockfish** (classical evaluation, no NNUE). Before that, the app used **Stockfish 19** with an NNUE network. The Stockfish 19 build was removed from the source tree on 29 September 2026 to simplify licensing, F-Droid builds and maintenance. This document keeps everything that is useful if we want to go back.

## Restore point

- Git tag **`stockfish19-baseline-last`** marks the last commit that builds the baseline with `-Pengine=stockfish`.
- Restore the files from that tag rather than rewriting them: `native/bridge.cpp`, the Stockfish branch of `native/CMakeLists.txt`, the non-Fairy branch of `Engine.kt` (`StockfishController.ensureStarted`), `scripts/fetch-network.ps1`, the `native/stockfish` submodule entry in `.gitmodules`, and the `engine` property in `app/build.gradle.kts`.

## What the baseline was

| Item | Value |
|---|---|
| Engine | Official Stockfish, release `sf_19`, commit `edb0d9db6731067ec50ce619ff372b463bc4dd5d`, as a git submodule at `native/stockfish` |
| Licence | GPL-3.0; no source changes; `native/bridge.cpp` was our JNI wrapper |
| Network | `nn-1a298aa575a0.nnue`, 98,511,183 bytes, SHA-256 `1a298aa575a085434d29027978dc36867fe9c5bcea9376654b7a8eba1e52dfc2`, from https://tests.stockfishchess.org/api/nn/nn-1a298aa575a0.nnue |
| Network packaging | Asset in `app/src/stockfish/assets` (never in git; fetched by `scripts/fetch-network.ps1`), compressed into the APK, copied once to private storage, SHA-256 checked on every start, then loaded with `EvalFile` |
| Integration | Direct C++ engine API (`Engine::go`, `set_on_bestmove`), no UCI pipe; therefore **not** affected by the pipe race fixed in `native/fairy/sfio.cpp` |
| Strength control | `Skill Level = clamp((level-1)*2, 0, 20)` and `movetime = 100 + level*80` ms; levels were uncalibrated search-time settings |
| Build flags | `NNUE_EMBEDDING_OFF`, `IS_64BIT`, NEON (arm64) or SSE2 (x86_64), `-O3`, 16 KB max page size |

## Measurements (Titan 2 Elite and emulator, 28 September 2026)

| Measurement | Stockfish 19 | Fairy-Stockfish |
|---|---:|---:|
| Debug APK (ARM64 + x86_64) | 113.6 MB | 33.4 MB |
| Release APK (ARM64, unsigned) | 104.4 MB | 25.6 MB |
| ARM64 engine library (release) | 1.63 MB | 1.84 MB |
| NNUE, uncompressed / in APK | 98.5 MB / 80.6 MB | none |
| Installed app bytes (Titan) | 113.7 MB | 33.5 MB |
| Private data (Titan, mostly the extracted network) | 98.8 MB | 8.1 MB |
| Engine start (network already extracted) | 575 ms | 197 ms |
| Mean / p95 reply, level 2 vs Fairy 500 ms budget | 212 / 264 ms | 408 / 505 ms |

Raw data: `docs/engine/calibration/DIFFICULTY_TEST_RESULTS.csv`, `ENGINE_SIZE_COMPARISON.md`.

## Why we switched

1. **Beginner play.** Stockfish 19's lowest skill levels were still far too strong for beginners (user playtest). Fairy-Stockfish accepts negative skill levels, which give a real beginner range (see `docs/engine/calibration/FAIRY_STOCKFISH_RECOMMENDATION.md`).
2. **Size.** The NNUE network made the app about 80 MB larger and stored a second copy in private storage.
3. **Licensing and distribution.** No network file to document, download or verify; simpler corresponding source.

## If we switch back, remember

- **Strength.** Stockfish 19 needs its own beginner solution: `UCI_LimitStrength` / `UCI_Elo` starts at about 1320 Elo, which is still too strong for real beginners. Negative skill levels do not exist in Stockfish 19.
- **Network size.** Consider a smaller network (for example the "small net" that Stockfish also ships) or an on-demand download; a download needs the INTERNET permission, which the app does not have today.
- **Do not store the network twice.** Load it from a file descriptor or embed it in the native library instead of copying the asset to private storage.
- **Licence notices.** Add Stockfish back to `THIRD_PARTY_LICENSES.md` and the in-app notices, and document the network provenance for recipients.
- **Tests.** `FairyEngineDeviceTest` and `BeginnerCalibrationDeviceTest` call `NativeEngine.search(fen, moves, skill, multiPv, millis)`; the baseline bridge ignored `multiPv` and read `skill` as the app level.
- **Hybrid option.** Both engines can live in one app (two native libraries, one `EngineController` per engine): Fairy for levels 1–5, Stockfish for strong levels. This costs the network size again.
