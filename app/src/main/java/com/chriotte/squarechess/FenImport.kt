package com.chriotte.squarechess

const val MAX_FEN_FILE_SIZE_BYTES = 4_096
private const val MAX_FEN_CHARACTERS = MAX_FEN_FILE_SIZE_BYTES
private const val FEN_PIECES = "pnbrqkPNBRQK"
private const val FEN_CASTLING_RIGHTS = "KQkq"

fun normalizeFenContent(content: String): String {
    val text = content.removePrefix("\uFEFF").trim()
    require(text.isNotEmpty()) { "The selected file is empty." }
    require(text.length <= MAX_FEN_CHARACTERS) { "The FEN file is larger than 4 KB." }
    val nonEmptyLines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
    require(nonEmptyLines.size == 1) { "Choose a FEN file containing one position." }

    val fields = nonEmptyLines.single().split(Regex("\\s+"))
    require(fields.size == 6) { "A FEN position must contain six fields." }
    validatePlacement(fields[0])
    require(fields[1] == "w" || fields[1] == "b") { "The FEN side-to-move field must be w or b." }
    validateCastling(fields[2])
    validateEnPassant(fields[3], fields[1])
    require(fields[4].toIntOrNull()?.let { it >= 0 } == true) { "The FEN halfmove counter is invalid." }
    require(fields[5].toIntOrNull()?.let { it > 0 } == true) { "The FEN fullmove number is invalid." }

    val fen = fields.joinToString(" ")
    validatePosition(ChessPosition(fen),fields)
    return fen
}

private fun validatePosition(position: ChessPosition,fields: List<String>) {
    val board=position.board
    val squares=com.github.bhlangonijr.chesslib.Square.entries.filter { it.name!="NONE" }
    require(squares.filter { it.ordinal/8 in listOf(0,7) }.none { board.getPiece(it).pieceType?.name=="PAWN" }) {
        "Pawns cannot be on the first or last rank."
    }
    val previous=board.sideToMove.flip()
    require(board.squareAttackedBy(board.getKingSquare(previous),board.sideToMove)==0L) {
        "The side that just moved cannot leave its king in check."
    }
    val requirements=mapOf('K' to listOf("E1" to "WHITE_KING","H1" to "WHITE_ROOK"),
        'Q' to listOf("E1" to "WHITE_KING","A1" to "WHITE_ROOK"),
        'k' to listOf("E8" to "BLACK_KING","H8" to "BLACK_ROOK"),
        'q' to listOf("E8" to "BLACK_KING","A8" to "BLACK_ROOK"))
    fields[2].filter { it!='-' }.forEach { right ->
        require(requirements.getValue(right).all { (square,piece) ->
            board.getPiece(com.github.bhlangonijr.chesslib.Square.valueOf(square)).name==piece
        }) { "Castling rights require the king and rook on their original squares." }
    }
    if(fields[3]!="-") {
        val target=com.github.bhlangonijr.chesslib.Square.valueOf(fields[3].uppercase())
        val pawn=com.github.bhlangonijr.chesslib.Square.squareAt(target.ordinal+if(fields[1]=="w") -8 else 8)
        require(board.getPiece(target)==com.github.bhlangonijr.chesslib.Piece.NONE &&
            board.getPiece(pawn).name==if(fields[1]=="w") "BLACK_PAWN" else "WHITE_PAWN") {
            "The en-passant target must match a pawn that just advanced two squares."
        }
    }
}

private fun validatePlacement(placement: String) {
    require(placement.isNotEmpty() && !placement.startsWith("/") && !placement.endsWith("/")) {
        "FEN piece placement cannot start or end with a rank separator."
    }
    val ranks = placement.split("/")
    require(ranks.size == 8) { "FEN piece placement must contain eight ranks." }
    var whiteKings = 0
    var blackKings = 0
    ranks.forEach { rank ->
        var squares = 0
        var previousWasDigit = false
        rank.forEach { symbol ->
            when {
                symbol in '1'..'8' -> {
                    require(!previousWasDigit) { "Adjacent empty-square counts are invalid in FEN." }
                    squares += symbol.digitToInt()
                    previousWasDigit = true
                }
                symbol in FEN_PIECES -> {
                    squares++
                    previousWasDigit = false
                    if (symbol == 'K') whiteKings++
                    if (symbol == 'k') blackKings++
                }
                else -> throw IllegalArgumentException("FEN piece placement contains an invalid character.")
            }
        }
        require(squares == 8) { "Each FEN rank must describe exactly eight squares." }
    }
    require(whiteKings == 1 && blackKings == 1) { "A FEN position must contain one king of each color." }
}

private fun validateCastling(rights: String) {
    require(
        rights == "-" || (
            rights.isNotEmpty() &&
                rights.all { it in FEN_CASTLING_RIGHTS } &&
                rights.toSet().size == rights.length
            )
    ) { "The FEN castling-rights field is invalid." }
}

private fun validateEnPassant(square: String, sideToMove: String) {
    if (square == "-") return
    require(square.matches(Regex("[a-h][36]"))) { "The FEN en-passant square is invalid." }
    require(square.last() == if (sideToMove == "w") '6' else '3') {
        "The FEN en-passant rank does not match the side to move."
    }
}
