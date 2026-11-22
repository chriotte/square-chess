--[[--
Offscreen UI check: starts KOReader's UI without a window and drives the game,
puzzle and clock screens through KOReader's own gesture dispatch, with the kinds
of gestures a pen produces (taps, wobbly taps that arrive as swipes, slow presses
that arrive as hold + release, and drags). Saves screenshots along the way.

    cd $KOREADER_DIR && SDL_VIDEODRIVER=offscreen KO_HOME=<scratch profile> \
        EMULATE_READER_W=1860 EMULATE_READER_H=2480 EMULATE_READER_DPI=300 \
        ./luajit <repo>/koreader/tests/ui_check.lua <output dir>

Exits non-zero if anything fails.
--]]--

local out = arg[1] or "."
local plugin = arg[0]:gsub("[^/]*$", "") .. "../squarechess.koplugin"
require("setupkoenv")
G_defaults = require("luadefaults"):open()
local DataStorage = require("datastorage")
G_reader_settings = require("luasettings"):open(DataStorage:getDataDir() .. "/settings.reader.lua")
G_reader_settings:saveSetting("home_dir", out)
require("ffi/blitbuffer"):setUseCBB(true)
local Device = require("device")
require("document/canvascontext"):init(Device)
local UIManager = require("ui/uimanager")
local Event = require("ui/event")
local Geom = require("ui/geometry")
package.path = plugin .. "/?.lua;" .. package.path
os.remove(DataStorage:getSettingsDir() .. "/squarechess.lua")
os.remove(DataStorage:getSettingsDir() .. "/squarechess_puzzles.lua")

local ffi = require("ffi")
local Chess = require("chess")
local Puzzles = require("puzzles")

local function settle(seconds, until_fn)
    local deadline = os.time() + (seconds or 0)
    repeat
        UIManager:_checkTasks()
        UIManager:_repaint()
        ffi.C.poll(nil, 0, 20)
    until os.time() >= deadline and (not until_fn or until_fn())
    UIManager:forceRePaint()
end
local function shot(name)
    settle()
    Device.screen:shot(out .. "/" .. name .. ".png")
    print("screenshot", name)
end
local function top() return UIManager._window_stack[#UIManager._window_stack].widget end
local function point(x, y) return Geom:new{ x = math.floor(x), y = math.floor(y), w = 0, h = 0 } end
local function gesture(g)
    g.time = g.time or require("ui/time").now()
    UIManager:sendEvent(Event:new("Gesture", g))
    settle()
end

--- Game screen ------------------------------------------------------------------
local game_screen = require("gamescreen"):new{ plugin_dir = plugin }
UIManager:show(game_screen)
settle(1, function() return not game_screen.busy end)
local board = game_screen.board
local function centre(square)
    board = game_screen.board -- a relayout (for example adding the clock bar) makes a new board
    local col, row = board:cellOf(Chess.squareIndex(square))
    return point(board.dimen.x + (col + 0.5) * board.cell, board.dimen.y + (row + 0.5) * board.cell)
end
local function waitComputer() settle(0, function() return not game_screen.thinking and not game_screen.busy end) end
local function plies() return #game_screen.game.moves end
shot("01-game-start")

-- 1. Plain taps.
gesture{ ges = "tap", pos = centre("e2") }
assert(board.selected == Chess.squareIndex("e2"), "tap selects")
gesture{ ges = "tap", pos = centre("e4") }
waitComputer()
assert(plies() == 2, "tap-tap plays e4 and the computer replies")

-- Each gesture plays some currently legal move, so the computer's random replies cannot break the test.
local used = {}
local function pickMove()
    local pos = game_screen.game:position()
    for _, m in ipairs(pos:legalMoves()) do
        local uci = Chess.uci(m)
        if not m.promotion and m.flags ~= "castle" and not used[uci] and pos:piece(m.from):lower() ~= "k" then
            used[uci] = true
            return Chess.squareName(m.from), Chess.squareName(m.to), uci
        end
    end
    error("no move to test with")
end
local function lastPlayer() return game_screen.game.moves[plies() - 1] end

-- 2. A drag that the detector reports as a swipe (a quick pen stroke).
local a, b, uci = pickMove()
gesture{ ges = "swipe", pos = centre(a), end_pos = centre(b), direction = "north", distance = 300 }
waitComputer()
assert(lastPlayer() == uci, "swipe drag plays " .. uci .. ", got " .. tostring(lastPlayer()))

-- 3. A slow pen press: hold, slide, release.
a, b, uci = pickMove()
gesture{ ges = "hold", pos = centre(a) }
gesture{ ges = "hold_pan", pos = centre(b), start_pos = centre(a) }
gesture{ ges = "hold_release", pos = centre(b) }
waitComputer()
assert(lastPlayer() == uci, "hold drag plays " .. uci .. ", got " .. tostring(lastPlayer()))

-- 4. A wobbly pen tap that stays inside one square counts as a tap ...
a, b, uci = pickMove()
local p = centre(a)
gesture{ ges = "swipe", pos = p, end_pos = point(p.x + board.cell * 0.2, p.y - board.cell * 0.15), direction = "east", distance = 30 }
assert(board.selected == Chess.squareIndex(a), "wobbly tap selects " .. a)
-- ... and a pan (finger or pen drag) finished by pan_release plays a move.
gesture{ ges = "pan", pos = centre(b), start_pos = centre(a), relative = { x = 0, y = -board.cell } }
gesture{ ges = "pan_release", pos = centre(b) }
waitComputer()
assert(lastPlayer() == uci, "pan drag plays " .. uci .. ", got " .. tostring(lastPlayer()))

-- 5. Typed move through the dialog.
game_screen:typeMove()
local dialog = top()
dialog:setInputText("Nonsense")
dialog.submit()
settle()
assert(top() == dialog or UIManager._window_stack[#UIManager._window_stack - 1].widget == dialog, "bad text keeps the dialog open")
for i = #UIManager._window_stack, 1, -1 do -- dismiss the error message
    local w = UIManager._window_stack[i].widget
    if w ~= dialog and w ~= game_screen then UIManager:close(w) end
end
local typed_a, typed_b, typed_uci = pickMove()
local typed_pos = game_screen.game:position()
local typed_san = typed_pos:san(typed_pos:parseMove(typed_uci))
dialog:setInputText(typed_san)
dialog.submit()
waitComputer()
assert(lastPlayer() == typed_uci, "typed " .. typed_san .. " plays it, got " .. tostring(lastPlayer()))
print("moves", table.concat(game_screen.game.sans, " "))

-- 6. Hint.
game_screen:showHint()
settle(0, function() return board.hint ~= nil end)
assert(board.hint and game_screen.game:position():parseMove(board.hint), "hint is a legal move")
shot("02-game-hint")

-- 7. Evaluation.
game_screen.evaluation = true
game_screen:queueEvaluation(plies())
settle(0, function() return game_screen.evals[plies()] ~= nil end)
local hinted = board.hint
gesture{ ges = "tap", pos = centre(hinted:sub(1, 2)) }
gesture{ ges = "tap", pos = centre(hinted:sub(3, 4)) }
local hint_ply = plies()
waitComputer()
settle(0, function() return game_screen.evals[plies()] ~= nil and not game_screen.busy end)
local Evaluation = require("evaluation")
local q = Evaluation.qualityAt(game_screen.evals, game_screen.game, hint_ply)
assert(q == "best" or q == "good", "the hinted move is rated best or good, got " .. tostring(q))
assert(game_screen:infoText():find("%(", 1) or game_screen:infoText():find("mates"), "evaluation shown: " .. game_screen:infoText())
assert(not board.hint, "hint cleared after the move")
shot("03-game-evaluation")

-- 8. Undo goes back to the player's turn.
local before = plies()
game_screen:undo()
assert(plies() == before - 2, "undo takes back two plies")

-- 9. Review.
game_screen:review(3)
assert(board.pos == game_screen.game.positions[4] and not board.interactive, "review shows ply 3 read-only")
shot("04-game-review")
game_screen:review(nil)

-- 10. PGN export and import.
game_screen:exportPgn()
settle()
local pgn_path = out .. "/two-games.pgn"
local f = assert(io.open(pgn_path, "w"))
f:write('[White "Ann"]\n[Black "Bob"]\n[Result "0-1"]\n\n1. f3 e5 2. g4 Qh4# 0-1\n\n[White "Cy"]\n[Black "Di"]\n\n1. d4 d5 2. c4 *\n')
f:close()
local function closeOthers(keep)
    for i = #UIManager._window_stack, 1, -1 do
        local w = UIManager._window_stack[i].widget
        if w ~= keep then UIManager:close(w) end
    end
    settle()
end
local saved = {}
for name in require("libs/libkoreader-lfs").dir(out .. "/Square Chess") do
    if name:match("%.pgn$") then saved[#saved + 1] = name end
end
assert(#saved >= 1, "export wrote a PGN file")
closeOthers(game_screen)
game_screen:loadPgnFile(pgn_path)
local menu = top()
assert(menu.item_table and #menu.item_table == 2, "two games listed")
menu.item_table[1].callback()
settle()
assert(game_screen.view == 0 and #game_screen.game.moves == 4 and game_screen:result() == "0-1", "imported game in review")
game_screen:review(4)
shot("05-game-pgn-import")
game_screen:review(nil)

-- 11. Two players with a 3+2 clock.
closeOthers(game_screen)
game_screen.time_control = 4
game_screen:startGame("both")
assert(game_screen.clock and game_screen.clock.phase == "ready", "clock ready")
gesture{ ges = "tap", pos = centre("e2") }; gesture{ ges = "tap", pos = centre("e4") }
settle(1)
gesture{ ges = "tap", pos = centre("e7") }; gesture{ ges = "tap", pos = centre("e5") }
assert(game_screen.clock.phase == "running" and game_screen.clock.active == "w", "clock running, White to move")
assert(game_screen.clock.b < 182000 and game_screen.clock.b > 170000, "Black charged about a second plus increment: " .. game_screen.clock.b)
shot("06-game-clock")
game_screen:startGame("w")
game_screen.time_control = 1
UIManager:close(game_screen)
settle()
assert(not game_screen.engine, "engine stops on close")

--- Puzzles --------------------------------------------------------------------
local puzzle_screen = require("puzzlescreen"):new{ plugin_dir = plugin }
UIManager:show(puzzle_screen)
settle()
local s = assert(puzzle_screen.session, "a puzzle is loaded")
shot("07-puzzle-start")
local pboard = puzzle_screen.board
local function playExpected()
    local pos = s.game:position()
    pboard.on_move(pos:parseMove(Puzzles.expected(s)))
    settle(1)
end
-- A wrong move first: any legal move that is not the answer and does not mate.
for _, m in ipairs(s.game:position():legalMoves()) do
    local after = s.game:position():apply(m)
    if Chess.uci(m) ~= Puzzles.expected(s) and not (after:inCheck() and #after:legalMoves() == 0) then
        pboard.on_move(m)
        break
    end
end
assert(s.mistakes == 1 and puzzle_screen.message:find("Try again"), "wrong move counted, not played")
puzzle_screen:hint()
assert(pboard.hint and pboard.hint:sub(1, 2) == pboard.hint:sub(3, 4), "first hint shows the piece")
puzzle_screen:hint()
assert(pboard.hint == Puzzles.expected(s), "second hint shows the move")
shot("08-puzzle-hint")
while Puzzles.state(s) ~= "solved" do
    if Puzzles.state(s) == "solving" then playExpected() else settle(1) end
end
local record = puzzle_screen.progress[s.puzzle.id]
assert(record and record.last_result == "with_help" and record.review_step == 0, "solved with help goes to review")
shot("09-puzzle-solved")
local first_id = s.puzzle.id
puzzle_screen:nextPuzzle()
local s2 = puzzle_screen.session
s = s2
puzzle_screen:showSolution()
settle(0, function() return Puzzles.state(s2) == "solved" and not puzzle_screen.solution_running end)
assert(puzzle_screen.progress[s2.puzzle.id].last_result == "abandoned", "giving up is recorded")
puzzle_screen.mode = "review"
puzzle_screen:nextPuzzle()
local due = puzzle_screen.session.puzzle.id
assert(due == first_id, "review serves the longest-overdue puzzle first, got " .. due)
shot("10-puzzle-review")
UIManager:close(puzzle_screen)
settle()

--- Chess clock ----------------------------------------------------------------
local clock_screen = require("clockscreen"):new{ plugin_dir = plugin }
UIManager:show(clock_screen)
settle()
shot("11-clock-ready")
local function half(side) local g = clock_screen.halves[side]; return point(g.x + g.w / 2, g.y + g.h / 2) end
gesture{ ges = "tap", pos = half("b") }
assert(clock_screen.clock.phase == "running" and clock_screen.clock.active == "w", "Black's press starts White")
settle(2)
gesture{ ges = "hold_release", pos = half("w") } -- a slow pen press still counts
assert(clock_screen.clock.active == "b", "White's press passes the turn")
gesture{ ges = "tap", pos = half("w") }
assert(clock_screen.clock.active == "b", "pressing out of turn does nothing")
settle(1)
shot("12-clock-running")
clock_screen:togglePause()
assert(clock_screen.clock.phase == "paused", "pause")
UIManager:close(clock_screen)
settle()

print("ui check passed")
os.exit(0)
