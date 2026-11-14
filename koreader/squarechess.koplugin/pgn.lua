--[[--
PGN reading and writing. Pure Lua; builds on chess.lua.

Reading keeps the main line only: comments, variations, NAGs and annotation marks
are dropped, as most chess apps do when opening a game for play or review.
--]]--

local Chess = require("chess")

local Pgn = {}

local function trim(s) return (s:gsub("^%s+", ""):gsub("%s+$", "")) end

--- Removes {comments}, ; comments, (variations, nested) and $NAGs from movetext.
local function stripMovetext(text)
    text = text:gsub("%b{}", " ")
    text = text:gsub(";[^\n]*", " ")
    -- Variations nest; %b() matches balanced pairs from the outside in.
    local previous
    repeat previous = text; text = text:gsub("%b()", " ") until text == previous
    text = text:gsub("%$%d+", " ")
    return text
end

local RESULTS = { ["1-0"] = true, ["0-1"] = true, ["1/2-1/2"] = true, ["*"] = true }

--- Splits PGN text into games: { tags = {...}, sans = {...}, result = "1-0" | ... }.
function Pgn.parse(text)
    text = text:gsub("\r\n?", "\n"):gsub("^\239\187\191", "") -- CRLF and a UTF-8 BOM
    local games, current, movetext = {}, nil, {}
    local function finish()
        if current then
            local body = stripMovetext(table.concat(movetext, "\n"))
            for token in body:gmatch("%S+") do
                -- "12.e4" and "12...e5" arrive as one token; drop the number part.
                token = token:gsub("^%d+%.+", "")
                if RESULTS[token] then
                    current.result = token
                elseif token ~= "" and not token:match("^%d+%.*$") then
                    token = token:gsub("[!?]+$", "")
                    if token ~= "" then current.sans[#current.sans + 1] = token end
                end
            end
            current.result = current.result or current.tags.Result or "*"
            games[#games + 1] = current
        end
        current, movetext = nil, {}
    end
    local in_moves = false
    for line in (text .. "\n"):gmatch("([^\n]*)\n") do
        local tag, value = line:match('^%s*%[(%w+)%s+"(.-)"%s*%]%s*$')
        if tag then
            if in_moves or not current then finish(); current = { tags = {}, sans = {} } end
            in_moves = false
            current.tags[tag] = value:gsub('\\"', '"')
        elseif trim(line) ~= "" then
            if not current then current = { tags = {}, sans = {} } end
            in_moves = true
            movetext[#movetext + 1] = line
        end
    end
    finish()
    return games
end

--- Replays a parsed game. Returns a Chess.Game, or nil and a reason naming the bad move.
function Pgn.toGame(parsed)
    local fen = parsed.tags.FEN or Chess.START_FEN
    local game, err = Chess.newGame(fen)
    if not game then return nil, "bad FEN: " .. tostring(err) end
    for i, san in ipairs(parsed.sans) do
        if not game:play(san) then
            local pos = game:position()
            local number = pos.fullmove .. (pos.turn == "w" and "." or "…")
            return nil, string.format("move %d (%s %s) is not legal", i, number, san), game
        end
    end
    return game
end

--- Short description for choosing between games in a file.
function Pgn.describe(parsed)
    local t = parsed.tags
    local white, black = t.White or "?", t.Black or "?"
    local date = (t.Date and t.Date ~= "????.??.??") and (" · " .. t.Date) or ""
    return string.format("%s – %s · %s%s", white, black, parsed.result or "*", date)
end

--- Numbered move text from ply `first` (default 1): "1. e4 e5 2. Nf3", or "3… Nc6 4. Bb5".
--- `mark` (a ply) is wrapped in brackets, for showing the current move in review.
function Pgn.moveText(game, first, mark)
    first = first or 1
    local start = game.positions[1]
    local start_white = start.turn == "w"
    local parts = {}
    for i = first, #game.sans do
        local white_move = ((i % 2 == 1) == start_white)
        local number = start.fullmove + math.floor((i - 1 + (start_white and 0 or 1)) / 2)
        local san = game.sans[i]
        if i == mark then san = "[" .. san .. "]" end
        if white_move then parts[#parts + 1] = number .. ". " .. san
        elseif i == first then parts[#parts + 1] = number .. "… " .. san
        else parts[#parts + 1] = san end
    end
    return table.concat(parts, " ")
end

local TAG_ORDER = { "Event", "Site", "Date", "Round", "White", "Black", "Result" }

--- PGN for a game. `tags` adds or overrides headers; the Seven Tag Roster comes first.
function Pgn.export(game, tags, result)
    tags = tags or {}
    result = result or "*"
    local merged = { Event = "Casual game", Site = "?", Date = os.date("%Y.%m.%d"), Round = "-",
        White = "?", Black = "?", Result = result }
    for k, v in pairs(tags) do merged[k] = v end
    merged.Result = result
    if game.initialFen ~= Chess.START_FEN then merged.SetUp = "1"; merged.FEN = game.initialFen end
    local lines, seen = {}, {}
    local function add(k)
        if merged[k] ~= nil and not seen[k] then
            lines[#lines + 1] = string.format('[%s "%s"]', k, tostring(merged[k]):gsub('"', '\\"'))
            seen[k] = true
        end
    end
    for _i, k in ipairs(TAG_ORDER) do add(k) end
    local rest = {}
    for k in pairs(merged) do if not seen[k] then rest[#rest + 1] = k end end
    table.sort(rest)
    for _i, k in ipairs(rest) do add(k) end
    lines[#lines + 1] = ""
    -- Wrap movetext at 80 columns, as the PGN standard recommends.
    local words = {}
    for w in (Pgn.moveText(game) .. " " .. result):gsub("^ ", ""):gmatch("%S+") do words[#words + 1] = w end
    local line = ""
    for _i, w in ipairs(words) do
        if #line + #w + 1 > 80 then lines[#lines + 1] = line; line = w
        else line = line == "" and w or (line .. " " .. w) end
    end
    if line ~= "" then lines[#lines + 1] = line end
    return table.concat(lines, "\n") .. "\n"
end

return Pgn
