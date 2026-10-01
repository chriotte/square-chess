# Puzzle data source

The bundled puzzles come from the **Lichess puzzle database**.

| Item | Value |
|---|---|
| Source | <https://database.lichess.org/#puzzles>, file `lichess_db_puzzle.csv.zst` |
| Licence | Creative Commons CC0 (checked on the Lichess database page on 1 October 2026: the exports may be used "for research, commercial purpose, publication, anything you like") |
| Source version | File dated 9 September 2026 (`Last-Modified: Wed, 09 Sep 2026 17:40:14 GMT`), 304,429,328 bytes, 6,100,952 puzzles |
| Source SHA-256 | `95fd454bec9efe8f940d5863d5db4c57474f281a865834997bd8cb5d6a149bb9` |
| Output | `app/src/main/assets/puzzles/puzzles-v1.tsv` |
| Puzzles in the pack | 5,000 |
| Output size | 562,508 bytes (about 235 KB compressed in the APK) |
| Output SHA-256 | `337c2c9cf7781fa32b2a3459201cecc62d4aa3c1cd1e77ccab1990fc8711cadf` |

The source file is not in the repository. Download it again from the URL above and compare the
SHA-256 to rebuild the same pack.

## Build the pack

```sh
pip install chess zstandard
python tools/puzzles/build_puzzle_pack.py <path>/lichess_db_puzzle.csv.zst
```

`--count N` changes the number of puzzles (default 5,000); `--out PATH` writes somewhere else.
The script prints the summary below. The result does not depend on the order of the source
file: inside each rating band, puzzles are ordered by a SHA-256 hash of their ID.

## Selection rules

1. Quality filter: at least 500 plays, popularity at least 85, rating deviation at most 80,
   rating from 600 to 2599.
2. Even spread: 20 bands of 100 rating points, 250 puzzles from each band.
3. Every chosen puzzle is replayed with python-chess: the FEN must be valid, the setup move and
   every move of the solution must be legal in order, and the solver must have at least one move.
4. Duplicate IDs are rejected.
5. Only the fields the app uses are kept: ID, FEN, moves, rating, popularity and the theme tags
   the app groups (see `THEMES_KEPT` in the script). Game URL, opening tags, play count and
   rating deviation are dropped.

The app checks the pack again: `PuzzleParsingTest.wholeBundledPackIsPlayable` replays all
5,000 puzzles with chesslib, and the app skips any line it cannot read or play.

## Pack format

UTF-8, one puzzle per line, six tab-separated fields:

```text
id  fen  moves  rating  popularity  themes
```

`moves` are UCI, separated by spaces. The FEN is the position **before** the opponent's setup
move; see [architecture.md](architecture.md#move-semantics).

## Generation summary (5,000 puzzles)

```text
source rows read: 6,100,952
rejected quality filter: 4,208,383
rejected invalid FEN: 0
rejected illegal move sequence: 0
rejected duplicate ID: 0
accepted: 5,000
rating distribution: 500 in each 200-point band from 600-799 to 2400-2599
solver side: White 2,588, Black 2,412
solver moves per puzzle: 1: 563, 2: 2,330, 3: 1,556, 4: 397, 5: 120, 6-9: 34
theme groups (a puzzle can be in several): Endgames 2,500, Tactical motifs 2,018,
  Checkmate 1,389, Forks 967, Pins 451, none of the groups 599
most common tags: endgame 2,435, mate 1,389, fork 671, mateIn2 577, mateIn1 563,
  sacrifice 439, kingsideAttack 420, advancedPawn 361, defensiveMove 353, pin 341
```

## Size

The pack is small on purpose. Each puzzle costs about 47 bytes in the compressed APK:

| Puzzles | Pack | Compressed (zlib, as in the APK) |
|---|---|---|
| 2,000 | 226 KB | 96 KB |
| 5,000 | 563 KB | 236 KB |
| 10,000 | 1,127 KB | 468 KB |

A larger pack needs only `--count` and a new build.
