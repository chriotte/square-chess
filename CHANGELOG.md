# Changelog

## 1.2.0 — 30 September 2026
- Optional evaluation: tick "Show evaluation" when you start a game against the computer or over the board. After each move the header shows the move's label (best move, good move, inaccuracy, mistake or blunder) and who is ahead, for example "+0.6 · White slightly better". The engine rates each position for 0.3 seconds at full strength, and the computer replies after the rating. The review shows the label of each move, and the move list counts each side's inaccuracies, mistakes and blunders. Off by default, because it uses more battery; the choice and the ratings are saved with the game (database version 6). Not available when you record a real-board game.
- Opening names: the header and the move list show the opening, for example "C60 Ruy Lopez", from the Lichess opening list (CC0). The names are in English in all languages.
- E-ink mode is on by default on more e-ink devices: Boyue/Likebook, Meebook, Bigme, Hanvon, Nook GlowLight, Tolino, Hisense A5 and A9, and other e-reader brands and boards. Brands that also make colour screens need an exact model.
- Castling: tap the king and then its own rook, or drag the king onto the rook. Tapping the king's destination square still works.
- With hints on, the Undo button now stays in the header and Review moves to the menu when there is not room for both.

## 1.1.0 — 30 September 2026
- Optional hints against the computer: tick "Allow hints" when you start the game. During your turn, Hint shows one suggested move from Fairy-Stockfish at full strength, as an arrow with a framed start square and a ring on the target square, plus the move in text ("Hint: Nf3 · Knight g1 → f3"). The shapes do not depend on colour. The hint never plays the move, does not stop the clock and is not saved in the PGN. It goes away when the position changes. Off by default; the choice is saved with the game (database version 5).
- E-ink mode (Settings): black and white only, without animations, for e-ink devices. Dark squares are hatched as in printed chess diagrams, and the selected square, the last move and check are shown by shapes. On by default on known e-ink devices.
- Languages: Simplified Chinese, Norwegian Bokmål, German, Spanish and French, in addition to English. The app follows the phone's language; Settings → Language chooses another one.
- Numbers from a held key: holding a letter key types its Alt character (E → 2 on a Titan), so long-press numbers work with keyboard apps such as PhysiBoard as well as Pastiera.
- E-ink mode: dialogs and menus have a black outline.
- Phones with a camera hole in the middle of the screen (for example the CMF Phone 2 Pro): the game header now sits below the camera instead of beside it.
- Fix: the standalone chess clock did not always pause when you left the app.

## 1.0.8 — 30 September 2026
- The APK no longer contains Google's encrypted dependency list (an extra signing block that F-Droid does not accept). The Play bundle still has it. No changes to the app itself.

## 1.0.7 — 30 September 2026
- Release builds use JDK 21, the same as the F-Droid build server, so F-Droid's build of the source matches the signed APK on GitHub Releases. No changes to the app itself.

## 1.0.6 — 29 September 2026
- Reproducible on any day and computer: the engine no longer embeds its compile date, and packaged assets keep LF line endings on every OS. No changes to the app itself.

## 1.0.5 — 29 September 2026
- Reproducible build: the native engine no longer embeds the build folder paths, so two builds of the same tag are byte-identical. This lets F-Droid publish the same signed APK as GitHub Releases. No changes to the app itself.
- F-Droid recipe (`.fdroid.yml`) and documentation (`docs/releases/fdroid.md`).
- CI uses the runner's Android SDK and current action versions; the release workflow can rebuild an existing tag.

## 1.0.4 — 29 September 2026
- Import games from a PGN file (Game history → Import): all games in the file, comments and variations ignored, duplicates skipped, unreadable games reported. Together with Export, this moves a game library between phones or between Play and other sources.
- New "How to play" guide in Help & About: game types, moving pieces, keyboard moves, levels, clocks, review, draws, game history and settings.
- About Square Chess and App information name the author, Christopher Ottesen.
- First standalone APK on GitHub Releases, signed with the standalone release key.
- Removed the Stockfish 19 build and the unused DataStore library; `verifyReleasePolicy` guards against proprietary Google libraries and new permissions.
- Emulator profile for the Light Phone III (1080 × 1240, 420 dpi).
- Repository prepared for open source: README, CONTRIBUTING, SECURITY, THIRD_PARTY_LICENSES, `docs/`, `fastlane/` store metadata, CI and tag-driven release workflows. Lint errors fixed.

## 1.0.3 — 29 September 2026 (Play internal testing)
- Drag-and-drop stays accurate after screen size changes.
- Game export works with more storage providers.

## 1.0.2 — 28 September 2026 (first Play release)
- Fairy-Stockfish (classical evaluation, no NNUE) replaces Stockfish 19; the app is about 80 MB smaller. Ten difficulty levels set the engine's own Skill Level and MultiPV.
- Fixed a race in the engine output pipe that could return the previous move.
- Full PGN export of the game library (filter by mode and period; save or share); game delete; dates and move counts in history.
- Resign against the computer; grouped game menu; last move in the header; review arrows no longer cover the board.
- Larger legal-move dots and capture rings, drag-and-drop, promotion with piece images, move sounds and vibration.
- Settings: sounds, vibration, legal moves, coordinates, board colours.
- Captured pieces and a move line appear only where the board cannot use the space.
- Privacy policy linked in Help & About.

## Before 1.0.2 — development
- Compact square board and physical-keyboard UCI/SAN entry.
- Computer, two-player and recording game types; integrated and standalone chess clocks.
- Room persistence with migrations, SAN history, promotion chooser, FEN import, PGN and FEN sharing.
