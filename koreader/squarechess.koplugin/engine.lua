--[[--
Runs Fairy-Stockfish as a child process and talks UCI to it over two pipes.

Reading is non-blocking: the caller polls (the plugin uses UIManager:scheduleIn),
so KOReader stays responsive while the engine thinks. The engine makes every move
choice itself; levels only set its Skill Level and MultiPV options, exactly as the
Android app does (Difficulty.kt).
--]]--

local ffi = require("ffi")
local C = ffi.C
require("ffi/posix_h")

-- A write to a pipe whose reader has died raises SIGPIPE, which would kill KOReader.
pcall(ffi.cdef, "typedef void (*sighandler_t)(int); sighandler_t signal(int, sighandler_t);")
pcall(function() C.signal(13, ffi.cast("sighandler_t", 1)) end) -- SIGPIPE = 13, SIG_IGN = 1

local Engine = {}
Engine.__index = Engine

-- Matches ENGINE_LEVELS in the Android app's Difficulty.kt.
Engine.LEVELS = {
    { skill = -4, multipv = 8, text = "Beginner · often misses captures" },
    { skill = -4, multipv = 4, text = "Beginner · makes clear mistakes" },
    { skill = -3, multipv = 8, text = "Casual · takes free pieces" },
    { skill = -1, multipv = 8, text = "Casual · misses some tactics" },
    { skill = 0, multipv = 8, text = "Improving player" },
    { skill = 3, multipv = 8, text = "Intermediate" },
    { skill = 6, multipv = 8, text = "Club player" },
    { skill = 10, multipv = 8, text = "Strong" },
    { skill = 15, multipv = 8, text = "Very strong" },
    { skill = 20, multipv = 1, text = "Full strength" },
}
Engine.DEFAULT_LEVEL = 3
Engine.MOVE_TIME_MS = 500

--- Picks the engine binary for this CPU from <plugin>/bin. Returns a path or nil.
function Engine.findBinary(plugin_dir)
    local machine = ""
    local p = io.popen("uname -m 2>/dev/null")
    if p then machine = (p:read("*l") or ""):lower(); p:close() end
    local candidates
    if machine:match("^arm") then
        candidates = { "fairy-armhf" }
    elseif machine == "aarch64" or machine == "arm64" then
        -- Many e-readers run a 32-bit system on a 64-bit kernel; either build may work.
        candidates = { "fairy-aarch64", "fairy-armhf" }
    elseif machine == "x86_64" or machine == "amd64" then
        candidates = { "fairy-x86_64" }
    else
        candidates = { "fairy-armhf", "fairy-aarch64", "fairy-x86_64" }
    end
    for _i, name in ipairs(candidates) do
        local path = plugin_dir .. "/bin/" .. name
        local f = io.open(path, "rb")
        if f then
            f:close()
            -- Unzipping can lose the executable bit; FAT storage ignores it anyway.
            os.execute("chmod +x '" .. path .. "' 2>/dev/null")
            return path
        end
    end
    return nil
end

function Engine.new(path)
    return setmetatable({ path = path, buffer = "", lines = {}, alive = false }, Engine)
end

function Engine:start()
    local to_child, from_child = ffi.new("int[2]"), ffi.new("int[2]")
    if C.pipe(to_child) ~= 0 then return false, "pipe failed" end
    if C.pipe(from_child) ~= 0 then
        C.close(to_child[0]); C.close(to_child[1])
        return false, "pipe failed"
    end
    local pid = C.fork()
    if pid < 0 then
        for _i, fd in ipairs({ to_child[0], to_child[1], from_child[0], from_child[1] }) do C.close(fd) end
        return false, "fork failed"
    end
    if pid == 0 then
        -- Child: wire the pipes to stdin and stdout, then become the engine.
        C.dup2(to_child[0], 0)
        C.dup2(from_child[1], 1)
        C.close(to_child[0]); C.close(to_child[1]); C.close(from_child[0]); C.close(from_child[1])
        C.execl(self.path, self.path, nil)
        C._exit(127)
    end
    C.close(to_child[0]); C.close(from_child[1])
    self.pid, self.write_fd, self.read_fd = pid, to_child[1], from_child[0]
    -- fcntl is variadic: LuaJIT would pass a plain Lua number there as a double.
    local flags = C.fcntl(self.read_fd, C.F_GETFL, ffi.cast("int", 0))
    if C.fcntl(self.read_fd, C.F_SETFL, ffi.cast("int", bit.bor(flags, C.O_NONBLOCK))) ~= 0 then
        self:stop()
        return false, "could not make the engine pipe non-blocking"
    end
    self.alive = true
    self.chunk = ffi.new("char[4096]")
    return true
end

function Engine:send(line)
    if not self.alive then return false end
    local data = line .. "\n"
    return C.write(self.write_fd, data, #data) == #data
end

--- Reads whatever the engine has written; returns false once it has exited.
function Engine:pump()
    if not self.alive then return false end
    while true do
        local n = tonumber(C.read(self.read_fd, self.chunk, 4096))
        if n > 0 then
            self.buffer = self.buffer .. ffi.string(self.chunk, n)
        elseif n == 0 then
            self.alive = false -- end of file: the engine exited
            break
        else
            break -- EAGAIN: nothing more for now
        end
    end
    for line in self.buffer:gmatch("([^\n]*)\n") do
        self.lines[#self.lines + 1] = (line:gsub("\r$", ""))
    end
    self.buffer = self.buffer:match("[^\n]*$")
    return self.alive
end

--- Removes and returns the first queued line that starts with prefix, dropping earlier lines.
function Engine:take(prefix)
    while #self.lines > 0 do
        local line = table.remove(self.lines, 1)
        if line:sub(1, #prefix) == prefix then return line end
    end
    return nil
end

function Engine:stop()
    if self.pid then
        if self.alive then self:send("quit") end
        C.close(self.write_fd); C.close(self.read_fd)
        local pid = self.pid
        self.pid, self.alive = nil, false
        -- Reap the child; give it a moment to quit, then make sure.
        local status = ffi.new("int[1]")
        for _ = 1, 20 do
            if C.waitpid(pid, status, C.WNOHANG) ~= 0 then return end
            C.poll(nil, 0, 10)
        end
        C.kill(pid, C.SIGTERM)
        C.waitpid(pid, status, 0)
    end
end

--[[--
A step-by-step UCI conversation driven by repeated calls to step().

Each request object waits for one reply line. The plugin calls step() from a
timer; tests call it in a loop.
--]]--

--- Starts the handshake. Returns a request that is done when the engine is ready.
function Engine:handshake()
    self:send("uci")
    return { engine = self, waiting = "uciok", after = function(e)
        e:send("setoption name Hash value 16")
        e:send("setoption name Threads value 1")
        e:send("isready")
        return "readyok"
    end }
end

--- Asks for a move. `level` indexes Engine.LEVELS. The result is a UCI move string.
function Engine:bestMove(initial_fen, moves, level)
    local l = Engine.LEVELS[level] or Engine.LEVELS[Engine.DEFAULT_LEVEL]
    self.lines = {} -- drop anything left over from an earlier search
    self:send("setoption name Skill Level value " .. l.skill)
    self:send("setoption name MultiPV value " .. l.multipv)
    -- Wait for isready first, so a stale bestmove can never answer this request.
    self:send("isready")
    return { engine = self, waiting = "readyok", after = function(e)
        local position = "position fen " .. initial_fen
        if #moves > 0 then position = position .. " moves " .. table.concat(moves, " ") end
        e:send(position)
        e:send("go movetime " .. Engine.MOVE_TIME_MS)
        return "bestmove"
    end }
end

--[[--
Full-strength analysis of a position, for hints and evaluation. The result is
{ best = uci, lines = { engine "info" lines } }; evaluation.lua reads the score.
--]]--
function Engine:analyse(initial_fen, moves, movetime)
    self.lines = {}
    self:send("setoption name Skill Level value 20")
    self:send("setoption name MultiPV value 1")
    self:send("isready")
    return { engine = self, waiting = "readyok", collect = {}, after = function(e)
        local position = "position fen " .. initial_fen
        if #moves > 0 then position = position .. " moves " .. table.concat(moves, " ") end
        e:send(position)
        e:send("go movetime " .. (movetime or 300))
        return "bestmove"
    end }
end

--- Advances a request. Returns "done", value | "waiting" | "failed", reason.
function Engine.step(request)
    local e = request.engine
    if not e:pump() and #e.lines == 0 then return "failed", "engine stopped" end
    local line
    if request.collect and request.waiting == "bestmove" then
        -- Keep the score lines while waiting for the move.
        while #e.lines > 0 do
            local l = table.remove(e.lines, 1)
            if l:sub(1, 8) == "bestmove" then line = l; break end
            if l:sub(1, 5) == "info " and l:find(" score ", 1, true) then request.collect[#request.collect + 1] = l end
        end
    else
        line = e:take(request.waiting)
    end
    if not line then return "waiting" end
    if request.after then
        request.waiting = request.after(e)
        request.after = nil
        return Engine.step(request)
    end
    if request.waiting == "bestmove" then
        local move = line:match("^bestmove%s+(%S+)")
        if request.collect then
            return "done", { best = (move ~= "(none)") and move or nil, lines = request.collect }
        end
        if not move or move == "(none)" then return "failed", "no move" end
        return "done", move
    end
    return "done", line
end

return Engine
