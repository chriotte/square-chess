package com.dataespresso.squarechess

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Beside the board: a wide side space, and a board tall enough to leave room above the captured pieces. */
internal val BUDDY_GAME_GUTTER_MIN = 180.dp
internal val BUDDY_GAME_SIDE_MIN = 700.dp
/** Below the board, after the review bar, move strip and captured rows. */
internal val BUDDY_GAME_ROW_MIN = 120.dp
internal val BUDDY_GAME_LARGE_BELOW = 290.dp

/** What the buddy says during a game against the computer. */
enum class GameRemark { YOUR_MOVE, THINKING, NICE_MOVE, CHECK_GIVEN, CAPTURE, MISTAKE, COMPUTER_MOVED, IN_CHECK, HINT, WON, GOOD_GAME }

/**
 * The remark for the game as it stands. While the computer thinks, the buddy comments on the
 * player's last move (praise from the evaluation when it is on, otherwise check or capture);
 * once the computer has replied, it says what the computer played. Null outside computer games.
 */
fun gameRemark(s: GameUi): GameRemark? {
    val game = s.game ?: return null
    if (game.mode != GameMode.COMPUTER.name) return null
    if (game.result != "*") {
        val won = game.result != "1/2-1/2" && (game.result == "1-0") == game.humanWhite
        return if (won) GameRemark.WON else GameRemark.GOOD_GAME
    }
    if (s.visibleHint() != null) return GameRemark.HINT
    val moves = s.position.moves
    if (moves.isEmpty()) return if (game.humanWhite == whiteToMove(s.position)) GameRemark.YOUR_MOVE else GameRemark.THINKING
    val humanMovedLast = whiteMovedAt(s.position.initialFen, moves.size) == game.humanWhite
    if (humanMovedLast) {
        when (qualityAt(s.evals, s.position.initialFen, moves, moves.size)) {
            MoveQuality.BEST, MoveQuality.GOOD -> return GameRemark.NICE_MOVE
            MoveQuality.MISTAKE, MoveQuality.BLUNDER -> return GameRemark.MISTAKE
            else -> Unit
        }
        return when (lastMoveSound(s.position)) {
            MoveSound.CHECK -> GameRemark.CHECK_GIVEN
            MoveSound.CAPTURE -> GameRemark.CAPTURE
            else -> GameRemark.THINKING
        }
    }
    return if (s.position.board.isKingAttacked) GameRemark.IN_CHECK else GameRemark.COMPUTER_MOVED
}

private fun whiteToMove(position: ChessPosition) = position.board.sideToMove == com.github.bhlangonijr.chesslib.Side.WHITE

fun remarkMoment(remark: GameRemark): BuddyMoment = when (remark) {
    GameRemark.YOUR_MOVE, GameRemark.COMPUTER_MOVED -> BuddyMoment.HOME
    GameRemark.THINKING, GameRemark.IN_CHECK -> BuddyMoment.THINK
    GameRemark.NICE_MOVE, GameRemark.CHECK_GIVEN, GameRemark.CAPTURE -> BuddyMoment.RIGHT
    GameRemark.MISTAKE, GameRemark.GOOD_GAME -> BuddyMoment.GOOD_GAME
    GameRemark.HINT -> BuddyMoment.HINT
    GameRemark.WON -> BuddyMoment.WIN
}

/**
 * The buddy beside or below the board in a computer game. Its text is the buddy's own line for
 * the moment, or a game fact ("The computer played Nf3. Your move."). Screen readers already hear
 * each move, so the card is read on request, not announced.
 */
@Composable fun GameBuddy(s: GameUi, settings: AppSettings, earned: Set<Sticker>, modifier: Modifier, large: Boolean, maxHeight: Dp? = null) {
    val remark = remember(s.positionKey(), s.evals, s.hint) { gameRemark(s) } ?: return
    val moment = remarkMoment(remark)
    val buddyLine = stringResource(buddyLine(settings.buddy, moment))
    val text = when (remark) {
        GameRemark.YOUR_MOVE -> stringResource(R.string.game_your_move)
        GameRemark.COMPUTER_MOVED -> stringResource(R.string.game_computer_played, s.position.san.lastOrNull().orEmpty())
        GameRemark.IN_CHECK -> stringResource(R.string.game_in_check)
        GameRemark.CHECK_GIVEN -> stringResource(R.string.game_check_given)
        GameRemark.CAPTURE -> stringResource(R.string.game_capture)
        GameRemark.MISTAKE -> stringResource(R.string.game_mistake)
        GameRemark.NICE_MOVE -> stringResource(niceLine(settings.buddy))
        else -> buddyLine
    }
    BuddyReaction(settings, earned, moment, modifier, large = large, text = text, maxHeight = maxHeight)
}

@androidx.annotation.StringRes private fun niceLine(buddy: Buddy): Int = when (buddy) {
    Buddy.ROBOT -> R.string.line_robot_nice
    Buddy.MAGNUS -> R.string.line_magnus_nice
    Buddy.GIRL -> R.string.line_girl_nice
    Buddy.PRINCESS -> R.string.line_princess_nice
    Buddy.MONKEY -> R.string.line_monkey_nice
    Buddy.SLOTH -> R.string.line_sloth_nice
    Buddy.BOB -> R.string.line_bob_nice
}
