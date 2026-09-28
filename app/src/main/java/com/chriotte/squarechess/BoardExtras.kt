package com.chriotte.squarechess

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side

// Heights of the optional rows below the board. They are shown only in space
// the board cannot use, so the board is never made smaller for them.
val REVIEW_BAR_HEIGHT = 52.dp
val MOVE_STRIP_HEIGHT = 32.dp
val CAPTURED_ROW_HEIGHT = 26.dp
val CAPTURED_GUTTER_MIN = 22.dp

private val VALUES = mapOf(PieceType.PAWN to 1, PieceType.KNIGHT to 3, PieceType.BISHOP to 3, PieceType.ROOK to 5, PieceType.QUEEN to 9)
private val ORDER = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN)

/** Pieces each side has taken, and White's material lead (negative when Black leads). */
data class Captures(val byWhite: List<Piece>, val byBlack: List<Piece>, val whiteLead: Int)

fun captures(position: ChessPosition): Captures {
    val start = ChessPosition(position.initialFen).board
    val now = position.board
    fun count(board: com.github.bhlangonijr.chesslib.Board, piece: Piece) = java.lang.Long.bitCount(board.getBitboard(piece))
    fun lost(side: Side): List<Piece> {
        // A promoted piece raises its count above the start; take it back off the pawn losses.
        var promotions = 0
        val result = mutableListOf<Piece>()
        for (type in ORDER.dropLast(1)) {
            val piece = Piece.make(side, type)
            val difference = count(start, piece) - count(now, piece)
            if (difference > 0) repeat(difference) { result += piece } else promotions -= difference
        }
        val pawn = Piece.make(side, PieceType.PAWN)
        repeat((count(start, pawn) - count(now, pawn) - promotions).coerceAtLeast(0)) { result += pawn }
        return result
    }
    fun material(side: Side) = VALUES.entries.sumOf { (type, value) -> count(now, Piece.make(side, type)) * value }
    return Captures(byWhite = lost(Side.BLACK), byBlack = lost(Side.WHITE), whiteLead = material(Side.WHITE) - material(Side.BLACK))
}

private fun describe(pieces: List<Piece>, lead: Int): String =
    if (pieces.isEmpty()) "No captures" else pieces.groupBy { it.pieceType }.entries.joinToString(", ") { (type, list) ->
        "${list.size} ${type.name.lowercase()}${if (list.size > 1) "s" else ""}"
    } + if (lead > 0) ", ahead by $lead" else ""

/** A row of pieces taken by one side, with the material lead when ahead. */
@Composable fun CapturedRow(pieces: List<Piece>, lead: Int, side: String, modifier: Modifier = Modifier) {
    Row(modifier.height(CAPTURED_ROW_HEIGHT).semantics(mergeDescendants = true) {
        contentDescription = "$side captured: ${describe(pieces, lead)}"
    }, verticalAlignment = Alignment.CenterVertically) {
        pieces.forEach { Image(painterResource(pieceDrawable(it)), null, Modifier.size(18.dp).offset(x = 0.dp)) }
        if (lead > 0) Text("+$lead", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

/** The same content stacked vertically, for the gutters beside a square board. */
@Composable fun CapturedColumn(pieces: List<Piece>, lead: Int, side: String, width: Dp, fromBottom: Boolean, modifier: Modifier = Modifier) {
    val icon = minOf(width - 4.dp, 20.dp)
    Column(modifier.width(width).semantics(mergeDescendants = true) {
        contentDescription = "$side captured: ${describe(pieces, lead)}"
    }, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (fromBottom) Arrangement.Bottom else Arrangement.Top) {
        val content = @Composable {
            pieces.forEach { Image(painterResource(pieceDrawable(it)), null, Modifier.size(icon)) }
            if (lead > 0) Text("+$lead", fontSize = 11.sp)
        }
        content()
    }
}

/** Last moves as one scrolling line; tapping opens the full move list. */
@Composable fun MoveStrip(san: List<String>, firstMoveNumber: Int, whiteFirst: Boolean, currentPly: Int?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(san.size, currentPly) { scroll.animateScrollTo(scroll.maxValue) }
    Row(modifier.height(MOVE_STRIP_HEIGHT).clickable(role = Role.Button, onClick = onOpen)
        .semantics { contentDescription = "Move list, ${san.size} moves" }
        .horizontalScroll(scroll).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (san.isEmpty()) Text("No moves yet", fontSize = 13.sp, color = Color(0xFFAFBCB4))
        san.forEachIndexed { ply, move ->
            val whiteMove = (ply % 2 == 0) == whiteFirst
            val number = firstMoveNumber + (ply + if (whiteFirst) 0 else 1) / 2
            val label = when {
                whiteMove -> "$number. $move"
                ply == 0 -> "$number… $move"
                else -> move
            }
            val current = currentPly?.let { it == ply + 1 } ?: (ply == san.lastIndex)
            Text(label, fontSize = 13.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(end = 8.dp).clearAndSetSemantics {})
        }
    }
}

/** "12… Nc6"-style text for the last move, used in the header when no strip fits. */
fun lastMoveText(san: List<String>, firstMoveNumber: Int, whiteFirst: Boolean): String? {
    if (san.isEmpty()) return null
    val ply = san.lastIndex
    val whiteMove = (ply % 2 == 0) == whiteFirst
    val number = firstMoveNumber + (ply + if (whiteFirst) 0 else 1) / 2
    return if (whiteMove) "$number. ${san.last()}" else "$number… ${san.last()}"
}

/** Review navigation below the board. The same content descriptions as the header version. */
@Composable fun ReviewBar(ply: Int, total: Int, onFirst: () -> Unit, onPrevious: () -> Unit, onMoves: () -> Unit,
                          onNext: () -> Unit, onLast: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.height(REVIEW_BAR_HEIGHT).padding(top = 4.dp), shape = RoundedCornerShape(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(enabled = ply > 0, onClick = onFirst, modifier = Modifier.semantics { contentDescription = "First position" }) { Text("|‹") }
            TextButton(enabled = ply > 0, onClick = onPrevious, modifier = Modifier.semantics { contentDescription = "Previous move" }) { Text("‹") }
            TextButton(onClick = onMoves) { Text("Moves") }
            TextButton(enabled = ply < total, onClick = onNext, modifier = Modifier.semantics { contentDescription = "Next move" }) { Text("›") }
            TextButton(enabled = ply < total, onClick = onLast, modifier = Modifier.semantics { contentDescription = "Last position" }) { Text("›|") }
        }
    }
}

/** Promotion choice with piece images; [moves] are UCI moves ending in q, r, b or n. */
@Composable fun PromotionDialog(moves: List<String>, white: Boolean, onPick: (String) -> Unit, onCancel: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel, title = { Text("Promote pawn") }, text = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf('q', 'r', 'b', 'n').mapNotNull { letter -> moves.firstOrNull { it.last() == letter }?.let { letter to it } }
                .forEach { (letter, move) ->
                    val type = when (letter) { 'q' -> PieceType.QUEEN; 'r' -> PieceType.ROOK; 'b' -> PieceType.BISHOP; else -> PieceType.KNIGHT }
                    val name = when (letter) { 'q' -> "Queen"; 'r' -> "Rook"; 'b' -> "Bishop"; else -> "Knight" }
                    Surface(onClick = { onPick(move) }, shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF2E7CF), modifier = Modifier.size(56.dp).semantics { contentDescription = name }) {
                        Image(painterResource(pieceDrawable(Piece.make(if (white) Side.WHITE else Side.BLACK, type))), null, Modifier.padding(4.dp))
                    }
                }
        }
    }, confirmButton = { TextButton(onClick = onCancel) { Text("Cancel") } })
}
