--[[--
The chessboard: draws a position in the Android app's e-ink style and turns touch
and pen input into moves.

White light squares, dark squares hatched with 45° lines as in printed diagrams,
corner marks on the last move, a thick outline on the selected square, dots and
rings for legal targets, a ring around a king in check, and an optional hint
overlay. Black and white only, so it reads the same on every e-ink screen.

Input: KOReader reports a pen like a finger, but a pen tap often wobbles into a
short swipe or pan, and a slow press becomes a hold. Every single-contact gesture
is therefore read as "down on square A, up on square B": the same square is a tap,
a different square is a drag from A to B. Taps, drags, pen and finger all work.
--]]--

local Blitbuffer = require("ffi/blitbuffer")
local Font = require("ui/font")
local Geom = require("ui/geometry")
local GestureRange = require("ui/gesturerange")
local ImageWidget = require("ui/widget/imagewidget")
local InputContainer = require("ui/widget/container/inputcontainer")
local TextWidget = require("ui/widget/textwidget")
local Device = require("device")
local Screen = Device.screen
local Chess = require("chess")

local BLACK, WHITE = Blitbuffer.COLOR_BLACK, Blitbuffer.COLOR_WHITE

local Board = InputContainer:extend{
    size = nil,        -- side length in pixels
    plugin_dir = nil,  -- for piece images
    pos = nil,         -- Chess.Position shown
    last = nil,        -- last move in UCI, for the corner marks
    hint = nil,        -- a UCI move drawn as an overlay, or nil
    flipped = false,   -- true: Black at the bottom
    interactive = true,
    show_coordinates = true,
    on_move = nil,     -- function(move) called with a legal move table
    on_promotion = nil, -- function(moves, choose) asks which piece; choose(move)
}

--- The side length a board asked for `size` actually uses (a whole number of pixels per square).
function Board.fittedSize(size) return math.floor(size / 8) * 8 end

function Board:init()
    self.cell = math.floor(self.size / 8)
    self.size = self.cell * 8
    self.dimen = Geom:new{ x = 0, y = 0, w = self.size, h = self.size }
    self.selected = nil
    self.pieces = {}
    local pad = math.floor(self.cell * 0.02)
    for _i, color in ipairs({ "w", "b" }) do
        for _i, kind in ipairs({ "K", "Q", "R", "B", "N", "P" }) do
            self.pieces[color == "w" and kind or kind:lower()] = ImageWidget:new{
                file = self.plugin_dir .. "/pieces/" .. color .. kind .. ".png",
                width = self.cell - 2 * pad, height = self.cell - 2 * pad,
                alpha = true, scale_factor = 0, original_in_nightmode = false,
            }
        end
    end
    self.piece_pad = pad
    self.hatch = self:makeHatch()
    if self.show_coordinates then
        -- getFace scales sizes by screen size; undo that so the label follows the cell.
        local px = math.max(8, math.floor(self.cell * 0.2))
        self.coord_face = Font:getFace("cfont", math.max(6, math.floor(px * 1000 / Screen:scaleBySize(1000) + 0.5)))
    end
    local range = function() return self.dimen end
    self.ges_events = {
        BoardTap = { GestureRange:new{ ges = "tap", range = range } },
        BoardSwipe = { GestureRange:new{ ges = "swipe", range = range } },
        BoardHold = { GestureRange:new{ ges = "hold", range = range } },
        BoardHoldPan = { GestureRange:new{ ges = "hold_pan", range = range } },
        BoardPan = { GestureRange:new{ ges = "pan", range = range } },
        BoardRelease = { GestureRange:new{ ges = "hold_release", range = range } },
        BoardPanRelease = { GestureRange:new{ ges = "pan_release", range = range } },
    }
end

--- One hatched square, reused for every dark square. Seven lines per square
--- keep the pattern continuous across squares, as in the app.
function Board:makeHatch()
    local c = self.cell
    local bb = Blitbuffer.new(c, c, Screen.bb:getType())
    bb:fill(WHITE)
    local step = c / 7
    local thick = math.max(1, c * 0.035 * math.sqrt(2))
    for y = 0, c - 1 do
        -- Black where (x + y) mod step < thick: lines running from bottom left to top right.
        local k = math.floor((y - thick) / step)
        while true do
            local start = k * step - y
            if start >= c then break end
            local x0 = math.max(0, math.floor(start + 0.5))
            local x1 = math.min(c, math.floor(start + thick + 0.5))
            if x1 > x0 then bb:paintRect(x0, y, x1 - x0, 1, BLACK) end
            k = k + 1
        end
    end
    return bb
end

function Board:free()
    for _i, w in pairs(self.pieces) do w:free() end
    if self.hatch then self.hatch:free(); self.hatch = nil end
end

function Board:getSize() return Geom:new{ w = self.size, h = self.size } end

--- Shows a position. `last` is the move that led to it, in UCI.
function Board:setPosition(pos, last)
    if pos ~= self.pos then self.selected = nil end
    self.pos, self.last = pos, last
end

--- Screen cell (col, row from top-left) to square index, honouring flipping.
function Board:squareAt(col, row)
    if self.flipped then return row * 8 + (7 - col) end
    return (7 - row) * 8 + col
end

function Board:cellOf(square)
    local f, r = square % 8, math.floor(square / 8)
    if self.flipped then return 7 - f, r end
    return f, 7 - r
end

function Board:targets()
    if not self.selected or not self.interactive then return {} end
    local t = {}
    for _i, m in ipairs(self.pos:legalMoves()) do
        if m.from == self.selected then t[m.to] = t[m.to] or {}; table.insert(t[m.to], m) end
    end
    return t
end

local function stroke(bb, x, y, w, h, color)
    if w > 0 and h > 0 then bb:paintRect(math.floor(x), math.floor(y), math.ceil(w), math.ceil(h), color or BLACK) end
end

--- paintCircle draws a ring inward from radius r; this centres it on r like a pen stroke.
local function ring(bb, cx, cy, r, width, color)
    bb:paintCircle(math.floor(cx), math.floor(cy), math.floor(r + width / 2), color, math.max(1, math.floor(width)))
end

local function outline(bb, x, y, c, w, color)
    stroke(bb, x, y, c, w, color); stroke(bb, x, y + c - w, c, w, color)
    stroke(bb, x, y, w, c, color); stroke(bb, x + c - w, y, w, c, color)
end

function Board:paintTo(bb, x, y)
    self.dimen.x, self.dimen.y = x, y
    local c, pos = self.cell, self.pos
    local targets = self:targets()
    local last = self.last
    local lastFrom = last and Chess.squareIndex(last:sub(1, 2))
    local lastTo = last and Chess.squareIndex(last:sub(3, 4))
    local checkSquare = pos:inCheck() and pos:kingSquare(pos.turn) or nil
    for row = 0, 7 do
        for col = 0, 7 do
            local s = self:squareAt(col, row)
            local sx, sy = x + col * c, y + row * c
            local dark = (s % 8 + math.floor(s / 8)) % 2 == 0
            if dark then bb:blitFrom(self.hatch, sx, sy, 0, 0, c, c)
            else bb:paintRect(sx, sy, c, c, WHITE) end
            if s == lastFrom or s == lastTo then
                local arm, w = c * 0.28, c * 0.09
                stroke(bb, sx, sy, arm, w); stroke(bb, sx, sy, w, arm)
                stroke(bb, sx + c - arm, sy, arm, w); stroke(bb, sx + c - w, sy, w, arm)
                stroke(bb, sx, sy + c - w, arm, w); stroke(bb, sx, sy + c - arm, w, arm)
                stroke(bb, sx + c - arm, sy + c - w, arm, w); stroke(bb, sx + c - w, sy + c - arm, w, arm)
            end
            if s == checkSquare then
                ring(bb, sx + c / 2, sy + c / 2, c * 0.44, c * 0.08, BLACK)
            end
            if s == self.selected then outline(bb, sx, sy, c, c * 0.12, BLACK) end
            local p = pos:piece(s)
            if p then self.pieces[p]:paintTo(bb, sx + self.piece_pad, sy + self.piece_pad) end
            if targets[s] then
                local cx, cy = sx + c / 2, sy + c / 2
                if p then
                    ring(bb, cx, cy, c * 0.44, c * 0.14, WHITE)
                    ring(bb, cx, cy, c * 0.44, c * 0.08, BLACK)
                else
                    bb:paintCircle(math.floor(cx), math.floor(cy), math.floor(c * 0.21), WHITE)
                    bb:paintCircle(math.floor(cx), math.floor(cy), math.floor(c * 0.15), BLACK)
                end
            end
        end
    end
    if self.hint then self:paintHint(bb, x, y) end
    if self.coord_face then self:paintCoordinates(bb, x, y) end
end

--- The hinted move: outlined source, ringed target and a dotted line between them.
--- Every shape is black on a white rim so it reads on hatching and on pieces.
function Board:paintHint(bb, x, y)
    local from, to = Chess.squareIndex(self.hint:sub(1, 2)), Chess.squareIndex(self.hint:sub(3, 4))
    if not from or not to then return end
    local c = self.cell
    local fc, fr = self:cellOf(from)
    local tc, tr = self:cellOf(to)
    local fx, fy = x + fc * c, y + fr * c
    outline(bb, fx, fy, c, c * 0.14, WHITE)
    outline(bb, fx + c * 0.03, fy + c * 0.03, c - c * 0.06, c * 0.08, BLACK)
    if from == to then return end
    local ax, ay = fx + c / 2, fy + c / 2
    local bx, by = x + tc * c + c / 2, y + tr * c + c / 2
    local dist = math.sqrt((bx - ax) ^ 2 + (by - ay) ^ 2)
    local dots = math.max(1, math.floor(dist / (c * 0.22)))
    for i = 1, dots - 1 do
        local t = i / dots
        local px, py = ax + (bx - ax) * t, ay + (by - ay) * t
        bb:paintCircle(math.floor(px), math.floor(py), math.floor(c * 0.075), WHITE)
        bb:paintCircle(math.floor(px), math.floor(py), math.floor(c * 0.045), BLACK)
    end
    ring(bb, bx, by, c * 0.40, c * 0.16, WHITE)
    ring(bb, bx, by, c * 0.40, c * 0.09, BLACK)
end

--- Files along the bottom edge and ranks along the right edge, on white patches
--- so they stay legible over the hatching.
function Board:paintCoordinates(bb, x, y)
    local c = self.cell
    local inset = math.max(1, math.floor(c * 0.03))
    for i = 0, 7 do
        local file = self.flipped and (7 - i) or i
        local fw = TextWidget:new{ text = string.char(97 + file), face = self.coord_face, padding = 0, fgcolor = BLACK }
        local fs = fw:getSize()
        local fx, fy = x + i * c + inset, y + 8 * c - fs.h - inset
        bb:paintRect(fx - 1, fy, fs.w + 2, fs.h, WHITE)
        fw:paintTo(bb, fx, fy)
        fw:free()
        local rank = self.flipped and (i + 1) or (8 - i)
        local rw = TextWidget:new{ text = tostring(rank), face = self.coord_face, padding = 0, fgcolor = BLACK }
        local rs = rw:getSize()
        local rx, ry = x + 8 * c - rs.w - inset, y + i * c + inset
        bb:paintRect(rx - 1, ry, rs.w + 2, rs.h, WHITE)
        rw:paintTo(bb, rx, ry)
        rw:free()
    end
end

--- Pointer input ------------------------------------------------------------

function Board:squareAtPoint(pos)
    if not pos then return nil end
    local col = math.floor((pos.x - self.dimen.x) / self.cell)
    local row = math.floor((pos.y - self.dimen.y) / self.cell)
    if col < 0 or col > 7 or row < 0 or row > 7 then return nil end
    return self:squareAt(col, row)
end

function Board:ownPiece(s)
    local p = self.pos:piece(s)
    return p ~= nil and ((p:upper() == p) == (self.pos.turn == "w"))
end

--- One finished contact: down on square a, up on square b (either may be nil, off the board).
function Board:pointer(a, b)
    self.press_start = nil
    if not self.interactive then return end
    if a and b and a ~= b and self:ownPiece(a) then
        -- A drag from an own piece: select it, then treat the end as the target tap.
        self.selected = a
        self:tapSquare(b)
    elseif b then
        self:tapSquare(b)
    elseif a then
        self:tapSquare(a)
    end
end

function Board:onBoardTap(_s, ges) self:pointer(self:squareAtPoint(ges.pos), self:squareAtPoint(ges.pos)); return true end
function Board:onBoardSwipe(_s, ges) self:pointer(self:squareAtPoint(ges.pos), self:squareAtPoint(ges.end_pos)); return true end
function Board:onBoardHold(_s, ges) self.press_start = ges.pos; return true end
function Board:onBoardPan(_s, ges) self.press_start = self.press_start or ges.start_pos or ges.pos; return true end
function Board:onBoardHoldPan(_s, ges) self.press_start = self.press_start or ges.start_pos or ges.pos; return true end
function Board:onBoardRelease(_s, ges)
    self:pointer(self:squareAtPoint(self.press_start or ges.pos), self:squareAtPoint(ges.pos))
    return true
end
Board.onBoardPanRelease = Board.onBoardRelease

--- The app's tap rules: select an own piece, tap a target to move, tap the
--- king's own rook to castle, tap elsewhere to deselect.
function Board:tapSquare(s)
    local pos = self.pos
    local targets = self:targets()
    if targets[s] then
        local moves = targets[s]
        self.selected = nil
        if #moves > 1 and self.on_promotion then
            self.on_promotion(moves, function(m) if self.on_move then self.on_move(m) end end)
        elseif self.on_move then
            self.on_move(moves[1])
        end
        return
    end
    local p = pos:piece(s)
    if self.selected and p and pos:piece(self.selected):lower() == "k" and p:lower() == "r" and self:ownPiece(s) then
        for _i, m in ipairs(pos:legalMoves()) do
            if m.flags == "castle" and m.from == self.selected and ((s > m.from) == (m.to > m.from)) then
                self.selected = nil
                if self.on_move then self.on_move(m) end
                return
            end
        end
    end
    if self:ownPiece(s) and self.selected ~= s then
        self.selected = s
    else
        self.selected = nil
    end
    self:refresh()
end

function Board:refresh()
    local UIManager = require("ui/uimanager")
    -- "ui" is a fast partial refresh of just the board; e-ink ghosting is cleared
    -- by KOReader's own periodic full refresh.
    UIManager:setDirty(self.show_parent or self, function() return "ui", self.dimen end)
end

return Board
