# Pinned engine provenance

Lichess mobile reference: `56eddc238fe485eb49beb6f3c4b483afd3624b93`.
Its pubspec.lock pins dart-multistockfish `896c3884921ae1d775fcd3088ddcd1559fb87308`.
Package: `pkgs/multistockfish_variant`, version 0.4.0.
Engine directory upstream base: `2b5d95121664fe564779d84aac171f16b725c147`.
The vendored engine src tree at that package commit is Git tree `4787415e0f4abe16ac0dcefb56e7ae33e4debc65`.
This is Lichess's modified source, not pristine upstream or today's master. It uses FairyStockfish namespace and private sfio streams.

Copied src, AUTHORS, Copying.txt, sfio files and stockfish_variant wrapper/header. Wrapper include paths shortened to `src/`; engine code otherwise unchanged except the explicit restart fix in `src/ucioption.cpp` (reset insertion order and clear option map at UCI init). Added `../fairy_bridge.cpp` and CMake integration. No custom move selection, evaluation or variant functionality is added to the app.

The restart fix is dated in source. It fixes options disappearing from the second UCI handshake after close/start in one process, observed by actual ARM64 tests. The option insertion counter was previously a function-local static, so its indices exceeded the map size on subsequent initialization.

All original third-party licence headers are retained. GPL-3.0-or-later engine/wrapper plus application corresponding-source obligations apply to distribution. This private branch is not an offer to public recipients. See experiment reports for distribution requirements.
