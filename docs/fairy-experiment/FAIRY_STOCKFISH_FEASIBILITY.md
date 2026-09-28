# Fairy-Stockfish feasibility — 28 September 2026

The isolated prototype works technically. The original A–D profiles failed human calibration: the user found all four implausibly weak. Legal moves and variety were never proof of a useful beginner opponent. Production remains Stockfish 19; no migration is approved.

## Reference and implementation

The exact pinned Lichess mobile reference is [56eddc2](https://github.com/lichess-org/mobile/blob/56eddc238fe485eb49beb6f3c4b483afd3624b93/lib/src/model/engine/opponent_level.dart). It pins dart-multistockfish 896c3884921ae1d775fcd3088ddcd1559fb87308; its Fairy source tree is 4787415e0f4abe16ac0dcefb56e7ae33e4debc65, based on upstream 2b5d95121664fe564779d84aac171f16b725c147. See native/fairy/PROVENANCE.md. Lichess has private stream and namespace patches; this is not today's upstream master.

The actual UCI handshake advertises Skill -20..20, MultiPV 1..500 and Threads 1..512. Runtime configuration is standard chess, Threads=1, Hash=16 MB, MultiPV=8, Ponder=false, Use NNUE=false and UCI_LimitStrength=false. JNI sends position plus the complete move history, then go movetime 500. Chesslib independently checks returned moves. A serialized operation lock, asynchronous stop, new-game readiness barrier and clean shutdown adapt the existing app interface.

Lichess's pinned Level 2 uses Skill -6, MultiPV 8 and 500 ms. Its hash allocation is adaptive (16–256 MB); ours is fixed at 16 MB. Its Fairy spec has no NNUE path. Its other levels vary MultiPV and time as well as skill. Our comparison fixes those variables and is not a reproduction of all Lichess levels. No NNUE is bundled, extracted or required in this prototype.

## Why A–D failed

At every negative skill, the pinned engine's Skill::time_to_pick selects its handicapped candidate at depth 1. The randomness/weakness formula can strongly favor inferior candidates. It may continue searching afterward, so the final reported depth is not the depth used to choose that candidate. This behavior is upstream; our JNI does not choose moves.

The Titan CSV supplied by the other AI shows A–D capturing an undefended queen 0/24 times, versus 6/6 for the full-strength Fairy control and 6/6 for Stockfish 19 Level 2. These are narrow tactical probes, not ratings. Human feedback independently rejects A–D.

After reconnection, six saved Fairy games were backed up to user-playtest/before-upgrade.db and games.json. A and C ended in White checkmate wins after 23 and 21 plies. One D game ended in a White win after 75 plies and includes the reported pawn advance. Another D record is a Black win; two records are unfinished. We do not infer human identity or skill from those outcomes. No historical-game analysis feature was added.

Round two retains A–D with their original IDs and adds E=0, F=4, G=8 and H=12 at the same MultiPV 8 / 500 ms. F is the default for new experimental games. These are candidates, not Elo ratings or a final 1–10 mapping.

## Safety and scope

The experiment package is com.chriotte.squarechess.fairyexperiment; baseline is com.chriotte.squarechess.stockfishbaseline. The main com.chriotte.squarechess installation and clean usability checkpoint are separate. Saved profile IDs are stable; ID 5 remains the test-only full-strength control. No custom move selection, mandatory download, online service or variants were added.

A same-process restart defect in the vendored UCI option insertion counter was found on ARM64 and fixed by clearing/resetting it at initialization. It does not alter evaluation or move selection. Original third-party notices remain intact.

## Licensing and release boundary

[Fairy-Stockfish](https://fairy-stockfish.github.io/) is GPL-covered. The [GPLv3 text](https://www.gnu.org/licenses/gpl-3.0.en.html) and [GNU FAQ](https://www.gnu.org/licenses/gpl-faq.html) distinguish private use from conveying copies. Development can remain in a private repository. Sending a GPL-covered binary to outside testers still invokes distribution obligations; a public repository is not inherently required, but recipients must receive the applicable rights and corresponding source through a compliant delivery method.

This tightly linked JNI application must be treated as a combined GPL-covered distribution, not presumed proprietary because the engine is in a native library. Corresponding source for each delivered build includes the matching application, JNI, modified engine, interface definitions and build/install scripts, plus notices and dependencies as required. A generic upstream link alone is insufficient for our modifications. No new licence declaration or public source publication was made here.

Google Play distribution is not ruled out by GPL, but a release must fulfill corresponding-source and notice obligations and complete dependency/store-term review. A future proprietary version cannot simply keep this GPL JNI combination closed; a differently licensed engine or separately reviewed architecture would be needed. Chesslib and Chessnut artwork are Apache-2.0 with notices retained. The Fairy build contains no NNUE; the preserved SF19 build has its separate network/source obligations. Complete third-party notice aggregation remains a public-release gate.
