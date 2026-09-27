package com.chriotte.squarechess

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

fun GameUi.positionKey() = "${game?.id}:${position.initialFen}:${position.moves.joinToString(" ")}:${game?.result}"
fun GameUi.canEnterMove(): Boolean = game?.let {
    !busy && it.result == "*" &&
        (clock == null || clock.phase == ClockPhase.RUNNING) &&
        (it.mode != GameMode.COMPUTER.name ||
         (position.board.sideToMove == com.github.bhlangonijr.chesslib.Side.WHITE) == it.humanWhite)
} ?: false
