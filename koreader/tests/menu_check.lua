-- Checks the Tools menu placement and the two-player entry, at Paperwhite size.
local plugin = arg[1]
require("setupkoenv")
G_defaults = require("luadefaults"):open()
local DataStorage = require("datastorage")
G_reader_settings = require("luasettings"):open(DataStorage:getDataDir() .. "/settings.reader.lua")
require("ffi/blitbuffer"):setUseCBB(true)
local Device = require("device")
require("document/canvascontext"):init(Device)
local UIManager = require("ui/uimanager")
package.path = plugin .. "/?.lua;" .. package.path
os.remove(DataStorage:getSettingsDir() .. "/squarechess.lua")

local fails = 0
local function check(ok, what)
    if not ok then fails = fails + 1; print("FAIL: " .. what) else print("ok: " .. what) end
end

-- A stub for the menu registration the plugin expects.
local registered
local SquareChess = dofile(plugin .. "/main.lua")
local inst = SquareChess:extend{ ui = { menu = { registerToMainMenu = function(_s, w) registered = w end } } }
inst = inst:new{ ui = { menu = { registerToMainMenu = function(_s, w) registered = w end } } }
check(registered == inst, "registered with the main menu")

local fm = require("ui/elements/filemanager_menu_order")
local rd = require("ui/elements/reader_menu_order")
check(fm.tools[1] == "squarechess", "first in the file browser Tools menu")
check(rd.tools[1] == "squarechess", "first in the reader Tools menu")
inst:init() -- again: must not add a second entry
local n = 0
for _i, id in ipairs(fm.tools) do if id == "squarechess" then n = n + 1 end end
check(n == 1, "listed once after a second init")

local items = {}
inst:addToMainMenu(items)
check(items.squarechess and items.squarechess.sorting_hint == "tools", "entry hints the Tools menu")
local labels = {}
for i, it in ipairs(items.squarechess.sub_item_table) do labels[i] = it.text end
check(table.concat(labels, "|") == "Play the computer|Two players|Puzzles|Chess clock",
    "four entries: " .. table.concat(labels, ", "))

-- The MenuSorter must put it at the top of Tools, not at the end.
local MenuSorter = require("ui/menusorter")
-- Stand-ins for the menu entries the app itself supplies, so the sorter has a full menu.
local all = { squarechess = items.squarechess }
local function stub(id)
    if type(id) == "string" and not id:match("^%-+$") and not all[id] then all[id] = { text = id } end
end
for key, list in pairs(fm) do
    stub(key)
    if type(list) == "table" then for _i, id in ipairs(list) do stub(id) end end
end
local sorted = MenuSorter:mergeAndSort("filemanager", all, fm)
local function findTools(t)
    for _i, v in ipairs(t) do
        if v.id == "tools" then return v end
        if v.sub_item_table then local f = findTools(v.sub_item_table); if f then return f end end
    end
end
local tools = findTools(sorted)
check(tools ~= nil, "found the Tools menu")
if tools then
    local first = (tools.sub_item_table or tools)[1]
    check(first and first.text == "Square Chess", "Square Chess is the first item in Tools, got " ..
        tostring(first and first.text))
end

-- Two players: a fresh screen starts straight away.
items.squarechess.sub_item_table[2].callback()
local screen = UIManager._window_stack[#UIManager._window_stack].widget
check(screen.player == "both", "two-player game started, player = " .. tostring(screen.player))
check(screen.clock == nil or screen.time_control == 1, "untimed by default")

-- With a game against the computer in progress, it must ask first.
screen:startGame("w")
screen.game:play("e2e4")
screen:startTwoPlayers()
local top = UIManager._window_stack[#UIManager._window_stack].widget
check(top ~= screen and top.ok_callback ~= nil, "asks before discarding a game in progress")
check(screen.player == "w", "the game in progress is untouched until confirmed")
top.ok_callback()
check(screen.player == "both", "confirming starts the two-player game")
screen:onCloseWidget()

print(fails == 0 and "ALL OK" or (fails .. " FAILED"))
os.exit(fails == 0 and 0 or 1)
