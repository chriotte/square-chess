--[[--
A narrated play-through of Square Chess in a visible KOReader window, for showing
the plugin to people. It drives the screens through KOReader's gesture dispatch,
like tests/ui_check.lua, but slowly and with captions, and saves a screenshot per
step to <out>.

    cd $KOREADER_DIR && KO_HOME=<profile> EMULATE_READER_W=744 EMULATE_READER_H=992 \
        EMULATE_READER_DPI=120 ./luajit <repo>/koreader/tools/demo.lua <out>
--]]--

local out = arg[1] or "."
local plugin = arg[0]:gsub("[^/]*$", "") .. "../squarechess.koplugin"
require("setupkoenv")
G_defaults = require("luadefaults"):open()
local DataStorage = require("datastorage")
G_reader_settings = require("luasettings"):open(DataStorage:getDataDir() .. "/settings.reader.lua")
require("ffi/blitbuffer"):setUseCBB(true)
local Device = require("device")
require("document/canvascontext"):init(Device)
local UIManager = require("ui/uimanager")
local Event = require("ui/event")
local Geom = require("ui/geometry")
local Notification = require("ui/widget/notification")
package.path = plugin .. "/?.lua;" .. package.path
os.remove(DataStorage:getSettingsDir() .. "/squarechess.lua")
os.remove(DataStorage:getSettingsDir() .. "/squarechess_puzzles.lua")

local ffi = require("ffi")
local Chess = require("chess")
local Puzzles = require("puzzles")
local UI = require("ui")

local function run(seconds, until_fn)
    local deadline = UI.now() + (seconds or 0) * 1000
    repeat
        UIManager:_checkTasks()
        UIManager:_repaint()
        ffi.C.poll(nil, 0, 20)
    until UI.now() >= deadline and (not until_fn or until_fn())
end
local step = 0
local function shot(caption)
    run(0.2)
    step = step + 1
    local name = string.format("%s/%02d.png", out, step)
    Device.screen:shot(name)
    local f = io.open(out .. "/captions.txt", "a")
    f:write(string.format("%02d\t%s\n", step, caption)); f:close()
end
--- Shows a caption as a toast, lets it be read, then captures the screen.
local function say(caption, seconds)
    print(caption)
    UIManager:show(Notification:new{ text = caption, timeout = (seconds or 2.5) + 1 })
    run(0.3)
    shot(caption)
    run(seconds or 2.5)
end
local function point(x, y) return Geom:new{ x = math.floor(x), y = math.floor(y), w = 0, h = 0 } end
local function gesture(g)
    g.time = require("ui/time").now()
    UIManager:sendEvent(Event:new("Gesture", g))
    run(0.6)
end

--- Play ---------------------------------------------------------------------------
local game = require("gamescreen"):new{ plugin_dir = plugin }
game.level = 2
UIManager:show(game)
run(1.5, function() return not game.busy end)
local function centre(square)
    local b = game.board
    local col, row = b:cellOf(Chess.squareIndex(square))
    return point(b.dimen.x + (col + 0.5) * b.cell, b.dimen.y + (row + 0.5) * b.cell)
end
local function waitComputer() run(0.8, function() return not game.thinking and not game.busy end) end
--- The first legal move from a preference list, so the demo survives any engine reply.
local function choose(preferred)
    local pos = game.game:position()
    for _, uci in ipairs(preferred) do
        local m = pos:parseMove(uci)
        if m then return uci end
    end
    for _, m in ipairs(pos:legalMoves()) do
        if not m.promotion and m.flags ~= "castle" then return Chess.uci(m) end
    end
end

say("Square Chess for KOReader. A new game against the computer, level 2.", 3)

local mv = choose({ "e2e4" })
say("Tap a piece to select it. Dots show where it can go.")
gesture{ ges = "tap", pos = centre(mv:sub(1, 2)) }
shot("Selected, with legal moves marked")
run(1.5)
gesture{ ges = "tap", pos = centre(mv:sub(3, 4)) }
say("Tap the target square to move. The computer replies; corner marks show its move.")
waitComputer()
shot("The computer's reply")
run(1.5)

mv = choose({ "g1f3", "b1c3", "d2d4" })
say("A pen stroke from one square to another drags a piece.")
gesture{ ges = "swipe", pos = centre(mv:sub(1, 2)), end_pos = centre(mv:sub(3, 4)), direction = "north", distance = 200 }
waitComputer()
shot("After the drag")
run(1.5)

say("Stuck? Hint asks the engine and draws its move on the board.")
game:showHint()
run(0.5, function() return game.board.hint ~= nil end)
shot("The hint")
run(3)
local hinted = game.board.hint
say("A slow press with the pen still works: hold, slide, release.")
gesture{ ges = "hold", pos = centre(hinted:sub(1, 2)) }
gesture{ ges = "hold_pan", pos = centre(hinted:sub(3, 4)), start_pos = centre(hinted:sub(1, 2)) }
gesture{ ges = "hold_release", pos = centre(hinted:sub(3, 4)) }
waitComputer()
shot("Hinted move played")
run(1.5)

say("Evaluation can be switched on in the menu: who is ahead, and how good each move was.")
game.evaluation = true
game:save()
game:queueEvaluation(#game.game.moves)
run(0.5, function() return game.evals[#game.game.moves] ~= nil end)
game:refreshAll()
run(1)
mv = choose({ "f1c4", "f1e2", "d2d3", "b1c3" })
gesture{ ges = "tap", pos = centre(mv:sub(1, 2)) }
gesture{ ges = "tap", pos = centre(mv:sub(3, 4)) }
waitComputer()
run(0.5, function() return game.evals[#game.game.moves] ~= nil and not game.busy end)
game:refreshAll()
say("The line above the moves rates the last move and shows the balance.", 3.5)

say("Moves can also be typed, with the on-screen keyboard or a real one.")
local dialog = game:typeMove() or nil
run(0.8)
local top = UIManager._window_stack[#UIManager._window_stack].widget
for i = #UIManager._window_stack, 1, -1 do
    local w = UIManager._window_stack[i].widget
    if w.submit then top = w end
end
local pos = game.game:position()
local typed = pos:parseMove("O-O") and "O-O" or pos:san(pos:parseMove(choose({ "d2d3", "a2a3", "h2h3" })))
top:setInputText(typed)
run(0.5)
shot("Typing " .. typed)
run(2)
top.submit()
waitComputer()
say("Typed " .. typed .. " and played it.")

say("Review steps back through the game, move by move.")
game:review(#game.game.moves)
run(1)
for _ = 1, 4 do game:review(game.view - 1); run(0.9) end
shot("Reviewing an earlier position")
run(1.5)
game:review(nil)
run(1)

game.time_control = 4
game:startGame("both")
run(1)
say("Two players on one device, with a 3+2 clock. The side to move is shown inverted.", 3)
for _, uci in ipairs({ "d2d4", "d7d5", "c2c4", "e7e6" }) do
    gesture{ ges = "tap", pos = centre(uci:sub(1, 2)) }
    gesture{ ges = "tap", pos = centre(uci:sub(3, 4)) }
    run(0.8)
end
shot("Clock running")
run(2)
game.time_control = 1
game:startGame("w")
UIManager:close(game)
run(1)

--- Puzzles ------------------------------------------------------------------------
local puzzles = require("puzzlescreen"):new{ plugin_dir = plugin }
UIManager:show(puzzles)
run(1)
local s = puzzles.session
say("Puzzles: 10,000 offline tactics, chosen to match your level.", 3)
say("Hint first marks the piece to move ...")
puzzles:hint()
run(0.5)
shot("Hint: the piece")
run(2)
puzzles:hint()
say("... and then the whole move.")
run(1)
while Puzzles.state(s) ~= "solved" do
    if Puzzles.state(s) == "solving" then
        local m = s.game:position():parseMove(Puzzles.expected(s))
        local from, to = Chess.squareName(m.from), Chess.squareName(m.to)
        local b = puzzles.board
        local function c(sq)
            local col, row = b:cellOf(Chess.squareIndex(sq))
            return point(b.dimen.x + (col + 0.5) * b.cell, b.dimen.y + (row + 0.5) * b.cell)
        end
        gesture{ ges = "tap", pos = c(from) }
        gesture{ ges = "tap", pos = c(to) }
        run(1.2)
    else
        run(0.5)
    end
end
say("Solved. A puzzle solved with help comes back later for review.", 3)
UIManager:close(puzzles)
run(1)

--- Clock --------------------------------------------------------------------------
local clock = require("clockscreen"):new{ plugin_dir = plugin }
UIManager:show(clock)
run(1)
say("The chess clock for games on a real board. The far half faces the other player.", 3)
local function half(side) local g = clock.halves[side]; return point(g.x + g.w / 2, g.y + g.h / 2) end
gesture{ ges = "tap", pos = half("b") }
run(3)
shot("White's clock running")
gesture{ ges = "tap", pos = half("w") }
run(3)
say("Each player presses their own half after moving.", 2.5)
clock:togglePause()
UIManager:close(clock)
run(0.5)
say("That's the tour. Everything runs offline on the e-reader.", 3)
print("demo finished")
os.exit(0)
