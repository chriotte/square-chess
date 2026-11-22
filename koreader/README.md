# Square Chess for KOReader

Square Chess as a [KOReader](https://koreader.rocks) plugin, for e-readers that cannot run Android apps: jailbroken Kindles (including the Kindle Scribe), Kobo, PocketBook and reMarkable.

This folder is where the plugin is developed. Users install it from its own repository, **[chriotte/squarechess.koplugin](https://github.com/chriotte/squarechess.koplugin)**, which `tools/make-release-repo.sh` assembles from here for each release.

It uses the same engine as the Android app (Fairy-Stockfish, ten levels) and the same black-and-white e-ink board: hatched dark squares, shape-based highlights and no animations. Everything runs on the device, with no network access.

## Features

- **Play the computer** at ten levels (Fairy-Stockfish, the Android app's level settings), as White, Black or random, or **two players** on one device
- **Time controls** from 1+0 to 30+0 for games, with a clock bar; tap it to pause. Running out of time loses, unless the opponent cannot mate
- **Hints**: the engine's suggestion drawn on the board (outlined piece, dotted path, ringed target)
- **Evaluation** (optional): who is ahead, each move labelled Best, Good, Inaccuracy, Mistake or Blunder, and a summary when the game ends
- **Review** any game move by move; continue playing from any position
- **Puzzles**: the app's 10,000 offline Lichess puzzles, with adaptive training, Quick 5, themes, two-step hints, show-solution, and review of missed puzzles after 0, 3, 7 and 30 days
- **Chess clock** for real-board games: face-to-face halves, ten presets, increment, pause
- **PGN**: open a file (pick one game from many), save the current game
- **Input**: tap-tap, drag, or type moves (`e4`, `Nf3`, `O-O`, `e2e4`) with the on-screen or a physical keyboard. Pen input works the same as touch: a pen tap that slides a little is still a tap, a slow press still counts, and a pen stroke from one square to another is a drag
- The app's draw rules: automatic draws for dead material, fivefold repetition and the 75-move rule; claimable draws for threefold repetition and the 50-move rule
- Games and puzzle progress are saved as you go; portrait and landscape layouts

## Install

1. Build the zip (needs `podman` or `docker`, `zip` and Python with Pillow):

   ```sh
   koreader/tools/package.sh
   ```

2. Unzip it into KOReader's `plugins` folder so that you get `plugins/squarechess.koplugin/`:
   - Kindle: `koreader/plugins/` on the USB drive
   - Kobo: `.adds/koreader/plugins/`
   - Other devices: the `plugins` folder next to KOReader's `reader.lua`
3. Restart KOReader, then open **Tools → Square Chess → Play chess, Puzzles or Chess clock**. Each of the three can also be assigned to a gesture under **Settings → Taps and gestures**.

## How it fits together

| File | What it does |
|---|---|
| `chess.lua` | Rules: legal moves, SAN, FEN, game end. Pure Lua with no KOReader dependencies |
| `pgn.lua`, `clock.lua`, `evaluation.lua`, `puzzles.lua` | PGN, chess clock, evaluation labels, and puzzle training: ports of the app's Kotlin, also pure Lua |
| `engine.lua` | Starts Fairy-Stockfish as a child process and talks UCI to it without blocking KOReader |
| `board.lua` | Draws the e-ink board and turns touch and pen gestures into moves |
| `gamescreen.lua`, `puzzlescreen.lua`, `clockscreen.lua` | The three full-screen modes |
| `ui.lua` | Shared pieces: promotion and typed-move dialogs, the clock bar |
| `main.lua` | Menu entries and gesture actions |
| `pieces/` | Chessnut pieces (Apache-2.0) with the app's white e-ink halo baked in |
| `data/puzzles-v1.tsv` | The app's puzzle pack (Lichess, CC0) |

All paths above are in `squarechess.koplugin/`. In `tools/`: `build-engine.sh` builds static engine binaries for `armhf`, `aarch64` and `x86_64` from `native/fairy`; `make-pieces.py` regenerates the piece images; `package.sh` builds the install zip.

The engine binaries are statically linked, so one `armhf` build runs on every 32-bit ARM e-reader whatever its C library or float ABI. The plugin picks the binary that matches `uname -m`.

## Tests

```sh
# Rules, PGN, clock, evaluation and puzzles (including perft on six standard
# positions, and a replay of all 10,000 puzzles). Needs LuaJIT, or KOREADER_DIR
# pointing at an unpacked KOReader Linux build.
KOREADER_DIR=/path/to/koreader koreader/tests/run.sh

# Engine bridge against the real x86_64 binary.
cd /path/to/koreader && ./luajit <repo>/koreader/tests/engine_check.lua

# Offscreen UI run at Kindle Scribe resolution: drives all three screens through
# KOReader's gesture dispatch, including pen-style gestures. Screenshots go to <out>.
cd /path/to/koreader && SDL_VIDEODRIVER=offscreen KO_HOME=/tmp/ko-test \
    EMULATE_READER_W=1860 EMULATE_READER_H=2480 EMULATE_READER_DPI=300 \
    ./luajit <repo>/koreader/tests/ui_check.lua <out>
```

To try it in a desktop window, unpack the KOReader Linux release, link `squarechess.koplugin` into `$KO_HOME/plugins/`, and run `KO_HOME=... EMULATE_READER_W=744 EMULATE_READER_H=992 ./reader.lua`.

## Licence

GPL-3.0-or-later, like the Android app and Fairy-Stockfish. The piece artwork is Apache-2.0; see `squarechess.koplugin/pieces/README.txt`. The puzzles come from the Lichess puzzle database (CC0).
