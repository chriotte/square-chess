package com.dataespresso.squarechess

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType

/*
 * Localized text for values that the app stores or computes in English or as data.
 * Saved games keep English names and reasons (they are also PGN tags); these functions
 * translate the known values on screen and show anything else unchanged.
 */

@Composable @ReadOnlyComposable
fun appResources(): Resources = LocalResources.current

@Composable @ReadOnlyComposable
fun levelLabel(level: Int): String = stringResource(R.string.level_label, level.coerceIn(1, ENGINE_LEVELS.size))

private val COMPUTER_NAME = Regex("""Computer \(Level (\d+)\)""")

/** "White", "Black" and "Computer (Level 3)" as saved by the app; other names are the players' own. */
fun displayName(res: Resources, name: String): String = when {
    name == "White" -> res.getString(R.string.white)
    name == "Black" -> res.getString(R.string.black)
    else -> COMPUTER_NAME.matchEntire(name)?.let { match ->
        res.getString(R.string.name_computer, res.getString(R.string.level_label, match.groupValues[1].toInt()))
    } ?: name
}

private val REASONS = mapOf(
    "Checkmate" to R.string.reason_checkmate,
    "Stalemate" to R.string.reason_stalemate,
    "Draw by insufficient material" to R.string.reason_insufficient,
    "Draw by fivefold repetition" to R.string.reason_fivefold,
    "Draw by the 75-move rule" to R.string.reason_75_moves,
    "You resigned" to R.string.reason_resigned,
    "Draw claimed" to R.string.reason_draw_claimed,
    "Result recorded by the players" to R.string.reason_players,
    "White ran out of time" to R.string.white_ran_out,
    "Black ran out of time" to R.string.black_ran_out,
    "Game finished" to R.string.reason_game_finished,
    "Imported result" to R.string.reason_imported
)

fun reasonText(res: Resources, reason: String): String = REASONS[reason]?.let(res::getString) ?: reason

@StringRes fun statusText(status: GameStatus): Int = when (status) {
    GameStatus.GAME_OVER -> R.string.status_game_over
    GameStatus.COMPUTER_THINKING -> R.string.status_thinking
    GameStatus.CLOCK_PAUSED -> R.string.clock_paused
    GameStatus.CLOCK_PAUSED_INTERRUPTED -> R.string.clock_paused_interrupted
    GameStatus.TIME_EXPIRED -> R.string.status_time_expired
    GameStatus.CHECK -> R.string.status_check
    GameStatus.COMPUTER_UNAVAILABLE -> R.string.status_unavailable
    GameStatus.YOUR_MOVE -> R.string.status_your_move
    GameStatus.COMPUTER_TO_MOVE -> R.string.status_computer_to_move
    GameStatus.WHITE_TO_MOVE -> R.string.status_white_to_move
    GameStatus.BLACK_TO_MOVE -> R.string.status_black_to_move
}

@StringRes fun clockPauseText(clock: ClockState): Int =
    if (clock.interrupted) R.string.clock_paused_interrupted else R.string.clock_paused

@StringRes fun pieceName(type: PieceType?): Int = when (type) {
    PieceType.KING -> R.string.piece_king
    PieceType.QUEEN -> R.string.piece_queen
    PieceType.ROOK -> R.string.piece_rook
    PieceType.BISHOP -> R.string.piece_bishop
    PieceType.KNIGHT -> R.string.piece_knight
    else -> R.string.piece_pawn
}

/** "white pawn" and similar, as TalkBack reads a square. */
@StringRes fun squarePieceName(piece: Piece): Int = when (piece) {
    Piece.WHITE_KING -> R.string.square_white_king
    Piece.WHITE_QUEEN -> R.string.square_white_queen
    Piece.WHITE_ROOK -> R.string.square_white_rook
    Piece.WHITE_BISHOP -> R.string.square_white_bishop
    Piece.WHITE_KNIGHT -> R.string.square_white_knight
    Piece.WHITE_PAWN -> R.string.square_white_pawn
    Piece.BLACK_KING -> R.string.square_black_king
    Piece.BLACK_QUEEN -> R.string.square_black_queen
    Piece.BLACK_ROOK -> R.string.square_black_rook
    Piece.BLACK_BISHOP -> R.string.square_black_bishop
    Piece.BLACK_KNIGHT -> R.string.square_black_knight
    Piece.BLACK_PAWN -> R.string.square_black_pawn
    else -> R.string.square_empty
}

/** Header line, detail and TalkBack sentence for a hint. */
fun hintText(res: Resources, hint: HintMove): HintText {
    val detail = when {
        hint.castle == Castle.KINGSIDE -> res.getString(R.string.hint_castle_kingside, hint.from, hint.to)
        hint.castle == Castle.QUEENSIDE -> res.getString(R.string.hint_castle_queenside, hint.from, hint.to)
        hint.promotion != null -> res.getString(R.string.hint_promotion, hint.from, hint.to, res.getString(pieceName(hint.promotion)))
        else -> res.getString(R.string.hint_move, res.getString(pieceName(hint.piece)), hint.from, hint.to)
    }
    val spoken = when {
        hint.castle == Castle.KINGSIDE -> res.getString(R.string.hint_spoken_castle_kingside, hint.san)
        hint.castle == Castle.QUEENSIDE -> res.getString(R.string.hint_spoken_castle_queenside, hint.san)
        hint.promotion != null -> res.getString(R.string.hint_spoken_promotion, hint.from, hint.to, res.getString(pieceName(hint.promotion)), hint.san)
        else -> res.getString(R.string.hint_spoken_move, res.getString(pieceName(hint.piece)), hint.from, hint.to, hint.san)
    }
    return HintText(hint.san, detail, spoken)
}

@StringRes fun qualityText(quality: MoveQuality): Int = when (quality) {
    MoveQuality.BEST -> R.string.quality_best
    MoveQuality.GOOD -> R.string.quality_good
    MoveQuality.INACCURACY -> R.string.quality_inaccuracy
    MoveQuality.MISTAKE -> R.string.quality_mistake
    MoveQuality.BLUNDER -> R.string.quality_blunder
}

/** "+1.8 · White better", "Black mates in 3" or "Checkmate". */
fun leadText(res: Resources, eval: Eval): String {
    if (eval.mate == 0) return res.getString(R.string.lead_checkmate)
    val mate = eval.mate
    val words = when (lead(eval)) {
        Lead.EQUAL -> res.getString(R.string.lead_equal)
        Lead.WHITE_SLIGHTLY -> res.getString(R.string.lead_white_slightly)
        Lead.BLACK_SLIGHTLY -> res.getString(R.string.lead_black_slightly)
        Lead.WHITE_BETTER -> res.getString(R.string.lead_white_better)
        Lead.BLACK_BETTER -> res.getString(R.string.lead_black_better)
        Lead.WHITE_WINNING -> res.getString(R.string.lead_white_winning)
        Lead.BLACK_WINNING -> res.getString(R.string.lead_black_winning)
        Lead.WHITE_MATES -> res.getQuantityString(R.plurals.lead_white_mates, mate ?: 0, mate ?: 0)
        Lead.BLACK_MATES -> res.getQuantityString(R.plurals.lead_black_mates, mate ?: 0, mate ?: 0)
    }
    val score = scoreText(eval)
    return if (score.isEmpty()) words else res.getString(R.string.lead_with_score, words, score)
}