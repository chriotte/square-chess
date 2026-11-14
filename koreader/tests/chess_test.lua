-- Rules tests. Run with: koreader/tests/run.sh (any LuaJIT or Lua 5.1+ works).
package.path = arg[0]:gsub("[^/]*$", "") .. "../squarechess.koplugin/?.lua;" .. package.path
local Chess = require("chess")

local failures, count = 0, 0
local function check(name, cond, detail)
    count = count + 1
    if not cond then
        failures = failures + 1
        print("FAIL " .. name .. (detail and (": " .. tostring(detail)) or ""))
    end
end
local function eq(name, got, want) check(name, got == want, "got " .. tostring(got) .. ", want " .. tostring(want)) end

-- Perft: standard positions and counts from the Chess Programming Wiki.
local perft = {
    { "start", Chess.START_FEN, { 20, 400, 8902, 197281 } },
    { "kiwipete", "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", { 48, 2039, 97862 } },
    { "position 3", "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", { 14, 191, 2812, 43238 } },
    { "position 4", "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", { 6, 264, 9467 } },
    { "position 5", "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", { 44, 1486, 62379 } },
    { "position 6", "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10", { 46, 2079, 89890 } },
}
for _, t in ipairs(perft) do
    local pos = assert(Chess.fromFen(t[2]))
    for depth, want in ipairs(t[3]) do eq("perft " .. t[1] .. " depth " .. depth, pos:perft(depth), want) end
end

-- FEN round trip and rejection of broken input.
for _, fen in ipairs({ Chess.START_FEN, perft[2][2], "8/8/8/8/8/8/8/K6k b - - 12 40" }) do
    eq("fen round trip " .. fen, assert(Chess.fromFen(fen)):fen(), fen)
end
for _, bad in ipairs({ "", "8/8/8/8 w - -", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBN w KQkq - 0 1",
        "8/8/8/8/8/8/8/K7 w - - 0 1", "k7/8/8/8/8/8/8/KQ6 x - - 0 1", "P6k/8/8/8/8/8/8/K7 w - - 0 1",
        "k7/1Q6/8/8/8/8/8/K7 w - - 0 1" }) do
    check("rejects " .. bad, Chess.fromFen(bad) == nil)
end
eq("unsupported castling rights are dropped", assert(Chess.fromFen("4k3/8/8/8/8/8/8/4K3 w KQkq - 0 1")):fen(),
    "4k3/8/8/8/8/8/8/4K3 w - - 0 1")

-- SAN output.
local game = Chess.newGame()
for _, m in ipairs({ "e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6", "O-O" }) do
    check("plays " .. m, game:play(m) ~= nil)
end
eq("castling SAN", game.sans[9], "O-O")
eq("UCI of castling", game.moves[9], "e1g1")
local function sanOf(fen, uci)
    local pos = assert(Chess.fromFen(fen))
    return pos:san(assert(pos:parseMove(uci), uci))
end
eq("file disambiguation", sanOf("4k3/8/8/8/8/8/4K3/R6R w - - 0 1", "a1d1"), "Rad1")
eq("rank disambiguation", sanOf("4k3/R7/8/8/8/8/8/R3K3 w - - 0 1", "a1a4"), "R1a4")
eq("double disambiguation", sanOf("k7/8/8/8/8/2Q1Q3/8/4Q1K1 w - - 0 1", "e3d2"), "Qe3d2")
eq("pinned piece needs no disambiguation", sanOf("4k3/8/8/6b1/8/2N5/3N4/2K5 w - - 0 1", "c3e4"), "Ne4")
eq("en passant", sanOf("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1", "e5d6"), "exd6")
eq("promotion with check", sanOf("4k3/1P6/8/8/8/8/8/4K3 w - - 0 1", "b7b8q"), "b8=Q+")
eq("mate sign", sanOf("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", "a1a8"), "Ra8#")
eq("queenside castling", sanOf("r3k3/8/8/8/8/8/8/4K3 b q - 0 1", "e8c8"), "O-O-O")

-- Parsing is forgiving about decoration but not about meaning.
local pos = Chess.new()
check("parse SAN", pos:parseMove("Nf3") and Chess.uci(pos:parseMove("Nf3")) == "g1f3")
check("parse UCI", pos:parseMove("e2e4") ~= nil)
check("parse with check sign", pos:parseMove("e4+") ~= nil)
check("refuses illegal", pos:parseMove("e5") == nil)
check("refuses nonsense", pos:parseMove("hello") == nil and pos:parseMove("") == nil)
check("parse zero castling", assert(Chess.fromFen("4k3/8/8/8/8/8/8/4K2R w K - 0 1")):parseMove("0-0") ~= nil)
check("pawn and bishop captures differ", Chess.uci(assert(Chess.fromFen("4k3/8/8/8/8/1p6/B1P5/4K3 w - - 0 1")):parseMove("cxb3")) == "c2b3")

-- Castling restrictions.
local function legalUci(fen)
    local set = {}
    for _, m in ipairs(assert(Chess.fromFen(fen)):legalMoves()) do set[Chess.uci(m)] = true end
    return set
end
check("no castling through check", not legalUci("4k3/8/8/8/8/8/5r2/4K2R w K - 0 1").e1g1)
check("no castling out of check", not legalUci("4k3/8/8/8/8/8/4r3/4K2R w K - 0 1").e1g1)
check("queenside allowed when only b1 is attacked", legalUci("4k3/8/8/8/8/8/1r6/R3K3 w Q - 0 1").e1c1)
local g2 = Chess.newGame("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
g2:play("Rxa8+")
check("capturing a rook removes that castling right", not g2:position().castling.q)
check("other rights remain", g2:position().castling.k and g2:position().castling.K)

-- Game end.
local mate = Chess.newGame()
for _, m in ipairs({ "f3", "e5", "g4", "Qh4#" }) do mate:play(m) end
eq("fool's mate", mate:result(), "0-1")
eq("fool's mate SAN", mate.sans[4], "Qh4#")
local stale = Chess.newGame("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
eq("stalemate", select(2, stale:result()), "Stalemate")
for _, fen in ipairs({ "8/8/8/8/8/8/8/K6k w - - 0 1", "8/8/8/8/8/8/8/KN5k w - - 0 1",
        "8/8/8/8/8/8/2b5/KB5k w - - 0 1" }) do
    eq("dead material " .. fen, select(2, Chess.newGame(fen):result()), "Draw by insufficient material")
end
check("opposite bishops are not dead", Chess.newGame("8/8/8/8/8/8/1b6/KB5k w - - 0 1"):result() == nil)
check("two knights are not proven dead", Chess.newGame("8/8/8/8/8/8/8/KNN4k w - - 0 1"):result() == nil)

local rep = Chess.newGame()
local shuffle = { "Nf3", "Nf6", "Ng1", "Ng8" }
for _ = 1, 2 do for _, m in ipairs(shuffle) do rep:play(m) end end
eq("threefold is claimable", rep:claimableDraw(), "Draw by threefold repetition")
check("threefold is not automatic", rep:result() == nil)
for _ = 1, 2 do for _, m in ipairs(shuffle) do rep:play(m) end end
eq("fivefold ends the game", select(2, rep:result()), "Draw by fivefold repetition")

local fifty = Chess.newGame("4k3/8/8/8/8/8/8/R3K3 w - - 99 80")
check("49.5 moves is not claimable", fifty:claimableDraw() == nil)
fifty:play("Ra2")
eq("50-move claim", fifty:claimableDraw(), "Draw by the 50-move rule")
eq("75-move rule", select(2, Chess.newGame("4k3/8/8/8/8/8/8/R3K3 w - - 150 120"):result()), "Draw by the 75-move rule")

-- En passant only counts for repetition when the capture is legal.
local noEp = Chess.newGame()
noEp:play("e4")
eq("ep square dropped when no pawn can capture", noEp:position():fen(),
    "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1")

-- Undo and restore.
local u = Chess.newGame()
u:play("e4"); u:play("c5")
check("undo", u:undo() and #u.moves == 1 and u:position().turn == "b")
local restored = Chess.restoreGame(u.initialFen, { "e2e4", "c7c5", "g1f3" })
eq("restore replays", restored and restored.sans[3], "Nf3")
check("restore refuses bad moves", Chess.restoreGame(Chess.START_FEN, { "e2e5" }) == nil)

print(string.format("%d checks, %d failed", count, failures))
os.exit(failures == 0 and 0 or 1)
