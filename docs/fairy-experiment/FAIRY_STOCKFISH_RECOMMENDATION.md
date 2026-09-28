# Final decision (28 September 2026) — Fairy-Stockfish replaces Stockfish 19

The user delegated the engine decision and the merge. Fairy-Stockfish is now the production engine on main, with a Level 1–10 slider. Stockfish 19 stays buildable with `-Pengine=stockfish`.

- **Blocker resolved.** The rejected engine response was a race in the Lichess `sfio.cpp` output buffer: the UCI thread's tied-stream flush and the search thread's `bestmove` write shared an unlocked put area, so a whole `bestmove` line could reach the pipe twice. The next search then returned the previous move. Evidence: a failure log with only a stale `bestmove d8d7` and no `info` lines; a control build with the original `sfio.cpp` reproduced a duplicate within two games. Fix: an internally synchronised `FdOutBuf`, plus an `isready` synchronisation before each `go` that discards and reports leftover lines. With both fixes: 40 of 40 emulator games, zero stale lines, zero rejected moves.
- **Why A–D failed.** Pinned `Skill::pick_best` uses weakness = 120 − 2 × skill. Above 128 (skill −5 and lower) the formula favours worse lines. Skill −4 picks uniformly at random among the MultiPV lines. Skill −3 and higher take free material in every probe. The usable beginner range is −4 to 0.
- **Level mapping** (app/src/main/java/com/chriotte/squarechess/Difficulty.kt): 1 = skill −4/MultiPV 8, 2 = −4/4, 3 = −3/8, 4 = −1/8, 5 = 0/8 (former profile E), 6 = 3, 7 = 6, 8 = 10, 9 = 15 (MultiPV 8), 10 = 20/MultiPV 1 (no handicap). 500 ms per move. These are engine settings, not Elo ratings.
- **Supporting data.** Probes (12 each): skill −4/MultiPV 8 took a free queen 4/12 and found Fool's Mate 1/12; −3, −2, −1 were 12/12 on every probe. Games against skill 0 from a poor fixed opening (10 each): −4, −3 and −2 lost all 10; −1 won 4. Titan: −3 lost 2/2, −2 and −1 each won 1/2. Level 2 (skill −4/MultiPV 4): free queen 3/12, mate in one 12/12, Fool's Mate 2/12; lost 6/6 against Level 5. The engine raises MultiPV to at least 4 whenever the handicap is on, so Level 2 is the smallest step above a random choice among eight that the engine's own options allow.
- **Size.** Debug APK 33.4 MB versus 113.6 MB; no NNUE and no download.
- **Still open.** Human confirmation of Levels 1–3 on the Titan; 16 KB page-size runtime test; public corresponding-source delivery before any distribution.

---

# Earlier recommendation — retain Stockfish in production; calibrate Fairy E–H

> Follow-up: see BEGINNER_CALIBRATION_RESULTS.md. E was judged too strong for beginners, and automated follow-up found an unresolved rejected engine response. Earlier passing tests do not close that new blocker.

28 September 2026.

**Do not merge or replace the production engine yet.** The smaller offline Fairy prototype works, but A–D failed the user's playtest and E–H need fresh human feedback. No final Level 1–10 mapping or Elo rating is justified.

## Findings and decisions

- **Better beginner experience?** Not established. A–D were too weak to be enjoyable; variety alone was a poor quality indicator. Round-two E=0, F=4, G=8 and H=12 improve the undefended-queen probe substantially, but tactical results are stochastic and not strictly monotonic. F is simply the default candidate, not a recommended finished difficulty.
- **Replace Stockfish 19 entirely?** Technically plausible for this standard-chess opponent, but not approved. Full-strength Fairy is not equivalent in strength to modern Stockfish 19. The user experience, long-play lifecycle and true 16 KB runtime checks remain migration gates.
- **Smaller app?** Yes. Fresh debug APK is 33.4 MB versus 113.6 MB; unsigned ARM64 release is 25.6 MB versus 104.4 MB. No NNUE and no compulsory online download. See ENGINE_SIZE_COMPARISON.md for exact bytes and comparison limitations.
- **Response time acceptable?** Around half a second for ordinary positions; no legality failures in the measured benchmark. This is consistent with the chosen 500 ms budget. User-perceived responsiveness still needs feedback. The baseline has a shorter 260 ms budget, so Fairy is not claimed faster or more battery-efficient.
- **Further calibration?** Required. Try E and F first, then G/H if too easy; judge whether it takes obvious material, makes understandable mistakes and feels beatable. Higher skill values do not guarantee an ordering on a tiny tactical sample. Keep A–D available only for reproducing earlier games.
- **Blockers/regressions?** A–D's weak play is a confirmed product failure. A same-process native restart defect was fixed and retested. No observed legal-move or save-upgrade regression. Public distribution still needs a complete corresponding-source/notices delivery audit. Extended physical-device heating/battery testing and a 16 KB runtime remain unverified.

## Verification boundaries

59 debug and 59 release unit tests passed. The full emulator suite reports 28 discovered tests: 24 execute, while two host-stage recovery methods and two opt-in experiment methods skip by design. All five dedicated extended emulator methods executed and passed, including another 180 legal searches and self-play. The Titan native run reports five discovered methods, four executed and the opt-in self-play skipped. It includes 180 searches with valid returned moves, actual configuration checks, cancellation, terminal positions, promotion/castling follow-ups and two start/close cycles.

Six actual user save rows were backed up and compared after the Titan upgrade and native checks; all six match. Fixture tests that clear data were confined to the emulator. A complete 120-ply F-versus-G emulator game ended in a Black win with every move checked through Chesslib. This exercises both sides but is not a human game or physical thermal test.

Existing clock, keyboard entry, review, migration, orientation, recording and lifecycle tests passed. Some use fake-engine controllers to make races deterministic; passing them is not a claim that every listed real-engine UI scenario has been manually replayed. The phone was locked during the final visual-check attempt; native tests still ran, but the updated setup screen is visually checked on the emulator instead.

## Review artifacts

- builds/square-chess-fairy-debug.apk — installable review build, already upgraded on the Titan in the separate Fairy Lab package.
- builds/square-chess-fairy-release-unsigned.apk — size comparison only, unsigned.
- FAIRY_STOCKFISH_FEASIBILITY.md and FAIRY_STOCKFISH_BUILD.md — provenance, mechanism and reproduction.
- DIFFICULTY_TEST_RESULTS.csv and DIFFICULTY_RESULTS_SUMMARY.md — labeled per-run measurements; phone and emulator results must not be pooled as one device.
- user-playtest/ — private backup and preservation evidence; do not publish user game records as source.

The main app and its Stockfish engine remain unchanged. No source publication, licence change, GitHub push or production migration was performed.

## Agreed next UI step

After human calibration, restore the previous difficulty slider and map its levels to the validated engine settings. The lettered selector is temporary; do not silently finalize a mapping before feedback.
