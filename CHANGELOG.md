# Changelog

## Unreleased
- Import games from a PGN file (Game history → Import): all games in the file, comments and variations ignored, duplicates skipped, unreadable games reported. Together with Export, this moves a game library between phones or between Play and other sources.
- Removed the Stockfish 19 build and the unused DataStore library; `verifyReleasePolicy` guards against proprietary Google libraries and new permissions.
- Emulator profile for the Light Phone III (1080 × 1240, 420 dpi).

## 0.1.0 — development, unreleased
- Fairy-Stockfish (classical evaluation, no NNUE) replaces Stockfish 19; APK about 80 MB smaller. Ten difficulty levels set the engine's Skill Level and MultiPV.
- Fixed a race in the engine output pipe that could return the previous move.
- Full PGN export of the game library (filters by mode and period; save or share), game delete, dates and move counts in history.
- Resign against the computer; grouped game menu; last move in the header; review arrows no longer cover the board.
- Larger legal-move dots and capture rings, drag-and-drop, promotion with piece images, move sounds and vibration.
- Settings screen: sounds, vibration, legal moves, coordinates, board colours.
- Captured pieces and a move line appear only where the board cannot use the space.
- First offline ARM64 engine feasibility implementation.
- Compact square board and physical-keyboard UCI/SAN entry.
- Basic computer, two-player and recording session types.
- Room session persistence, SAN history and promotion chooser.
- Pinned Stockfish/Chesslib source and build documentation.
