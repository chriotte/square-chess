--[[--
Puzzles: the bundled Lichess pack, attempts, the adaptive training level and the
review cycle. Ported from the Android app's Puzzle.kt and PuzzleTraining.kt.

A puzzle's fen is the position before the opponent's setup move; its moves (UCI)
start with that setup move, then alternate solver and opponent, ending with the
solver's last move. Times are milliseconds since the epoch, passed in by callers.
--]]--

local Chess = require("chess")

local Puzzles = {}

Puzzles.START_LEVEL, Puzzles.MIN_LEVEL, Puzzles.MAX_LEVEL = 1100, 600, 2500
Puzzles.QUICK_SET_SIZE = 5
local DAY = 24 * 60 * 60 * 1000
Puzzles.REVIEW_DELAYS = { 0, 3 * DAY, 7 * DAY, 30 * DAY } -- by review step 0..3

local function set(list) local s = {} for _i, v in ipairs(list) do s[v] = true end return s end

--- Small theme groups for choosing what to train; puzzles keep the Lichess tags themselves.
Puzzles.GROUPS = {
    { id = "checkmate", name = "Checkmates", tags = set{ "mate", "mateIn1", "mateIn2", "mateIn3", "mateIn4", "mateIn5",
        "backRankMate", "smotheredMate", "anastasiaMate", "arabianMate", "bodenMate", "doubleBishopMate",
        "dovetailMate", "hookMate" } },
    { id = "forks", name = "Forks and double attacks", tags = set{ "fork", "doubleCheck", "discoveredAttack" } },
    { id = "pins", name = "Pins and skewers", tags = set{ "pin", "skewer", "xRayAttack" } },
    { id = "motifs", name = "Tactical ideas", tags = set{ "sacrifice", "deflection", "attraction", "clearance",
        "interference", "intermezzo", "quietMove", "trappedPiece", "hangingPiece", "capturingDefender",
        "exposedKing", "kingsideAttack", "queensideAttack", "zugzwang", "defensiveMove" } },
    { id = "endgames", name = "Endgames", tags = set{ "endgame", "pawnEndgame", "rookEndgame", "bishopEndgame",
        "knightEndgame", "queenEndgame", "queenRookEndgame", "promotion", "underPromotion", "advancedPawn" } },
}

local function isUci(m)
    return (#m == 4 or #m == 5) and m:match("^[a-h][1-8][a-h][1-8][qrbn]?$") ~= nil
end

--- One pack line: "id \t fen \t moves \t rating \t popularity \t themes". Nil when unreadable.
function Puzzles.parseLine(line)
    local parts = {}
    for field in (line .. "\t"):gmatch("([^\t]*)\t") do parts[#parts + 1] = field end
    if #parts ~= 6 then return nil end
    local id, fen, moves, rating, popularity, themes = unpack(parts)
    local _, spaces = fen:gsub(" ", "")
    if id == "" or spaces ~= 5 then return nil end
    local list = {}
    for m in moves:gmatch("%S+") do
        if not isUci(m) then return nil end
        list[#list + 1] = m
    end
    if #list < 2 or not tonumber(rating) or not tonumber(popularity) then return nil end
    local tags = {}
    for t in themes:gmatch("%S+") do tags[t] = true end
    local groups = {}
    for _i, g in ipairs(Puzzles.GROUPS) do
        for t in pairs(tags) do if g.tags[t] then groups[g.id] = true; break end end
    end
    return { id = id, fen = fen, moves = list, rating = tonumber(rating), popularity = tonumber(popularity),
        themes = tags, groups = groups }
end

--- Reads the pack, skipping unreadable lines and repeated IDs.
function Puzzles.load(path)
    local f = io.open(path, "r")
    if not f then return nil, "cannot open " .. path end
    local list, seen, by_id = {}, {}, {}
    for line in f:lines() do
        local p = Puzzles.parseLine(line)
        if p and not seen[p.id] then
            seen[p.id] = true
            list[#list + 1] = p
            by_id[p.id] = p
        end
    end
    f:close()
    return list, by_id
end

--- Session -------------------------------------------------------------------

--- Starts a puzzle after its setup move; nil if its moves do not replay (a damaged line).
function Puzzles.start(puzzle)
    local game = Chess.restoreGame(puzzle.fen, puzzle.moves)
    if not game then return nil end
    return Puzzles.restore(puzzle, 1, 0, false)
end

--- Rebuilds a session from saved fields.
function Puzzles.restore(puzzle, ply, mistakes, hint)
    ply = math.max(1, math.min(ply or 1, #puzzle.moves))
    local game = Chess.newGame(puzzle.fen)
    if not game then return nil end
    for i = 1, ply do if not game:play(puzzle.moves[i]) then return nil end end
    return { puzzle = puzzle, ply = ply, mistakes = mistakes or 0, hint = hint or false, game = game }
end

--- "solving", "opponent" (a scripted reply is due) or "solved".
function Puzzles.state(s)
    if s.final or s.ply >= #s.puzzle.moves then return "solved" end
    if s.ply % 2 == 0 then return "opponent" end
    return "solving"
end

--- The solver's colour: the side to move after the setup move.
function Puzzles.solverColor(s) return s.puzzle.fen:match("^%S+ (%a)") == "b" and "w" or "b" end

function Puzzles.expected(s)
    if Puzzles.state(s) == "solving" then return s.puzzle.moves[s.ply + 1] end
    return nil
end

--[[--
Judges the solver's move (move table, SAN or UCI). Returns a verdict:
"correct", "solved", "wrong", "illegal" or "not_now". The session never plays a
wrong move; it only counts it. Any move that mates at once also solves the puzzle.
--]]--
function Puzzles.judge(s, move)
    local expected = Puzzles.expected(s)
    if not expected then return "not_now" end
    local pos = s.game:position()
    local m = type(move) == "table" and move or pos:parseMove(move)
    if not m then return "illegal" end
    if Chess.uci(m) == expected then
        s.game:play(m)
        s.ply = s.ply + 1
        return Puzzles.state(s) == "solved" and "solved" or "correct"
    end
    local after = pos:apply(m)
    if after:inCheck() and #after:legalMoves() == 0 then
        s.game:play(m)
        s.final = Chess.uci(m)
        return "solved"
    end
    s.mistakes = s.mistakes + 1
    return "wrong"
end

--- Plays the opponent's scripted reply, if one is due.
function Puzzles.reply(s)
    if Puzzles.state(s) ~= "opponent" then return false end
    s.ply = s.ply + 1
    return s.game:play(s.puzzle.moves[s.ply]) ~= nil
end

function Puzzles.firstTry(s) return s.mistakes == 0 and not s.hint end

--- Training ------------------------------------------------------------------

--- result: "first_try", "with_help" or "abandoned". Returns the new progress record.
function Puzzles.recordAttempt(old, result, mistakes, hint, now)
    local step, due
    if result ~= "first_try" then
        step, due = 0, now + Puzzles.REVIEW_DELAYS[1]
    elseif not old or old.review_step == nil then
        step, due = nil, nil
    elseif old.review_step + 1 < #Puzzles.REVIEW_DELAYS then
        step = old.review_step + 1
        due = now + Puzzles.REVIEW_DELAYS[step + 1]
    end
    old = old or {}
    return {
        attempts = (old.attempts or 0) + 1,
        solved = (old.solved or 0) + (result ~= "abandoned" and 1 or 0),
        first_try = (old.first_try or 0) + (result == "first_try" and 1 or 0),
        mistakes = (old.mistakes or 0) + mistakes,
        hints = (old.hints or 0) + (hint and 1 or 0),
        last_result = result, last_attempt = now, review_step = step, next_review = due,
    }
end

--- The level selection aims at: up after a clean solve, down after help or giving up.
function Puzzles.nextLevel(level, result, rating)
    local change
    if result == "first_try" then
        change = math.max(5, math.min(25, 15 + math.floor((rating - level) / 20)))
    elseif result == "with_help" then
        change = -5
    else
        change = -20
    end
    return math.max(Puzzles.MIN_LEVEL, math.min(Puzzles.MAX_LEVEL, level + change))
end

--[[--
A new puzzle near level: unseen ones within 150 points, then 300, then the nearest
unseen, then the least recently seen. group (an id or nil) limits themes; avoid
(a set of ids) is skipped while anything else is left. random(n) gives 1..n.
--]]--
function Puzzles.choose(pool, level, progress, avoid, group, random)
    random = random or math.random
    local matching = {}
    for _i, p in ipairs(pool) do
        if (not group or p.groups[group]) and not (avoid and avoid[p.id]) then matching[#matching + 1] = p end
    end
    if #matching == 0 then return nil end
    local unseen = {}
    for _i, p in ipairs(matching) do if not progress[p.id] then unseen[#unseen + 1] = p end end
    for _i, band in ipairs({ 150, 300 }) do
        local near = {}
        for _i, p in ipairs(unseen) do if math.abs(p.rating - level) <= band then near[#near + 1] = p end end
        if #near > 0 then return near[random(#near)] end
    end
    local best
    if #unseen > 0 then
        for _i, p in ipairs(unseen) do
            if not best or math.abs(p.rating - level) < math.abs(best.rating - level) then best = p end
        end
        return best
    end
    for _i, p in ipairs(matching) do
        if not best or (progress[p.id].last_attempt or 0) < (progress[best.id].last_attempt or 0) then best = p end
    end
    return best
end

--- IDs due for review at now, longest overdue first.
function Puzzles.due(progress, now)
    local list = {}
    for id, p in pairs(progress) do
        if p.next_review and p.next_review <= now then list[#list + 1] = { id = id, at = p.next_review } end
    end
    table.sort(list, function(a, b) if a.at ~= b.at then return a.at < b.at end return a.id < b.id end)
    local ids = {}
    for i, e in ipairs(list) do ids[i] = e.id end
    return ids
end

function Puzzles.totals(progress, now)
    local t = { attempted = 0, first_try = 0, with_help = 0, due = #Puzzles.due(progress, now) }
    for _i, p in pairs(progress) do
        t.attempted = t.attempted + 1
        if (p.first_try or 0) > 0 then t.first_try = t.first_try + 1
        elseif (p.solved or 0) > 0 then t.with_help = t.with_help + 1 end
    end
    return t
end

return Puzzles
