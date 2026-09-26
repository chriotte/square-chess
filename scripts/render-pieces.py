"""Regenerate Android pieces from the pinned, unmodified Chessnut SVG sources.

Requires Python and CairoSVG (tested with CairoSVG 2.9.1).
"""
from pathlib import Path

import cairosvg

root = Path(__file__).resolve().parents[1]
sources = root / "app/src/main/assets/pieces/chessnut"
output = root / "app/src/main/res/drawable-nodpi"
output.mkdir(parents=True, exist_ok=True)
for side in "wb":
    for kind in "KQRBNP":
        name = side + kind
        cairosvg.svg2png(
            url=str(sources / f"{name}.svg"),
            write_to=str(output / f"piece_{name.lower()}.png"),
            output_width=256,
            output_height=256,
        )
