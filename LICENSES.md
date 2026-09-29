# Licences and pinned source

Square Chess original code: GNU GPL v3 or later; full licence in LICENSE.

## Fairy-Stockfish (production engine)

- Vendored under native/fairy from lichess-org/dart-multistockfish `896c3884921ae1d775fcd3088ddcd1559fb87308` (multistockfish_variant 0.4.0); engine base Fairy-Stockfish `2b5d95121664fe564779d84aac171f16b725c147`.
- Local changes and evidence: native/fairy/PROVENANCE.md. native/fairy_bridge.cpp is our GPL JNI wrapper.
- GPL-3.0-or-later; native/fairy/Copying.txt and AUTHORS. No NNUE network is bundled.

Stockfish 19 is no longer part of the source tree or any build. Its former provenance (release, commit, NNUE network and checksum) is recorded in docs/engine/stockfish19-baseline.md.

## Chesslib

- https://github.com/bhlangonijr/chesslib
- Commit `12dac82e072696c209143f3b10a440044da9531b`, pom version 1.3.7.
- Vendored Java source under chesslib/src/main/java, unmodified.
- Apache-2.0, licence in chesslib/LICENSE; source retains original copyright notices.
- Commons Lang 3.18.0: Apache-2.0. AndroidX/Compose/Room: Apache-2.0. Kotlin: Apache-2.0. Full dependency notice aggregation is a release gate.

## Visuals

The Chessnut chess-piece artwork is by Alexis Luengas and is used under the
Apache License 2.0. Source links and the full notice are in
`THIRD_PARTY_NOTICES.md` and `app/src/main/assets/pieces/`. The Android PNGs
are rasterized copies of the corresponding SVGs.
