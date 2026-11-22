--[[--
Standalone chess clock for games on a real board, as in the Android app.

The device lies between the players. Each player has a half of the screen; the
far half is upside down so it faces the other player. Press your half when you
finish your move: your clock stops and your opponent's starts. The running side
is drawn white on black so it is obvious at a glance on e-ink. A finger or the
pen works, and a press that slides a little still counts.
--]]--

local Blitbuffer = require("ffi/blitbuffer")
local ButtonDialog = require("ui/widget/buttondialog")
local ButtonTable = require("ui/widget/buttontable")
local DataStorage = require("datastorage")
local Device = require("device")
local Geom = require("ui/geometry")
local GestureRange = require("ui/gesturerange")
local InputContainer = require("ui/widget/container/inputcontainer")
local LuaSettings = require("luasettings")
local Size = require("ui/size")
local TextWidget = require("ui/widget/textwidget")
local UIManager = require("ui/uimanager")
local _ = require("gettext")
local Screen = Device.screen

local Clock = require("clock")
local UI = require("ui")
local T = UI.T

local BLACK, WHITE = Blitbuffer.COLOR_BLACK, Blitbuffer.COLOR_WHITE

local ClockScreen = InputContainer:extend{
    plugin_dir = nil,
    covers_fullscreen = true,
}

function ClockScreen:init()
    self.settings = LuaSettings:open(DataStorage:getSettingsDir() .. "/squarechess.lua")
    self.preset = self.settings:readSetting("clock_preset", 5) -- 5+0
    if not (Clock.PRESETS[self.preset] and Clock.PRESETS[self.preset].base) then self.preset = 5 end
    self:resetClock()
    if Device:hasKeys() then self.key_events.Close = { { Device.input.group.Back } } end
    self:layout()
end

function ClockScreen:resetClock()
    local p = Clock.PRESETS[self.preset]
    self.clock = Clock.new(p.base, p.inc)
    self.moves = { w = 0, b = 0 }
end

function ClockScreen:layout()
    local w, h = Screen:getWidth(), Screen:getHeight()
    self.dimen = Geom:new{ x = 0, y = 0, w = w, h = h }
    local pad = Size.padding.large
    self.buttons = ButtonTable:new{
        width = w - 2 * pad, show_parent = self,
        buttons = { {
            { text = _("Pause"), callback = function() self:togglePause() end },
            { text = _("Reset"), callback = function() self:confirmReset() end },
            { text = T(_("Time: %1"), Clock.PRESETS[self.preset].label), callback = function() self:choosePreset() end },
            { text = _("Close"), callback = function() self:onClose() end },
        } },
    }
    -- A child, so its buttons get taps; paintTo below places it in the middle bar.
    self[1] = self.buttons
    local bar_h = self.buttons:getSize().h + 2 * pad
    local half_h = math.floor((h - bar_h) / 2)
    -- Black sits at the far end, upside down; White is nearest the bottom edge.
    self.halves = {
        b = Geom:new{ x = 0, y = 0, w = w, h = half_h },
        w = Geom:new{ x = 0, y = h - half_h, w = w, h = half_h },
    }
    self.bar = Geom:new{ x = 0, y = half_h, w = w, h = h - 2 * half_h }
    self.time_face = UI.face(math.floor(math.min(w, half_h) * 0.28), true)
    self.label_face = UI.face(math.floor(math.min(w, half_h) * 0.06))
    local range_b = function() return self.halves.b end
    local range_w = function() return self.halves.w end
    self.ges_events = {}
    for side, range in pairs({ b = range_b, w = range_w }) do
        for _i, ges in ipairs({ "tap", "swipe", "hold_release", "pan_release" }) do
            self.ges_events["Press" .. side .. ges] = { GestureRange:new{ ges = ges, range = range }, args = side }
            self["onPress" .. side .. ges] = function(_s, s) self:press(s); return true end
        end
    end
end

function ClockScreen:paintTo(bb, x, y)
    bb:paintRect(x, y, self.dimen.w, self.dimen.h, WHITE)
    local c = self.clock.phase == "running" and Clock.settled(self.clock, UI.now()) or self.clock
    for _i, side in ipairs({ "b", "w" }) do self:paintHalf(bb, side, c) end
    local pad = Size.padding.large
    self.buttons:paintTo(bb, self.bar.x + pad, self.bar.y + pad)
end

function ClockScreen:paintHalf(bb, side, c)
    local g = self.halves[side]
    local running = (c.phase == "running" or c.phase == "paused") and c.active == side
    local flagged = c.phase == "flagged" and c.active == side
    local bg, fg = (running or flagged) and BLACK or WHITE, (running or flagged) and WHITE or BLACK
    -- Draw upright into a scratch buffer, then rotate it for the far player.
    local tmp = Blitbuffer.new(g.w, g.h, bb:getType())
    tmp:fill(bg)
    if not (running or flagged) then tmp:paintBorder(0, 0, g.w, g.h, Size.border.thick, BLACK) end
    local time_text = flagged and _("Time") or Clock.text(c[side])
    local tw = TextWidget:new{ text = time_text, face = self.time_face, fgcolor = fg, bgcolor = bg, padding = 0 }
    local ts = tw:getSize()
    tw:paintTo(tmp, math.floor((g.w - ts.w) / 2), math.floor((g.h - ts.h) / 2))
    tw:free()
    local label
    if c.phase == "ready" then label = _("Press your side after your move. Black presses first to start White's clock.")
    elseif c.phase == "paused" and running then label = _("Paused · press Pause to resume")
    else label = T(_("%1 · moves %2"), side == "w" and _("White") or _("Black"), self.moves[side]) end
    local lw = TextWidget:new{ text = label, face = self.label_face, fgcolor = fg, bgcolor = bg, padding = 0,
        max_width = g.w - 2 * Size.padding.large }
    local ls = lw:getSize()
    lw:paintTo(tmp, math.floor((g.w - ls.w) / 2), math.floor(g.h * 0.78))
    lw:free()
    if side == "b" then
        local rotated = tmp:rotatedCopy(180)
        tmp:free()
        tmp = rotated
    end
    bb:blitFrom(tmp, g.x, g.y, 0, 0, g.w, g.h)
    tmp:free()
end

--- A press on `side`'s half: start the clock, or end that side's turn.
function ClockScreen:press(side)
    local now = UI.now()
    local c = self.clock
    if c.phase == "ready" then
        -- The side that presses starts the other clock.
        self.clock = Clock.start(c, now, side == "w" and "b" or "w")
    elseif c.phase == "running" and c.active == side then
        self.moves[side] = self.moves[side] + 1
        self.clock = Clock.switch(c, side, now)
    else
        return
    end
    UIManager:preventStandby()
    self.standby_prevented = true
    self:redraw("full_halves")
    self:scheduleTick()
end

function ClockScreen:togglePause()
    local now = UI.now()
    if self.clock.phase == "running" then self.clock = Clock.pause(self.clock, now)
    elseif self.clock.phase == "paused" then self.clock = Clock.resume(self.clock, now)
    else return end
    self:redraw("full_halves")
    self:scheduleTick()
end

function ClockScreen:confirmReset()
    local dialog
    dialog = ButtonDialog:new{ title = _("Reset both clocks?"), buttons = { {
        { text = _("Cancel"), callback = function() UIManager:close(dialog) end },
        { text = _("Reset"), callback = function()
            UIManager:close(dialog)
            self:resetClock()
            self:allowStandby()
            self:redraw("full_halves")
        end },
    } } }
    UIManager:show(dialog)
end

function ClockScreen:choosePreset()
    local dialog
    local rows, row = {}, {}
    for i, p in ipairs(Clock.PRESETS) do
        if p.base then
            row[#row + 1] = { text = (i == self.preset and "✓ " or "") .. p.label, callback = function()
                UIManager:close(dialog)
                self.preset = i
                self.settings:saveSetting("clock_preset", i)
                self.settings:flush()
                self:resetClock()
                self:allowStandby()
                self:layout()
                UIManager:setDirty(self, "full")
            end }
            if #row == 3 then rows[#rows + 1] = row; row = {} end
        end
    end
    if #row > 0 then rows[#rows + 1] = row end
    dialog = ButtonDialog:new{ title = _("Time control (minutes + seconds per move)"), buttons = rows }
    UIManager:show(dialog)
end

function ClockScreen:scheduleTick()
    UIManager:unschedule(self.tick_fn)
    if self.clock.phase ~= "running" or self.closed then return end
    local c = Clock.settled(self.clock, UI.now())
    if c.phase == "flagged" then
        self.clock = c
        self:allowStandby()
        return self:redraw("full_halves")
    end
    self.tick_fn = self.tick_fn or function()
        self:redraw("active")
        self:scheduleTick()
    end
    UIManager:scheduleIn(Clock.tickDelay(c[c.active]) / 1000, self.tick_fn)
end

--- "active": only the running half, with the fast black-and-white waveform.
--- "full_halves": both halves, after a press, so the inversion swaps cleanly.
function ClockScreen:redraw(what)
    if what == "active" then
        local g = self.halves[self.clock.active]
        UIManager:setDirty(self, function() return "fast", g end)
    else
        UIManager:setDirty(self, function() return "ui", self.dimen end)
    end
end

function ClockScreen:allowStandby()
    if self.standby_prevented then UIManager:allowStandby(); self.standby_prevented = false end
end

function ClockScreen:onSuspend()
    if self.clock.phase == "running" then self:togglePause() end
end

function ClockScreen:onClose()
    UIManager:close(self)
    return true
end

function ClockScreen:onCloseWidget()
    self.closed = true
    UIManager:unschedule(self.tick_fn)
    self:allowStandby()
    self.buttons:free()
    UIManager:setDirty(nil, "full")
end

return ClockScreen
