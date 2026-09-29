# Architecture overview

Square Chess is one Android app module (`:app`) plus vendored Chesslib (`:chesslib`) and a native engine library built by CMake from `native/`.

```text
Compose UI (MainActivity, GameToolbar, BoardExtras, Settings, Help)
        │ immutable GameUi snapshots
        ▼
GameViewModel ── Room (SavedGame, one row per game) ── PGN export/import
        │ revision token per search
        ▼
StockfishController (Engine.kt) ── JNI ── native/fairy_bridge.cpp ── Fairy-Stockfish (UCI over an in-process pipe)
```

## Game state

- `ChessPosition` (Chess.kt) rebuilds a Chesslib `Board` from the starting FEN and the authoritative list of UCI moves. SAN is derived, never stored. Every move — typed, tapped, dragged, imported or from the engine — is resolved against Chesslib's legal moves before it is committed.
- `GameViewModel` serialises commits with a mutex and saves each move to Room at once. A revision counter invalidates engine replies after undo, new game, backgrounding or navigation.
- Clocks (`ClockDomain.kt`) use the monotonic clock, pause when the app leaves the foreground, and checkpoint every second.

## Engine

- Fairy-Stockfish (classical evaluation, no NNUE) is vendored in `native/fairy/`; provenance and the three local fixes are in `native/fairy/PROVENANCE.md`.
- The Lichess wrapper runs the engine's UCI loop on its own thread, connected by an in-process pipe. `fairy_bridge.cpp` sends UCI commands, reads replies line by line, and sends `isready` before each `go` so leftover output can never be taken as a new `bestmove`.
- One search thread, 16 MB hash, 500 ms per move. Levels 1–10 (`Difficulty.kt`) set only the engine's own Skill Level and MultiPV options. Calibration evidence: `docs/engine/calibration/`.
- `stop()` may be called from the main thread; all other calls hold a mutex. A failed engine call shows "Computer unavailable" with a retry, never a crash.

## Persistence and files

- Room database `square-chess.db`, schema version 4, with migrations from version 1 (schemas in `app/schemas/`).
- Settings in `SharedPreferences`.
- Import and export use the Storage Access Framework; sharing uses a `FileProvider` on the app cache. No storage permission, no Internet permission.

## Layout rule

The board size is decided first (`min(width, height)` of the available area). Optional rows — review bar, move line, captured pieces — appear only in space the square board cannot use, so they never make the board smaller. On square screens the review arrows and last move move into the header instead.
