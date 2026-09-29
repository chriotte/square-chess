# Project audit — open-source, Google-free and F-Droid readiness

> **Status, 29 September 2026 (later the same day):** items 1–3 of section 6 are done — working notes and `test-artifacts/` removed, documentation moved to `docs/`, new README, `CONTRIBUTING.md`, `SECURITY.md`, `THIRD_PARTY_LICENSES.md`, and `fastlane/` metadata (now the single source for the Play listing). CI and release workflows are in `.github/workflows/`. The table below describes the repository as it was before that cleanup.

29 September 2026, at commit `6dc2e57` (after the Stockfish 19 and DataStore removal). The repository is **private**; nothing here changes that.

## 1. Repository

| Path | Contents | Keep in public repo? |
|---|---|---|
| `app/` | Android app (Kotlin, Compose, Room); 92 files incl. unit and device tests, Room schemas | Yes |
| `chesslib/` | Vendored Chesslib 1.3.7 source (Apache-2.0), Gradle module `:chesslib` | Yes |
| `native/` | `fairy_bridge.cpp` (JNI), `CMakeLists.txt`, `native/fairy/` vendored Fairy-Stockfish + Lichess wrapper (GPL-3.0) | Yes |
| `scripts/` | Emulator setup, clock recovery test, piece/brand rendering, `play/` Play API scripts | Yes (Play scripts contain no secrets) |
| `design/` | Icon sources, Play store graphics | Yes |
| `docs/` | `engine/`, `fairy-experiment/` evidence, `releases/signing.md`, this audit | Yes, reorganise (see 6) |
| `test-artifacts/` | Old screenshots and early benchmark CSVs | **Remove** (history only) |
| Root notes | `CODEX_HANDOVER.md`, `PHASE_0_REPORT.md`, `REVIEW_MILESTONE.md`, `USABILITY_REVIEW_WRAP_UP.md`, `IMPLEMENTATION_PLAN.md`, `PROJECT.md`, `TEST_PLAN.md`, `RELEASE_CHECKLIST.md`, `ARCHITECTURE.md` | **Move** to `docs/` or remove (see 6) |
| Root public files | `README.md`, `LICENSE` (GPL-3.0), `LICENSES.md`, `THIRD_PARTY_NOTICES.md`, `CHANGELOG.md`, `PRIVACY_POLICY.md`, `PLAY_STORE_LISTING.md` | Yes; merge the two licence notes into `THIRD_PARTY_LICENSES.md` |

No `.github/` directory: there is **no CI** yet.

## 2. Build

- Gradle modules: `:app`, `:chesslib` (`settings.gradle.kts`). Repositories: Google Maven and Maven Central only.
- Application ID `com.dataespresso.squarechess`; `-Pdev=true` adds `.dev` for the test app. No product flavours, and none are needed (see 4).
- Toolchain: JDK 17, Gradle 8.13 (wrapper committed), compile/target SDK 36, min SDK 29, NDK 28.2.13676358, CMake 3.22.1.
- ABIs: release `arm64-v8a`; debug adds `x86_64` for emulators. 16 KB page alignment is set in CMake.
- Signing: `docs/releases/signing.md` (Play upload key; separate standalone release key; keys outside the repository).

## 3. Dependencies (release runtime classpath)

102 libraries after this cleanup (120 before; DataStore was unused).

| Group | Licence | Notes |
|---|---|---|
| `androidx.*` (activity, compose ui/foundation/material/material3/runtime/animation, lifecycle, room, sqlite, core, savedstate, …) | Apache-2.0 | FLOSS; built from AOSP/AndroidX sources, F-Droid accepted |
| `org.jetbrains.kotlin*`, `kotlinx-coroutines`, `org.jetbrains:annotations` | Apache-2.0 | |
| `com.google.guava:listenablefuture` (empty stub, via AndroidX) | Apache-2.0 | Not Play Services |
| `com.squareup.okio` (via Room/sqlite) | Apache-2.0 | |
| `org.apache.commons:commons-lang3` (via Chesslib) | Apache-2.0 | |
| `org.jspecify` | Apache-2.0 | |

**No** `com.google.android.gms`, Firebase, Play Billing, Play Core/Review/Update, Crashlytics or analytics. `verifyReleasePolicy` (run by `check`) enforces this.

## 4. Permissions and Google independence

- Release manifest permissions: only `com.dataespresso.squarechess.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (added by AndroidX for the app's own receivers; grants nothing to other apps). **No INTERNET**, no network state, no storage, no VIBRATE (Compose haptics need none). Enforced by `verifyReleasePolicy`.
- The app never calls Google APIs; the Play and standalone builds are the **same build**, differing only in signer. A `play`/`standalone` flavour split has no current purpose; add it only if a Play-only feature ever appears.
- File access uses the system document picker (Storage Access Framework) and a `FileProvider` for sharing; no storage permission.

## 5. Chess engine

- Fairy-Stockfish, vendored under `native/fairy` from lichess-org/dart-multistockfish `896c388…` (engine base `2b5d951…`); three local fixes documented in `native/fairy/PROVENANCE.md`.
- Classical evaluation; **no NNUE** file anywhere. One engine thread, 16 MB hash, 500 ms per move; levels 1–10 in `Difficulty.kt`.
- Compiled from source by CMake on every build; no prebuilt `.so` or binary in the repository.
- Stockfish 19 was removed; see `docs/engine/stockfish19-baseline.md` and tag `stockfish19-baseline-last`.

## 6. Cleanup before publication

1. Delete `test-artifacts/` and the root AI/working notes, or move durable content to `docs/` (`docs/architecture/`, `docs/testing/`, `docs/releases/`).
2. Rewrite `README.md` as the public front page; add `CONTRIBUTING.md`, `SECURITY.md`; one `THIRD_PARTY_LICENSES.md`.
3. Add `fastlane/metadata/android/en-US/` from the Play listing and `design/play-store/`.
4. History: the owner chose to keep this repository with **cleaned history**. Before publication, rewrite history to drop removed paths (`test-artifacts/`, working notes, the Stockfish submodule pointer) with `git filter-repo`, force-push while private, re-clone, and scan again.

## 7. Security scan (so far)

- Current tree and full history searched for private keys, keystore passwords, the owner's email and postal address: **none found** (29 September 2026, `git log -S`).
- Keystores, `keystore.properties`, `standalone.properties` and the Play service-account JSON are outside the repository.
- To do: a Gitleaks scan of the full history before publication (Work Package 8).

## 8. Risks and open items

- **Signatures:** Play installs and GitHub/F-Droid installs can never update each other (Google holds the Play signing key). Mitigation: PGN export/import, now implemented.
- **Reproducible builds:** native code needs deterministic paths and no build ID; not yet attempted.
- **Light Phone III:** LightOS SDK access and rules unknown; the emulator profile `SquareChess_Light_Phone_III` (1080 × 1240, 420 dpi) only tests layout on plain Android.
- **GPL:** version 1.0.2 is distributed on Play while the source is private; publication resolves this.
