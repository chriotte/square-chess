"""Builds app/src/main/assets/openings.tsv from the Lichess opening list (CC0).

Each output line is "position<TAB>ECO<TAB>name". The position is the first three FEN
fields (pieces, side to move, castling rights), so a transposition finds its opening too.

Usage: python scripts/openings/build_openings.py   (needs python-chess: pip install chess)
"""
import io
import urllib.request
from pathlib import Path

import chess
import chess.pgn

COMMIT = "c67912be581f0793dbaa776be5ccf111e01f88d9"
SOURCE = f"https://raw.githubusercontent.com/lichess-org/chess-openings/{COMMIT}/{{}}.tsv"
OUTPUT = Path(__file__).resolve().parents[2] / "app/src/main/assets/openings.tsv"


def key(board: chess.Board) -> str:
    return " ".join(board.fen().split(" ")[:3])


def main() -> None:
    names: dict[str, tuple[str, str]] = {}
    for part in "abcde":
        text = urllib.request.urlopen(SOURCE.format(part)).read().decode("utf-8")
        for line in text.splitlines()[1:]:
            eco, name, pgn = line.split("\t")
            game = chess.pgn.read_game(io.StringIO(pgn))
            board = game.end().board()
            # The first line for a position wins; the list has few duplicates.
            names.setdefault(key(board), (eco, name))
    lines = [f"{position}\t{eco}\t{name}" for position, (eco, name) in sorted(names.items())]
    OUTPUT.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(f"{len(lines)} positions written to {OUTPUT}")


if __name__ == "__main__":
    main()
