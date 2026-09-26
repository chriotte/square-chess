# Licences and pinned source

Square Chess original code: GNU GPL v3 or later; full licence in LICENSE.

## Stockfish

- Official repository: https://github.com/official-stockfish/Stockfish
- Release: sf_19, commit `edb0d9db6731067ec50ce619ff372b463bc4dd5d`.
- Source submodule: native/stockfish. No source modifications; native/bridge.cpp is our GPL wrapper.
- Licence and authors: native/stockfish/Copying.txt and AUTHORS.
- Network: `nn-1a298aa575a0.nnue`, official distribution at https://tests.stockfishchess.org/api/nn/nn-1a298aa575a0.nnue
- SHA-256: `1a298aa575a085434d29027978dc36867fe9c5bcea9376654b7a8eba1e52dfc2`; 98,511,183 bytes.
- Fetch script verifies the network. Public releases must provide network provenance, matching source and reproducible build instructions, including wrapper and exact toolchain, to recipients. Complete distribution audit remains pending.

## Chesslib

- https://github.com/bhlangonijr/chesslib
- Commit `12dac82e072696c209143f3b10a440044da9531b`, pom version 1.3.7.
- Vendored Java source under chesslib/src/main/java, unmodified.
- Apache-2.0, licence in chesslib/LICENSE; source retains original copyright notices.
- Commons Lang 3.18.0: Apache-2.0. AndroidX/Compose/Room: Apache-2.0. Kotlin: Apache-2.0. Full dependency notice aggregation is a release gate.

## Visuals

The Fantasy chess-piece artwork is by Maurizio Monge and is used under the
MIT License. Source links and the full notice are in
`THIRD_PARTY_NOTICES.md` and `app/src/main/assets/pieces/`. The Android PNGs
are rasterized copies of the corresponding SVGs.
