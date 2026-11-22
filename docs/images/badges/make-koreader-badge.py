#!/usr/bin/env python3
"""Draws koreader.png, the download badge for the KOReader plugin.

KOReader publishes no badge of its own, so this is original artwork matching
the Google Play, GitHub and F-Droid badges beside it in the README. Those
three share a 646x250 canvas with the visible button inset at (41, 41) to
(605, 209), a 4px #A6A6A6 border and a corner radius of about 20, so this one
uses the same geometry and lines up with them at any height.

The icon is an e-reader whose screen holds a chessboard.

    python3 docs/images/badges/make-koreader-badge.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H = 646, 250
BOX = (41, 41, 605, 209)  # the visible button, matching the other three badges
RADIUS, BORDER = 20, 4
SCALE = 4  # drawn large, then reduced, so the curves and the board stay clean
BLACK, WHITE, GREY = (0, 0, 0, 255), (255, 255, 255, 255), (166, 166, 166, 255)
FONT_DIR = Path("/usr/share/fonts/google-noto")
SMALL = FONT_DIR / "NotoSans-Regular.ttf"
LARGE = FONT_DIR / "NotoSans-Bold.ttf"


def draw_reader(d, x, y, w, h):
    """An e-reader: a rounded body, a chessboard screen, a home button."""
    d.rounded_rectangle([x, y, x + w, y + h], radius=max(2, h // 14), fill=WHITE)
    bezel_x, bezel_y = w // 10, h // 11
    sx0, sy0 = x + bezel_x, y + bezel_y
    sx1, sy1 = x + w - bezel_x, y + h - bezel_y * 3
    d.rectangle([sx0, sy0, sx1, sy1], fill=BLACK)
    # Four ranks and files are enough to read as a chessboard at this size.
    cells = 4
    cw, ch = (sx1 - sx0) / cells, (sy1 - sy0) / cells
    for row in range(cells):
        for col in range(cells):
            if (row + col) % 2 == 0:
                d.rectangle([sx0 + col * cw, sy0 + row * ch,
                             sx0 + (col + 1) * cw, sy0 + (row + 1) * ch], fill=WHITE)
    cx, cy = x + w / 2, y + h - bezel_y * 1.5
    r = bezel_y * 0.55
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=BLACK, width=max(2, int(r / 3)))


def main():
    s = SCALE
    img = Image.new("RGBA", (W * s, H * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    x0, y0, x1, y1 = (v * s for v in BOX)
    box_h = y1 - y0
    # The outline straddles the path, so inset it by half its width to keep the
    # ink inside BOX and the badge the same size as the other three.
    half = BORDER * s // 2
    d.rounded_rectangle([x0 + half, y0 + half, x1 - half, y1 - half],
                        radius=RADIUS * s, fill=BLACK, outline=GREY, width=BORDER * s)

    icon_h = int(box_h * 0.68)
    icon_w = int(icon_h * 0.68)
    icon_x = x0 + int(box_h * 0.21)
    draw_reader(d, icon_x, y0 + (box_h - icon_h) // 2, icon_w, icon_h)

    small = ImageFont.truetype(str(SMALL), int(box_h * 0.165))
    large = ImageFont.truetype(str(LARGE), int(box_h * 0.35))
    text_x = icon_x + icon_w + int(box_h * 0.20)
    d.text((text_x, y0 + box_h * 0.17), "GET IT FOR", font=small, fill=WHITE)
    d.text((text_x - 3 * s, y0 + box_h * 0.38), "KOReader", font=large, fill=WHITE)

    out = Path(__file__).with_name("koreader.png")
    img.resize((W, H), Image.LANCZOS).save(out, optimize=True)
    print(out)


if __name__ == "__main__":
    main()
