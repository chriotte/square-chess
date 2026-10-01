# Puzzle data source

The bundled puzzles come from the **Lichess puzzle database**.

| Item | Value |
|---|---|
| Source | <https://database.lichess.org/#puzzles>, file `lichess_db_puzzle.csv.zst` |
| Licence | Creative Commons CC0 (checked on the Lichess database page on 1 October 2026: the exports may be used "for research, commercial purpose, publication, anything you like") |
| Source version | File dated 9 September 2026 (`Last-Modified: Wed, 09 Sep 2026 17:40:14 GMT`), 304,429,328 bytes, 6,100,952 puzzles |
| Source SHA-256 | `95fd454bec9efe8f940d5863d5db4c57474f281a865834997bd8cb5d6a149bb9` |
| Output | `app/src/main/assets/puzzles/puzzles-v1.tsv` |
| Puzzles in the pack | 10,000 |
| Output size | 1,126,808 bytes (about 470 KB compressed in the APK) |
| Output SHA-256 | `824986b477211035e4f019ed9db99c96e38891557e8086b46d2d9fa652ad0749` |

The source file is not in the repository. Download it again from the URL above and compare the
SHA-256 to rebuild the same pack.

## Build the pack

```sh
pip install chess zstandard
python tools/puzzles/build_puzzle_pack.py <path>/lichess_db_puzzle.csv.zst
```

`--count N` changes the number of puzzles (default 10,000); `--out PATH` writes somewhere else.
The script prints the summary below. The result does not depend on the order of the source
file: inside each rating band, puzzles are ordered by a SHA-256 hash of their ID.

## Selection rules

1. Quality filter: at least 500 plays, popularity at least 85, rating deviation at most 80,
   rating from 600 to 2599.
2. Even spread: 20 bands of 100 rating points, 500 puzzles from each band.
3. Every chosen puzzle is replayed with python-chess: the FEN must be valid, the setup move and
   every move of the solution must be legal in order, and the solver must have at least one move.
4. Duplicate IDs are rejected.
5. Only the fields the app uses are kept: ID, FEN, moves, rating, popularity and the theme tags
   the app groups (see `THEMES_KEPT` in the script). Game URL, opening tags, play count and
   rating deviation are dropped.

The app checks the pack again: `PuzzleParsingTest.wholeBundledPackIsPlayable` replays all
10,000 puzzles with chesslib, and the app skips any line it cannot read or play.

## Pack format

UTF-8, one puzzle per line, six tab-separated fields:

```text
id  fen  moves  rating  popularity  themes
```

`moves` are UCI, separated by spaces. The FEN is the position **before** the opponent's setup
move; see [architecture.md](architecture.md#move-semantics).

## Generation summary (10,000 puzzles)

```text
source rows read: 6,100,952
rejected quality filter: 4,208,383
rejected invalid FEN: 0
rejected illegal move sequence: 0
rejected duplicate ID: 0
accepted: 10,000
rating distribution: 1,000 in each 200-point band from 600-799 to 2400-2599
solver side: White 5,225, Black 4,775
solver moves per puzzle: 1: 1,147, 2: 4,626, 3: 3,127, 4: 776, 5: 242, 6-9: 82
theme groups (a puzzle can be in several): Endgames 4,987, Tactical motifs 4,124,
  Checkmate 2,738, Forks 1,884, Pins 928, none of the groups 1,193
most common tags: endgame 4,871, mate 2,738, fork 1,280, mateIn1 1,145, mateIn2 1,110,
  sacrifice 888, kingsideAttack 848, defensiveMove 729, advancedPawn 723, pin 686
```

## Size

The pack is small on purpose. Each puzzle costs about 47 bytes in the compressed APK:

| Puzzles | Pack | Compressed (zlib, as in the APK) |
|---|---|---|
| 2,000 | 226 KB | 96 KB |
| 5,000 | 563 KB | 236 KB |
| 10,000 | 1,127 KB | 468 KB |

The first version had 5,000 puzzles; it was raised to 10,000 (the lower end of the 10,000 to 20,000 the spec recommends) after the size was measured. 20,000 would add about 940 KB to the APK in total. A different size needs only `--count` and a new build. Because each band takes the puzzles with the smallest ID hashes, a larger pack always contains the smaller one.
