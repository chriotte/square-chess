--[[--
Pieces shared by the game, puzzle and clock screens.
--]]--

local Blitbuffer = require("ffi/blitbuffer")
local ButtonDialog = require("ui/widget/buttondialog")
local Font = require("ui/font")
local Geom = require("ui/geometry")
local InfoMessage = require("ui/widget/infomessage")
local InputDialog = require("ui/widget/inputdialog")
local TextWidget = require("ui/widget/textwidget")
local UIManager = require("ui/uimanager")
local Widget = require("ui/widget/widget")
local time = require("ui/time")
local _ = require("gettext")
local T = require("ffi/util").template
local Screen = require("device").screen

local UI = {}

local BLACK, WHITE = Blitbuffer.COLOR_BLACK, Blitbuffer.COLOR_WHITE

--- Monotonic milliseconds, for clocks. Does not advance while the device sleeps.
function UI.now() return time.to_ms(time.monotonic()) end

--- A font face whose glyphs are about `px` pixels tall, whatever the screen size.
function UI.face(px, bold)
    return Font:getFace(bold and "smalltfont" or "cfont", math.max(6, math.floor(px * 1000 / Screen:scaleBySize(1000) + 0.5)))
end

function UI.promotion(moves, choose)
    local names = { q = _("Queen"), r = _("Rook"), b = _("Bishop"), n = _("Knight") }
    local dialog
    local row = {}
    for _i, m in ipairs(moves) do
        row[#row + 1] = { text = names[m.promotion], callback = function() UIManager:close(dialog); choose(m) end }
    end
    dialog = ButtonDialog:new{ title = _("Promote to"), buttons = { row } }
    UIManager:show(dialog)
end

--[[--
Asks for a move as text. `try(text)` returns nil when the move was accepted, or a
message to show while the dialog stays open. Works with the on-screen keyboard
and with physical keyboards.
--]]--
function UI.typeMove(title, try)
    local dialog
    local function submit()
        local text = dialog:getInputText()
        if text:match("^%s*$") then return end
        local problem = try(text)
        if problem then
            UIManager:show(InfoMessage:new{ text = problem, timeout = 3 })
        else
            UIManager:close(dialog)
        end
    end
    dialog = InputDialog:new{
        title = title or _("Type a move"),
        input_hint = _("e4, Nf3, exd5, O-O, e8=Q or e2e4"),
        description = _("Algebraic notation (SAN) or from-to squares."),
        buttons = { {
            { text = _("Cancel"), id = "close", callback = function() UIManager:close(dialog) end },
            { text = _("Move"), is_enter_default = true, callback = submit },
        } },
    }
    dialog.submit = submit -- lets tests drive the dialog
    UIManager:show(dialog)
    dialog:onShowKeyboard()
    return dialog
end

function UI.info(text, timeout)
    UIManager:show(InfoMessage:new{ text = text, timeout = timeout })
end

--[[--
A row of two clock boxes. The side to move is drawn inverted (white on black) so it
is obvious at a glance on e-ink, and stays that way until the next move, so each
tick only redraws the digits.
--]]--
local ClockBar = Widget:extend{ width = nil, height = nil }
UI.ClockBar = ClockBar

function ClockBar:init()
    self.dimen = Geom:new{ x = 0, y = 0, w = self.width, h = self.height }
    self.face = UI.face(math.floor(self.height * 0.5), true)
    self.texts = { w = "", b = "" }
end

function ClockBar:getSize() return Geom:new{ w = self.width, h = self.height } end

--- clock: a clock.lua state; texts: { w = "4:05", b = "5:00" }; flipped puts Black first.
function ClockBar:set(texts, active, flipped, paused)
    self.texts, self.active, self.flipped, self.paused = texts, active, flipped, paused
end

function ClockBar:paintTo(bb, x, y)
    self.dimen.x, self.dimen.y = x, y
    local half = math.floor(self.width / 2)
    local order = self.flipped and { "b", "w" } or { "w", "b" }
    for i, side in ipairs(order) do
        local bx = x + (i - 1) * half
        local active = side == self.active
        local bg, fg = active and BLACK or WHITE, active and WHITE or BLACK
        local inset = math.max(1, math.floor(self.height * 0.06))
        bb:paintRect(bx, y, half, self.height, WHITE)
        bb:paintRect(bx + inset, y + inset, half - 2 * inset, self.height - 2 * inset, bg)
        if not active then
            bb:paintBorder(bx + inset, y + inset, half - 2 * inset, self.height - 2 * inset, math.max(1, inset), BLACK)
        end
        local label = (side == "w" and _("White") or _("Black")) .. "  " .. (self.texts[side] or "")
        if active and self.paused then label = label .. "  ‖" end
        local tw = TextWidget:new{ text = label, face = self.face, fgcolor = fg, bgcolor = bg, padding = 0 }
        local ts = tw:getSize()
        tw:paintTo(bb, bx + math.floor((half - ts.w) / 2), y + math.floor((self.height - ts.h) / 2))
        tw:free()
    end
end

--- Plain-language theme names for the puzzle screen.
UI.THEME_NAMES = {
    mate = _("Checkmate"), mateIn1 = _("Mate in 1"), mateIn2 = _("Mate in 2"), mateIn3 = _("Mate in 3"),
    mateIn4 = _("Mate in 4"), mateIn5 = _("Mate in 5"), fork = _("Fork"), pin = _("Pin"), skewer = _("Skewer"),
    discoveredAttack = _("Discovered attack"), doubleCheck = _("Double check"), sacrifice = _("Sacrifice"),
    deflection = _("Deflection"), attraction = _("Attraction"), endgame = _("Endgame"), promotion = _("Promotion"),
    backRankMate = _("Back-rank mate"), hangingPiece = _("Hanging piece"), trappedPiece = _("Trapped piece"),
    exposedKing = _("Exposed king"), kingsideAttack = _("Kingside attack"), queensideAttack = _("Queenside attack"),
    zugzwang = _("Zugzwang"), quietMove = _("Quiet move"), intermezzo = _("Intermezzo"), clearance = _("Clearance"),
    interference = _("Interference"), xRayAttack = _("X-ray attack"), capturingDefender = _("Capturing the defender"),
    advancedPawn = _("Advanced pawn"), smotheredMate = _("Smothered mate"), defensiveMove = _("Defensive move"),
    opening = _("Opening"), middlegame = _("Middlegame"), short = _("Short"), long = _("Long"), veryLong = _("Very long"),
    crushing = _("Crushing"), advantage = _("Advantage"), equality = _("Equality"), oneMove = _("One move"),
    rookEndgame = _("Rook endgame"), pawnEndgame = _("Pawn endgame"), queenEndgame = _("Queen endgame"),
    bishopEndgame = _("Bishop endgame"), knightEndgame = _("Knight endgame"),
}

function UI.themeList(themes)
    local names = {}
    for t in pairs(themes) do
        if UI.THEME_NAMES[t] and t ~= "short" and t ~= "long" and t ~= "veryLong" and t ~= "oneMove" then
            names[#names + 1] = UI.THEME_NAMES[t]
        end
    end
    table.sort(names)
    return table.concat(names, " · ")
end

UI.T = T
return UI
