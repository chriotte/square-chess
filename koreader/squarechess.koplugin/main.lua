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

--[[--
Puts the plugin at the top of the Tools menu. KOReader appends a plugin's entry to
the end of the menu it asks for, unless the menu's own order lists it, so add the
id to that order. Both menus are listed, and nothing happens if the id is there.
--]]--
local function placeFirstInTools()
    for _i, module in ipairs({ "ui/elements/filemanager_menu_order", "ui/elements/reader_menu_order" }) do
        local ok, order = pcall(require, module)
        if ok and type(order) == "table" and type(order.tools) == "table" then
            local listed = false
            for _j, id in ipairs(order.tools) do
                if id == "squarechess" then listed = true; break end
            end
            if not listed then table.insert(order.tools, 1, "squarechess") end
        end
    end
end

function SquareChess:init()
    placeFirstInTools()
    Dispatcher:registerAction("squarechess_open", {
        category = "none", event = "SquareChessOpen", title = _("Square Chess: play"), general = true,
    })
    Dispatcher:registerAction("squarechess_two", {
        category = "none", event = "SquareChessTwoPlayers", title = _("Square Chess: two players"), general = true,
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
            { text = _("Play the computer"), callback = function() self:onSquareChessOpen() end },
            { text = _("Two players"), callback = function() self:onSquareChessTwoPlayers() end },
            { text = _("Puzzles"), callback = function() self:onSquareChessPuzzles() end },
            { text = _("Chess clock"), callback = function() self:onSquareChessClock() end },
        },
    }
end

function SquareChess:onSquareChessOpen()
    UIManager:show(require("gamescreen"):new{ plugin_dir = plugin_dir })
    return true
end

function SquareChess:onSquareChessTwoPlayers()
    local screen = require("gamescreen"):new{ plugin_dir = plugin_dir }
    UIManager:show(screen)
    screen:startTwoPlayers()
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
