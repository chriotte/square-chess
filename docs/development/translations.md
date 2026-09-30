# Translations

Square Chess is available in English, Simplified Chinese, Norwegian Bokmål, German, Spanish and French. The app uses the phone's language by default; Settings → Language chooses another one.

## Files

| File | Content |
|---|---|
| `app/src/main/res/values/strings_ui.xml` | All screen text except help (English source) |
| `app/src/main/res/values/help.xml` | Help & About, How to play, privacy and feedback pages |
| `app/src/main/res/values/strings.xml` | Home-screen entries and the FEN import help |
| `values-zh-rCN`, `values-nb`, `values-de`, `values-es`, `values-fr` | The same three files, translated |
| `app/src/main/res/xml/locales_config.xml` | Languages that Android 13+ lists under the system's App languages |
| `Language.kt` → `APP_LANGUAGES` | Languages in Settings → Language, each named in its own language |

## Rules

- Keep every placeholder (`%1$s`, `%2$d`, `%d`) and its number. `LocalizationDeviceTest` formats every string in every language and fails on a wrong placeholder.
- Counts use `<plurals>`. Give each language the quantities it needs: Chinese only `other`; Spanish and French also `many`.
- French uses the typographic apostrophe `’`, so the XML needs no `\'` escapes.
- Toolbar labels (Hint, Hide, Review, Return, Undo, Menu, Pause, Resume) must stay short. They never wrap (so the header and the board keep their size); a long label only takes space from the status text.
- Lint reports a missing translation as an error, so a new English string needs all translations before the build passes.

## What stays English on purpose

- Chess notation (e4, Nf3, O-O) and keyboard move entry: the parser reads English piece letters. The keyboard help says so in each language.
- Data in saved games and PGN files: player names ("White", "Computer (Level 3)"), result reasons ("Checkmate") and PGN tags. The screens translate the known values (`displayName`, `reasonText` in `Texts.kt`) and show other values as they are.
- The technical detail of a rejected FEN or PGN file (for example "move 12 (Nf3) is not legal"). The sentence around it is translated.
- The bug-report e-mail body, which the developer reads.

## Adding a language

1. Copy the three files from `values` to `values-<code>` and translate them.
2. Add the language to `APP_LANGUAGES` (tag and its own name) and to `locales_config.xml`.
3. Run `./gradlew -Pdev=true lintDebug connectedDebugAndroidTest` (the localization test covers the new language automatically).
4. Check the game screen on the narrowest emulator (Light Phone III, 411 dp) in the new language.

## Review status

The translations were written on 30 September 2026 and have not yet been checked by native speakers. Norwegian: the developer. Others: open for review.
