# E-ink mode

Prototype on the branch `feature/eink` (30 September 2026). It replaces the monochrome plan saved on 29 September for the Light Phone III. It is for e-paper screens (Boox readers) and for black-and-white screens such as LightOS.

## Why the standard colours fail on e-paper

Brightness contrast of the board colours as a monochrome screen shows them (WCAG relative luminance):

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

E-paper has more problems: grey is dithered in the fast 1-bit modes, animations leave ghost images, and each screen change is a visible refresh.

## Design

The model is a printed chess diagram: black ink on white paper.

- **Only pure black and white.** The one exception is the inactive part of the difficulty slider (light grey), which still reads when it is dithered.
- **Board:** light squares are white. Dark squares are white with 45° black hatching, seven lines per square, so the pattern continues from square to square.
- **Pieces:** the normal piece set. On hatched squares each piece has a white halo (the piece shape, tinted white, 14 % larger, drawn behind it), so the outline does not merge with the lines.
- **Highlights are shapes:**
  - selected square: a thick black frame inside the square;
  - last move: L-shaped marks in the four corners of the two squares. They stay until the next move: the standard mode removes its highlight after 1.2 s, and on e-paper that is one more refresh;
  - check: a black ring around the king;
  - legal moves: a black dot, or a black ring on a capture, both with a white rim;
  - hint: the same black-and-white arrow, frame and ring as in the standard mode.
- **Coordinates:** black, on a small white patch so they read on hatching.
- **Controls:** filled and tonal buttons are black with white text; cards and the side selector have a black outline, because a fill colour is not visible. The running clock (in the game and in the standalone chess clock) is shown inverted, white on black.
- **No animation:** no ripple on touch, and the move list jumps to the end instead of scrolling smoothly.
- **Start-up:** the window background is white, so the app does not flash dark before its first frame.

## Setting

Settings → **E-ink mode** ("Black and white only, no animations. For e-paper screens such as Boox."). It is on by default on Onyx (Boox) devices (`Build.MANUFACTURER == "ONYX"`) until the player changes it. While it is on, the board colour choice is replaced by one line of text.

## To test on a Boox

1. Board and pieces in each Boox refresh mode (HD, Balanced, Fast, Ultrafast), portrait and landscape.
2. Tap-tap moves and drag moves. The dragged piece follows the finger; if this ghosts badly in the slower modes, turn the drag preview off in E-ink mode.
3. A timed game: the clock changes every second. Check whether this causes too much refreshing; if it does, show seconds only in the last minute.
4. Dialogs: Android dims the screen behind a dialog. Check that the dim looks acceptable in 1-bit mode.
5. Hint arrow, check ring, last-move corners and legal-move marks, in normal and flipped orientation.
6. Help & About, Game history and the standalone chess clock.

## Not changed

- The standard mode looks exactly as before.
- The launcher icon and the Android 12+ splash icon keep their colours.
