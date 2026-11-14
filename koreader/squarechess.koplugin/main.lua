--[[--
Square Chess for KOReader: play the computer (Fairy-Stockfish, ten levels) or a
friend, solve puzzles, or use the chess clock, offline on e-ink. Open it from the
Tools menu, or assign its actions to gestures.

@module koplugin.SquareChess
--]]--

local Dispatcher = require("dispatcher")
local UIManager = require("ui/uimanager")
local WidgetContainer = require("ui/widget/container/widgetcontainer")
local _ = require("gettext")

local SquareChess = WidgetContainer:extend{
    name = "squarechess",
    is_doc_only = false,
}

-- The plugin's own modules (chess, engine, board ...) live next to this file.
local plugin_dir = debug.getinfo(1, "S").source:match("^@(.*)/[^/]*$") or "plugins/squarechess.koplugin"
if not package.path:find(plugin_dir, 1, true) then
    package.path = plugin_dir .. "/?.lua;" .. package.path
end

function SquareChess:init()
    Dispatcher:registerAction("squarechess_open", {
        category = "none", event = "SquareChessOpen", title = _("Square Chess: play"), general = true,
    })
    Dispatcher:registerAction("squarechess_puzzles", {
        category = "none", event = "SquareChessPuzzles", title = _("Square Chess: puzzles"), general = true,
    })
    Dispatcher:registerAction("squarechess_clock", {
        category = "none", event = "SquareChessClock", title = _("Square Chess: chess clock"), general = true,
    })
    self.ui.menu:registerToMainMenu(self)
end

function SquareChess:addToMainMenu(menu_items)
    menu_items.squarechess = {
        text = _("Square Chess"),
        sorting_hint = "tools",
        sub_item_table = {
            { text = _("Play chess"), callback = function() self:onSquareChessOpen() end },
            { text = _("Puzzles"), callback = function() self:onSquareChessPuzzles() end },
            { text = _("Chess clock"), callback = function() self:onSquareChessClock() end },
        },
    }
end

function SquareChess:onSquareChessOpen()
    UIManager:show(require("gamescreen"):new{ plugin_dir = plugin_dir })
    return true
end

function SquareChess:onSquareChessPuzzles()
    UIManager:show(require("puzzlescreen"):new{ plugin_dir = plugin_dir })
    return true
end

function SquareChess:onSquareChessClock()
    UIManager:show(require("clockscreen"):new{ plugin_dir = plugin_dir })
    return true
end

return SquareChess
