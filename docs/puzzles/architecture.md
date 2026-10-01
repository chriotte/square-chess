# Puzzles: architecture

Puzzles are an offline tactics trainer. They are a separate feature, not a game mode: a puzzle
never becomes a `SavedGame`, never appears in Game history or PGN export, has no clock, and never
starts the engine.

## Files

| File | Job |
|---|---|
| `Puzzle.kt` | `Puzzle` (one pack line), `PuzzleSession` (one attempt, immutable), theme groups, pack-line parser |
| `PuzzleTraining.kt` | `PuzzleProgress` (Room entity and DAO), review schedule, training level, puzzle selection, totals. Pure functions |
| `PuzzleCatalog.kt` | Reads the pack from assets once, off the UI thread, when Puzzles first opens |
| `PuzzleViewModel.kt` | Modes (training, Quick 5, review, theme), judging moves, the scripted reply, hints, saving progress, restore |
| `PuzzleScreen.kt` | Landing screen, puzzle screen, Quick 5 summary |
| `ChessBoard.kt` | The board shared with games (extracted from `MainActivity.kt`), and the typed-move card |
| `tools/puzzles/build_puzzle_pack.py` | Builds and checks the pack; see [data-source.md](data-source.md) |

## Move semantics

A Lichess puzzle's FEN is the position **before** the opponent's move that sets up the puzzle:

```text
moves[0]  opponent's setup move      played when the puzzle starts
moves[1]  solver's first move        the player must find it
moves[2]  opponent's reply           played automatically
moves[3]  solver's next move
...       the last move is always the solver's
```

`PuzzleSession.ply` is how many of these moves are on the board. It starts at 1 (after the
setup move). An odd `ply` means the solver is to move (`SOLVING`); an even `ply` below the end
means the scripted reply is waiting (`OPPONENT_TO_MOVE`); `ply == moves.size` is `SOLVED`.
The solver's colour is the side to move after the setup move, and the board puts that side at
the bottom.

`PuzzleSession.judge(text)` reads the move with `ChessPosition.resolve`, the same parser that
games use, so SAN (`Nf3`, `Bxh7+`, `O-O`, `e8=Q`) and UCI (`g1f3`, `e7e8q`) both work:

| Result | When | Effect |
|---|---|---|
| `CORRECT` | the scripted move | `ply + 1`; the view model plays the reply after 450 ms |
| `SOLVED` | the last scripted move, or **any move that mates** | the attempt is recorded |
| `WRONG` | another legal move | mistake count + 1; the position does not change |
| `ILLEGAL` | text that is not a legal move (a typing error) | nothing; the typed move shows an error |
| `NOT_NOW` | not the solver's turn | nothing |

Other lines than the script are not checked with the engine in this version; only mates are
accepted as alternatives.

## Hints

The next move is already known, so Hint does not use Fairy-Stockfish. It shows the game's
`HintOverlay` (framed start square, ring, arrow; readable in greyscale and on e-ink) and the
same header text as a game hint. A hint marks the attempt (`hintUsed`), so it is not a
first-try solve.

## Progress and review

Database version 7 adds one table, `puzzle_progress` (migration `MIGRATION_6_7`; the games table
is not changed). One row per puzzle tried: attempts, solves, first-try solves, mistakes, hints,
the last result and time, and the review state.

An attempt ends as `FIRST_TRY`, `WITH_HELP` (a mistake or a hint) or `ABANDONED` (the player left
after a move or a hint; a puzzle only looked at is not recorded).

Review steps (`REVIEW_DELAYS_MS`):

| After | Next review |
|---|---|
| `WITH_HELP` or `ABANDONED` | at once (step 0) |
| first-try solve in review, step 0 | 3 days |
| step 1 | 7 days |
| step 2 | 30 days |
| step 3 | leaves the review cycle |

A new mistake starts the cycle again. The spec suggested "next day" for the first step; this
version makes it due at once, so Review mistakes can bring a puzzle back in the same sitting.
Change `REVIEW_DELAYS_MS[0]` to change this.

## Training level

A local number that aims the selection; the screen says that it is not a chess rating.
It starts at 1100 and stays between 600 and 2500 (`nextTrainingLevel`):

| Result | Change |
|---|---|
| First-try solve | +15, plus 1 for each 20 points the puzzle is above the level (5 to 25) |
| Solved with help | -5 |
| Left unsolved | -20 |

Review puzzles do not move the level. The level is stored in the `puzzles` shared preferences.

## Selection

`choosePuzzle`: unseen puzzles within 150 points of the level, then within 300, then the nearest
unseen puzzle, then the puzzle seen longest ago. The last 30 puzzles and the current Quick 5 set
are avoided while anything else is left. A theme mode filters by theme group first. Review takes
the due puzzles, the longest overdue first; when none are due, the landing screen says so.

Theme groups (`ThemeGroup`) map the Lichess tags to five groups: Checkmate, Forks and double
attacks, Pins and skewers, Tactical motifs, Endgames and promotion. Themes show only after the
solve, so they do not give the idea away, except in a theme mode that the player chose.

## Keyboard entry

`MainActivity.dispatchKeyEvent` has one move-entry target: the puzzle while a puzzle is on screen,
otherwise the game (`entryOpen()`, `entryKey()`). Both use the same `NotationDraft`, held-key
digits (PhysiBoard, Pastiera), Backspace, Escape, Enter and the promotion choice. There is no
text field, so the software keyboard never opens and the board keeps its size. Back clears a
typed move first, then leaves the puzzle.

## Lifecycle

The view model survives rotation and recreation. For process death it keeps the puzzle ID, `ply`,
mistakes, hint, mode, theme and Quick 5 results in `SavedStateHandle`; on restore, a reply that
was waiting is played at once, so the board is never left between two moves.

## Performance

The pack loads only when Puzzles opens (never at app start), on `Dispatchers.IO`. See the
overnight report for measured times, memory and APK size.

## Future work: puzzles from the player's games

Not built. A possible offline pipeline:

1. The player asks for it on a finished game (never automatically).
2. The engine evaluates each position, as the evaluation feature already does.
3. A move that drops the winning chance by 30 points or more, where the engine's best move wins
   material or mates, becomes a candidate: the FEN before the opponent's previous move, that move
   as the setup move, and the engine's line as the solution.
4. The candidate is stored locally as a puzzle the player can review, in its own table.
