# Puzzles: testing

## JVM tests

```sh
./gradlew testDebugUnitTest testReleaseUnitTest
```

`app/src/test/.../PuzzleTest.kt` has three classes (27 tests):

| Class | Covers |
|---|---|
| `PuzzleParsingTest` | Reading pack rows; rejecting broken rows (field count, non-UCI moves, rating, FEN, empty ID, no solver move); skipping bad lines and repeated IDs; a script with an illegal move cannot start; **every puzzle in the bundled pack can be played** |
| `PuzzleSessionTest` | The setup move is played first; correct move, scripted reply, solve; a wrong move leaves the position and counts a mistake; a typing error is not a mistake; SAN and UCI; Black to solve; promotion; castling (`O-O` and `e1g1`); captures; an alternative mate in one; hints; moves while the reply is pending; restore |
| `PuzzleTrainingTest` | Review schedule (due at once, 3, 7, 30 days, then out); first-try solves of new puzzles are not reviewed; due list order; training-level steps and limits; selection near the level, theme filter, avoid list, least recently seen; five different puzzles for Quick 5 (fixed `Random` seeds); totals |

## Device tests

`app/src/androidTest/.../PuzzleDeviceTest.kt` (6 tests, dev app only; it clears saved games and
puzzle progress). It starts fixed puzzles from the bundled pack, so the solutions are known.

| Test | Covers |
|---|---|
| `wrongTapThenKeyboardSolutionWithReplyAndPromotion` | White at the bottom; a wrong move by tap; SAN by keyboard; the automatic reply; UCI without the piece opens the promotion choice; solve; progress row; no saved game |
| `blackPuzzleIsFlippedAndDragWorks` | Black at the bottom; drag and drop; reply; keyboard solve |
| `hintShowsTheMoveAndCounts` | Hint text for TalkBack, Hide, solve, hint counted |
| `backClearsTypedMoveThenLeavesAndMistakesComeBackInReview` | "Nothing due" note; Backspace and Escape on a typed move; Back leaves an unsolved puzzle; it is then due; Review mistakes shows it |
| `recreationKeepsThePuzzleAndMove` | Activity recreation in the middle of a line |
| `quickFiveEndsWithASummary` | Five puzzles, the summary counts, back to the landing screen |

`GameMigrationDeviceTest` checks the upgrade from database version 1 to 7, including the new
`puzzle_progress` table.

Run only the puzzle tests on one device:

```sh
./gradlew -Pdev=true assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.dataespresso.squarechess.PuzzleDeviceTest \
  com.dataespresso.squarechess.dev.test/androidx.test.runner.AndroidJUnitRunner
```

## Emulator matrix

| Profile | Screen | Used for |
|---|---|---|
| `SquareChess_Titan_2_Elite` | 1080 × 1200, 300 dpi, keyboard | Full device suite; screenshots; keyboard entry; process death; large fonts |
| `SquareChess_Clicks_Communicator` | 1080 × 1200, 400 dpi, keyboard | Puzzle tests; compact keyboard layout |
| `SquareChess_Titan_2` | 1440 × 1440, 480 dpi, keyboard | Puzzle tests; true square screen |
| `SquareChess_Light_Phone_III` | 1080 × 1240, 420 dpi, touch only | Puzzle tests; memory; release load time |
| `SquareChess_Fold_Outer` | 1080 × 2092, 420 dpi, touch only | Puzzle tests; conventional tall phone |
| `SquareChess_Eink_Tablet` | 1404 × 1872, 227 dpi, touch only | Puzzle tests and screenshots with Settings → E-ink mode on |

The e-ink tablet profile is new (`scripts/setup-emulators.ps1 -RequestedDevice Eink_Tablet`). It
has the size of a 10.3-inch e-reader such as the BOOX Note3, but an emulator cannot show e-paper
refresh; a real e-ink device is still needed for that.

## Manual checks

1. Process death: start a puzzle, play a move, press Home, `adb shell am kill com.dataespresso.squarechess.dev`, open the app: the same puzzle and move come back.
2. Font scale 1.0, 1.15, 1.25, 1.3, 1.5 and 2.0 (`adb shell settings put system font_scale X`): the board keeps its size; from 1.25 the header shows one line.
3. On a physical keyboard phone with PhysiBoard or Pastiera: a held key types its digit in a puzzle as in a game (same code path; not yet tried on a device).
4. On an e-ink device: wrong move, correct move, reply, hint and solve redraw only the changed parts.
