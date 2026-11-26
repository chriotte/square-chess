#!/usr/bin/env python3
"""Draws web.png, the "Play online" badge for squarechess.org.

Original artwork in the same geometry as the other badges (see
make-koreader-badge.py): a 646x250 canvas with the visible button inset at
(41, 41) to (605, 209), a 4px #A6A6A6 border and a corner radius of about 20.

The icon is a browser window whose page holds a chessboard.

    python3 docs/images/badges/make-web-badge.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H = 646, 250
BOX = (41, 41, 605, 209)
RADIUS, BORDER = 20, 4
SCALE = 4
BLACK, WHITE, GREY = (0, 0, 0, 255), (255, 255, 255, 255), (166, 166, 166, 255)
FONT_DIR = Path("/usr/share/fonts/google-noto")
SMALL = FONT_DIR / "NotoSans-Regular.ttf"
LARGE = FONT_DIR / "NotoSans-Bold.ttf"


def draw_browser(d, x, y, w, h):
    """A browser window: a title bar with three dots, and a chessboard page."""
    d.rounded_rectangle([x, y, x + w, y + h], radius=max(2, h // 10), fill=WHITE)
    bar = h * 0.2
    r = bar * 0.18
    for i in range(3):
        cx, cy = x + bar * 0.45 + i * bar * 0.5, y + bar * 0.55
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=BLACK)
    pad = w * 0.08
    sx0, sy0, sx1, sy1 = x + pad, y + bar, x + w - pad, y + h - pad
    d.rectangle([sx0, sy0, sx1, sy1], fill=BLACK)
    cols, rows = 5, 4
    cw, ch = (sx1 - sx0) / cols, (sy1 - sy0) / rows
    for row in range(rows):
        for col in range(cols):
            if (row + col) % 2 == 0:
                d.rectangle([sx0 + col * cw, sy0 + row * ch,
                             sx0 + (col + 1) * cw, sy0 + (row + 1) * ch], fill=WHITE)


def main():
    s = SCALE
    img = Image.new("RGBA", (W * s, H * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    x0, y0, x1, y1 = (v * s for v in BOX)
    box_h = y1 - y0
    half = BORDER * s // 2
    d.rounded_rectangle([x0 + half, y0 + half, x1 - half, y1 - half],
                        radius=RADIUS * s, fill=BLACK, outline=GREY, width=BORDER * s)

    icon_h = int(box_h * 0.56)
    icon_w = int(icon_h * 1.2)
    icon_x = x0 + int(box_h * 0.17)
    draw_browser(d, icon_x, y0 + (box_h - icon_h) // 2, icon_w, icon_h)

    small = ImageFont.truetype(str(SMALL), int(box_h * 0.165))
    large = ImageFont.truetype(str(LARGE), int(box_h * 0.27))
    text_x = icon_x + icon_w + int(box_h * 0.16)
    d.text((text_x, y0 + box_h * 0.17), "PLAY ONLINE AT", font=small, fill=WHITE)
    d.text((text_x - 2 * s, y0 + box_h * 0.42), "squarechess.org", font=large, fill=WHITE)

    out = Path(__file__).with_name("web.png")
    img.resize((W, H), Image.LANCZOS).save(out, optimize=True)
    print(out)


if __name__ == "__main__":
    main()
