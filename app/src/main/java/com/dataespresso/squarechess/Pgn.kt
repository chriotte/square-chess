package com.dataespresso.squarechess

import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PGN_RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*")

private fun pgnDate(millis: Long) = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(millis))

private fun modeEvent(mode: String) = when (mode) {
    GameMode.COMPUTER.name -> "Game against computer"
    GameMode.LOCAL_TWO_PLAYER.name -> "Over the board"
    GameMode.PHYSICAL_BOARD_RECORDING.name -> "Recorded physical game"
    else -> "Casual game"
}

fun SavedGame.toPgn(): String {
    require(result in PGN_RESULTS) { "Invalid PGN result: $result" }
    val tags = mutableListOf(
        "Event" to modeEvent(mode),
        "Site" to "Square Chess",
        "Date" to pgnDate(updated),
        "Round" to "-",
        "White" to white,
        "Black" to black,
        "Result" to result
    )
    if (initialFen != START_FEN) {
        tags += "SetUp" to "1"
        tags += "FEN" to initialFen
    }
    clockBaseMs?.let { base -> tags += "TimeControl" to "${base / 1000}+${(clockIncrementMs ?: 0) / 1000}" }
    if (mode == GameMode.COMPUTER.name) tags += "Difficulty" to difficultyLabel(level)
    if (resultReason.isNotBlank()) tags += "Termination" to resultReason
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

/** Filters for exporting the game library. */
enum class ExportModeFilter(val label: String, val mode: GameMode?) {
    ALL("All games", null),
    COMPUTER("Against computer", GameMode.COMPUTER),
    OVER_THE_BOARD("Over the board", GameMode.LOCAL_TWO_PLAYER),
    RECORDED("Recorded games", GameMode.PHYSICAL_BOARD_RECORDING)
}
enum class ExportPeriod(val label: String, val days: Int?) {
    ALL_TIME("All time", null), LAST_30_DAYS("Last 30 days", 30), LAST_YEAR("Last 12 months", 365)
}

fun selectForExport(games: List<SavedGame>, mode: ExportModeFilter, period: ExportPeriod, now: Long = System.currentTimeMillis()) =
    games.filter { game ->
        (mode.mode == null || game.mode == mode.mode.name) &&
            (period.days == null || game.updated >= now - period.days * 86_400_000L)
    }

/** One PGN file: games oldest first, separated by blank lines as the PGN standard requires. */
fun libraryPgn(games: List<SavedGame>) = games.sortedBy { it.updated }.joinToString("\n\n", postfix = "\n") { it.toPgn() }

fun exportFileName(now: Long = System.currentTimeMillis()) =
    "square-chess-games-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))}.pgn"

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
