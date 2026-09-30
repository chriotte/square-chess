package com.dataespresso.squarechess

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.MoveList
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** A suggested move. It belongs to one position and is never shown for another. */
data class Hint(val move: String, val positionKey: String)

/** The hint to draw, or null when it was calculated for a different position. */
fun GameUi.visibleHint(): Hint? = hint?.takeIf { it.positionKey==positionKey() && game?.result=="*" }

fun GameUi.hintsAvailable(): Boolean = game?.let { it.hintsEnabled && it.mode==GameMode.COMPUTER.name } ?: false

/** Written forms of a hint: "Nf3", "Knight g1 → f3" and a sentence for TalkBack. */
data class HintText(val san: String, val detail: String, val spoken: String)

fun hintText(position: ChessPosition, uci: String): HintText? {
    val move=position.resolve(uci) ?: return null
    val san=runCatching { MoveList(position.board.fen).apply { add(move) }.toSanArray().first() }.getOrNull() ?: uci
    val from=move.from.name.lowercase(); val to=move.to.name.lowercase()
    val piece=position.board.getPiece(move.from)
    val name=pieceName(piece)
    val castle=piece.pieceType?.name=="KING" && kotlin.math.abs(move.from.file.ordinal-move.to.file.ordinal)==2
    val promotion=move.promotion.takeIf { it!=Piece.NONE }?.let { pieceName(it) }
    val detail=when {
        castle -> "Castle ${if(move.to.file.ordinal>move.from.file.ordinal) "kingside" else "queenside"} · $from → $to"
        promotion!=null -> "Pawn $from → $to, promote to $promotion"
        else -> "$name $from → $to"
    }
    val spoken=when {
        castle -> "Hint: castle ${if(move.to.file.ordinal>move.from.file.ordinal) "kingside" else "queenside"}, $san"
        promotion!=null -> "Hint: pawn from $from to $to, promote to $promotion, $san"
        else -> "Hint: $name from $from to $to, $san"
    }
    return HintText(san,detail,spoken)
}

private fun pieceName(piece: Piece): String = when(piece.pieceType?.name) {
    "KING" -> "King"; "QUEEN" -> "Queen"; "ROOK" -> "Rook"
    "BISHOP" -> "Bishop"; "KNIGHT" -> "Knight"; else -> "Pawn"
}

/** Displayed column and row (0..7, top-left origin) of [square]; the inverse of [squareAtCell]. */
fun cellOf(square: Square, flipped: Boolean): Pair<Int,Int> {
    val file=square.file.ordinal; val rank=square.rank.ordinal
    return (if(flipped) 7-file else file) to (if(flipped) rank else 7-rank)
}

/** The square shown at displayed [col] and [row]. ChessBoard and HintOverlay both use this mapping. */
fun squareAtCell(col: Int, row: Int, flipped: Boolean): Square {
    val file=if(flipped) 7-col else col; val rank=if(flipped) row else 7-row
    return Square.squareAt(rank*8+file)
}

private val HintOuter=Color(0xFF101010)
private val HintInner=Color(0xFFFFFFFF)

/**
 * Draws [move] over the board: an outlined source square, a ring on the target and an arrow
 * between them. Each shape is a dark stroke under a light one, so it reads on light squares,
 * dark squares, pieces and in grayscale. It has no pointer input, so taps and drags reach the board.
 */
@Composable fun HintOverlay(move: String, flipped: Boolean, modifier: Modifier = Modifier) {
    val from=runCatching { Square.fromValue(move.substring(0,2).uppercase()) }.getOrNull() ?: return
    val to=runCatching { Square.fromValue(move.substring(2,4).uppercase()) }.getOrNull() ?: return
    Canvas(modifier) {
        val cell=size.width/8f
        fun centre(square: Square): Offset {
            val (col,row)=cellOf(square,flipped)
            return Offset((col+0.5f)*cell,(row+0.5f)*cell)
        }
        val start=centre(from); val end=centre(to)
        // Source: a square outline just inside the cell.
        val inset=cell*0.07f
        val topLeft=Offset(start.x-cell/2+inset,start.y-cell/2+inset)
        val box=Size(cell-2*inset,cell-2*inset)
        drawRect(HintOuter,topLeft,box,style=Stroke(cell*0.13f))
        drawRect(HintInner,topLeft,box,style=Stroke(cell*0.06f))
        // Target: a ring, a different shape from the source.
        val radius=cell*0.40f
        drawCircle(HintOuter,radius,end,style=Stroke(cell*0.13f))
        drawCircle(HintInner,radius,end,style=Stroke(cell*0.06f))
        drawArrow(start,end,cell,radius)
    }
}

private fun DrawScope.drawArrow(start: Offset, end: Offset, cell: Float, targetRadius: Float) {
    val length=hypot(end.x-start.x,end.y-start.y)
    if(length<=0f) return
    val angle=atan2(end.y-start.y,end.x-start.x)
    val ux=cos(angle); val uy=sin(angle)
    // Start at the source edge; the head's tip touches the target ring.
    val tail=Offset(start.x+ux*cell*0.30f,start.y+uy*cell*0.30f)
    val tip=Offset(end.x-ux*targetRadius,end.y-uy*targetRadius)
    val headLength=cell*0.42f; val headWidth=cell*0.36f
    val base=Offset(tip.x-ux*headLength,tip.y-uy*headLength)
    fun head(grow: Float): Path {
        val px=-uy; val py=ux
        val back=Offset(base.x-ux*grow,base.y-uy*grow)
        val front=Offset(tip.x+ux*grow,tip.y+uy*grow)
        return Path().apply {
            moveTo(front.x,front.y)
            lineTo(back.x+px*(headWidth+grow),back.y+py*(headWidth+grow))
            lineTo(back.x-px*(headWidth+grow),back.y-py*(headWidth+grow))
            close()
        }
    }
    val shaftEnd=Offset(base.x+ux*cell*0.05f,base.y+uy*cell*0.05f)
    drawLine(HintOuter,tail,shaftEnd,strokeWidth=cell*0.22f,cap=StrokeCap.Round)
    drawPath(head(cell*0.07f),HintOuter)
    drawLine(HintInner,tail,shaftEnd,strokeWidth=cell*0.09f,cap=StrokeCap.Round)
    drawPath(head(0f),HintInner)
}
