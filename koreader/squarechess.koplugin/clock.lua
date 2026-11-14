--[[--
Chess clock state, ported from the Android app's ClockDomain.kt.

Every change takes a monotonic timestamp in milliseconds; screen ticks never
drive time, they only redraw. Values are plain tables so they can be saved.
--]]--

local Clock = {}

Clock.PRESETS = {
    { label = "Untimed" },
    { label = "1+0", base = 60000, inc = 0 },
    { label = "3+0", base = 180000, inc = 0 },
    { label = "3+2", base = 180000, inc = 2000 },
    { label = "5+0", base = 300000, inc = 0 },
    { label = "5+3", base = 300000, inc = 3000 },
    { label = "10+0", base = 600000, inc = 0 },
    { label = "10+5", base = 600000, inc = 5000 },
    { label = "15+10", base = 900000, inc = 10000 },
    { label = "30+0", base = 1800000, inc = 0 },
}

local function other(side) return side == "w" and "b" or "w" end

--- phase: "ready", "running", "paused", "flagged" or "finished".
function Clock.new(base, inc, delay)
    return { base = base, inc = inc or 0, delay = delay or 0, w = base, b = base,
        active = "w", phase = "ready", anchor = 0, delay_left = delay or 0 }
end

local function copy(c)
    local n = {}
    for k, v in pairs(c) do n[k] = v end
    return n
end

--- Charges the running side for time since the anchor.
function Clock.settled(c, now)
    if c.phase ~= "running" then return c end
    local n = copy(c)
    local elapsed = math.max(0, now - c.anchor)
    local delay_used = math.min(c.delay_left, elapsed)
    n.delay_left = c.delay_left - delay_used
    local left = math.max(0, c[c.active] - (elapsed - delay_used))
    n[c.active] = left
    n.anchor = now
    if left == 0 then n.phase = "flagged" end
    return n
end

--- Starts the clock with `active` to move (White unless given).
function Clock.start(c, now, active)
    if c.phase ~= "ready" then return c end
    local n = copy(c)
    n.phase, n.anchor, n.active = "running", now, active or c.active
    return n
end

--- The side `mover` finished its move: add its increment and start the other side.
function Clock.switch(c, mover, now)
    if c.phase ~= "running" or c.active ~= mover then return c end
    local n = Clock.settled(c, now)
    if n.phase == "flagged" then return n end
    n[mover] = n[mover] + c.inc
    n.active, n.anchor, n.delay_left = other(mover), now, c.delay
    return n
end

function Clock.pause(c, now)
    local n = Clock.settled(c, now)
    if n.phase == "running" then n = copy(n); n.phase = "paused" end
    return n
end

function Clock.resume(c, now)
    if c.phase ~= "paused" then return c end
    local n = copy(c)
    n.phase, n.anchor = "running", now
    return n
end

function Clock.finish(c, now)
    local n = Clock.settled(c, now)
    if n.phase ~= "flagged" then n = copy(n); n.phase = "finished" end
    return n
end

function Clock.restart(c) return Clock.new(c.base, c.inc, c.delay) end

--- For saving: settle and pause, and drop the anchor (it belongs to this boot only).
function Clock.snapshot(c, now)
    local n = copy(Clock.pause(c, now))
    n.anchor = 0
    return n
end

--- "4:05", rounding up so a clock never shows 0:00 while time remains.
function Clock.text(ms)
    local seconds = math.floor((math.max(0, ms) + 999) / 1000)
    if seconds >= 3600 then
        return string.format("%d:%02d:%02d", math.floor(seconds / 3600), math.floor((seconds % 3600) / 60), seconds % 60)
    end
    return string.format("%d:%02d", math.floor(seconds / 60), seconds % 60)
end

--- Milliseconds until the shown second changes, so redraws happen once a second at most.
function Clock.tickDelay(remaining)
    if remaining <= 0 then return 1 end
    return ((remaining - 1) % 1000) + 1
end

return Clock
