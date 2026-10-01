"""Builds the bundled Square Chess puzzle pack from the Lichess puzzle database (CC0).

Usage:
    python tools/puzzles/build_puzzle_pack.py <lichess_db_puzzle.csv.zst> [--count N] [--out PATH]

Needs python-chess and zstandard (pip install chess zstandard). The source file is not part
of the repository: download it from https://database.lichess.org/#puzzles and record its
SHA-256 in docs/puzzles/data-source.md.

Selection (deterministic, no randomness):
  * quality: at least MIN_PLAYS plays, popularity at least MIN_POPULARITY, rating deviation
    at most MAX_DEVIATION, rating between MIN_RATING and MAX_RATING;
  * every puzzle is replayed with python-chess: valid FEN, legal setup move, every scripted
    move legal, at least one move for the solver;
  * the pack is spread evenly over 100-point rating bands; inside a band the puzzles are
    ordered by a hash of their ID, so the choice does not depend on the order of the source.

Output: UTF-8 TSV, one puzzle per line, "id  fen  moves  rating  popularity  themes".
Moves are UCI, space separated; themes are the Lichess tags in THEMES_KEPT, space separated.
"""
import argparse
import collections
import csv
import hashlib
import heapq
import io
import sys
from pathlib import Path

import chess
import zstandard

MIN_PLAYS = 500
MIN_POPULARITY = 85
MAX_DEVIATION = 80
MIN_RATING = 600
MAX_RATING = 2600
BAND = 100

# Tags the app shows after a solve or uses for its theme groups. Length tags (short, long),
# game-phase tags other than endgame, and evaluation tags (crushing, advantage) are left out.
THEMES_KEPT = {
    "mate", "mateIn1", "mateIn2", "mateIn3", "mateIn4", "mateIn5", "backRankMate", "smotheredMate",
    "anastasiaMate", "arabianMate", "bodenMate", "doubleBishopMate", "dovetailMate", "hookMate",
    "fork", "doubleCheck", "discoveredAttack", "pin", "skewer", "xRayAttack",
    "sacrifice", "deflection", "attraction", "clearance", "interference", "intermezzo",
    "quietMove", "trappedPiece", "hangingPiece", "capturingDefender", "exposedKing", "kingsideAttack",
    "queensideAttack", "zugzwang", "defensiveMove",
    "endgame", "pawnEndgame", "rookEndgame", "bishopEndgame", "knightEndgame", "queenEndgame",
    "queenRookEndgame", "promotion", "underPromotion", "advancedPawn", "castling", "enPassant",
}

DEFAULT_OUT = Path(__file__).resolve().parents[2] / "app/src/main/assets/puzzles/puzzles-v1.tsv"


def replay(fen: str, moves: list[str]) -> str | None:
    """Returns a rejection reason, or None when the whole line is legal."""
    try:
        board = chess.Board(fen)
    except ValueError:
        return "invalid FEN"
    if not board.is_valid():
        return "invalid FEN"
    if len(moves) < 2:
        return "no solver move"
    for text in moves:
        try:
            move = chess.Move.from_uci(text)
        except ValueError:
            return "illegal move sequence"
        if move not in board.legal_moves:
            return "illegal move sequence"
        board.push(move)
    return None


class _Reverse:
    """Orders hashes backwards, so heapq's min-heap keeps the smallest hashes."""
    __slots__ = ("key",)

    def __init__(self, key: str):
        self.key = key

    def __lt__(self, other: "_Reverse") -> bool:
        return self.key > other.key

    def __gt__(self, other: "_Reverse") -> bool:
        return self.key < other.key


def order_key(puzzle_id: str) -> str:
    return hashlib.sha256(puzzle_id.encode()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("--count", type=int, default=10000)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args()

    stats = collections.Counter()
    band_count = (MAX_RATING - MIN_RATING) // BAND
    per_band = args.count // band_count
    # Per band, only the candidates with the smallest ID hashes are kept (a max-heap on the
    # negated hash), with room for rejections, so memory stays small for millions of rows.
    keep = per_band * 2
    bands: dict[int, list[tuple[str, list[str]]]] = collections.defaultdict(list)
    with open(args.source, "rb") as raw:
        text = io.TextIOWrapper(zstandard.ZstdDecompressor().stream_reader(raw), encoding="utf-8")
        for row in csv.DictReader(text):
            stats["source rows read"] += 1
            try:
                rating = int(row["Rating"])
                deviation = int(row["RatingDeviation"])
                popularity = int(row["Popularity"])
                plays = int(row["NbPlays"])
            except (KeyError, ValueError):
                stats["rejected unreadable row"] += 1
                continue
            if not row.get("PuzzleId") or plays < MIN_PLAYS or popularity < MIN_POPULARITY \
                    or deviation > MAX_DEVIATION or not MIN_RATING <= rating < MAX_RATING:
                stats["rejected quality filter"] += 1
                continue
            entry = (_Reverse(order_key(row["PuzzleId"])), [
                row["PuzzleId"], row["FEN"], row["Moves"], str(rating), str(popularity), row["Themes"]])
            heap = bands[rating // BAND]
            if len(heap) < keep:
                heapq.heappush(heap, entry)
            elif entry[0] > heap[0][0]:
                heapq.heapreplace(heap, entry)

    chosen: list[list[str]] = []
    seen: set[str] = set()
    for band in sorted(bands):
        taken = 0
        for _, (pid, fen, moves, rating, popularity, themes) in sorted(bands[band], key=lambda e: e[0].key):
            if taken >= per_band:
                break
            if pid in seen:
                stats["rejected duplicate ID"] += 1
                continue
            reason = replay(fen, moves.split())
            if reason:
                stats[f"rejected {reason}"] += 1
                continue
            kept = " ".join(t for t in themes.split() if t in THEMES_KEPT)
            chosen.append([pid, fen, moves, rating, popularity, kept])
            seen.add(pid)
            taken += 1
    chosen.sort(key=lambda p: p[0])

    args.out.parent.mkdir(parents=True, exist_ok=True)
    body = "".join("\t".join(p) + "\n" for p in chosen)
    args.out.write_text(body, encoding="utf-8", newline="\n")
    data = args.out.read_bytes()

    stats["accepted"] = len(chosen)
    for key in sorted(stats):
        print(f"{key}: {stats[key]}")
    ratings = collections.Counter(int(p[3]) // 200 * 200 for p in chosen)
    print("rating distribution:", ", ".join(f"{r}-{r + 199}: {n}" for r, n in sorted(ratings.items())))
    themes = collections.Counter(t for p in chosen for t in p[5].split())
    print("theme distribution:", ", ".join(f"{t} {n}" for t, n in themes.most_common()))
    print(f"output: {args.out} ({len(data)} bytes)")
    print(f"output SHA-256: {hashlib.sha256(data).hexdigest()}")


if __name__ == "__main__":
    sys.exit(main())
