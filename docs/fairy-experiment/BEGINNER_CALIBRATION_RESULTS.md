# Beginner calibration: automated game results

> **Update, 28 September 2026.** The rejected-response blocker below is resolved; see the final decision in FAIRY_STOCKFISH_RECOMMENDATION.md. The Titan batch was retrieved: skill −3 lost 2/2; −2 and −1 each won 1/2; the two skill-0 controls both ended 1-0. Raw data: square-chess-fairy-reports/titan-backup-2026-09-28 and race-investigation/.

28 September 2026. User feedback: E is fun but still too strong when deliberately playing like a beginner. The objective here is to find a plausible weaker candidate, not assign Elo or finalize the slider.

## Recommendation

Skill -3 is the strongest candidate for the next **easier** human playtest: it lost all four observed games against E across Titan and emulator, yet consistently captured the free queen and delivered simple mates in the probes. This avoids the most obvious failure of A-D. It is not proven beginner-appropriate and could still punish novice mistakes too reliably. Skills -2 and -1 did not show a useful separation from E in this small sample.

**Do not promote this experiment into production yet.** One emulator engine response was rejected by Chesslib during the first -1 game. It did not reproduce in 30 fresh searches of the saved position or the four-game continuation rerun. The original returned string/history was not captured, so the cause is unknown. The improved harness now records both, plus legal moves and the full UCI trace on failure. A passing rerun is not a fix. Treat this as an unresolved engine/integration validation blocker, not a confirmed upstream engine bug.

## Complete outcomes available locally

| Candidate skill | Recorded games | Wins vs E | Losses vs E | Draws |
|---|---:|---:|---:|---:|
| -3 | 4 | 0 | 4 | 0 |
| -2 | 4 | 2 | 2 | 0 |
| -1 | 2 | 1 | 1 | 0 |
| 0 | 2 | 1 | 1 | 0 |

These are candidate results, with the candidate playing White once and Black once per device/run. The two skill-0 games are E-versus-E controls; the candidate label only identifies one side. Twelve completed game outcomes are locally available: four from the Titan and eight from the emulator. All twelve ended by checkmate. Eight emulator PGNs are in beginner-calibration-summary/automated-games.pgn.zip.

The full eight-game Titan batch passed in 412.898 seconds, but the phone disconnected before its final files could be exported. Only its first four results were captured live. The remaining outcomes and all Titan PGNs remain in the app under files/beginner-calibration. Do not overwrite that folder before retrieving it. This is a data-export limitation, not a failed Titan test.

The first emulator batch completed four games, then failed the fifth at ply 10. A separate four-game rerun completed the -1 and 0 games, in 197.275 seconds including probes. We retain the failed run and do not label the whole test history as passing.

## Method and limitations

All searches used the pinned Fairy engine, classical evaluation, one worker, Hash 16 MB, MultiPV 8, 500 ms, UCI_LimitStrength=false and Use NNUE=false. No alternative move-selection algorithm was implemented. Test-only profile IDs 10/11/12 select skills -3/-2/-1; the visible E-H selector, saved IDs and production app are unchanged.

Each game begins with a fixed legal poor opening by the E reference side: a- and h-pawn pushes, while the candidate occupies the centre. After four plies, both sides use the real engine. This is a weak-opening recovery scenario, **not a simulated novice for an entire game**, and no claim is made about matching Lichess Level 2. The side assignments reverse for the second game. Random handicapping, different hardware/search depth, only two games per setting/device, and unequal openings preclude statistically meaningful ratings or a final level mapping.

Chesslib validates every move and determines results. The harness claims available repetition/50-move draws and imposes a 400-ply cap; a capped game would be marked '*' rather than counted as a win/draw. No completed locally recorded game hit the cap.

The emulator first run performed 144 tactical probes: four settings, three positions, twelve repetitions each. Every setting took the undefended queen 12/12 times, delivered the simple queen mate 12/12 times and found Fool's Mate 12/12 times. The continuation repeated 72 probes for -1 and 0, also all successful. These deliberately simple probes measure obvious tactical competence, not overall strength. The pinned handicap formula changes sharply near these negative skill values, so linear slider-to-skill assumptions are unsafe.

## Rejected-response evidence

Initial failure: beginner-calibration-emulator/instrumentation.txt, skill -1 as White, ply 10. Saved FEN:

```text
rnb1kbnr/1p2ppp1/2pq4/p2p3p/3PP3/2N3N1/PPP2PPP/R1BQKB1R w KQkq - 0 6
```

Thirty isolated searches passed; see beginner-calibration-emulator/repro-log.txt. They start from the FEN and reset the hash, so they do not reproduce the missing original history or warm-engine state. The continuation run also passed, but uses new random play and therefore does not recreate the exact failed game. Do not infer that the original response was a chess move rather than an error/sentinel string: it was not logged. Future failures now preserve that distinction.

## Verification and preservation

59 debug unit tests passed. The native test-only ID extension built for ARM64 and x86_64. All eight user saves were backed up before the phone tests. The new harness writes only its own test-artifact folders and does not operate on the saved-game database. A final row comparison is pending the phone reconnecting; do not claim it has been verified for this batch.

No production engine migration, public source publication, licence change, Elo calibration or final slider mapping occurred. The user's requested familiar difficulty slider remains the intended final UI once calibration and the reliability blocker are resolved.
