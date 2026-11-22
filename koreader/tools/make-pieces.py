#!/usr/bin/env python3
"""Makes the plugin's piece images from the Android app's Chessnut PNGs.

Each image gets the white halo the app draws in e-ink mode (a white silhouette
scaled 1.14x behind the piece, ChessBoard.kt), so pieces stay readable on hatched
squares. KOReader cannot tint and scale at draw time, so the halo is baked in.
Needs Pillow. Run from anywhere: python3 koreader/tools/make-pieces.py
"""
from pathlib import Path

from PIL import Image

REPO = Path(__file__).resolve().parents[2]
SOURCE = REPO / "app/src/main/res/drawable-nodpi"
OUT = REPO / "koreader/squarechess.koplugin/pieces"
HALO_SCALE = 1.14

OUT.mkdir(parents=True, exist_ok=True)
for path in sorted(SOURCE.glob("piece_*.png")):
    piece = Image.open(path).convert("RGBA")
    size = piece.width
    grown = round(size * HALO_SCALE)
    halo = Image.new("RGBA", piece.size, (255, 255, 255, 0))
    white = Image.new("RGBA", piece.size, (255, 255, 255, 255))
    silhouette = Image.new("RGBA", piece.size, (0, 0, 0, 0))
    silhouette.paste(white, mask=piece.getchannel("A"))
    silhouette = silhouette.resize((grown, grown), Image.LANCZOS)
    offset = (size - grown) // 2
    halo.alpha_composite(silhouette.crop((-offset, -offset, -offset + size, -offset + size)))
    halo.alpha_composite(piece)
    # File names match what board.lua loads: wK.png, bQ.png ...
    name = path.stem.split("_")[1]
    halo.save(OUT / f"{name[0]}{name[1].upper()}.png", optimize=True)
    print("wrote", OUT / f"{name[0]}{name[1].upper()}.png")
