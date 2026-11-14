--[[--
Chess rules: positions, legal moves, SAN and FEN, and the end-of-game rules.

Pure Lua with no KOReader dependencies, so the tests run under plain LuaJIT.
Squares are numbered 0..63 from a1 (0) to h8 (63). Pieces are FEN letters:
upper case for White, lower case for Black.

The draw rules match the Android app (Chess.kt): checkmate, stalemate, dead
material, fivefold repetition and the 75-move rule end the game automatically;
threefold repetition and the 50-move rule can be claimed.
--]]--

local Chess = {}

Chess.START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

local FILES = "abcdefgh"
local KNIGHT = { {1,2},{2,1},{2,-1},{1,-2},{-1,-2},{-2,-1},{-2,1},{-1,2} }
local KING = { {1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1} }
local ROOK_DIRS = { {1,0},{-1,0},{0,1},{0,-1} }
local BISHOP_DIRS = { {1,1},{1,-1},{-1,1},{-1,-1} }
local PROMOTIONS = { "q", "r", "b", "n" }

local function fileOf(s) return s % 8 end
local function rankOf(s) return (s - s % 8) / 8 end
local function at(f, r) if f < 0 or f > 7 or r < 0 or r > 7 then return nil end return r * 8 + f end
local function isWhite(p) return p ~= nil and p:upper() == p end
local function colorOf(p) if p == nil then return nil end return isWhite(p) and "w" or "b" end
local function own(p, color) return p ~= nil and colorOf(p) == color end
local function other(color) return color == "w" and "b" or "w" end

function Chess.squareName(s)
    return FILES:sub(fileOf(s) + 1, fileOf(s) + 1) .. (rankOf(s) + 1)
end

function Chess.squareIndex(name)
    if type(name) ~= "string" or #name ~= 2 then return nil end
    local f, r = name:byte(1) - 97, name:byte(2) - 49
    return at(f, r)
end
local squareName, squareIndex = Chess.squareName, Chess.squareIndex

--- Position ------------------------------------------------------------------

local Position = {}
Position.__index = Position
Chess.Position = Position

--- Parses a FEN string. Returns the position, or nil and a reason.
function Chess.fromFen(fen)
    if type(fen) ~= "string" then return nil, "not a string" end
    local fields = {}
    for field in fen:gmatch("%S+") do fields[#fields + 1] = field end
    if #fields < 4 then return nil, "FEN needs at least four fields" end
    local pos = setmetatable({ board = {} }, Position)
    local rank, file = 7, 0
    for c in fields[1]:gmatch(".") do
        if c == "/" then
            if file ~= 8 then return nil, "rank " .. (rank + 1) .. " has the wrong length" end
            rank, file = rank - 1, 0
        elseif c:match("%d") then
            file = file + tonumber(c)
        elseif c:match("[prnbqkPRNBQK]") then
            if file > 7 or rank < 0 then return nil, "too many squares" end
            pos.board[rank * 8 + file] = c
            file = file + 1
        else
            return nil, "unexpected character " .. c
        end
        if file > 8 then return nil, "rank " .. (rank + 1) .. " is too long" end
    end
    if rank ~= 0 or file ~= 8 then return nil, "the board needs eight full ranks" end
    if fields[2] ~= "w" and fields[2] ~= "b" then return nil, "side to move must be w or b" end
    pos.turn = fields[2]
    if not fields[3]:match("^[KQkq]+$") and fields[3] ~= "-" then return nil, "bad castling field" end
    pos.castling = {}
    for c in fields[3]:gmatch("[KQkq]") do pos.castling[c] = true end
    if fields[4] ~= "-" then
        pos.ep = squareIndex(fields[4])
        if not pos.ep then return nil, "bad en passant square" end
    end
    pos.halfmove = tonumber(fields[5] or "0") or 0
    pos.fullmove = tonumber(fields[6] or "1") or 1
    local kings = { w = 0, b = 0 }
    for s = 0, 63 do
        local p = pos.board[s]
        if p == "K" then kings.w = kings.w + 1; pos.wk = s end
        if p == "k" then kings.b = kings.b + 1; pos.bk = s end
        if (p == "P" or p == "p") and (rankOf(s) == 0 or rankOf(s) == 7) then
            return nil, "pawn on the first or last rank"
        end
    end
    if kings.w ~= 1 or kings.b ~= 1 then return nil, "each side needs exactly one king" end
    -- Drop castling rights the board cannot support, as most chess software does.
    local c = pos.castling
    if pos.board[4] ~= "K" then c.K, c.Q = nil, nil end
    if pos.board[60] ~= "k" then c.k, c.q = nil, nil end
    if pos.board[7] ~= "R" then c.K = nil end
    if pos.board[0] ~= "R" then c.Q = nil end
    if pos.board[63] ~= "r" then c.k = nil end
    if pos.board[56] ~= "r" then c.q = nil end
    if pos:isAttacked(pos:kingSquare(other(pos.turn)), pos.turn) then
        return nil, "the side not to move is in check"
    end
    return pos
end

function Chess.new() return assert(Chess.fromFen(Chess.START_FEN)) end

function Position:clone()
    local board = {}
    for s, p in pairs(self.board) do board[s] = p end
    local castling = {}
    for k, v in pairs(self.castling) do castling[k] = v end
    return setmetatable({ board = board, turn = self.turn, castling = castling, ep = self.ep,
        halfmove = self.halfmove, fullmove = self.fullmove, wk = self.wk, bk = self.bk }, Position)
end

function Position:piece(s) return self.board[s] end
function Position:kingSquare(color) return color == "w" and self.wk or self.bk end

--- True if `color` attacks square s.
function Position:isAttacked(s, color)
    local b, f, r = self.board, fileOf(s), rankOf(s)
    -- Pawns: a White pawn attacks diagonally upwards, so look one rank below.
    local pr, pawn = color == "w" and r - 1 or r + 1, color == "w" and "P" or "p"
    for _i, df in ipairs({ -1, 1 }) do
        local t = at(f + df, pr)
        if t and b[t] == pawn then return true end
    end
    local knight, king = color == "w" and "N" or "n", color == "w" and "K" or "k"
    for _i, d in ipairs(KNIGHT) do
        local t = at(f + d[1], r + d[2])
        if t and b[t] == knight then return true end
    end
    for _i, d in ipairs(KING) do
        local t = at(f + d[1], r + d[2])
        if t and b[t] == king then return true end
    end
    local rook, bishop, queen = color == "w" and "R" or "r", color == "w" and "B" or "b", color == "w" and "Q" or "q"
    for _i, dirs in ipairs({ { ROOK_DIRS, rook }, { BISHOP_DIRS, bishop } }) do
        for _i, d in ipairs(dirs[1]) do
            local tf, tr = f + d[1], r + d[2]
            local t = at(tf, tr)
            while t do
                local p = b[t]
                if p then
                    if p == dirs[2] or p == queen then return true end
                    break
                end
                tf, tr = tf + d[1], tr + d[2]
                t = at(tf, tr)
            end
        end
    end
    return false
end

function Position:inCheck() return self:isAttacked(self:kingSquare(self.turn), other(self.turn)) end

local function addMove(list, pos, from, to, flags)
    local piece = pos.board[from]
    local captured = pos.board[to]
    if flags == "ep" then captured = pos.turn == "w" and "p" or "P" end
    if (piece == "P" and rankOf(to) == 7) or (piece == "p" and rankOf(to) == 0) then
        for _i, promo in ipairs(PROMOTIONS) do
            list[#list + 1] = { from = from, to = to, piece = piece, captured = captured, promotion = promo }
        end
    else
        list[#list + 1] = { from = from, to = to, piece = piece, captured = captured, flags = flags }
    end
end

--- Moves that obey piece movement but may leave the own king in check.
function Position:pseudoMoves()
    local list, b, us = {}, self.board, self.turn
    for s = 0, 63 do
        local p = b[s]
        if own(p, us) then
            local f, r, kind = fileOf(s), rankOf(s), p:lower()
            if kind == "p" then
                local dir, startRank = us == "w" and 1 or -1, us == "w" and 1 or 6
                local one = at(f, r + dir)
                if one and not b[one] then
                    addMove(list, self, s, one)
                    local two = at(f, r + 2 * dir)
                    if r == startRank and not b[two] then addMove(list, self, s, two, "double") end
                end
                for _i, df in ipairs({ -1, 1 }) do
                    local t = at(f + df, r + dir)
                    if t then
                        if b[t] and not own(b[t], us) then addMove(list, self, s, t)
                        elseif t == self.ep and not b[t] then addMove(list, self, s, t, "ep") end
                    end
                end
            elseif kind == "n" or kind == "k" then
                for _i, d in ipairs(kind == "n" and KNIGHT or KING) do
                    local t = at(f + d[1], r + d[2])
                    if t and not own(b[t], us) then addMove(list, self, s, t) end
                end
            else
                local dirs = kind == "r" and ROOK_DIRS or kind == "b" and BISHOP_DIRS or KING
                for _i, d in ipairs(dirs) do
                    local tf, tr = f + d[1], r + d[2]
                    local t = at(tf, tr)
                    while t do
                        if own(b[t], us) then break end
                        addMove(list, self, s, t)
                        if b[t] then break end
                        tf, tr = tf + d[1], tr + d[2]
                        t = at(tf, tr)
                    end
                end
            end
        end
    end
    -- Castling: the king may not start in, pass through or land in check.
    local them = other(us)
    local function castle(right, king, rookFrom, empty, safe, to)
        if not self.castling[right] then return end
        for _i, e in ipairs(empty) do if b[e] then return end end
        for _i, t in ipairs(safe) do if self:isAttacked(t, them) then return end end
        if b[rookFrom] ~= (us == "w" and "R" or "r") then return end
        addMove(list, self, king, to, "castle")
    end
    if us == "w" and b[4] == "K" then
        castle("K", 4, 7, { 5, 6 }, { 4, 5, 6 }, 6)
        castle("Q", 4, 0, { 1, 2, 3 }, { 4, 3, 2 }, 2)
    elseif us == "b" and b[60] == "k" then
        castle("k", 60, 63, { 61, 62 }, { 60, 61, 62 }, 62)
        castle("q", 60, 56, { 57, 58, 59 }, { 60, 59, 58 }, 58)
    end
    return list
end

local CORNER_RIGHTS = { [0] = "Q", [7] = "K", [56] = "q", [63] = "k" }

--- Returns the position after move m. m must come from this position's move list.
function Position:apply(m)
    local n = self:clone()
    local b, us = n.board, self.turn
    b[m.to] = m.promotion and (us == "w" and m.promotion:upper() or m.promotion) or m.piece
    b[m.from] = nil
    if m.flags == "ep" then b[m.to + (us == "w" and -8 or 8)] = nil end
    if m.flags == "castle" then
        local rookFrom = m.to > m.from and m.to + 1 or m.to - 2
        local rookTo = m.to > m.from and m.to - 1 or m.to + 1
        b[rookTo], b[rookFrom] = b[rookFrom], nil
    end
    if m.piece == "K" then n.wk = m.to; n.castling.K, n.castling.Q = nil, nil end
    if m.piece == "k" then n.bk = m.to; n.castling.k, n.castling.q = nil, nil end
    if CORNER_RIGHTS[m.from] then n.castling[CORNER_RIGHTS[m.from]] = nil end
    if CORNER_RIGHTS[m.to] then n.castling[CORNER_RIGHTS[m.to]] = nil end
    n.ep = m.flags == "double" and (m.from + m.to) / 2 or nil
    n.halfmove = (m.piece:lower() == "p" or m.captured) and 0 or self.halfmove + 1
    if us == "b" then n.fullmove = self.fullmove + 1 end
    n.turn = other(us)
    return n
end

function Position:legalMoves()
    if self._legal then return self._legal end
    local legal = {}
    for _i, m in ipairs(self:pseudoMoves()) do
        local n = self:apply(m)
        if not n:isAttacked(n:kingSquare(self.turn), n.turn) then legal[#legal + 1] = m end
    end
    self._legal = legal
    return legal
end

--- The en passant square only when a capture there is legal, so that repetition
--- keys and exported FENs agree with other chess software.
function Position:legalEp()
    if not self.ep then return nil end
    for _i, m in ipairs(self:legalMoves()) do
        if m.flags == "ep" then return self.ep end
    end
    return nil
end

local function placement(pos)
    local rows = {}
    for r = 7, 0, -1 do
        local row, empty = {}, 0
        for f = 0, 7 do
            local p = pos.board[r * 8 + f]
            if p then
                if empty > 0 then row[#row + 1] = tostring(empty); empty = 0 end
                row[#row + 1] = p
            else
                empty = empty + 1
            end
        end
        if empty > 0 then row[#row + 1] = tostring(empty) end
        rows[#rows + 1] = table.concat(row)
    end
    return table.concat(rows, "/")
end

local function castlingField(pos)
    local c = ""
    for _i, k in ipairs({ "K", "Q", "k", "q" }) do if pos.castling[k] then c = c .. k end end
    return c == "" and "-" or c
end

--- Position identity for repetition: placement, side, castling and a legal en passant.
function Position:key()
    local ep = self:legalEp()
    return placement(self) .. " " .. self.turn .. " " .. castlingField(self) .. " " .. (ep and squareName(ep) or "-")
end

function Position:fen()
    return self:key() .. " " .. self.halfmove .. " " .. self.fullmove
end

--- Move notation -------------------------------------------------------------

function Chess.uci(m)
    return squareName(m.from) .. squareName(m.to) .. (m.promotion or "")
end

--- Standard algebraic notation, with + or # for check and mate.
function Position:san(m)
    local text
    if m.flags == "castle" then
        text = m.to > m.from and "O-O" or "O-O-O"
    else
        local kind = m.piece:upper()
        local dest = squareName(m.to)
        if kind == "P" then
            text = m.captured and (FILES:sub(fileOf(m.from) + 1, fileOf(m.from) + 1) .. "x" .. dest) or dest
            if m.promotion then text = text .. "=" .. m.promotion:upper() end
        else
            -- Disambiguate by file, then rank, then both, as the PGN standard asks.
            local sameFile, sameRank, others = false, false, false
            for _i, o in ipairs(self:legalMoves()) do
                if o.piece == m.piece and o.to == m.to and o.from ~= m.from then
                    others = true
                    if fileOf(o.from) == fileOf(m.from) then sameFile = true end
                    if rankOf(o.from) == rankOf(m.from) then sameRank = true end
                end
            end
            local from = squareName(m.from)
            local prefix = ""
            if others then
                if not sameFile then prefix = from:sub(1, 1)
                elseif not sameRank then prefix = from:sub(2, 2)
                else prefix = from end
            end
            text = kind .. prefix .. (m.captured and "x" or "") .. dest
        end
    end
    local n = self:apply(m)
    if n:inCheck() then
        text = text .. (#n:legalMoves() == 0 and "#" or "+")
    end
    return text
end

local function normalise(text)
    return (text:gsub("[+#!?x=%s]", ""):gsub("0", "O"))
end

--- Finds the legal move for SAN ("Nf3", "exd5", "O-O", "e8=Q") or UCI ("g1f3", "e7e8q").
function Position:parseMove(text)
    if type(text) ~= "string" then return nil end
    text = text:match("^%s*(.-)%s*$")
    local lower = text:lower()
    for _i, m in ipairs(self:legalMoves()) do
        if Chess.uci(m) == lower then return m end
    end
    -- SAN, ignoring check signs, capture marks and "=" (so "exd5" and "ed5" both work).
    -- A promotion without a piece ("e8") matches nothing: it is ambiguous, so refuse it.
    local want = normalise(text)
    if want == "" then return nil end
    for _i, m in ipairs(self:legalMoves()) do
        if normalise(self:san(m)) == want then return m end
    end
    return nil
end

--- End of game ---------------------------------------------------------------

--- True when neither side can ever mate: kings only, a single minor piece, or
--- bishops that all stand on one colour (the app's provenDeadMaterial).
function Position:deadMaterial()
    local others, bishopColours, minorOnly = {}, {}, true
    for s = 0, 63 do
        local p = self.board[s]
        if p and p:lower() ~= "k" then
            others[#others + 1] = p
            if p:lower() == "b" then bishopColours[(fileOf(s) + rankOf(s)) % 2] = true end
        end
    end
    if #others == 0 then return true end
    if #others == 1 then return others[1]:lower() == "b" or others[1]:lower() == "n" end
    for _i, p in ipairs(others) do if p:lower() ~= "b" then minorOnly = false end end
    local colours = 0
    for _ in pairs(bishopColours) do colours = colours + 1 end
    return minorOnly and colours == 1
end

--- Side with no pieces except the king cannot mate (timeout adjudication).
function Position:cannotMate(color)
    if self:deadMaterial() then return true end
    for s = 0, 63 do
        local p = self.board[s]
        if own(p, color) and p:lower() ~= "k" then return false end
    end
    return true
end

--- Game: a starting position and the moves played ---------------------------

local Game = {}
Game.__index = Game
Chess.Game = Game

function Chess.newGame(fen)
    local start, err = Chess.fromFen(fen or Chess.START_FEN)
    if not start then return nil, err end
    return setmetatable({ initialFen = start:fen(), positions = { start }, moves = {}, sans = {} }, Game)
end

function Game:position() return self.positions[#self.positions] end

--- Plays a move given as a move table, SAN or UCI. Returns the move, or nil.
function Game:play(move)
    local pos = self:position()
    local m = type(move) == "table" and move or pos:parseMove(move)
    if not m then return nil end
    self.sans[#self.sans + 1] = pos:san(m)
    self.moves[#self.moves + 1] = Chess.uci(m)
    self.positions[#self.positions + 1] = pos:apply(m)
    return m
end

function Game:undo()
    if #self.moves == 0 then return false end
    table.remove(self.moves); table.remove(self.sans); table.remove(self.positions)
    return true
end

function Game:repetitions()
    local key, count = self:position():key(), 0
    for _i, p in ipairs(self.positions) do if p:key() == key then count = count + 1 end end
    return count
end

function Game:lastMove() return self.moves[#self.moves] end

--- Automatic result: "1-0", "0-1" or "1/2-1/2" with a reason, or nil while play goes on.
function Game:result()
    local pos = self:position()
    if #pos:legalMoves() == 0 then
        if pos:inCheck() then return pos.turn == "w" and "0-1" or "1-0", "Checkmate" end
        return "1/2-1/2", "Stalemate"
    end
    if pos:deadMaterial() then return "1/2-1/2", "Draw by insufficient material" end
    if self:repetitions() >= 5 then return "1/2-1/2", "Draw by fivefold repetition" end
    if pos.halfmove >= 150 then return "1/2-1/2", "Draw by the 75-move rule" end
    return nil
end

--- A draw the player to move may claim: threefold repetition or the 50-move rule.
function Game:claimableDraw()
    if self:repetitions() >= 3 then return "Draw by threefold repetition" end
    if self:position().halfmove >= 100 then return "Draw by the 50-move rule" end
    return nil
end

--- Rebuilds a game from saved data; returns nil if anything no longer replays.
function Chess.restoreGame(initialFen, moves)
    local game = Chess.newGame(initialFen)
    if not game then return nil end
    for _i, m in ipairs(moves or {}) do
        if not game:play(m) then return nil end
    end
    return game
end

--- Divide-free perft, for tests.
function Position:perft(depth)
    if depth == 0 then return 1 end
    local moves = self:legalMoves()
    if depth == 1 then return #moves end
    local n = 0
    for _i, m in ipairs(moves) do n = n + self:apply(m):perft(depth - 1) end
    return n
end

return Chess
