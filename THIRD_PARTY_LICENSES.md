# Licences

## Square Chess

Copyright © 2026 Christopher Ottesen (DataEspresso). Square Chess is free software under the **GNU General Public License v3.0 or later**; the full text is in [LICENSE](LICENSE). The app includes a GPL chess engine, so the complete app is distributed under the GPL.

## Third-party components

| Component | Licence | Source | How Square Chess uses it |
|---|---|---|---|
| Fairy-Stockfish, with the Lichess `multistockfish_variant` wrapper | GPL-3.0-or-later | [lichess-org/dart-multistockfish](https://github.com/lichess-org/dart-multistockfish) `896c3884921ae1d775fcd3088ddcd1559fb87308` (package 0.4.0); engine base [fairy-stockfish/Fairy-Stockfish](https://github.com/fairy-stockfish/Fairy-Stockfish) `2b5d95121664fe564779d84aac171f16b725c147` | Vendored in `native/fairy/`, compiled from source into `libsquarefish.so`. Classical evaluation; no NNUE network. Local changes: `native/fairy/PROVENANCE.md`. Authors: `native/fairy/AUTHORS`; licence: `native/fairy/Copying.txt`. |
| Chesslib 1.3.7 | Apache-2.0 | [bhlangonijr/chesslib](https://github.com/bhlangonijr/chesslib) `12dac82e072696c209143f3b10a440044da9531b` | Vendored unmodified in `chesslib/` for move generation and notation. Licence: `chesslib/LICENSE`. |
| Apache Commons Lang 3.18.0 | Apache-2.0 | [commons.apache.org](https://commons.apache.org/proper/commons-lang/) | Dependency of Chesslib. |
| Chessnut chess pieces | Apache-2.0 | [LexLuengas/chessnut-pieces](https://github.com/LexLuengas/chessnut-pieces) `2b8eaf14a31edad7e9deb53b1473e1d4857868a9`, © 2015 Alexis Luengas | Board pieces. The PNGs in `app/src/main/res/drawable-nodpi` are unmodified rasterisations of the source SVGs. Licence and notice: `app/src/main/assets/pieces/`. |
| Lichess chess openings | CC0-1.0 | [lichess-org/chess-openings](https://github.com/lichess-org/chess-openings) `c67912be581f0793dbaa776be5ccf111e01f88d9` | Opening names and ECO codes. `scripts/openings/build_openings.py` turns the lists into `app/src/main/assets/openings.tsv`, indexed by position. |
| Lichess puzzle database | CC0-1.0 | [database.lichess.org](https://database.lichess.org/#puzzles), file dated 9 September 2026 | 5,000 puzzles in `app/src/main/assets/puzzles/puzzles-v1.tsv`, chosen and checked by `tools/puzzles/build_puzzle_pack.py`. Provenance and checksums: `docs/puzzles/data-source.md`. |
| AndroidX (Activity, Compose, Lifecycle, Room, SQLite, Core, …) | Apache-2.0 | [developer.android.com/jetpack](https://developer.android.com/jetpack) | User interface, state and the game database. |
| Kotlin standard library, kotlinx.coroutines | Apache-2.0 | [kotlinlang.org](https://kotlinlang.org) | Language runtime and background work. |
| Okio, Guava `listenablefuture` stub, JSpecify | Apache-2.0 | Maven Central | Transitive dependencies of AndroidX. |

The app icon and the Square Chess brand artwork in `design/` are original work of DataEspresso, under the project licence.

No component depends on Google Play Services, Firebase or any proprietary runtime library; `./gradlew verifyReleasePolicy` checks this on every build.

The app shows these licences offline in **Help & About → Third-party licences** (`app/src/main/assets/licences/`).

## Former engine

Stockfish 19 was used before September 2026 and is no longer part of the source tree or any build. Its provenance (release, commit, NNUE network and checksum) is recorded in [docs/engine/stockfish19-baseline.md](docs/engine/stockfish19-baseline.md).
