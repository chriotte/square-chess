#!/usr/bin/env python3
"""Draws koreader.png, the download badge for the KOReader plugin.

KOReader publishes no badge of its own, so this is original artwork in the
shape of the Google Play, GitHub and F-Droid badges beside it: 646x250, a
black rounded rectangle with a grey border, shown at height 64 in the README.
The icon is an e-reader whose screen holds a chessboard.

    python3 docs/images/badges/make-koreader-badge.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H = 646, 250
SCALE = 4  # drawn large, then reduced, so the curves and the board stay clean
BLACK, WHITE, BORDER = (0, 0, 0, 255), (255, 255, 255, 255), (154, 154, 154, 255)
FONT_DIR = Path("/usr/share/fonts/google-noto")
SMALL = FONT_DIR / "NotoSans-Regular.ttf"
LARGE = FONT_DIR / "NotoSans-Bold.ttf"


def draw_reader(d, x, y, w, h):
    """An e-reader: a rounded body, a chessboard screen, a home button."""
    r = h // 14
    d.rounded_rectangle([x, y, x + w, y + h], radius=r, fill=WHITE)
    bezel_x, bezel_top = w // 10, h // 11
    sx0, sy0 = x + bezel_x, y + bezel_top
    sx1, sy1 = x + w - bezel_x, y + h - bezel_top * 3
    d.rectangle([sx0, sy0, sx1, sy1], fill=BLACK)
    # Four ranks and files are enough to read as a chessboard at this size.
    cells = 4
    cw, ch = (sx1 - sx0) / cells, (sy1 - sy0) / cells
    for row in range(cells):
        for col in range(cells):
            if (row + col) % 2 == 0:
                d.rectangle([sx0 + col * cw, sy0 + row * ch,
                             sx0 + (col + 1) * cw, sy0 + (row + 1) * ch], fill=WHITE)
    cx, cy = x + w / 2, y + h - bezel_top * 1.5
    rr = bezel_top * 0.55
    d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], outline=BLACK, width=max(2, int(rr / 3)))


def main():
    img = Image.new("RGBA", (W * SCALE, H * SCALE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = SCALE

    pad, radius, border = 8 * s, 30 * s, 6 * s
    d.rounded_rectangle([pad, pad, W * s - pad, H * s - pad],
                        radius=radius, fill=BLACK, outline=BORDER, width=border)

    draw_reader(d, 46 * s, 62 * s, 86 * s, 126 * s)

    small = ImageFont.truetype(str(SMALL), 38 * s)
    large = ImageFont.truetype(str(LARGE), 82 * s)
    text_x = 170 * s
    d.text((text_x, 64 * s), "GET IT FOR", font=small, fill=WHITE)
    d.text((text_x - 4 * s, 104 * s), "KOReader", font=large, fill=WHITE)

    out = Path(__file__).with_name("koreader.png")
    img.resize((W, H), Image.LANCZOS).save(out, optimize=True)
    print(out)


if __name__ == "__main__":
    main()
