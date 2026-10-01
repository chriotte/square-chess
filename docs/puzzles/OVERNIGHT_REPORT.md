# Offline puzzles: overnight report (1 October 2026)

## Git

| Item | Value |
|---|---|
| Base commit | `f393be9` (main, release 1.2.0) |
| Branch | `feature/offline-puzzles` |
| Pushed | Yes, the feature branch only. `main` is not changed. No release, no Play upload, no version change. |

Commits on the branch:

```text
5f42dc8 refactor: extract reusable chess board and move-entry card
840ae18 feat: add offline puzzle domain, pack and progress storage
aefbd56 feat: add puzzle training screens, keyboard entry and translations
aab8a7d test: add puzzle device tests and database 7 migration check; faster pack parsing
e4089ca fix: restore the puzzle after process death; keep the board size at large fonts; e-ink tablet emulator profile
(last)  docs: puzzle architecture, data source, testing and this report
```

## Implementation

**Complete**

- Home → Puzzles ("Offline tactical training"). Landing screen: Continue training, Quick 5,
  Review mistakes (shows how many are due), Choose a theme (five groups), Progress.
- Board-first puzzle screen with the same board as games (extracted to `ChessBoard.kt`; normal
  games behave as before, see the test results). The side to solve is at the bottom.
- Lichess move semantics: the setup move is played first; correct moves are followed by the
  scripted reply after 450 ms; a wrong move only shows "Not quite · try again" and leaves the
  position; any mating move is accepted.
- Touch (tap-tap, drag, tap king then rook), keyboard SAN and UCI, Backspace, Escape, Enter,
  promotion choice. One move-entry target in `dispatchKeyEvent`, so the PhysiBoard/Pastiera held-key
  code is shared, not copied. No text field, so no software keyboard.
- Hints reuse `HintOverlay` and the game's hint text; no engine is used for puzzles at all.
- Themes show only after the solve (or in a theme mode the player chose).
- Progress in a new `puzzle_progress` table (database 7, migration tested); never in Game history.
- Review schedule and a local training level (shown as "Training level", not a rating).
- Quick 5 with a plain summary (solved first try, after a mistake, hints).
- Restore after rotation, recreation and process death (puzzle, move, mistakes, hint, mode).
- E-ink mode: black and white, hatched squares, corner marks, outlined cards; no animations.
- All texts are string resources, with translations in the five other languages.
- Docs: [architecture.md](architecture.md), [data-source.md](data-source.md), [testing.md](testing.md).

**Partial or different from the spec**

- Pack size: **5,000** puzzles, not 10,000 to 20,000, because of your request for a small APK.
  `--count 10000` doubles it (+235 KB more).
- First review step: a missed puzzle is due **at once**, not the next day, so Review mistakes
  works in the same sitting. One constant changes it (`REVIEW_DELAYS_MS[0]`).
- No flip-board button in puzzles; no two-step (progressive) hint. Both were optional.

**Left out on purpose** (out of scope): online features, engine checks of alternative lines,
puzzles from the player's own games (a possible design is at the end of architecture.md).

## Puzzle data

| Item | Value |
|---|---|
| Source | Lichess puzzle database, file dated 9 September 2026, 6,100,952 puzzles |
| Licence | CC0, checked on database.lichess.org on 1 October 2026 |
| Source SHA-256 | `95fd454b…149bb9` (full value in data-source.md) |
| Pack | `app/src/main/assets/puzzles/puzzles-v1.tsv`, 5,000 puzzles, 562,508 bytes |
| Pack SHA-256 | `337c2c9c…11cadf` |
| Ratings | 600 to 2599, 500 puzzles in each 200-point band |
| Solver | White 2,588, Black 2,412; 1 to 9 solver moves (most have 2) |
| Theme groups | Endgames 2,500, Tactical motifs 2,018, Checkmate 1,389, Forks 967, Pins 451 (a puzzle can be in several; 599 are in none and appear only in mixed training) |
| Rejected after the quality filter | 0 invalid FEN, 0 illegal lines, 0 duplicate IDs |

## Tests (actual results)

| Suite | Result |
|---|---|
| JVM debug (`testDebugUnitTest`) | 134 passed / 0 failed (27 of them are new puzzle tests) |
| JVM release (`testReleaseUnitTest`) | 134 passed / 0 failed |
| `./gradlew check` (lint, release policy, unit tests) | PASS. The release manifest has no Internet permission and no proprietary libraries. |
| Full device suite, Titan 2 Elite emulator | 52 tests: 46 passed, 0 failed, 6 skipped. The skips are the same as on main: 5 opt-in experiments, and the held-key test (no Alt digits in the emulator key map). |
| `PuzzleDeviceTest` (6 tests), Titan 2 Elite | 6 / 6 (inside the full suite) |
| `PuzzleDeviceTest`, Clicks Communicator | 6 / 6. The first run after a cold boot was 5 / 6: the drag test failed while Android showed its first-run "Viewing full screen" message. |
| `PuzzleDeviceTest`, Titan 2 (square) | 6 / 6 |
| `PuzzleDeviceTest`, Light Phone III | 6 / 6 |
| `PuzzleDeviceTest`, Fold outer (tall phone) | 6 / 6 |
| `PuzzleDeviceTest`, e-ink tablet profile, e-ink mode on | 6 / 6 |
| Board refactor alone (before the puzzle code) | Full suite on Titan 2 Elite: 0 failed |
| Process death (manual, Titan 2 Elite) | Same puzzle and position after `am kill` |
| Font scale 1.0 to 2.0 (manual) | Board size unchanged (134 px squares) |

Not tested: physical devices (Titan, BOOX). I did not use your Titan tonight.

## Screenshots

In `outputs/puzzle-report/` (not in the repository, 22 files, about 2.7 MB):

```text
01-07  Titan 2 Elite: home, landing, White puzzle, hint, wrong move, correct move with reply, solved
08     Black to move (board from Black's side)
09     keyboard entry ("Move: Nxe" over the board)
10-16  e-ink tablet: landing, puzzle, hint, wrong move, correct move with reply, solved, Review mistakes
17     Quick 5 summary
18-19  font scale 1.5 landing, font scale 2.0 puzzle
20-*   one puzzle each on Clicks, Titan 2 and the tall phone
```

## Performance

| Measurement | Result |
|---|---|
| Puzzle asset | 562,508 bytes; 234,938 bytes compressed in the APK |
| Release APK | 24,693,033 → 25,088,592 bytes (+395,559, +1.6 %) |
| Play bundle | 9,407,960 → 9,698,444 bytes (+290,484) |
| First load of the pack, release build | 72, 88, 73 ms (three cold starts, Light Phone emulator) |
| First load, debug build | 465 to 805 ms (debug builds are not optimised) |
| Choosing the next puzzle | 24 ms (debug build, first call) |
| Memory after loading the pack | Java heap +3.0 MB (15.8 → 18.8 MB); total PSS 74.7 → 74.8 MB |
| App start | The pack is not read at start; it loads only when Puzzles opens |

## Known issues

1. The translations of the 45 new texts (German, Spanish, French, Norwegian, Chinese) are my own
   and need a check by native speakers.
2. On tall phones the board is at the top with empty space below (the same as the game screen).
3. Back on Settings, History and the game screen leaves the app. This is older than this branch
   (only Help and the chess clock handle Back on main); puzzles handle Back.
4. Held-key digits (PhysiBoard, Pastiera) use the same code in puzzles as in games, but I have not
   tried them in a puzzle on a real device.
5. The real e-paper refresh on an e-ink device is not tested; the emulator shows layout only.

## Recommended next steps

1. Try the branch on your Titan: a puzzle by keyboard, including a held-key digit.
2. Try it on the BOOX in e-ink mode.
3. Decide the pack size (5,000 now) and the first review delay (at once now).
4. Have the translations checked.
5. Then merge into main for the next release.

## Update, 1 October 2026 (morning)

After your review:

1. **10,000 puzzles** (was 5,000), the lower end of the spec's 10,000 to 20,000. All 10,000 pass
   the checks; the 5,000 from the first pack are all still in it. Numbers in
   [data-source.md](data-source.md).
2. **Back key**: Settings and Game history go back to Home; on the game screen Back first clears a
   typed move, then leaves the review, then saves the game and goes Home (as Menu → Save & home).
   Before, Back on these screens closed the app. New test: `BackNavigationDeviceTest`.

| Measurement | Result |
|---|---|
| Release APK | 24,693,033 (1.2.0) → 25,320,840 bytes (+627,807, +2.5 %) |
| Puzzle asset | 1,126,808 bytes; about 468 KB compressed |
| First load of the pack, release build | 181, 144, 107 ms (three cold starts, Light Phone emulator) |
| Java heap after the load, release build | 22.6 MB |
| JVM tests | 134 passed / 0 failed; `./gradlew check` PASS |
| Full device suite, Titan 2 Elite emulator | 54 tests: 48 passed, 0 failed, 6 skipped (as before) |
| Full device suite, your Titan 2 (physical) | 54 tests: OK, 6 skipped (the opt-in tests, and the held-key test because PhysiBoard is active) |
| Back and puzzle tests, Light Phone and e-ink tablet emulators | 8 / 8 each |
