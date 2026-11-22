# Playroom

Optional colour and play for Square Chess. With the Classic world and Playful extras off (the default), the app looks and behaves as before.

## Parts

| Part | Setting | Code |
|---|---|---|
| Recent games on Home | Always on, below the menu | `RecentGamesList` in `PlayroomUi.kt` |
| Worlds: Forest, Banana Jungle, Ocean, Space | Settings → World | `World` (`Playroom.kt`), palettes in `Palette.kt`, drawings in `WorldArt.kt` |
| Buddy and stickers | Settings → Playful extras | `Buddy`, `Item`, `Sticker` (`Playroom.kt`), figure in `BuddyArt.kt`, rules in `Stickers.kt`, game remarks in `GameBuddy.kt` |

## Rules

- **The board never gets smaller.** In puzzles and computer games the buddy uses only the space beside the board (wide screens) or what is left below it after the review bar, move strip and captured rows (tall screens). On small screens such as the Titan 2 Elite it does not appear there at all.
- **The first screen of Home stays the same.** The recent-games list sits below the menu, reached by scrolling, with a link to the whole history.
- **On board screens the buddy stands on the screen itself** and speaks in a bubble, instead of sitting in a box.
- **The board screen stays plain.** Worlds are drawn behind Home, Settings, history, the buddy screen and the puzzle menu, never behind a board or the clock. A world gives the board its own square colours.
- **E-ink mode wins.** It ignores the world and draws the buddy as a still black line drawing.
- **Gentle motion, no nagging.** The buddy breathes, waves, jumps, cries and throws pancakes at 30 frames a second while its screen is in front; it holds still in e-ink mode and when the phone's animations are off. No streaks, no notifications. Losing never takes a sticker away.
- **Locked items explain themselves.** Tapping one shows its sticker and how to earn it under the chips. Unlock all items lets every item be worn; stickers are still earned by playing.
- **Stickers are worked out, not stored.** `earnedStickers` replays saved games and reads puzzle progress, so turning Playful extras on later gives credit for earlier games. Only the outfit, the chosen buddy and the stickers already announced are saved, with the other settings.
- **Drawn in code.** Worlds and buddies are shapes, so the APK carries no pictures and stays reproducible for F-Droid.

## Characters

Little Robot, the girl, the princess and the monkey follow Square Home's characters; the boy wears Magnus's outfit from the Pancakeverse stories (crown with red and cyan gems, red cape, blue suit with a gold pancake emblem). Sloth and Bob come from the same stories. Every character has to make sense without the stories: its name and first line say who it is ("My name is Sloth, and I'm a monkey").

The drawings follow the Playroom mockup and are not shared with Square Home yet. Sharing them would mean moving the new characters and outfits into Square Home's character package first.

## In games against the computer

`gameRemark` decides what the buddy says: its own line while the computer thinks, praise or a gentle "keep going" when the evaluation is on, "Check!" or "Nice capture!" otherwise, then "The computer played Nf3. Your move." and a warning when the king is in check. Screen readers already hear each move, so the buddy's card is read on request, not announced.

## Stickers

Each sticker is one condition in `earnedStickers`. "Player" means the person at the device: the human against the computer, and either side in a two-player or recorded game. The two puzzle-theme stickers (Hot streak, Far sight) wait until the puzzle pack has been read, which happens the first time Puzzles is opened.

## Tests

- `PlayroomTest.kt`: sticker rules on short real games, outfit saving, game remarks, and text contrast on every world's cards.
- `PlayroomDeviceTest.kt`: the buddy on Home, the recent-games list, the buddy screen, and no buddy with Playful extras off.
