package com.dataespresso.squarechess

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Square

/** A position being set up: what stands on each square, and who moves first. */
data class EditorPosition(val pieces: Map<Square, Piece>, val whiteToMove: Boolean = true) {
    fun with(square: Square, piece: Piece?): EditorPosition =
        copy(pieces = if (piece == null || piece == Piece.NONE) pieces - square else pieces + (square to piece))

    /**
     * The position as FEN. Castling is allowed wherever king and rook still stand on their
     * starting squares; there is no en-passant square, and the counters start fresh.
     */
    fun toFen(): String {
        val placement = (7 downTo 0).joinToString("/") { rank ->
            var empty = 0
            buildString {
                for (file in 0..7) {
                    val piece = pieces[Square.squareAt(rank * 8 + file)]
                    if (piece == null) { empty++; continue }
                    if (empty > 0) { append(empty); empty = 0 }
                    append(piece.fenSymbol)
                }
                if (empty > 0) append(empty)
            }
        }
        fun has(square: Square, piece: Piece) = pieces[square] == piece
        val castling = buildString {
            if (has(Square.E1, Piece.WHITE_KING) && has(Square.H1, Piece.WHITE_ROOK)) append('K')
            if (has(Square.E1, Piece.WHITE_KING) && has(Square.A1, Piece.WHITE_ROOK)) append('Q')
            if (has(Square.E8, Piece.BLACK_KING) && has(Square.H8, Piece.BLACK_ROOK)) append('k')
            if (has(Square.E8, Piece.BLACK_KING) && has(Square.A8, Piece.BLACK_ROOK)) append('q')
        }.ifEmpty { "-" }
        return "$placement ${if (whiteToMove) "w" else "b"} $castling - 0 1"
    }

    companion object {
        val Empty = EditorPosition(emptyMap())
        val Start = fromFen(START_FEN)

        /**
         * The pieces and side to move of [fen]. Read by hand, not by Chesslib, so a board without
         * kings (half set up) reads too. Anything unreadable gives the starting position.
         */
        fun fromFen(fen: String): EditorPosition = runCatching {
            val fields = fen.trim().split(Regex("\\s+"))
            val ranks = fields[0].split('/')
            require(ranks.size == 8)
            val pieces = mutableMapOf<Square, Piece>()
            ranks.forEachIndexed { i, text ->
                var file = 0
                for (c in text) {
                    if (c.isDigit()) file += c.digitToInt()
                    else { require(file < 8); pieces[Square.squareAt((7 - i) * 8 + file)] = Piece.fromFenSymbol(c.toString()); file++ }
                }
                require(file == 8)
            }
            EditorPosition(pieces, fields.getOrNull(1) != "b")
        }.getOrElse { if (fen == START_FEN) error("Starting position unreadable") else fromFen(START_FEN) }
    }
}

/** Why a set-up position cannot be played yet, in words a player can act on. */
enum class EditorProblem(@StringRes val text: Int) {
    KINGS(R.string.editor_problem_kings),
    PAWN_ON_EDGE(R.string.editor_problem_pawns),
    WAITING_SIDE_IN_CHECK(R.string.editor_problem_check),
    GAME_OVER(R.string.editor_problem_over)
}

/** The first problem with [position], or null when it can be played. */
fun editorProblem(position: EditorPosition): EditorProblem? {
    val pieces = position.pieces
    if (pieces.values.count { it == Piece.WHITE_KING } != 1 || pieces.values.count { it == Piece.BLACK_KING } != 1) return EditorProblem.KINGS
    if (pieces.any { (sq, p) -> p.pieceType == PieceType.PAWN && sq.rank.ordinal in setOf(0, 7) }) return EditorProblem.PAWN_ON_EDGE
    val board = runCatching { Board().apply { loadFromFen(position.toFen()) } }.getOrNull() ?: return EditorProblem.KINGS
    val waiting = board.sideToMove.flip()
    if (board.squareAttackedBy(board.getKingSquare(waiting), board.sideToMove) != 0L) return EditorProblem.WAITING_SIDE_IN_CHECK
    if (board.legalMoves().isEmpty()) return EditorProblem.GAME_OVER
    return null
}

private val PALETTE_WHITE = listOf(Piece.WHITE_KING, Piece.WHITE_QUEEN, Piece.WHITE_ROOK, Piece.WHITE_BISHOP, Piece.WHITE_KNIGHT, Piece.WHITE_PAWN)
private val PALETTE_BLACK = listOf(Piece.BLACK_KING, Piece.BLACK_QUEEN, Piece.BLACK_ROOK, Piece.BLACK_BISHOP, Piece.BLACK_KNIGHT, Piece.BLACK_PAWN)

/**
 * Set up a position: choose a piece (or the eraser), tap squares to place it, choose who moves,
 * then play it against the computer or with a friend. Tapping a square that already holds the
 * chosen piece empties it, so a slip is undone with a second tap.
 */
@Composable fun PositionEditorScreen(initialFen: String, prefs: AppSettings, onBack: () -> Unit, onPlay: (String, GameMode) -> Unit) {
    val palette = LocalPalette.current
    // Kept as FEN, so the board survives rotation and the app being closed in the background.
    var fen by rememberSaveable { mutableStateOf(initialFen) }
    val current = remember(fen) { EditorPosition.fromFen(fen) }
    var toolName by rememberSaveable { mutableStateOf(Piece.WHITE_PAWN.name) }
    val tool = Piece.valueOf(toolName)
    fun update(next: EditorPosition) { fen = next.toFen() }
    val problem = editorProblem(current)

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        val wide = maxWidth > maxHeight * 1.15f
        val boardSide = if (wide) minOf(maxHeight - 64.dp, maxWidth * 0.58f) else minOf(maxWidth, maxHeight - 260.dp).coerceAtLeast(200.dp)
        val board = @Composable { EditorBoard(current, prefs, tool, Modifier.size(boardSide)) { sq ->
            update(current.with(sq, if (tool == Piece.NONE || current.pieces[sq] == tool) null else tool)) } }
        val controls = @Composable { mod: Modifier ->
            Column(mod.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PiecePalette(tool) { toolName = it.name }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = current.whiteToMove, onClick = { update(current.copy(whiteToMove = true)) },
                        label = { Text(stringResource(R.string.editor_white_moves)) })
                    FilterChip(selected = !current.whiteToMove, onClick = { update(current.copy(whiteToMove = false)) },
                        label = { Text(stringResource(R.string.editor_black_moves)) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { update(EditorPosition.Start) }) { Text(stringResource(R.string.editor_start_position)) }
                    OutlinedButton(onClick = { update(EditorPosition.Empty.copy(whiteToMove = current.whiteToMove)) }) { Text(stringResource(R.string.editor_clear)) }
                }
                Text(problem?.let { stringResource(it.text) } ?: stringResource(R.string.editor_ready), fontSize = 14.sp,
                    color = if (problem != null) MaterialTheme.colorScheme.error else palette.muted,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Button(onClick = { onPlay(current.toFen(), GameMode.COMPUTER) }, enabled = problem == null, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.play_against_computer))
                }
                OutlinedButton(onClick = { onPlay(current.toFen(), GameMode.LOCAL_TWO_PLAYER) }, enabled = problem == null, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.over_the_board))
                }
            }
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.home_back)) }
                Text(stringResource(R.string.editor_title), fontFamily = FontFamily.Serif, fontSize = 22.sp, modifier = Modifier.semantics { heading() })
            }
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { board(); controls(Modifier.weight(1f)) }
            else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                board(); controls(Modifier.fillMaxWidth())
            }
        }
    }
}

/** The editor's board: 64 buttons, each read as "e4, white pawn" like the game board. */
@Composable private fun EditorBoard(position: EditorPosition, prefs: AppSettings, tool: Piece, modifier: Modifier, onTap: (Square) -> Unit) {
    val palette = LocalPalette.current
    val res = appResources()
    val light = if (palette.eink) Color.White else prefs.lightSquare
    val dark = if (palette.eink) (if (prefs.einkGreySquares) EinkGreySquare else Color.White) else prefs.darkSquare
    val hatched = palette.eink && !prefs.einkGreySquares
    Column(modifier.then(if (palette.eink) Modifier.border(1.dp, Color.Black) else Modifier)) {
        for (rank in 7 downTo 0) Row(Modifier.weight(1f)) {
            for (file in 0..7) {
                val square = Square.squareAt(rank * 8 + file)
                val piece = position.pieces[square]
                val isLight = (rank + file) % 2 == 1
                val label = "${square.name.lowercase()}, ${res.getString(piece?.let { squarePieceName(it) } ?: R.string.square_empty)}"
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(if (isLight) light else dark)
                        .then(if (!isLight && hatched) Modifier.drawHatch() else Modifier)
                        .clickable(role = Role.Button) { onTap(square) }
                        .clearAndSetSemantics { contentDescription = label; role = Role.Button },
                    contentAlignment = Alignment.Center
                ) {
                    if (piece != null) Image(painterResource(pieceDrawable(piece)), null, Modifier.fillMaxSize(0.9f))
                }
            }
        }
    }
}

private fun Modifier.drawHatch() = this.drawBehind { hatch() }

/** The piece to place, or the eraser. */
@Composable private fun PiecePalette(tool: Piece, onPick: (Piece) -> Unit) {
    val palette = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(PALETTE_WHITE, PALETTE_BLACK).forEachIndexed { row, pieces ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pieces.forEach { piece -> PaletteButton(tool == piece, stringResource(squarePieceName(piece)), { onPick(piece) }) {
                    Image(painterResource(pieceDrawable(piece)), null, Modifier.fillMaxSize(0.85f))
                } }
                if (row == 0) PaletteButton(tool == Piece.NONE, stringResource(R.string.editor_eraser), { onPick(Piece.NONE) }) {
                    // An X for "empty this square".
                    Canvas(Modifier.fillMaxSize(0.5f)) {
                        drawLine(palette.text, Offset.Zero, Offset(size.width, size.height), 3.dp.toPx())
                        drawLine(palette.text, Offset(size.width, 0f), Offset(0f, size.height), 3.dp.toPx())
                    }
                }
            }
        }
    }
}

@Composable private fun PaletteButton(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.size(44.dp).clip(shape)
            .background(if (selected && !palette.eink) palette.accent.copy(alpha = 0.35f) else palette.card)
            .border(if (selected) (if (palette.eink) 4.dp else 2.dp) else 1.dp, if (selected) palette.accent else palette.muted.copy(alpha = 0.4f), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { content() }
}
