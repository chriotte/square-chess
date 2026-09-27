package com.chriotte.squarechess

import android.content.Intent

private val PGN_RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*")

fun SavedGame.toPgn(): String {
    require(result in PGN_RESULTS) { "Invalid PGN result: $result" }
    val tags = mutableListOf(
        "Event" to "Casual game",
        "Site" to "?",
        "Date" to "????.??.??",
        "Round" to "?",
        "White" to white,
        "Black" to black,
        "Result" to result
    )
    if (initialFen != START_FEN) {
        tags += "SetUp" to "1"
        tags += "FEN" to initialFen
    }
    val headers = tags.joinToString("\n") { (name, value) ->
        "[$name \"${value.replace("\r", " ").replace("\n", " ").replace("\\", "\\\\").replace("\"", "\\\"")}\"]"
    }

    val fenFields = initialFen.trim().split(Regex("\\s+"))
    var whiteToMove = fenFields.getOrNull(1) != "b"
    var moveNumber = fenFields.getOrNull(5)?.toIntOrNull() ?: 1
    val notation = buildList {
        ChessPosition(initialFen, moves.split(" ").filter(String::isNotBlank)).san.forEach { san ->
            if (whiteToMove) add("$moveNumber. $san")
            else {
                if (isEmpty()) add("$moveNumber...")
                add(san)
                moveNumber++
            }
            whiteToMove = !whiteToMove
        }
        add(result)
    }
    val wrappedNotation = notation.fold(mutableListOf<String>()) { lines, token ->
        if (lines.isEmpty() || lines.last().length + token.length + 1 > 80) lines.add(token)
        else lines[lines.lastIndex] = "${lines.last()} $token"
        lines
    }.joinToString("\n")
    return "$headers\n\n$wrappedNotation"
}

fun pgnShareIntent(game: SavedGame) = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_SUBJECT, "Chess game PGN")
    putExtra(Intent.EXTRA_TEXT, game.toPgn())
}

fun fenShareIntent(position: ChessPosition) = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_SUBJECT, "Chess position FEN")
    putExtra(Intent.EXTRA_TEXT, position.board.fen)
}
