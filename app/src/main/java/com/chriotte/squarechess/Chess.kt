package com.chriotte.squarechess

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveList

const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
enum class GameMode { COMPUTER, LOCAL_TWO_PLAYER, PHYSICAL_BOARD_RECORDING, POST_GAME_REVIEW }

class ChessPosition(val initialFen: String = START_FEN, val moves: List<String> = emptyList()) {
    val board = Board().apply {
        loadFromFen(initialFen)
        for (text in moves) {
            val move = legalMoves().firstOrNull { it.toString() == text }
            require(move != null && doMove(move, true)) { "Invalid saved move: $text" }
        }
    }
    val legal: List<Move> get() = board.legalMoves()
    val san: List<String> get() = MoveList(initialFen).apply { moves.forEach { add(Move(it, boardSideForIndex(size))) } }.toSanArray().toList()
    private fun boardSideForIndex(index: Int): Side {
        val first = if (initialFen.split(" ")[1] == "w") Side.WHITE else Side.BLACK
        return if(index % 2 == 0) first else first.flip()
    }
    fun resolve(text: String): Move? {
        legal.firstOrNull { it.toString().equals(text.trim(), true) }?.let { return it }
        return runCatching {
            val list = MoveList(board.fen); list.addSanMove(text.trim()); val m=list.first()
            legal.firstOrNull { it == m }
        }.getOrNull()
    }
    fun append(move: Move) = ChessPosition(initialFen, moves + move.toString())
    fun automaticResult(): String? = when {
        board.isMated -> if(board.sideToMove == Side.WHITE) "0-1" else "1-0"
        board.isStaleMate -> "1/2-1/2"
        provenDeadMaterial() -> "1/2-1/2"
        board.isRepetition(5) || board.halfMoveCounter >= 150 -> "1/2-1/2"
        else -> null
    }
    fun automaticResultReason(): String? = when {
        board.isMated -> "Checkmate"
        board.isStaleMate -> "Stalemate"
        provenDeadMaterial() -> "Draw by insufficient material"
        board.isRepetition(5) -> "Draw by fivefold repetition"
        board.halfMoveCounter >= 150 -> "Draw by the 75-move rule"
        else -> null
    }
    fun canClaimDraw() = board.isRepetition(3) || board.halfMoveCounter >= 100
    // Do not use Chesslib's heuristic: it incorrectly draws some minor-piece endings.
    // Blocked dead positions require broader analysis and remain a release gate.
    fun provenDeadMaterial(): Boolean {
        val pieces = com.github.bhlangonijr.chesslib.Square.entries.filter { it.name != "NONE" }
            .map { it to board.getPiece(it) }.filter { it.second != Piece.NONE && it.second.pieceType.name != "KING" }
        if(pieces.isEmpty()) return true
        if(pieces.size == 1) return pieces[0].second.pieceType.name in listOf("BISHOP","KNIGHT")
        return pieces.all { it.second.pieceType.name == "BISHOP" } && pieces.map { (sq,_) -> (sq.ordinal / 8 + sq.ordinal % 8) % 2 }.distinct().size == 1
    }
    fun perft(depth: Int): Long {
        fun count(b: Board, d: Int): Long {
            if(d == 0) return 1
            var n=0L
            for(m in b.legalMoves()) { b.doMove(m); n+=count(b,d-1); b.undoMove() }
            return n
        }
        return count(board,depth)
    }
}
