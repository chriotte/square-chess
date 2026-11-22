-- Tests for pgn.lua, clock.lua, evaluation.lua and puzzles.lua. Run with tests/run.sh.
local here = arg[0]:gsub("[^/]*$", "")
package.path = here .. "../squarechess.koplugin/?.lua;" .. package.path
local Chess = require("chess")
local Pgn = require("pgn")
local Clock = require("clock")
local Evaluation = require("evaluation")
local Puzzles = require("puzzles")

local failures, count = 0, 0
local function check(name, cond, detail)
    count = count + 1
    if not cond then failures = failures + 1; print("FAIL " .. name .. (detail and (": " .. tostring(detail)) or "")) end
end
local function eq(name, got, want) check(name, got == want, "got " .. tostring(got) .. ", want " .. tostring(want)) end

--- PGN ---------------------------------------------------------------------
local text = [[
[Event "Club night"]
[White "Ann"]
[Black "Bob"]
[Result "1-0"]

1. e4 {best by test} e5 2. Nf3 (2. f4 exf4 (2... d5) 3. Nf3) Nc6 3. Bb5 $1 a6?! 4.Ba4 Nf6 5. O-O Be7
6. Re1 b5 7. Bb3 d6 8. c3 O-O 9. h3 ; a comment
Nb8 10. d4 1-0

[Event "Second"]
[SetUp "1"]
[FEN "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"]

1. e4 Kd7 2. e5 *
]]
local games = Pgn.parse(text)
eq("pgn game count", #games, 2)
eq("pgn tags", games[1].tags.White, "Ann")
eq("pgn result", games[1].result, "1-0")
eq("pgn main line length", #games[1].sans, 19)
eq("pgn skips variations", games[1].sans[3], "Nf3")
eq("pgn number glued to move", games[1].sans[7], "Ba4")
local g1 = assert(Pgn.toGame(games[1]))
eq("pgn replays", g1.sans[19], "d4")
local g2 = assert(Pgn.toGame(games[2]))
eq("pgn FEN start", g2.initialFen, "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1")
eq("pgn result star", games[2].result, "*")
local bad = Pgn.parse("1. e4 e5 2. Ke3 *")[1]
local game, reason = Pgn.toGame(bad)
check("pgn reports illegal move", game == nil and reason:find("Ke3", 1, true), reason)
eq("pgn crlf and bom", #Pgn.parse("\239\187\191[White \"A\"]\r\n\r\n1. d4 d5 *\r\n")[1].sans, 2)
eq("pgn move text", Pgn.moveText(g2), "1. e4 Kd7 2. e5")
local black_first = assert(Chess.newGame("4k3/8/8/8/8/8/4P3/4K3 b - - 0 7"))
black_first:play("Kd7"); black_first:play("e4")
eq("pgn move text from black", Pgn.moveText(black_first), "7… Kd7 8. e4")
eq("pgn mark", Pgn.moveText(g2, 1, 2), "1. e4 [Kd7] 2. e5")
local exported = Pgn.export(g1, { White = "Ann", Black = "Bob" }, "1-0")
check("export has tags first", exported:find('^%[Event "Casual game"%]\n%[Site'))
local longest = 0
for l in exported:gmatch("[^\n]+") do longest = math.max(longest, #l) end
check("export wraps lines", longest <= 80, longest)
local round = Pgn.parse(exported)[1]
eq("export round trip", #round.sans, 19)
eq("export round trip result", round.result, "1-0")
check("export of FEN game has SetUp", Pgn.export(g2):find('%[SetUp "1"%]') ~= nil)

--- Clock -------------------------------------------------------------------
local c = Clock.new(60000, 2000)
eq("clock ready", c.phase, "ready")
c = Clock.start(c, 1000, "w")
c = Clock.switch(c, "w", 11000)
eq("white charged and incremented", c.w, 52000)
eq("black active", c.active, "b")
c = Clock.switch(c, "w", 12000)
eq("wrong side cannot switch", c.active, "b")
c = Clock.pause(c, 21000)
eq("pause settles", c.b, 50000)
c = Clock.resume(c, 100000)
c = Clock.settled(c, 101000)
eq("paused time not charged", c.b, 49000)
c = Clock.settled(c, 200000)
eq("flag", c.phase, "flagged")
eq("flag zero", c.b, 0)
local snap = Clock.snapshot(Clock.start(Clock.new(5000), 50), 1050)
eq("snapshot pauses", snap.phase, "paused")
eq("snapshot anchor", snap.anchor, 0)
eq("clock text rounds up", Clock.text(59001), "1:00")
eq("clock text", Clock.text(4999), "0:05")
eq("clock text hours", Clock.text(3600000), "1:00:00")
eq("tick delay", Clock.tickDelay(4321), 321)
local d = Clock.start(Clock.new(10000, 0, 3000), 0, "w")
d = Clock.settled(d, 5000)
eq("delay is free time", d.w, 8000)

--- Evaluation --------------------------------------------------------------
local lines = { "info depth 1 score cp 20 pv e2e4", "info depth 9 multipv 2 score cp -40 pv d2d4",
    "info depth 9 score cp 35 lowerbound", "info depth 9 multipv 1 score cp 31 pv e2e4" }
eq("parse score", Evaluation.parse(lines, true, "e2e4").score, 31)
eq("parse score for black", Evaluation.parse(lines, false).score, -31)
local mate = Evaluation.parse({ "info depth 5 score mate -2 pv a1a2" }, true)
check("mate against side to move", mate.score < 0 and mate.mate == 2)
eq("lead text", Evaluation.leadText({ score = 120 }), "White slightly better")
eq("lead mate", Evaluation.leadText(mate), "Black mates in 2")
eq("score text", Evaluation.scoreText({ score = -180 }), "−1.8")
eq("score text zero", Evaluation.scoreText({ score = 3 }), "0.0")
eq("best move", Evaluation.quality({ score = 30, best = "e2e4" }, { score = 30 }, "e2e4", true), "best")
eq("blunder", Evaluation.quality({ score = 30, best = "e2e4" }, { score = -600 }, "f2f3", true), "blunder")
eq("blunder by black", Evaluation.quality({ score = 0, best = "e7e5" }, { score = 700 }, "f7f6", false), "blunder")
eq("good", Evaluation.quality({ score = 30, best = "e2e4" }, { score = 10 }, "d2d4", true), "good")

--- Puzzles -----------------------------------------------------------------
local line = "00fxc\t4r2k/1pQ2p2/p4N1p/5P2/1P6/P1Pn1K2/4r3/6R1 w - - 1 36\tc7f7 e8e3 f3g4 d3e5 g4h5 e2h2\t2092\t96\tendgame exposedKing fork mate mateIn3"
local p = assert(Puzzles.parseLine(line))
check("puzzle groups", p.groups.checkmate and p.groups.forks and p.groups.endgames and not p.groups.pins)
check("bad line rejected", Puzzles.parseLine("x\ty\tz") == nil)
check("bad move rejected", Puzzles.parseLine(line:gsub("c7f7", "c7f9")) == nil)
-- The setup move is White's c7f7; the solver plays Black.
local s = assert(Puzzles.start(p))
eq("solver colour", Puzzles.solverColor(s), "b")
eq("first state", Puzzles.state(s), "solving")
eq("wrong move counted", Puzzles.judge(s, "h6h5"), "wrong")
eq("wrong move not played", #s.game.moves, 1)
eq("illegal ignored", Puzzles.judge(s, "h8g8"), "illegal")
eq("ambiguous SAN refused", Puzzles.judge(s, "Re3+"), "illegal")
eq("mistakes", s.mistakes, 1)
eq("correct move", Puzzles.judge(s, "R8e3+"), "correct")
eq("opponent due", Puzzles.state(s), "opponent")
eq("not now", Puzzles.judge(s, "d3e5"), "not_now")
check("reply", Puzzles.reply(s))
eq("second move", Puzzles.judge(s, "d3e5"), "correct")
Puzzles.reply(s)
eq("solved", Puzzles.judge(s, "e2h2"), "solved")
eq("solved state", Puzzles.state(s), "solved")
-- An alternative mate also solves.
local alt = assert(Puzzles.parseLine("alt\t6k1/5ppp/8/8/8/8/1q6/R5K1 b - - 0 1\tb2b1 a1b1 h7h6 b1b8\t900\t90\tmate mateIn2"))
local sa = assert(Puzzles.start(alt))
eq("alt solver", Puzzles.solverColor(sa), "w")
-- After b2b1 (queen to b1 pinned? no: Black's setup move), White's scripted reply is a1b1.
eq("alt first", Puzzles.judge(sa, "a1b1"), "correct")
Puzzles.reply(sa)
eq("alternative mate", Puzzles.judge(sa, "Rb8#"), "solved")


now = 10 * 24 * 3600 * 1000
local r1 = Puzzles.recordAttempt(nil, "with_help", 1, true, now)
check("help goes to review now", r1.review_step == 0 and r1.next_review == now)
local r2 = Puzzles.recordAttempt(r1, "first_try", 0, false, now)
eq("then three days", r2.next_review, now + 3 * 24 * 3600 * 1000)
local r3 = r2
for _ = 1, 3 do r3 = Puzzles.recordAttempt(r3, "first_try", 0, false, now) end
check("leaves review", r3.review_step == nil and r3.next_review == nil, tostring(r3.review_step))
check("clean first solve stays out of review", Puzzles.recordAttempt(nil, "first_try", 0, false, now).next_review == nil)
eq("level up", Puzzles.nextLevel(1100, "first_try", 1300), 1125)
eq("level up small", Puzzles.nextLevel(1100, "first_try", 800), 1105)
eq("level down", Puzzles.nextLevel(1100, "abandoned", 1100), 1080)
eq("level floor", Puzzles.nextLevel(600, "abandoned", 600), 600)

local pool = {}
for i, rating in ipairs({ 900, 1100, 1180, 1500, 2100 }) do
    pool[i] = { id = "p" .. i, rating = rating, groups = { endgames = i % 2 == 0 } }
end
local first = function() return 1 end
eq("choose near unseen", Puzzles.choose(pool, 1120, {}, nil, nil, first).id, "p2")
eq("choose avoids", Puzzles.choose(pool, 1120, {}, { p2 = true }, nil, first).id, "p3")
eq("choose by group", Puzzles.choose(pool, 1120, {}, nil, "endgames", first).id, "p2")
local seen = { p1 = { last_attempt = 5 }, p2 = { last_attempt = 1 }, p3 = { last_attempt = 9 },
    p4 = { last_attempt = 7 }, p5 = { last_attempt = 8 } }
eq("choose least recent when all seen", Puzzles.choose(pool, 1120, seen, nil, nil, first).id, "p2")
local prog = { a = { next_review = 5 }, b = { next_review = 2 }, c = { next_review = 99 }, d = {} }
eq("due order", table.concat(Puzzles.due(prog, 10), ","), "b,a")

-- The whole bundled pack loads and every puzzle replays.
local pack = here .. "../squarechess.koplugin/data/puzzles-v1.tsv"
local list = Puzzles.load(pack)
eq("pack size", list and #list, 10000)
local broken = 0
for _, pz in ipairs(list or {}) do if not Puzzles.start(pz) then broken = broken + 1 end end
eq("every puzzle replays", broken, 0)

print(string.format("%d checks, %d failed", count, failures))
os.exit(failures == 0 and 0 or 1)
