# Playroom

Optional colour and play for Square Chess. With the Classic world and Playful extras off (the default), the app looks and behaves as before.

## Parts

| Part | Setting | Code |
|---|---|---|
| Recent games on Home | Always on, shown only when it fits | `RecentGames`, `MenuWithOptionalExtra` in `PlayroomUi.kt` |
| Worlds: Forest, Banana Jungle, Ocean, Space | Settings → World | `World` (`Playroom.kt`), palettes in `Palette.kt`, drawings in `WorldArt.kt` |
| Buddy and stickers | Settings → Playful extras | `Buddy`, `Item`, `Sticker` (`Playroom.kt`), figure in `BuddyArt.kt`, rules in `Stickers.kt` |

## Rules

- **The board never gets smaller.** Recent games appear under the Home menu only when the whole menu and the strip fit without scrolling; the boards shrink to the space left and disappear below 64 dp. On a puzzle the buddy uses only the space beside the board (wide screens) or below it (tall screens), never space the board needs.
- **The board screen stays plain.** Worlds are drawn behind Home, Settings, history, the buddy screen and the puzzle menu, never behind a board or the clock. A world gives the board its own square colours.
- **E-ink mode wins.** It ignores the world and draws the buddy as a still black line drawing.
- **Nothing moves, nothing nags.** No animation, no streaks, no notifications. Losing never takes a sticker away.
- **Stickers are worked out, not stored.** `earnedStickers` replays saved games and reads puzzle progress, so turning Playful extras on later gives credit for earlier games. Only the outfit, the chosen buddy and the stickers already announced are saved, with the other settings.
- **Drawn in code.** Worlds and buddies are shapes, so the APK carries no pictures and stays reproducible for F-Droid.

## Characters

Little Robot, the girl, the princess and the monkey follow Square Home's characters; the boy wears Magnus's outfit from the Pancakeverse stories (crown with red and cyan gems, red cape, blue suit with a gold pancake emblem). Sloth and Bob come from the same stories. Every character has to make sense without the stories: its name and first line say who it is ("My name is Sloth, and I'm a monkey").

The drawings follow the Playroom mockup and are not shared with Square Home yet. Sharing them would mean moving the new characters and outfits into Square Home's character package first.

## Stickers

Each sticker is one condition in `earnedStickers`. "Player" means the person at the device: the human against the computer, and either side in a two-player or recorded game. The two puzzle-theme stickers (Hot streak, Far sight) wait until the puzzle pack has been read, which happens the first time Puzzles is opened.

## Tests

- `PlayroomTest.kt`: sticker rules on short real games, outfit saving, and text contrast on every world's cards.
- `PlayroomDeviceTest.kt`: the buddy on Home, Recent games on a tall screen, the buddy screen, and no buddy with Playful extras off.
