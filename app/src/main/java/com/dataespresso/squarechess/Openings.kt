package com.dataespresso.squarechess

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.bhlangonijr.chesslib.Board
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A named opening from the Lichess list (CC0), for example C60 "Ruy Lopez". */
data class Opening(val eco: String, val name: String)

/**
 * Opening names by position, from assets/openings.tsv (see scripts/openings). The table loads
 * once in the background; until then, and for positions it does not know, no name is shown.
 */
object Openings {
    var table by mutableStateOf<Map<String,Opening>?>(null)
        private set

    suspend fun load(context: Context) {
        if(table!=null) return
        table=withContext(Dispatchers.IO) {
            context.assets.open("openings.tsv").bufferedReader().useLines { lines -> parseOpenings(lines) }
        }
    }
}

fun parseOpenings(lines: Sequence<String>): Map<String,Opening> = lines.mapNotNull { line ->
    val parts=line.split('\t')
    if(parts.size==3) parts[0] to Opening(parts[1],parts[2]) else null
}.toMap()

/** Pieces, side to move and castling rights: the first three FEN fields, as in openings.tsv. */
fun openingKey(fen: String): String = fen.split(' ').take(3).joinToString(" ")

/**
 * The opening of the game after [ply] moves: the last named position on the way there, so the
 * name stays when play leaves the list. Null when no position in the game is named.
 */
fun openingAt(table: Map<String,Opening>, initialFen: String, moves: List<String>, ply: Int = moves.size): Opening? {
    val board=Board().apply { loadFromFen(initialFen) }
    var found=table[openingKey(board.fen)]
    for(text in moves.take(ply)) {
        val move=board.legalMoves().firstOrNull { it.toString()==text } ?: break
        board.doMove(move)
        table[openingKey(board.fen)]?.let { found=it }
    }
    return found
}
