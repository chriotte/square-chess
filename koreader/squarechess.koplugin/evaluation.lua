--[[--
Engine evaluations and move labels, ported from the Android app's Evaluation.kt.

An eval is { score = centipawns from White's side, mate = moves to mate or nil,
best = engine's move in UCI or nil }. With a mate, the sign of score names the side.
--]]--

local Evaluation = {}

Evaluation.MATE_SCORE = 100000
Evaluation.TIME_MS = 300

--- Converts a UCI score ("cp 31", "mate -2", from the side to move) to White's side.
function Evaluation.fromScore(kind, value, white_to_move, best)
    local sign = white_to_move and 1 or -1
    if kind == "cp" then return { score = value * sign, best = best } end
    if kind == "mate" then
        local winner = value > 0 and sign or -sign
        return { score = winner * Evaluation.MATE_SCORE, mate = math.abs(value), best = best }
    end
    return nil
end

--- The last full-width score for the first MultiPV line in a list of engine output lines.
function Evaluation.parse(lines, white_to_move, best)
    local found
    for _i, line in ipairs(lines) do
        if line:sub(1, 5) == "info " and line:find(" score ", 1, true) and not line:find("bound", 1, true) then
            local multipv = line:match(" multipv (%d+)")
            if not multipv or multipv == "1" then
                local kind, value = line:match(" score (%a+) (%-?%d+)")
                if kind then found = Evaluation.fromScore(kind, tonumber(value), white_to_move, best) or found end
            end
        end
    end
    return found
end

--- Evaluation of a finished game: the side to move is mated, or a draw.
function Evaluation.terminal(game)
    local result = game:result()
    if result == "1-0" then return { score = Evaluation.MATE_SCORE, mate = 0 } end
    if result == "0-1" then return { score = -Evaluation.MATE_SCORE, mate = 0 } end
    if result == "1/2-1/2" then return { score = 0 } end
    return nil
end

--- Winning chance 0..100 for the side with cp, the curve Lichess uses for move labels.
function Evaluation.winChance(cp)
    cp = math.max(-1000, math.min(1000, cp))
    return 50 + 50 * (2 / (1 + math.exp(-0.00368208 * cp)) - 1)
end

--- "best", "good", "inaccuracy", "mistake" or "blunder" for a move between two evals.
function Evaluation.quality(before, after, move, white_moved)
    if before.best and before.best == move then return "best" end
    if after.mate == 0 and ((after.score > 0) == white_moved) then return "best" end
    local sign = white_moved and 1 or -1
    local loss = Evaluation.winChance(before.score * sign) - Evaluation.winChance(after.score * sign)
    if loss >= 30 then return "blunder" end
    if loss >= 20 then return "mistake" end
    if loss >= 10 then return "inaccuracy" end
    return "good"
end

Evaluation.QUALITY_TEXT = {
    best = "Best move", good = "Good move", inaccuracy = "Inaccuracy", mistake = "Mistake", blunder = "Blunder",
}

--- Who is ahead, in words: "Equal", "White slightly better", "Black mates in 3" ...
function Evaluation.leadText(e)
    local white = e.score > 0
    local side = white and "White" or "Black"
    if e.mate == 0 then return "Checkmate" end
    if e.mate then return string.format("%s mates in %d", side, e.mate) end
    local a = math.abs(e.score)
    if a < 50 then return "Equal" end
    if a < 150 then return side .. " slightly better" end
    if a < 300 then return side .. " better" end
    return side .. " winning"
end

--- "+0.3", "−1.8", or "" for a mate.
function Evaluation.scoreText(e)
    if e.mate then return "" end
    local text = string.format("%.1f", math.abs(e.score / 100))
    if text == "0.0" then return "0.0" end
    return (e.score > 0 and "+" or "−") .. text
end

--- Whether the move that leads to position `ply` (1-based) was White's.
function Evaluation.whiteMovedAt(initial_white, ply)
    return ((ply - 1) % 2 == 0) == initial_white
end

--- Label of the move leading to `ply`, when both positions are evaluated.
function Evaluation.qualityAt(evals, game, ply)
    if ply < 1 or ply > #game.moves then return nil end
    local before, after = evals[ply - 1], evals[ply]
    if not before or not after then return nil end
    return Evaluation.quality(before, after, game.moves[ply],
        Evaluation.whiteMovedAt(game.positions[1].turn == "w", ply))
end

--- Counts of inaccuracies, mistakes and blunders: { w = {i, m, b}, b = {i, m, b} }.
function Evaluation.summary(evals, game)
    local count = { w = { 0, 0, 0 }, b = { 0, 0, 0 } }
    local slot = { inaccuracy = 1, mistake = 2, blunder = 3 }
    for ply = 1, #game.moves do
        local q = Evaluation.qualityAt(evals, game, ply)
        if q and slot[q] then
            local side = Evaluation.whiteMovedAt(game.positions[1].turn == "w", ply) and "w" or "b"
            count[side][slot[q]] = count[side][slot[q]] + 1
        end
    end
    return count
end

return Evaluation
