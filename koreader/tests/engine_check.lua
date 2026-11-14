-- Engine bridge check against a real binary. Needs KOReader's ffi modules, so run
-- from a KOReader directory: cd $KOREADER_DIR && ./luajit <repo>/koreader/tests/engine_check.lua
local plugin = arg[0]:gsub("[^/]*$", "") .. "../squarechess.koplugin"
package.path = plugin .. "/?.lua;./?.lua;./frontend/?.lua;" .. package.path
local ffi = require("ffi")
local Engine = require("engine")
local Chess = require("chess")
local path = assert(Engine.findBinary(plugin), "no engine binary for this machine")
print("binary", path)
local e = Engine.new(path)
assert(e:start())
local function run(req)
    for _ = 1, 600 do
        local state, value = Engine.step(req)
        if state ~= "waiting" then return state, value end
        ffi.C.poll(nil, 0, 10)
    end
    return "timeout"
end
assert(run(e:handshake()) == "done", "handshake")
local game = Chess.newGame()
-- Ten engine moves at mixed levels; every reply must be legal in the game.
for i = 1, 10 do
    local state, move = run(e:bestMove(game.initialFen, game.moves, (i % 10) + 1))
    assert(state == "done", "search " .. i .. ": " .. tostring(move))
    assert(game:play(move), "illegal engine move " .. move)
end
print("game", table.concat(game.sans, " "))
e:stop()
print("stopped cleanly", not e.alive)

-- Analysis returns a score: a free queen is clearly winning for the side that can take it.
local e2 = Engine.new(path)
assert(e2:start())
assert(run(e2:handshake()) == "done")
local Evaluation = require("evaluation")
local state, result = run(e2:analyse("4k3/8/8/8/8/8/3q4/3RK3 w - - 0 1", {}, 300))
assert(state == "done", "analyse: " .. tostring(result))
local ev = Evaluation.parse(result.lines, true, result.best)
print("analysis", result.best, ev and ev.score)
assert(result.best == "d1d2" and ev.score > 500, "expected Rxd2 and a winning score")
e2:stop()
print("engine check passed")
