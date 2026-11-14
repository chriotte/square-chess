--[[--
Puzzles: 10,000 offline tactics from the Lichess puzzle database, as in the Android
app. Three ways to train: an adaptive level that follows your results, a quick set
of five, and review of the puzzles you missed (due again after 0, 3, 7 and 30 days).
--]]--

local Blitbuffer = require("ffi/blitbuffer")
local ButtonDialog = require("ui/widget/buttondialog")
local ButtonTable = require("ui/widget/buttontable")
local CenterContainer = require("ui/widget/container/centercontainer")
local DataStorage = require("datastorage")
local Device = require("device")
local FrameContainer = require("ui/widget/container/framecontainer")
local Geom = require("ui/geometry")
local HorizontalGroup = require("ui/widget/horizontalgroup")
local InputContainer = require("ui/widget/container/inputcontainer")
local LuaSettings = require("luasettings")
local Size = require("ui/size")
local TextBoxWidget = require("ui/widget/textboxwidget")
local TitleBar = require("ui/widget/titlebar")
local UIManager = require("ui/uimanager")
local VerticalGroup = require("ui/widget/verticalgroup")
local VerticalSpan = require("ui/widget/verticalspan")
local _ = require("gettext")
local Screen = Device.screen

local Board = require("board")
local Chess = require("chess")
local Puzzles = require("puzzles")
local UI = require("ui")
local T = UI.T

local REPLY_DELAY = 0.6

local PuzzleScreen = InputContainer:extend{
    plugin_dir = nil,
    covers_fullscreen = true,
}

-- The pack is read once per KOReader session, on first use.
local pack, pack_by_id

function PuzzleScreen:init()
    if not pack then pack, pack_by_id = Puzzles.load(self.plugin_dir .. "/data/puzzles-v1.tsv") end
    self.settings = LuaSettings:open(DataStorage:getSettingsDir() .. "/squarechess_puzzles.lua")
    local s = self.settings
    self.progress = s:readSetting("progress", {})
    self.level = s:readSetting("level", Puzzles.START_LEVEL)
    self.group = s:readSetting("group")
    self.mode = s:readSetting("mode", "train") -- "train", "quick" or "review"
    self.quick = s:readSetting("quick", { ids = {}, results = {} })
    self.recent = s:readSetting("recent", {})
    if Device:hasKeys() then self.key_events.Close = { { Device.input.group.Back } } end
    self:layout()
    local current = s:readSetting("current")
    local p = current and pack_by_id and pack_by_id[current.id]
    self.session = p and Puzzles.restore(p, current.ply, current.mistakes, current.hint)
    if not pack or #pack == 0 then
        self.message = _("The puzzle pack is missing from the plugin's data folder.")
        self:refreshAll()
    elseif self.session and Puzzles.state(self.session) ~= "solved" then
        self:showSession()
    else
        self:nextPuzzle()
    end
end

--- Saving -------------------------------------------------------------------------

function PuzzleScreen:save()
    local s = self.settings
    s:saveSetting("progress", self.progress)
    s:saveSetting("level", self.level)
    s:saveSetting("group", self.group)
    s:saveSetting("mode", self.mode)
    s:saveSetting("quick", self.quick)
    s:saveSetting("recent", self.recent)
    local se = self.session
    s:saveSetting("current", se and { id = se.puzzle.id, ply = se.ply, mistakes = se.mistakes, hint = se.hint } or nil)
    s:flush()
end

local function wallMs() return os.time() * 1000 end

--- Choosing puzzles ---------------------------------------------------------------

function PuzzleScreen:nextPuzzle()
    self.hint_step, self.board.hint, self.message, self.solution_running = 0, nil, nil, false
    local p
    if self.mode == "review" then
        local due = Puzzles.due(self.progress, wallMs())
        if #due == 0 then
            UI.info(_("Nothing left to review. Back to training."), 3)
            self.mode = "train"
        else
            p = pack_by_id[due[1]]
        end
    end
    if self.mode == "quick" and #self.quick.ids >= Puzzles.QUICK_SET_SIZE then
        self:quickSummary()
        self.quick = { ids = {}, results = {} }
    end
    if not p then
        local avoid = {}
        for _i, id in ipairs(self.recent) do avoid[id] = true end
        for _i, id in ipairs(self.quick.ids) do avoid[id] = true end
        p = Puzzles.choose(pack, self.level, self.progress, avoid, self.group)
    end
    if not p then
        self.session = nil
        self.message = _("No puzzles match this theme.")
        return self:refreshAll()
    end
    table.insert(self.recent, p.id)
    while #self.recent > 20 do table.remove(self.recent, 1) end
    if self.mode == "quick" then table.insert(self.quick.ids, p.id) end
    self.session = Puzzles.start(p)
    if not self.session then return self:nextPuzzle() end
    self.flipped = Puzzles.solverColor(self.session) == "b"
    self:save()
    self:showSession()
end

function PuzzleScreen:quickSummary()
    local clean = 0
    for _i, r in ipairs(self.quick.results) do if r == "first_try" then clean = clean + 1 end end
    UI.info(T(_("Quick 5 finished: %1 of %2 solved first time."), clean, #self.quick.results), 5)
end

function PuzzleScreen:showSession()
    self.flipped = Puzzles.solverColor(self.session) == "b"
    self.hint_step = self.hint_step or 0
    self:refreshAll()
end

--- Solving ------------------------------------------------------------------------

function PuzzleScreen:solverMove(m)
    local s = self.session
    if not s or Puzzles.state(s) ~= "solving" then return end
    local verdict = Puzzles.judge(s, m)
    self.board.hint, self.hint_step = nil, 0
    if verdict == "wrong" then
        self.message = _("Not the move. Try again.")
    elseif verdict == "correct" then
        self.message = _("Correct! Keep going.")
        self:refreshAll()
        UIManager:scheduleIn(REPLY_DELAY, function()
            if self.session == s and Puzzles.reply(s) then
                self.message = _("Your move.")
                self:save()
                self:refreshAll()
            end
        end)
    elseif verdict == "solved" then
        self:finished(Puzzles.firstTry(s) and "first_try" or "with_help")
    end
    self:save()
    self:refreshAll()
end

function PuzzleScreen:finished(result)
    local s = self.session
    local p = s.puzzle
    self.progress[p.id] = Puzzles.recordAttempt(self.progress[p.id], result, s.mistakes, s.hint, wallMs())
    if self.mode ~= "review" then self.level = Puzzles.nextLevel(self.level, result, p.rating) end
    if self.mode == "quick" then table.insert(self.quick.results, result) end
    if result == "first_try" then self.message = _("Solved! Well done.")
    elseif result == "with_help" then self.message = _("Solved with help. It will come back for review.")
    else self.message = _("Solution shown. It will come back for review.") end
    self:save()
end

function PuzzleScreen:hint()
    local s = self.session
    if not s or Puzzles.state(s) ~= "solving" then return end
    local expected = Puzzles.expected(s)
    s.hint = true
    self.hint_step = (self.hint_step or 0) + 1
    if self.hint_step == 1 then
        -- First the piece to move, then the whole move.
        self.board.hint = expected:sub(1, 2) .. expected:sub(1, 2)
        self.message = _("Hint: move this piece. Tap Hint again for the move.")
    else
        self.board.hint = expected
        local m = s.game:position():parseMove(expected)
        self.message = T(_("Hint: %1"), m and s.game:position():san(m) or expected)
    end
    self:save()
    self:refreshAll()
end

--- Gives up: counts as unsolved and plays the rest of the solution.
function PuzzleScreen:showSolution()
    local s = self.session
    if not s or Puzzles.state(s) == "solved" or self.solution_running then return end
    self.solution_running = true
    self:finished("abandoned")
    -- One move a second, so each can be followed on the board.
    local function step()
        if self.session ~= s or self.closed then return end
        local state = Puzzles.state(s)
        if state == "solved" then
            self.solution_running = false
            self:save()
            return self:refreshAll()
        end
        if state == "opponent" then Puzzles.reply(s) else Puzzles.judge(s, Puzzles.expected(s)) end
        self.board.hint = nil
        self:refreshAll()
        UIManager:scheduleIn(1, step)
    end
    step()
end

function PuzzleScreen:typeMove()
    UI.typeMove(_("Type your move"), function(text)
        local s = self.session
        if not s or Puzzles.state(s) ~= "solving" then return _("It is not your move.") end
        local m = s.game:position():parseMove(text)
        if not m then return T(_("“%1” is not a legal move here."), text) end
        self:solverMove(m)
        return nil
    end)
end

--- Text and layout ----------------------------------------------------------------

function PuzzleScreen:statusText()
    local s = self.session
    if not s then return _("Puzzles") end
    local state = Puzzles.state(s)
    if state == "solved" then return T(_("Solved · rating %1"), s.puzzle.rating) end
    local side = Puzzles.solverColor(s) == "w" and _("White") or _("Black")
    return T(_("%1 to move · find the best move · rating %2"), side, s.puzzle.rating)
end

function PuzzleScreen:infoText()
    local lines = {}
    if self.message then lines[#lines + 1] = self.message end
    local s = self.session
    local mode
    if self.mode == "quick" then
        mode = T(_("Quick 5 · puzzle %1 of %2"), math.max(1, #self.quick.ids), Puzzles.QUICK_SET_SIZE)
    elseif self.mode == "review" then
        mode = T(_("Review · %1 due"), #Puzzles.due(self.progress, wallMs()))
    else
        mode = T(_("Training · level %1"), self.level)
    end
    if self.group then
        for _i, g in ipairs(Puzzles.GROUPS) do if g.id == self.group then mode = mode .. " · " .. _(g.name) end end
    end
    lines[#lines + 1] = mode
    if s and Puzzles.state(s) == "solved" then
        local themes = UI.themeList(s.puzzle.themes)
        if themes ~= "" then lines[#lines + 1] = themes end
    end
    return table.concat(lines, "\n")
end

function PuzzleScreen:buttonRow()
    local s = self.session
    local solved = not s or Puzzles.state(s) == "solved"
    local row = {}
    if solved then
        row[#row + 1] = { text = _("Next puzzle"), callback = function() self:nextPuzzle() end }
        row[#row + 1] = { text = _("Retry"), enabled = s ~= nil, callback = function()
            self.session = Puzzles.start(s.puzzle); self.message = nil; self:showSession()
        end }
    else
        row[#row + 1] = { text = _("Hint"), callback = function() self:hint() end }
        row[#row + 1] = { text = _("Type"), callback = function() self:typeMove() end }
        row[#row + 1] = { text = _("Solution"), callback = function() self:showSolution() end }
        row[#row + 1] = { text = _("Skip"), callback = function() self:nextPuzzle() end }
    end
    row[#row + 1] = { text = _("Flip"), callback = function() self.flipped = not self.flipped; self:refreshAll() end }
    return row
end

function PuzzleScreen:refreshAll()
    local s = self.session
    if s then
        self.board:setPosition(s.game:position(), s.game:lastMove())
        self.board.interactive = Puzzles.state(s) == "solving" and not self.solution_running
    end
    self.board.flipped = self.flipped
    self.title_bar:setSubTitle(self:statusText(), true)
    local row = self:buttonRow()
    if self.button_table then self.button_table:free() end
    local rows = self.landscape and {} or { row }
    if self.landscape then for _i, b in ipairs(row) do rows[#rows + 1] = { b } end end
    self.button_table = ButtonTable:new{ width = self.buttons_w, buttons = rows, show_parent = self }
    self.button_holder[1] = self.button_table
    local text = TextBoxWidget:new{ text = self:infoText(), face = self.text_face, width = self.text_width }
    self.text_container[1] = text
    self.text_spacer.width = math.max(0, self.text_height - text:getSize().h)
    UIManager:setDirty(self, function() return "ui", self.dimen end)
end

function PuzzleScreen:layout()
    local w, h = Screen:getWidth(), Screen:getHeight()
    self.dimen = Geom:new{ x = 0, y = 0, w = w, h = h }
    self.landscape = w > h
    self.title_bar = TitleBar:new{
        width = w, fullscreen = true, with_bottom_line = true,
        title = _("Puzzles"), subtitle = " ",
        left_icon = "appbar.menu",
        left_icon_tap_callback = function() self:showMenu() end,
        close_callback = function() self:onClose() end,
        show_parent = self,
    }
    local pad = Size.padding.large
    self.text_face = UI.face(math.floor(math.min(w, h) / 50))
    local line_h = self.text_face.size * 1.5
    self.buttons_w = (self.landscape and math.floor(w * 0.34) or w) - 2 * pad
    local probe = ButtonTable:new{ width = self.buttons_w, buttons = { { { text = "Hint" } } }, show_parent = self }
    local buttons_h = probe:getSize().h * (self.landscape and 5 or 1)
    probe:free()
    self.button_holder = CenterContainer:new{ dimen = Geom:new{ w = self.buttons_w + 2 * pad, h = buttons_h } }
    local free_h = h - self.title_bar:getHeight()
    local side
    if self.landscape then
        side = math.min(free_h, w - self.buttons_w - 2 * pad)
        self.text_width = w - side - 2 * pad
        self.text_height = math.max(line_h, free_h - buttons_h - 4 * pad)
    else
        local rest = buttons_h + 3 * pad
        self.text_width = w - 2 * pad
        side = math.min(w, free_h - math.floor(line_h * 3) - rest)
        self.text_height = free_h - Board.fittedSize(side) - rest
    end
    if self.board then self.board:free() end
    self.board = Board:new{
        size = side, plugin_dir = self.plugin_dir, pos = Chess.new(), show_parent = self,
        on_move = function(m) self:solverMove(m) end,
        on_promotion = UI.promotion,
    }
    self.text_container = FrameContainer:new{ bordersize = 0, padding = pad, padding_bottom = 0,
        TextBoxWidget:new{ text = "", face = self.text_face, width = self.text_width } }
    self.text_spacer = VerticalSpan:new{ width = 0 }
    local body
    if self.landscape then
        body = HorizontalGroup:new{ align = "top",
            CenterContainer:new{ dimen = Geom:new{ w = side, h = free_h }, self.board },
            VerticalGroup:new{ align = "left", self.text_container, self.text_spacer, VerticalSpan:new{ width = pad }, self.button_holder } }
    else
        body = VerticalGroup:new{
            CenterContainer:new{ dimen = Geom:new{ w = w, h = Board.fittedSize(side) }, self.board },
            self.text_container, self.text_spacer, VerticalSpan:new{ width = pad }, self.button_holder }
    end
    self[1] = FrameContainer:new{ width = w, height = h, bordersize = 0, padding = 0, margin = 0,
        background = Blitbuffer.COLOR_WHITE, VerticalGroup:new{ align = "left", self.title_bar, body } }
end

function PuzzleScreen:relayout()
    self:layout()
    self:refreshAll()
    UIManager:setDirty(self, "full")
end
function PuzzleScreen:onScreenResize() self:relayout() end
function PuzzleScreen:onSetRotationMode() self:relayout() end

--- Menu ---------------------------------------------------------------------------

function PuzzleScreen:showMenu()
    local dialog
    local function item(text, cb) return { { text = text, align = "left", callback = function() UIManager:close(dialog); cb() end } } end
    local due = #Puzzles.due(self.progress, wallMs())
    local function mark(m) return self.mode == m and "✓ " or "  " end
    local buttons = {
        item(mark("train") .. _("Training: puzzles at your level"), function() self.mode = "train"; self:nextPuzzle() end),
        item(mark("quick") .. _("Quick 5: a short set of five"), function()
            self.mode = "quick"; self.quick = { ids = {}, results = {} }; self:nextPuzzle()
        end),
        item(mark("review") .. T(_("Review missed puzzles (%1 due)"), due), function() self.mode = "review"; self:nextPuzzle() end),
        item(T(_("Theme: %1…"), self:groupName()), function() self:showGroups() end),
        item(_("Progress"), function() self:showProgress() end),
    }
    dialog = ButtonDialog:new{ title = _("Puzzles"), buttons = buttons }
    UIManager:show(dialog)
end

function PuzzleScreen:groupName()
    for _i, g in ipairs(Puzzles.GROUPS) do if g.id == self.group then return _(g.name) end end
    return _("All themes")
end

function PuzzleScreen:showGroups()
    local dialog
    local buttons = { { { text = (self.group == nil and "✓ " or "") .. _("All themes"), align = "left", callback = function()
        UIManager:close(dialog); self.group = nil; self:nextPuzzle()
    end } } }
    for _i, g in ipairs(Puzzles.GROUPS) do
        buttons[#buttons + 1] = { { text = (self.group == g.id and "✓ " or "") .. _(g.name), align = "left", callback = function()
            UIManager:close(dialog); self.group = g.id; self:nextPuzzle()
        end } }
    end
    dialog = ButtonDialog:new{ title = _("Train a theme"), buttons = buttons }
    UIManager:show(dialog)
end

function PuzzleScreen:showProgress()
    local t = Puzzles.totals(self.progress, wallMs())
    UI.info(T(_("Training level: %1\nPuzzles tried: %2\nSolved first time: %3\nSolved with help: %4\nDue for review: %5"),
        self.level, t.attempted, t.first_try, t.with_help, t.due))
end

function PuzzleScreen:onClose()
    UIManager:close(self)
    return true
end

function PuzzleScreen:onCloseWidget()
    self.closed = true
    self:save()
    if self.board then self.board:free() end
    if self.button_table then self.button_table:free() end
    UIManager:setDirty(nil, "full")
end

return PuzzleScreen
