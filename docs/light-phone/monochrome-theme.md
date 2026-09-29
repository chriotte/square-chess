# Monochrome board theme (planned, not started)

Saved on 29 September 2026 for the Light Phone III work. LightOS uses a black-and-white interface, and Light's Tool Library expects the same style, so a Light version must look correct without colour.

## Measurement

Brightness contrast of the current board colours as a monochrome screen shows them (WCAG relative luminance):

| Board | Light vs dark squares |
|---|---|
| Green (default) | 4.6 : 1 — clear |
| Walnut | 2.3 : 1 — weak |
| Slate | 2.8 : 1 — weak |

The highlights carry information by colour only, and in greyscale they almost match the dark squares:

| Highlight | Colour | vs light squares | vs dark squares |
|---|---|---|---|
| Selected square | `#C8B56E` | 1.5–1.7 : 1 | 1.5–2.8 : 1 |
| Last move | `#A7AC78` | 1.7–1.9 : 1 | 1.3–2.4 : 1 |
| Check | `#BF7669` | 2.5–2.8 : 1 | 1.0–1.6 : 1 (almost invisible) |

## Plan

1. Add a board theme "Monochrome": light-grey and dark-grey squares with strong contrast; the pieces are already black and white.
2. Show highlights with shapes as well as colour, in every theme: a thick frame on the selected square, corner marks on the last-move squares, a ring around a king in check. This also helps colour-blind players and does not change the board size.
3. Test on the `SquareChess_Light_Phone_III` emulator (1080 × 1240, 420 dpi) and with a greyscale conversion of each theme.

The app already works on the Light Phone III profile: the board is 1070 × 1070 px and the squares are about 51 dp (tested 29 September 2026).
