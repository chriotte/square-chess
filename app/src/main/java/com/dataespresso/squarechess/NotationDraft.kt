package com.dataespresso.squarechess

/** A draft belongs to one position. Submission is explicit and idempotent. */
data class NotationDraft(
    val text: String = "",
    val positionKey: String? = null,
    val submitting: Boolean = false,
    val error: String? = null
) {
    fun type(characters: String, key: String, repeated: Boolean = false): NotationDraft {
        if (repeated || submitting) return this
        val base = if (positionKey == key) text else ""
        val accepted = characters.filter { it.isLetterOrDigit() || it in "-+#=" }
        return if (accepted.isEmpty()) this else NotationDraft((base + accepted).take(16), key)
    }
    fun backspace() = if (submitting) this else copy(text=text.dropLast(1), error=null)
    fun submit(key: String): NotationDraft =
        if (text.isBlank() || submitting || key != positionKey) this else copy(submitting=true, error=null)
    fun rejected(message: String) = copy(submitting=false, error=message)
}

/**
 * A held key ("hold E for 2"): [typed] is the letter the key typed when it went down, [held]
 * its Alt character. The letter is replaced only if it is still the last character of this draft.
 */
fun NotationDraft.hold(typed: Char, held: Char?, key: String): NotationDraft =
    if (held == null || submitting || positionKey != key || text.lastOrNull()?.equals(typed, ignoreCase = true) != true) this
    else backspace().type(held.toString(), key)

/** The first of a key's Alt characters (key-map codes) that can be part of a move, or null. */
fun heldMoveCharacter(altCodes: List<Int>): Char? = altCodes
    .filter { it != 0 && it and android.view.KeyCharacterMap.COMBINING_ACCENT == 0 }
    .map { it.toChar() }
    .firstOrNull { it.isDigit() || it in "-+#=" }

fun GameUi.positionKey() = "${game?.id}:${position.initialFen}:${position.moves.joinToString(" ")}:${game?.result}"
/** Whether the game board takes moves now: the game runs, it is a human's turn and the clock (if any) runs. */
fun GameUi.boardInteractive(): Boolean {
    val g=game ?: return false
    val humanTurn=g.mode!=GameMode.COMPUTER.name || ((position.board.sideToMove==com.github.bhlangonijr.chesslib.Side.WHITE)==g.humanWhite)
    return !busy && g.result=="*" && humanTurn && (clock==null || clock.phase==ClockPhase.RUNNING)
}
fun GameUi.canEnterMove(): Boolean = game?.let {
    !busy && it.result == "*" &&
        (clock == null || clock.phase == ClockPhase.RUNNING) &&
        (it.mode != GameMode.COMPUTER.name ||
         (position.board.sideToMove == com.github.bhlangonijr.chesslib.Side.WHITE) == it.humanWhite)
} ?: false
