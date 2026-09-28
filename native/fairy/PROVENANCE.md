# Pinned engine provenance

Lichess mobile reference: `56eddc238fe485eb49beb6f3c4b483afd3624b93`.
Its pubspec.lock pins dart-multistockfish `896c3884921ae1d775fcd3088ddcd1559fb87308`.
Package: `pkgs/multistockfish_variant`, version 0.4.0.
Engine directory upstream base: `2b5d95121664fe564779d84aac171f16b725c147`.
The vendored engine src tree at that package commit is Git tree `4787415e0f4abe16ac0dcefb56e7ae33e4debc65`.
This is Lichess's modified source, not pristine upstream or today's master. It uses FairyStockfish namespace and private sfio streams.

Copied src, AUTHORS, Copying.txt, sfio files and stockfish_variant wrapper/header. Wrapper include paths shortened to `src/`; engine code otherwise unchanged except the explicit restart fix in `src/ucioption.cpp` (reset insertion order and clear option map at UCI init). Added `../fairy_bridge.cpp` and CMake integration. No custom move selection, evaluation or variant functionality is added to the app.

The restart fix is dated in source. It fixes options disappearing from the second UCI handshake after close/start in one process, observed by actual ARM64 tests. The option insertion counter was previously a function-local static, so its indices exceeded the map size on subsequent initialization.

Second local fix, 2026-09-28, in `sfio.cpp` (Lichess wrapper, not engine code): `FdOutBuf` is now internally synchronised. The engine's input stream is tied to its output stream, so the UCI thread flushes the output buffer before each `getline()` without holding `IO_LOCK`, while the search thread may be writing `bestmove` under `sync_cout`. The shared put area allowed both threads to `write()` the same bytes, so a complete `bestmove` line sometimes reached the pipe twice. The next search then returned the previous move, which Chesslib rejected. Evidence: failure log with a single stale `bestmove d8d7` and no `info` lines; a control build with the original `sfio.cpp` reproduced a duplicate `bestmove` within two games. `fairy_bridge.cpp` also now sends `isready` before each `go` and reports any leftover lines as `stale:` metrics. The production Stockfish 19 bridge calls the engine API directly and does not use this pipe layer.

All original third-party licence headers are retained. GPL-3.0-or-later engine/wrapper plus application corresponding-source obligations apply to distribution. This private branch is not an offer to public recipients. See experiment reports for distribution requirements.
