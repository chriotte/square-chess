# Android 8 compatibility validation — 2 October 2026

## Scope and isolation

Continued the existing, uncommitted work on `feature/android-8-support`, based on
`822d8d240f2418fe3716ad777a99791a77b34c2c` (1.3.0). Main was not changed, merged,
tagged or published. Keep this branch separate while deciding whether to offer a
legacy Android 8 APK rather than support the older platform in the main release.

The compatibility changes already in the working tree were retained:

- Minimum API 26, with ARM64 and ARMv7 release libraries; x86 and x86_64 in debug.
- API guards for display-cutout access and compatible version-code lookup.
- One application-lifetime Room database, avoiding closure while another view
  model or an outstanding operation still uses it.
- Emulator profiles and UI-test window lookup adjusted for older Android.

This continuation adds a settling delay to the layout screenshot test. On API 26,
accessibility nodes can exist before the corresponding window finishes drawing;
the original screenshots sometimes captured the launcher or a fading dialog.
The rerun produced correctly rendered Home and Review screenshots.
The puzzle drag test now consumes the shell-command output to completion before
checking the reply, replacing a fixed sleep that could expire before a swipe ran
on a loaded emulator. All six puzzle tests passed again on API 26 afterward.

## Verified results

| Check | Result |
|---|---|
| Debug unit tests | 134, no failures |
| Release unit tests | 134, no failures |
| `check` (lint and release policy included) | Passed |
| ARM64 + ARMv7 release APK build | Passed |
| Fresh isolated dev app + instrumentation build | Passed |
| Android 8.0 / API 26 / x86, e-ink tablet profile | 54 tests reported: 48 passed, 6 conditional skips, no failures |
| Host-driven process-death clock recovery on API 26 | Both stages passed; restored clock remains paused until resumed |
| API 26 layout test after screenshot fix | Passed; Home and Review visually inspected |
| API 26 puzzle suite after gesture-test fix | 6 passed |
| APK 16 KB zip alignment | Passed (`zipalign -c -P 16 -v 4`) |
| ARM64 and ARMv7 engine ELF load alignment | `0x4000` (16 KB) |
| `git diff --check` | Passed (line-ending warnings only) |

The Android 8 suite exercised the actual 32-bit native engine at every difficulty,
legal moves, cancellation, terminal positions and restart, plus clocks, migrations,
keyboard entry, hints, evaluation, translations, puzzles, e-ink mode and navigation.
The six skips were opt-in calibration, self-play and benchmarking, the two
host-driven recovery stages (subsequently run separately), and a held-key test
whose keyboard-map requirements the emulator does not meet.

## Evidence and local preview

Logs are under the workspace `work/` directory:

- `android8-continuation-check.log`
- `android8-continuation-dev-build.log`
- `android8-continuation-device-tests.log`
- `android8-test-logcat-complete.txt`
- `android8-continuation-process-recovery.log`
- `android8-layout-final.log`
- `android8-puzzles-final.log`

## Newer Android comparison

The API 33 BOOX-size emulator passed the engine, game migration, clocks, input,
evaluation, hints and feedback portions of the full run. One puzzle-drag check
failed to observe the reply, motivating the shell-command completion fix above.
The final standalone-clock case later stalled; the test app was stopped to rerun
the affected suites in a fresh process. Do not describe the initial full run as
passing. Logs: `android13-continuation-device-tests.log` and
`android13-targeted-final.log`.

The API 36 Titan emulator could not complete instrumentation. It had repeated
Bluetooth/system-server failures and long package-manager stalls, including ones
from before this run. This is an inconclusive test environment, not evidence of a
specific Square Chess regression or a successful Android 16 validation. Log:
`android16-continuation-device-tests.log`.

Earlier the same day, a separate session ran the full suite on this branch on its own,
freshly cold-booted emulators. After the test-helper fixes for Android 13 (share sheet and
the recreated-window lookup), it passed 54 of 54 on `SquareChess_Boox_Go_6` (API 33) and
54 of 54 on `SquareChess_Clicks_Communicator` (API 36). After the screenshot and drag
changes above, `PuzzleDeviceTest` and `LayoutDeviceTest` passed again on both (7 of 7).
The failures above came from overloaded emulators: several emulators and two sessions ran
at the same time.

## Local build artifact

The workspace `outputs/android8-validation-20261002/` contains inspected screenshots
and `Square-Chess-Android8-preview-1.3.0.apk`. This is a **local build artifact**, not
a published legacy release. Its manifest is API 26 minimum / API 36 target and it
contains `arm64-v8a` and `armeabi-v7a` libraries.

SHA-256: `747cd79235b37b1e88afab9be38a7a85d530efa8f7c62b321b537032269a30ff`.

Before distributing it, choose an explicit legacy version/channel and the correct
standalone signing key, then build and verify that exact distribution artifact.
The preview retains version 1.3.0/code 12 and the local build's signing configuration;
it should not be advertised as a drop-in GitHub/Play update.

## Limits and next decision

- No physical ARMv7 device was available. A 32-bit x86 emulator verifies the older
  Android APIs and the engine's 32-bit code path, not ARM instruction compatibility.
- E-paper refresh, vendor firmware and real physical-keyboard mappings require hardware.
- Packaging alignment passed; a 16 KB-page runtime device was not tested.
- No battery/power claim follows from these functional tests.
- The separate face-to-face piece feature is uncommitted in `feature/eink-requests`
  at `outputs/requestswt`. It rotates the pieces belonging to the player at the top
  and includes unit/device tests. It was located and inspected, not merged or
  validated as part of Android 8 support.

There is no Android 8 functional blocker in the completed API 26 tests. Preserve
mainline isolation as requested; a physical ARMv7 check remains advisable before
offering the optional legacy APK to users.
