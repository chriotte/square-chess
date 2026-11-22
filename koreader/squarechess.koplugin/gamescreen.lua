--[[--
The full-screen game: title bar with status, the board as large as the screen
allows, an optional clock bar, an information and move list area, and a row of
buttons. The board comes first; everything else fits around the space that
remains, as in the Android app.

Modes: against the computer (Fairy-Stockfish at ten levels) or two players on one
device, with optional time control, hints and evaluation. A finished or imported
game can be reviewed move by move.
--]]--

local Blitbuffer = require("ffi/blitbuffer")
local ButtonDialog = require("ui/widget/buttondialog")
local ButtonTable = require("ui/widget/buttontable")
local CenterContainer = require("ui/widget/container/centercontainer")
local ConfirmBox = require("ui/widget/confirmbox")
local DataStorage = require("datastorage")
local Device = require("device")
local FrameContainer = require("ui/widget/container/framecontainer")
local Geom = require("ui/geometry")
local HorizontalGroup = require("ui/widget/horizontalgroup")
local InputContainer = require("ui/widget/container/inputcontainer")
local GestureRange = require("ui/gesturerange")
local LuaSettings = require("luasettings")
local Size = require("ui/size")
local TextBoxWidget = require("ui/widget/textboxwidget")
local TitleBar = require("ui/widget/titlebar")
local UIManager = require("ui/uimanager")
local VerticalGroup = require("ui/widget/verticalgroup")
local VerticalSpan = require("ui/widget/verticalspan")
local logger = require("logger")
local _ = require("gettext")
local Screen = Device.screen

local Board = require("board")
local Chess = require("chess")
local Clock = require("clock")
local Engine = require("engine")
local Evaluation = require("evaluation")
local Pgn = require("pgn")
local UI = require("ui")
local T = UI.T

local POLL_SECONDS = 0.05
local HINT_TIME_MS = 800

local GameScreen = InputContainer:extend{
    plugin_dir = nil,
    covers_fullscreen = true,
}

function GameScreen:init()
    self.settings = LuaSettings:open(DataStorage:getSettingsDir() .. "/squarechess.lua")
    local s = self.settings
    self.level = s:readSetting("level", Engine.DEFAULT_LEVEL)
    self.player = s:readSetting("player", "w") -- "w", "b" or "both"
    self.hints = s:readSetting("hints", true)
    self.evaluation = s:readSetting("evaluation", false)
    self.time_control = s:readSetting("time_control", 1) -- index into Clock.PRESETS
    self.game = Chess.restoreGame(s:readSetting("initial_fen", Chess.START_FEN), s:readSetting("moves", {}))
        or Chess.newGame()
    self.ended = s:readSetting("ended") -- { result=, reason= } for resign, timeout, claimed draw, PGN result
    self.evals = s:readSetting("evals", {})
    self.pgn_tags = s:readSetting("pgn_tags")
    self.clock = s:readSetting("clock") -- saved paused; see Clock.snapshot
    self.flipped = self.player == "b"
    self.queue, self.generation = {}, 0
    if Device:hasKeys() then
        self.key_events.Close = { { Device.input.group.Back } }
    end
    self:startEngine()
    self:layout()
    self:afterChange(true)
end

--- Engine work queue ----------------------------------------------------------
--- One request runs at a time. A newer game state bumps the generation, and
--- results from older generations are dropped.

function GameScreen:startEngine()
    local path = Engine.findBinary(self.plugin_dir)
    if not path then
        self.engine_error = _("No chess engine for this device was found in the plugin's bin folder.")
        return
    end
    self.engine = Engine.new(path)
    local ok, err = self.engine:start()
    if not ok then
        self.engine_error = T(_("The chess engine could not start: %1"), err)
        self.engine = nil
        return
    end
    self:enqueue(function(e) return e:handshake() end, function(state, value)
        if state ~= "done" then self:engineFailed(value) end
    end, true)
end

function GameScreen:engineFailed(reason)
    logger.warn("SquareChess: engine failed", reason)
    self.engine_error = T(_("The chess engine stopped: %1"), tostring(reason))
    if self.engine then self.engine:stop(); self.engine = nil end
    self.queue, self.busy, self.thinking = {}, false, false
    self:refreshAll()
end

--- `make(engine)` builds the request; `done(state, value)` gets the result.
--- `always` delivers the result even if the game has changed since.
function GameScreen:enqueue(make, done, always)
    if not self.engine then return false end
    table.insert(self.queue, { make = make, done = done, generation = self.generation, always = always })
    self:pumpQueue()
    return true
end

function GameScreen:pumpQueue()
    if self.busy or not self.engine or #self.queue == 0 then return end
    local item = table.remove(self.queue, 1)
    if not item.always and item.generation ~= self.generation then return self:pumpQueue() end
    self.busy = true
    local request = item.make(self.engine)
    local function poll()
        if self.closed or not self.engine then return end
        local state, value = Engine.step(request)
        if state == "waiting" then
            UIManager:scheduleIn(POLL_SECONDS, poll)
            return
        end
        self.busy = false
        if state == "failed" then return self:engineFailed(value) end
        if item.always or item.generation == self.generation then item.done(state, value) end
        self:pumpQueue()
    end
    poll()
end

--- Drops queued work and ignores anything still running.
function GameScreen:invalidate()
    self.generation = self.generation + 1
    self.queue = {}
    self.thinking = false
    self.hint, self.board.hint, self.hint_text = nil, nil, nil
    self.message = nil
end

--- Game state -----------------------------------------------------------------

function GameScreen:result()
    if self.ended then return self.ended.result, self.ended.reason end
    return self.game:result()
end

function GameScreen:computerToMove()
    if self.player == "both" or self:result() or self.view then return false end
    return self.game:position().turn ~= self.player
end

function GameScreen:save()
    local s = self.settings
    s:saveSetting("initial_fen", self.game.initialFen)
    s:saveSetting("moves", self.game.moves)
    s:saveSetting("level", self.level)
    s:saveSetting("player", self.player)
    s:saveSetting("ended", self.ended)
    s:saveSetting("hints", self.hints)
    s:saveSetting("evaluation", self.evaluation)
    s:saveSetting("evals", self.evals)
    s:saveSetting("time_control", self.time_control)
    s:saveSetting("pgn_tags", self.pgn_tags)
    s:saveSetting("clock", self.clock and Clock.snapshot(self.clock, UI.now()) or nil)
    s:flush()
end

--- Called after every change to the game: saves, redraws and queues engine work.
function GameScreen:afterChange(quiet)
    self:save()
    local result, reason = self:result()
    if result and self.clock and self.clock.phase ~= "flagged" then self.clock = Clock.finish(self.clock, UI.now()) end
    self:showCurrent()
    if result and not quiet then
        UI.info(T("%1\n%2", reason, result), 4)
    end
    if self.evaluation then self:queueEvaluation(#self.game.moves) end
    if self:computerToMove() then self:queueComputerMove() end
    self:scheduleTick()
end

--- Puts the live position or the reviewed one on the board and redraws everything.
function GameScreen:showCurrent()
    local ply = self.view or #self.game.moves
    self.board:setPosition(self.game.positions[ply + 1], self.game.moves[ply])
    self.board.interactive = not self.view and not self:result() and not self:computerToMove()
    self.board.flipped = self.flipped
    self:rebuildButtons()
    self:refreshAll()
end

function GameScreen:queueComputerMove()
    self.thinking = true
    self:enqueue(function(e)
        return e:bestMove(self.game.initialFen, self.game.moves, self.level)
    end, function(_s, move)
        self.thinking = false
        if self.game:play(move) then
            self:moved()
        else
            self:engineFailed("illegal move " .. tostring(move))
        end
    end)
    if not self.engine and self.engine_error and not self.engine_error_shown then
        self.engine_error_shown = true
        self.thinking = false
        UI.info(self.engine_error)
    end
end

--- Evaluates position `ply` unless already known. Finished positions need no engine.
function GameScreen:queueEvaluation(ply)
    if self.evals[ply] then return end
    if ply == #self.game.moves then
        local terminal = Evaluation.terminal(self.game)
        if terminal then self.evals[ply] = terminal; return end
    end
    local moves = { unpack(self.game.moves, 1, ply) }
    local white = self.game.positions[ply + 1].turn == "w"
    self:enqueue(function(e) return e:analyse(self.game.initialFen, moves, Evaluation.TIME_MS) end,
        function(_s, value)
            local ev = Evaluation.parse(value.lines, white, value.best)
            if ev then
                self.evals[ply] = ev
                self:save()
                self:updateText()
            end
        end)
end

--- After any move by either side: clock, then the usual change handling.
function GameScreen:moved()
    local mover = self.game:position().turn == "w" and "b" or "w"
    if self.clock then
        local now = UI.now()
        if self.clock.phase == "ready" then self.clock = Clock.start(self.clock, now, mover) end
        if self.clock.phase == "paused" then self.clock = Clock.resume(self.clock, now) end
        self.clock = Clock.switch(self.clock, mover, now)
        if self.clock.phase == "flagged" then return self:flagged() end
    end
    self.hint, self.board.hint, self.message, self.hint_text = nil, nil, nil, nil
    self:afterChange()
end

function GameScreen:flagged()
    local loser = self.clock.active
    local winner = loser == "w" and "b" or "w"
    if self.game:position():cannotMate(winner) then
        self.ended = { result = "1/2-1/2", reason = _("Time out · opponent cannot mate") }
    else
        self.ended = { result = winner == "w" and "1-0" or "0-1", reason = T(_("%1 ran out of time"), loser == "w" and _("White") or _("Black")) }
    end
    self:invalidate()
    self:afterChange()
end

--- Clock ticks ----------------------------------------------------------------

function GameScreen:scheduleTick()
    UIManager:unschedule(self.tick_fn)
    if not self.clock or self.clock.phase ~= "running" or self.closed then return end
    local settled = Clock.settled(self.clock, UI.now())
    if settled.phase == "flagged" then
        self.clock = settled
        return self:flagged()
    end
    self.tick_fn = self.tick_fn or function()
        self:updateClockBar(true)
        self:scheduleTick()
    end
    UIManager:scheduleIn(Clock.tickDelay(settled[settled.active]) / 1000, self.tick_fn)
end

function GameScreen:updateClockBar(refresh)
    if not self.clock_bar then return end
    local c = self.clock.phase == "running" and Clock.settled(self.clock, UI.now()) or self.clock
    local active = (c.phase == "running" or c.phase == "paused" or c.phase == "ready") and c.active or nil
    if c.phase == "ready" then active = self.game:position().turn end
    self.clock_bar:set({ w = Clock.text(c.w), b = Clock.text(c.b) }, active, self.flipped, c.phase == "paused")
    if refresh then
        -- "fast" is the quick black-and-white waveform: right for changing digits.
        UIManager:setDirty(self, function() return "fast", self.clock_bar.dimen end)
    end
end

function GameScreen:toggleClockPause()
    if not self.clock then return end
    local now = UI.now()
    if self.clock.phase == "running" then self.clock = Clock.pause(self.clock, now)
    elseif self.clock.phase == "paused" then self.clock = Clock.resume(self.clock, now) end
    self:save()
    self:updateClockBar(true)
    self:scheduleTick()
end

function GameScreen:onSuspend()
    if self.clock and self.clock.phase == "running" then self:toggleClockPause() end
end

--- Text -------------------------------------------------------------------------

function GameScreen:statusText()
    if self.view then
        return T(_("Review · move %1 of %2"), self.view, #self.game.moves)
    end
    local result, reason = self:result()
    if result then return T("%1 · %2", reason, result) end
    local level = T(_("Level %1"), self.level)
    if self.thinking then return T(_("Computer is thinking… · %1"), level) end
    if self.engine_error and self.player ~= "both" then return _("Engine unavailable") end
    local pos = self.game:position()
    local side = pos.turn == "w" and _("White") or _("Black")
    local check = pos:inCheck() and (" · " .. _("Check")) or ""
    if self.player == "both" then return T(_("%1 to move"), side) .. check end
    return T(_("Your move · %1"), level) .. check
end

--- The line above the moves: a message, the hint, or the evaluation.
function GameScreen:infoText()
    if self.message then return self.message end
    if self.hint_text then return self.hint_text end
    if not self.evaluation then return nil end
    local ply = self.view or #self.game.moves
    local ev = self.evals[ply]
    if not ev then return _("Evaluating…") end
    local text = Evaluation.leadText(ev)
    local score = Evaluation.scoreText(ev)
    if score ~= "" then text = text .. " (" .. score .. ")" end
    local quality = Evaluation.qualityAt(self.evals, self.game, ply)
    if quality then text = Evaluation.QUALITY_TEXT[quality] .. " · " .. text end
    if self:result() and not self.view then
        local c = Evaluation.summary(self.evals, self.game)
        text = text .. T(_("\nWhite: %1 inaccuracies, %2 mistakes, %3 blunders. Black: %4, %5, %6."),
            c.w[1], c.w[2], c.w[3], c.b[1], c.b[2], c.b[3])
    end
    return text
end

function GameScreen:fullText(first)
    local moves = #self.game.sans > 0 and Pgn.moveText(self.game, first, self.view) or _("No moves yet")
    local info = self:infoText()
    return info and (info .. "\n" .. moves) or moves
end

function GameScreen:updateText()
    if not self.moves_container then return end
    local first = 1
    if self.view then
        -- Keep the reviewed move visible.
        first = math.max(1, self.view - 12 - (self.view - 12) % 2 + 1)
    end
    local widget
    repeat
        if widget then widget:free() end
        widget = TextBoxWidget:new{ text = self:fullText(first), face = self.moves_face, width = self.moves_width }
        first = first + 2
    until widget:getSize().h <= self.moves_height or first > #self.game.sans
    self.moves_container[1] = widget
    self.moves_spacer.width = math.max(0, self.moves_height - widget:getSize().h)
    UIManager:setDirty(self, function() return "ui", self.text_area.dimen end)
end

function GameScreen:refreshAll()
    self.title_bar:setSubTitle(self:statusText(), true)
    if self.clock_bar then self:updateClockBar(false) end
    self:updateText()
    UIManager:setDirty(self, function() return "ui", self.dimen end)
end

--- Layout -----------------------------------------------------------------------

function GameScreen:buttonRows()
    local b = {}
    local function add(text, cb, enabled)
        b[#b + 1] = { text = text, callback = cb, enabled = enabled ~= false }
    end
    if self.view then
        add("⏮", function() self:review(0) end)
        add("◀", function() self:review(self.view - 1) end)
        add("▶", function() self:review(self.view + 1) end)
        add("⏭", function() self:review(#self.game.moves) end)
        add(_("Done"), function() self:review(nil) end)
    elseif self:result() then
        add(_("New game"), function() self:showNewGame() end)
        add(_("Undo"), function() self:undo() end)
        add(_("Review"), function() self:review(0) end)
        add(_("Flip"), function() self:flip() end)
    else
        add(_("New game"), function() self:showNewGame() end)
        add(_("Undo"), function() self:undo() end, #self.game.moves > 0)
        if self.hints and self.player ~= "both" then add(_("Hint"), function() self:showHint() end) end
        add(_("Type"), function() self:typeMove() end)
        add(_("Flip"), function() self:flip() end)
    end
    return b
end

function GameScreen:rebuildButtons()
    if not self.button_holder then return end
    local row = self:buttonRows()
    local rows = self.landscape and {} or { row }
    if self.landscape then for _i, b in ipairs(row) do rows[#rows + 1] = { b } end end
    local key = #row .. (row[1] and row[1].text or "") .. (row[3] and row[3].text or "") .. (row[2] and tostring(row[2].enabled) or "")
    if key == self.button_key then return end
    self.button_key = key
    if self.button_table then self.button_table:free() end
    self.button_table = ButtonTable:new{ width = self.buttons_w, buttons = rows, show_parent = self }
    self.button_holder[1] = self.button_table
end

function GameScreen:layout()
    local w, h = Screen:getWidth(), Screen:getHeight()
    self.dimen = Geom:new{ x = 0, y = 0, w = w, h = h }
    self.landscape = w > h
    self.title_bar = TitleBar:new{
        width = w, fullscreen = true, with_bottom_line = true,
        title = _("Square Chess"), subtitle = " ",
        left_icon = "appbar.menu",
        left_icon_tap_callback = function() self:showMenu() end,
        close_callback = function() self:onClose() end,
        show_parent = self,
    }
    local pad = Size.padding.large
    self.moves_face = UI.face(math.floor(math.min(w, h) / 50))
    local line_h = self.moves_face.size * 1.5
    self.buttons_w = (self.landscape and math.floor(w * 0.34) or w) - 2 * pad
    self.button_key = nil
    self.button_holder = CenterContainer:new{ dimen = Geom:new{ w = self.buttons_w + 2 * pad, h = 1 } }
    self:rebuildButtons()
    local buttons_h = self.button_table:getSize().h
    -- In landscape there are five stacked buttons at most.
    if self.landscape then buttons_h = buttons_h / #self.button_table.buttons * 5 end
    self.button_holder.dimen.h = buttons_h
    local clock_h = (self.clock or self.time_control > 1) and math.floor(line_h * 1.6) or 0
    local free_h = h - self.title_bar:getHeight()
    local side
    if self.landscape then
        side = math.min(free_h, w - self.buttons_w - 2 * pad)
        self.moves_width = w - side - 2 * pad
        self.moves_height = math.max(line_h, free_h - buttons_h - clock_h - 4 * pad)
    else
        local rest = buttons_h + clock_h + 3 * pad
        self.moves_width = w - 2 * pad
        side = math.min(w, free_h - math.floor(line_h * 2) - rest)
        self.moves_height = free_h - Board.fittedSize(side) - rest
    end
    if not (self.board and self.board.size == Board.fittedSize(side)) then
        if self.board then self.board:free() end
        self.board = Board:new{
            size = side, plugin_dir = self.plugin_dir, pos = self.game:position(), flipped = self.flipped,
            show_parent = self,
            on_move = function(m) self:playerMove(m) end,
            on_promotion = UI.promotion,
        }
    end
    self.clock_bar = nil
    local clock_widget = VerticalSpan:new{ width = 0 }
    if clock_h > 0 then
        self.clock = self.clock or self:newClock()
        self.clock_bar = UI.ClockBar:new{ width = self.landscape and (w - side) or w, height = clock_h }
        clock_widget = InputContainer:new{ dimen = Geom:new{ w = self.clock_bar.width, h = clock_h }, self.clock_bar }
        -- Tapping the clock pauses or resumes it.
        clock_widget.ges_events = { TapClock = { GestureRange:new{ ges = "tap", range = function() return self.clock_bar.dimen end } } }
        clock_widget.onTapClock = function() self:toggleClockPause(); return true end
    end
    self.moves_container = FrameContainer:new{ bordersize = 0, padding = pad, padding_bottom = 0,
        TextBoxWidget:new{ text = "", face = self.moves_face, width = self.moves_width } }
    self.moves_spacer = VerticalSpan:new{ width = 0 }
    self.text_area = VerticalGroup:new{ align = "left", self.moves_container, self.moves_spacer }
    local body
    if self.landscape then
        body = HorizontalGroup:new{
            align = "top",
            CenterContainer:new{ dimen = Geom:new{ w = side, h = free_h }, self.board },
            VerticalGroup:new{ align = "left", clock_widget, self.text_area, VerticalSpan:new{ width = pad }, self.button_holder },
        }
    else
        body = VerticalGroup:new{
            CenterContainer:new{ dimen = Geom:new{ w = w, h = Board.fittedSize(side) }, self.board },
            clock_widget,
            self.text_area,
            VerticalSpan:new{ width = pad },
            self.button_holder,
        }
    end
    self[1] = FrameContainer:new{
        width = w, height = h, bordersize = 0, padding = 0, margin = 0,
        background = Blitbuffer.COLOR_WHITE,
        VerticalGroup:new{ align = "left", self.title_bar, body },
    }
end

function GameScreen:newClock()
    local preset = Clock.PRESETS[self.time_control]
    if not preset or not preset.base then return nil end
    return Clock.new(preset.base, preset.inc)
end

function GameScreen:relayout()
    self:layout()
    self:showCurrent()
    UIManager:setDirty(self, "full")
end

function GameScreen:onScreenResize() self:relayout() end
function GameScreen:onSetRotationMode() self:relayout() end

--- Actions ------------------------------------------------------------------------

function GameScreen:playerMove(m)
    if self:result() or self:computerToMove() or self.view then return end
    self.game:play(m)
    self:moved()
end

function GameScreen:typeMove()
    UI.typeMove(_("Type a move"), function(text)
        if self:result() or self:computerToMove() then return _("It is not your move.") end
        local m = self.game:position():parseMove(text)
        if not m then return T(_("“%1” is not a legal move here."), text) end
        self:playerMove(m)
        return nil
    end)
end

function GameScreen:showHint()
    if self:result() or self:computerToMove() then return end
    if not self.engine then return UI.info(self.engine_error or _("Engine unavailable")) end
    self.message = _("Looking for a good move…")
    self:updateText()
    local pos = self.game:position()
    self:enqueue(function(e) return e:analyse(self.game.initialFen, self.game.moves, HINT_TIME_MS) end,
        function(_s, value)
            self.message = nil
            local m = value.best and pos:parseMove(value.best)
            if not m then return self:updateText() end
            self.board.hint = value.best
            self.hint_text = T(_("Hint: %1"), pos:san(m))
            self:showCurrent()
        end)
end

function GameScreen:flip()
    self.flipped = not self.flipped
    self:showCurrent()
end

function GameScreen:undo()
    self:invalidate()
    self.hint_text = nil
    if self.ended then
        self.ended = nil
    elseif #self.game.moves == 0 then
        return
    else
        self.game:undo()
        if self.player ~= "both" and self.game:position().turn ~= self.player and #self.game.moves > 0 then
            self.game:undo()
        end
    end
    for ply in pairs(self.evals) do if ply > #self.game.moves then self.evals[ply] = nil end end
    if self.clock and self.clock.phase == "finished" then self.clock.phase = "paused" end
    self:afterChange(true)
end

--- Enters review at `ply`, moves within it, or leaves it with nil.
function GameScreen:review(ply)
    self.hint_text, self.board.hint = nil, nil
    if ply == nil then
        self.view = nil
    else
        self.view = math.max(0, math.min(ply, #self.game.moves))
        if self.evaluation then
            self:queueEvaluation(self.view)
            if self.view > 0 then self:queueEvaluation(self.view - 1) end
        end
    end
    self:showCurrent()
end

--- Starts a new game; `fen` and `moves` load a position (from PGN, for example).
--- Starts a game between two people on this device, keeping an unfinished game if asked.
function GameScreen:startTwoPlayers()
    if self.player ~= "both" and not self.ended and #self.game.moves > 0 then
        UIManager:show(ConfirmBox:new{
            text = _("Start a two-player game? The game against the computer will be lost."),
            ok_text = _("Two players"),
            ok_callback = function() self:startGame("both") end,
        })
    else
        self:startGame("both")
    end
end

function GameScreen:startGame(player, game, ended, tags)
    self:invalidate()
    self.hint_text = nil
    self.player, self.ended, self.view, self.pgn_tags = player, ended, nil, tags
    self.game = game or Chess.newGame()
    self.evals = {}
    self.flipped = player == "b"
    self.clock = self:newClock()
    self:relayout()
    self:afterChange(true)
end

function GameScreen:showNewGame()
    local dialog
    local function start(player)
        UIManager:close(dialog)
        if player == "random" then player = math.random(2) == 1 and "w" or "b" end
        self:startGame(player)
    end
    local preset = Clock.PRESETS[self.time_control].label
    dialog = ButtonDialog:new{
        title = T(_("New game · Level %1 · %2"), self.level, preset),
        buttons = {
            { { text = _("Play White"), callback = function() start("w") end },
              { text = _("Play Black"), callback = function() start("b") end },
              { text = _("Random"), callback = function() start("random") end } },
            { { text = _("Two players on this device"), callback = function() start("both") end } },
            { { text = T(_("Level %1…"), self.level), callback = function() UIManager:close(dialog); self:showLevels(function() self:showNewGame() end) end },
              { text = T(_("Time: %1…"), preset), callback = function() UIManager:close(dialog); self:showTimeControls() end } },
        },
    }
    UIManager:show(dialog)
end

function GameScreen:showTimeControls()
    local dialog
    local rows, row = {}, {}
    for i, p in ipairs(Clock.PRESETS) do
        row[#row + 1] = { text = (i == self.time_control and "✓ " or "") .. p.label, callback = function()
            UIManager:close(dialog)
            self.time_control = i
            self:save()
            self:showNewGame()
        end }
        if #row == 2 then rows[#rows + 1] = row; row = {} end
    end
    if #row > 0 then rows[#rows + 1] = row end
    dialog = ButtonDialog:new{ title = _("Time control (minutes + seconds per move)"), buttons = rows }
    UIManager:show(dialog)
end

function GameScreen:showLevels(after)
    local dialog
    local buttons = {}
    for i, l in ipairs(Engine.LEVELS) do
        buttons[#buttons + 1] = { {
            text = T(_("Level %1 · %2"), i, l.text) .. (i == self.level and "  ✓" or ""),
            align = "left",
            callback = function()
                UIManager:close(dialog)
                self.level = i
                self:save()
                self:refreshAll()
                if after then after() end
            end,
        } }
    end
    dialog = ButtonDialog:new{ title = _("Computer strength"), buttons = buttons }
    UIManager:show(dialog)
end

function GameScreen:showMenu()
    local dialog
    local function item(text, cb) return { { text = text, align = "left", callback = function() UIManager:close(dialog); cb() end } } end
    local buttons = {}
    local claim = not self:result() and not self.view and self.game:claimableDraw()
    if claim then
        buttons[#buttons + 1] = item(T(_("Claim draw (%1)"), claim), function()
            self:invalidate()
            self.ended = { result = "1/2-1/2", reason = claim }
            self:afterChange()
        end)
    end
    if not self:result() and not self.view and #self.game.moves > 0 then
        buttons[#buttons + 1] = item(self.player == "both" and _("End game…") or _("Resign"), function() self:confirmResign() end)
    end
    if self.clock and (self.clock.phase == "running" or self.clock.phase == "paused") then
        buttons[#buttons + 1] = item(self.clock.phase == "running" and _("Pause clock") or _("Resume clock"),
            function() self:toggleClockPause() end)
    end
    if self.view then
        buttons[#buttons + 1] = item(_("Continue playing from this position"), function() self:continueFromView() end)
    elseif #self.game.moves > 0 then
        buttons[#buttons + 1] = item(_("Review game"), function() self:review(#self.game.moves) end)
    end
    buttons[#buttons + 1] = item(T(_("Computer level: %1…"), self.level), function() self:showLevels() end)
    buttons[#buttons + 1] = item((self.hints and "✓ " or "  ") .. _("Allow hints"), function()
        self.hints = not self.hints; self.button_key = nil; self:save(); self:showCurrent()
    end)
    buttons[#buttons + 1] = item((self.evaluation and "✓ " or "  ") .. _("Show evaluation (uses more battery)"), function()
        self.evaluation = not self.evaluation
        self:save()
        if self.evaluation then self:queueEvaluation(self.view or #self.game.moves) end
        self:refreshAll()
    end)
    buttons[#buttons + 1] = item(_("Open PGN file…"), function() self:importPgn() end)
    buttons[#buttons + 1] = item(_("Save game as PGN"), function() self:exportPgn() end)
    buttons[#buttons + 1] = item(_("Puzzles"), function() self:openPuzzles() end)
    buttons[#buttons + 1] = item(_("Chess clock"), function() self:openClock() end)
    dialog = ButtonDialog:new{ title = _("Square Chess"), buttons = buttons }
    UIManager:show(dialog)
end

function GameScreen:confirmResign()
    if self.player == "both" then
        local dialog
        local function finish(result, reason)
            UIManager:close(dialog)
            self:invalidate()
            self.ended = { result = result, reason = reason }
            self:afterChange()
        end
        dialog = ButtonDialog:new{ title = _("End this game"), buttons = {
            { { text = _("White resigns"), callback = function() finish("0-1", _("White resigned")) end },
              { text = _("Black resigns"), callback = function() finish("1-0", _("Black resigned")) end } },
            { { text = _("Draw agreed"), callback = function() finish("1/2-1/2", _("Draw agreed")) end } },
        } }
        return UIManager:show(dialog)
    end
    UIManager:show(ConfirmBox:new{ text = _("Resign this game?"), ok_text = _("Resign"), ok_callback = function()
        self:invalidate()
        self.ended = { result = self.player == "w" and "0-1" or "1-0", reason = _("Resigned") }
        self:afterChange()
    end })
end

function GameScreen:continueFromView()
    local keep = self.view
    while #self.game.moves > keep do self.game:undo() end
    for ply in pairs(self.evals) do if ply > keep then self.evals[ply] = nil end end
    self.view, self.ended = nil, nil
    self.player = "both"
    self:invalidate()
    self:afterChange(true)
end

function GameScreen:openPuzzles()
    local PuzzleScreen = require("puzzlescreen")
    UIManager:show(PuzzleScreen:new{ plugin_dir = self.plugin_dir })
end

function GameScreen:openClock()
    local ClockScreen = require("clockscreen")
    UIManager:show(ClockScreen:new{ plugin_dir = self.plugin_dir })
end

--- PGN --------------------------------------------------------------------------

function GameScreen:homeDir()
    return (G_reader_settings and G_reader_settings:readSetting("home_dir")) or Device.home_dir or DataStorage:getDataDir()
end

function GameScreen:pgnTags()
    local result = self:result() or "*"
    local engine = T("Fairy-Stockfish (Level %1)", self.level)
    local tags = { Site = "Square Chess for KOReader", Result = result }
    if self.player == "w" then tags.White, tags.Black = "Player", engine
    elseif self.player == "b" then tags.White, tags.Black = engine, "Player" end
    for k, v in pairs(self.pgn_tags or {}) do tags[k] = v end
    local preset = Clock.PRESETS[self.time_control]
    if preset and preset.base then tags.TimeControl = (preset.base / 1000) .. "+" .. (preset.inc / 1000) end
    return tags, result
end

function GameScreen:exportPgn()
    local dir = self:homeDir() .. "/Square Chess"
    require("libs/libkoreader-lfs").mkdir(dir)
    local path = dir .. "/game-" .. os.date("%Y%m%d-%H%M%S") .. ".pgn"
    local f = io.open(path, "w")
    if not f then return UI.info(T(_("Could not write %1"), path)) end
    local tags, result = self:pgnTags()
    f:write(Pgn.export(self.game, tags, result)); f:close()
    UI.info(T(_("Saved to %1"), path))
end

function GameScreen:importPgn()
    local PathChooser = require("ui/widget/pathchooser")
    UIManager:show(PathChooser:new{
        title = _("Open a PGN file"),
        select_directory = false, select_file = true,
        file_filter = function(name) return name:lower():match("%.pgn$") ~= nil end,
        path = self:homeDir(),
        onConfirm = function(path) self:loadPgnFile(path) end,
    })
end

function GameScreen:loadPgnFile(path)
    local f = io.open(path, "r")
    if not f then return UI.info(T(_("Could not open %1"), path)) end
    local text = f:read("*a"); f:close()
    local games = Pgn.parse(text)
    if #games == 0 then return UI.info(_("No games found in this file.")) end
    if #games == 1 then return self:loadParsed(games[1]) end
    local Menu = require("ui/widget/menu")
    local menu
    local items = {}
    for i, g in ipairs(games) do
        items[i] = { text = i .. ". " .. Pgn.describe(g), callback = function()
            UIManager:close(menu)
            self:loadParsed(g)
        end }
    end
    menu = Menu:new{ title = T(_("%1 games"), #games), item_table = items, is_popout = false,
        covers_fullscreen = true, width = Screen:getWidth(), height = Screen:getHeight() }
    UIManager:show(menu)
end

function GameScreen:loadParsed(parsed)
    local game, problem, partial = Pgn.toGame(parsed)
    if not game then
        if not partial or #partial.moves == 0 then return UI.info(T(_("Could not read this game: %1"), problem)) end
        UI.info(T(_("Loaded up to the error: %1"), problem))
        game = partial
    end
    local ended
    if parsed.result ~= "*" and not game:result() and not problem then
        ended = { result = parsed.result, reason = _("Result from PGN") }
    end
    local tags = {}
    for k, v in pairs(parsed.tags) do
        if k ~= "Result" and k ~= "FEN" and k ~= "SetUp" then tags[k] = v end
    end
    self:startGame("both", game, ended, tags)
    self:review(0)
end

--- Closing ----------------------------------------------------------------------

function GameScreen:onClose()
    UIManager:close(self)
    return true
end

function GameScreen:onCloseWidget()
    self.closed = true
    self:save()
    UIManager:unschedule(self.tick_fn)
    if self.engine then self.engine:stop(); self.engine = nil end
    if self.board then self.board:free() end
    UIManager:setDirty(nil, "full")
end

return GameScreen
