# Physical-keyboard apps

Square Chess reads moves from key events (`MainActivity.dispatchKeyEvent`). The game screen has no text field. Keyboard apps (input methods) differ in how they deliver characters when there is no text field.

## Investigation, 30 September 2026

User report: on a Titan 2 Elite with PhysiBoard, letters work but numbers do not. Tested on a Titan 2 Elite (Android 16, build `Titan 2 Elite_EEA_V02.00.02`) with Pastiera Nightly 0.86 and PhysiBoard 2.0.7 (GitHub release, SHA-256 `565d60e3…f55e`), using temporary logging of every key event (branch `debug/keyboard-diagnostics`).

| Input | Pastiera | PhysiBoard |
|---|---|---|
| `e` | `KEYCODE_E`, `unicodeChar='e'` | the same |
| Alt + E → `2` | `ALT_RIGHT` down, then `KEYCODE_E` with `metaState=0x22`, `unicodeChar='2'` | the same (the Alt key-down itself is not passed on) |
| Hold E | key repeats; the first repeat has `FLAG_LONG_PRESS` (flags `0x8c`) | `e` down, one repeat with `FLAG_LONG_PRESS`, then nothing more |
| Backspace | `KEYCODE_DEL` | `KEYCODE_DEL` |
| Enter | `KEYCODE_ENTER` | `KEYCODE_ENTER` |

PhysiBoard's long press (`AltSymManager.kt`) calls `inputConnection.deleteSurroundingText(1, 0)` and `commitText(altChar, 1)`: it writes the digit into the focused text field. A game has no text field, so the digit is lost. Alt digits work with both apps because they arrive as ordinary key events.

## Rejected fix: a hidden text connection

A text-input connection on the board (`PlatformTextInputModifierNode`, branch `experiment/ime-text-connection`) makes PhysiBoard see an editable field. It then:

- shows its toolbar (clipboard, microphone) for every key, which shrank the board or, when the app hid it, blinked;
- commits every letter as text instead of sending key events;
- still did not turn a held key into a digit (it repeated the letter).

With `TYPE_NULL` the toolbar stays away, but PhysiBoard then treats the field as not editable and behaves as before. Pastiera is a close relative, so its working behaviour would probably change too.

## Fix: held keys

Both apps pass the first long-press repeat (`FLAG_LONG_PRESS`) to the app. When the letter that key typed is still the last character of the move, Square Chess replaces it with the key's Alt character from the keyboard's own key map (`KeyCharacterMap.get(keyCode, META_ALT_ON…)`): E → 2 on a Titan. Only digits and `-+#=` are used. Nothing is specific to one phone or layout; a keyboard without Alt characters is not affected.

Tests: `NotationTest` (the rule), `HeldKeyDeviceTest` (the event path; it runs only on a device whose key map has Alt digits on letter keys, such as the Titan, and is skipped on the emulators).

## Manual test on a Titan

With Pastiera and with PhysiBoard, in a game: `e4`, `Nf3`, `e2e4`, `O-O`, digits with Alt and by holding the key, Backspace, Enter. Check that no on-screen keyboard or toolbar appears and the board keeps its size.
