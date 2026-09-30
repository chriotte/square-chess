package com.dataespresso.squarechess

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/** Largest PGN file the app reads, so a wrong file cannot exhaust memory. */
const val MAX_PGN_IMPORT_BYTES = 8 * 1024 * 1024

data class PgnImportResult(val games: List<SavedGame>, val failures: List<String>)

/**
 * Reads PGN text with one or more games. Every move is checked by Chesslib;
 * a game with an illegal or unreadable move is reported in [PgnImportResult.failures]
 * and the other games are still imported. Comments, variations and annotations are ignored.
 */
fun parsePgnLibrary(text: String, now: Long = System.currentTimeMillis()): PgnImportResult {
    val games = mutableListOf<SavedGame>()
    val failures = mutableListOf<String>()
    splitPgnGames(text).forEachIndexed { index, (tags, movetext) ->
        val label = listOfNotNull(tags["White"], tags["Black"]).joinToString(" – ").ifBlank { "game ${index + 1}" }
        runCatching { toSavedGame(tags, movetext, now) }
            .onSuccess { games += it }
            .onFailure { failures += "Game ${index + 1} ($label): ${it.message ?: "not readable"}" }
    }
    return PgnImportResult(games, failures)
}

/** The message shown after an import. The details of an unreadable game stay English (they quote PGN). */
fun importSummaryText(res: android.content.res.Resources, imported: Int, duplicates: Int, failures: List<String>): String = buildString {
    append(if (imported == 0 && duplicates == 0 && failures.isEmpty()) res.getString(R.string.import_no_games)
        else res.getQuantityString(R.plurals.import_imported, imported, imported))
    if (duplicates > 0) append("\n").append(res.getQuantityString(R.plurals.import_skipped, duplicates, duplicates))
    if (failures.isNotEmpty()) {
        append("\n").append(res.getQuantityString(R.plurals.import_unreadable, failures.size, failures.size))
        failures.take(5).forEach { append("\n• ").append(it) }
        if (failures.size > 5) append("\n• ").append(res.getString(R.string.import_more, failures.size - 5))
    }
}

/** Games not already present: same start position, moves, players and result. */
fun withoutDuplicates(imported: List<SavedGame>, existing: List<SavedGame>): List<SavedGame> {
    fun key(g: SavedGame) = listOf(g.initialFen, g.moves, g.white, g.black, g.result)
    val seen = existing.mapTo(HashSet()) { key(it) }
    return imported.filter { seen.add(key(it)) }
}

private val TAG = Regex("""^\s*\[(\w+)\s+"((?:[^"\\]|\\.)*)"\s*]\s*$""")

private fun splitPgnGames(text: String): List<Pair<Map<String, String>, String>> {
    val games = mutableListOf<Pair<Map<String, String>, String>>()
    var tags = linkedMapOf<String, String>()
    val movetext = StringBuilder()
    fun flush() {
        if (tags.isNotEmpty() || movetext.isNotBlank()) games += tags to movetext.toString()
        tags = linkedMapOf(); movetext.setLength(0)
    }
    // Drop a UTF-8 byte-order mark (U+FEFF) at the start of the file.
    for (line in text.removePrefix(0xFEFF.toChar().toString()).lineSequence()) {
        val tag = TAG.matchEntire(line)
        if (tag != null) {
            // A tag after movetext starts the next game.
            if (movetext.isNotBlank()) flush()
            tags[tag.groupValues[1]] = tag.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\")
        } else if (!line.startsWith("%")) movetext.append(line).append('\n')
    }
    flush()
    return games
}

private val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*")

/** Removes comments, variations, annotations and move numbers; returns SAN tokens and the result. */
private fun movetextTokens(movetext: String): Pair<List<String>, String?> {
    val clean = StringBuilder()
    var depth = 0
    var i = 0
    while (i < movetext.length) {
        val c = movetext[i]
        when {
            c == '{' -> { val end = movetext.indexOf('}', i); i = if (end < 0) movetext.length else end }
            c == ';' -> { val end = movetext.indexOf('\n', i); i = if (end < 0) movetext.length else end }
            c == '(' -> depth++
            c == ')' -> if (depth > 0) depth--
            depth == 0 -> clean.append(c)
        }
        i++
    }
    var result: String? = null
    val moves = clean.split(Regex("\\s+")).mapNotNull { raw ->
        var token = raw.replace(Regex("^\\d+\\.+"), "")          // "12." or "12..." glued to a move
        if (token in RESULTS) { result = token; return@mapNotNull null }
        token = token.replace(Regex("[!?]+$"), "")                // "!", "?!" and similar
        token.takeUnless { it.isBlank() || it.startsWith("$") || it.matches(Regex("\\d+\\.*")) }
    }
    return moves to result
}

private fun toSavedGame(tags: Map<String, String>, movetext: String, now: Long): SavedGame {
    val initialFen = tags["FEN"]?.takeIf { tags["SetUp"] != "0" }?.trim() ?: START_FEN
    val (sanMoves, movetextResult) = movetextTokens(movetext)
    // Continue from each position's FEN, so reading a game is linear in its length
    // (ChessPosition(initialFen, moves) replays every move). Repetition history is
    // not needed to read moves; the saved game replays the full list when opened.
    var fen = runCatching { ChessPosition(initialFen).board.fen }.getOrElse { throw IllegalArgumentException("invalid FEN") }
    val uciMoves = ArrayList<String>(sanMoves.size)
    for ((ply, san) in sanMoves.withIndex()) {
        val step = ChessPosition(fen)
        val move = step.resolve(san) ?: throw IllegalArgumentException("move ${ply + 1} ($san) is not legal")
        uciMoves += move.toString()
        fen = step.append(move).board.fen
    }
    val position = ChessPosition(initialFen, uciMoves)
    val result = (tags["Result"] ?: movetextResult ?: "*").takeIf { it in RESULTS } ?: "*"
    val mode = when (tags["Event"]) {
        "Game against computer" -> GameMode.COMPUTER
        "Over the board" -> GameMode.LOCAL_TWO_PLAYER
        else -> GameMode.PHYSICAL_BOARD_RECORDING
    }
    val white = tags["White"]?.takeUnless { it.isBlank() || it == "?" } ?: "White"
    val black = tags["Black"]?.takeUnless { it.isBlank() || it == "?" } ?: "Black"
    val level = Regex("\\d+").find(tags["Difficulty"].orEmpty())?.value?.toIntOrNull()?.coerceIn(1, ENGINE_LEVELS.size) ?: DEFAULT_LEVEL
    val date = tags["Date"]?.let { runCatching { SimpleDateFormat("yyyy.MM.dd", Locale.US).apply { isLenient = false }.parse(it)?.time }.getOrNull() }
    return SavedGame(
        id = UUID.randomUUID().toString(),
        mode = mode.name,
        initialFen = initialFen,
        moves = position.moves.joinToString(" "),
        white = white,
        black = black,
        humanWhite = !(mode == GameMode.COMPUTER && white.startsWith("Computer")),
        level = level,
        result = result,
        resultReason = tags["Termination"].orEmpty().ifBlank { if (result != "*") "Imported result" else "" },
        updated = date ?: now
    )
}
