package com.dataespresso.squarechess

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square

/**
 * What one saved game shows, found by replaying its moves once. "Player" means the person at the
 * device: the human's side against the computer, and either side in a two-player or recorded
 * game (both players are at the device).
 */
internal data class GameFacts(
    val game: SavedGame,
    val finished: Boolean,
    /** The side that won, or null for a draw or a game still running. */
    val winner: Side?,
    /** A player won: the human against the computer, or anyone in a game between two people. */
    val playerWon: Boolean,
    val playerLostToComputer: Boolean,
    val plies: Int,
    /** The piece that gave the final checkmate, when the game ended in checkmate. */
    val matingPiece: PieceType?,
    val playerCastled: Boolean,
    val playerQueenPromotions: Int,
    /** A player's knight attacked the opponent's king and queen at once. */
    val playerRoyalFork: Boolean,
    /** The winner's king reached the opponent's half of the board. */
    val winnerKingMarched: Boolean
)

private fun playerSides(game: SavedGame): Set<Side> =
    if(game.mode==GameMode.COMPUTER.name) setOf(if(game.humanWhite) Side.WHITE else Side.BLACK) else setOf(Side.WHITE,Side.BLACK)

private val KNIGHT_STEPS=listOf(1 to 2,2 to 1,2 to -1,1 to -2,-1 to -2,-2 to -1,-2 to 1,-1 to 2)

private fun knightTargets(from: Square): List<Square> = KNIGHT_STEPS.mapNotNull { (df,dr) ->
    val file=from.file.ordinal+df; val rank=from.rank.ordinal+dr
    if(file in 0..7 && rank in 0..7) Square.squareAt(rank*8+file) else null
}

/** Replays [game]; null when its moves cannot be replayed (a damaged row never stops the rest). */
internal fun gameFacts(game: SavedGame): GameFacts? = runCatching {
    val board=Board().apply { loadFromFen(game.initialFen) }
    val players=playerSides(game)
    val moves=game.moves.split(' ').filter(String::isNotBlank)
    var castled=false; var promotions=0; var fork=false
    val kingMarched=mutableSetOf<Side>()
    var lastMover: PieceType?=null
    for(text in moves) {
        val move=board.legalMoves().firstOrNull { it.toString()==text } ?: error("Illegal move $text")
        val side=board.sideToMove
        val piece=board.getPiece(move.from)
        val player=side in players
        if(player && piece.pieceType==PieceType.KING && kotlin.math.abs(move.to.file.ordinal-move.from.file.ordinal)==2) castled=true
        if(player && move.promotion!=Piece.NONE && move.promotion.pieceType==PieceType.QUEEN) promotions++
        board.doMove(move)
        val moved=board.getPiece(move.to)
        lastMover=moved.pieceType
        if(player && moved.pieceType==PieceType.KNIGHT) {
            val targets=knightTargets(move.to).map { board.getPiece(it) }
            val enemy=side.flip()
            if(targets.any { it.pieceType==PieceType.KING && it.pieceSide==enemy } &&
                targets.any { it.pieceType==PieceType.QUEEN && it.pieceSide==enemy }) fork=true
        }
        if(moved.pieceType==PieceType.KING) {
            val rank=move.to.rank.ordinal
            if((side==Side.WHITE && rank>=4) || (side==Side.BLACK && rank<=3)) kingMarched+=side
        }
    }
    val winner=when(game.result) { "1-0" -> Side.WHITE; "0-1" -> Side.BLACK; else -> null }
    val computer=game.mode==GameMode.COMPUTER.name
    GameFacts(
        game=game,
        finished=game.result!="*",
        winner=winner,
        playerWon=winner!=null && winner in players,
        playerLostToComputer=computer && winner!=null && winner !in players,
        plies=moves.size,
        matingPiece=if(board.isMated && game.result!="*") lastMover else null,
        playerCastled=castled,
        playerQueenPromotions=promotions,
        playerRoyalFork=fork,
        winnerKingMarched=winner!=null && winner in kingMarched
    )
}.getOrNull()

/** The winner was clearly behind (two pawns or a mate against them) at some point of an evaluated game. */
private fun cameBack(facts: GameFacts): Boolean {
    val winner=facts.winner ?: return false
    val evals=decodeEvaluations(facts.game.evaluations)
    val sign=if(winner==Side.WHITE) 1 else -1
    return evals.values.any { (it.score*sign)<=-200 }
}

/** The winner's clock had less than ten seconds left. */
private fun closeOnTime(facts: GameFacts): Boolean {
    val winner=facts.winner ?: return false
    if(facts.game.clockBaseMs==null) return false
    val left=(if(winner==Side.WHITE) facts.game.clockWhiteMs else facts.game.clockBlackMs) ?: return false
    return left in 1 until 10_000
}

private val MATE_TAGS=ThemeGroup.CHECKMATE.tags

/**
 * Every sticker earned so far. [puzzleThemes] maps puzzle IDs to their themes; while the puzzle
 * pack is not loaded it is null, and the two theme stickers wait until it is.
 */
internal fun earnedStickers(games: List<SavedGame>, puzzles: Collection<PuzzleProgress>, puzzleThemes: Map<String,Set<String>>?,
                            replay: (SavedGame) -> GameFacts? = ::gameFacts): Set<Sticker> {
    val facts=games.mapNotNull(replay)
    val finished=facts.filter { it.finished }
    val won=finished.filter { it.playerWon }
    val computerWins=won.filter { it.game.mode==GameMode.COMPUTER.name }
    val earned=mutableSetOf<Sticker>()
    fun award(sticker: Sticker, condition: Boolean) { if(condition) earned+=sticker }

    award(Sticker.FIRST_GAME, finished.isNotEmpty())
    award(Sticker.FIRST_CHECKMATE, won.any { it.matingPiece!=null })
    award(Sticker.KNIGHT_RIDER, won.any { it.matingPiece==PieceType.KNIGHT })
    award(Sticker.BISHOP_MATE, won.any { it.matingPiece==PieceType.BISHOP })
    award(Sticker.ROOK_MATE, won.any { it.matingPiece==PieceType.ROOK })
    award(Sticker.PROMOTION, facts.any { it.playerQueenPromotions>0 })
    award(Sticker.ROYAL_COURT, facts.sumOf { it.playerQueenPromotions }>=5)
    award(Sticker.SAFE_KING, facts.count { it.playerCastled }>=5)
    award(Sticker.ROYAL_FORK, facts.any { it.playerRoyalFork })
    award(Sticker.KING_OF_BOLSENA, won.any { it.winnerKingMarched })
    award(Sticker.STALEMATE, finished.any { it.game.resultReason=="Stalemate" })
    award(Sticker.JUNGLE_HOP, won.any { (it.plies+1)/2<=15 })
    award(Sticker.LONG_GAME, finished.any { it.plies>=120 })
    award(Sticker.MAGIC_PAINTBRUSH, won.any(::cameBack))
    award(Sticker.LEVEL_2, computerWins.any { it.game.level>=2 })
    award(Sticker.LEVEL_4, computerWins.any { it.game.level>=4 })
    award(Sticker.LEVEL_6, computerWins.any { it.game.level>=6 })
    award(Sticker.ADMIRAL, computerWins.any { it.game.level>=8 })
    award(Sticker.TOP_LEVEL, computerWins.any { it.game.level>=10 })
    award(Sticker.CLIMBER, (1..5).all { level -> computerWins.any { it.game.level==level } })
    award(Sticker.PANCAKE_CHEF, computerWins.size>=5)
    val computerGames=facts.filter { it.game.mode==GameMode.COMPUTER.name }.sortedBy { it.game.updated }
    award(Sticker.REMATCH, computerGames.withIndex().any { (i,f) -> f.playerLostToComputer && i<computerGames.lastIndex })
    award(Sticker.COOL_HEAD, won.any { it.game.clockBaseMs!=null })
    award(Sticker.ICE_COLD, won.any(::closeOnTime))
    award(Sticker.REAL_BOARD, facts.any { it.game.mode==GameMode.PHYSICAL_BOARD_RECORDING.name && it.plies>0 })
    award(Sticker.GOOD_COMPANY, finished.count { it.game.mode==GameMode.LOCAL_TWO_PLAYER.name }>=10)
    award(Sticker.GOOD_GAME, finished.size>=25)

    val solved=puzzles.filter { it.solved>0 }
    award(Sticker.PUZZLES_10, solved.size>=10)
    award(Sticker.PUZZLES_50, solved.size>=50)
    award(Sticker.PUZZLES_200, solved.size>=200)
    award(Sticker.SPELLBOUND, solved.count { it.hints==0 }>=25)
    // Three puzzles in a row, each solved at the first try, by the time of each puzzle's last attempt.
    val inOrder=puzzles.sortedBy { it.lastAttemptAt }.map { it.lastResult==AttemptResult.FIRST_TRY.name }
    award(Sticker.ONE_TWO_THREE, inOrder.windowed(3).any { it.all { first -> first } })
    if(puzzleThemes!=null) {
        award(Sticker.HOT_STREAK, solved.count { p -> puzzleThemes[p.puzzleId].orEmpty().any { it in MATE_TAGS } }>=5)
        award(Sticker.FAR_SIGHT, solved.any { "mateIn3" in puzzleThemes[it.puzzleId].orEmpty() })
    }
    return earned
}

/** The most recently played games (finished or not), newest first, for the list on Home. */
fun recentGames(games: List<SavedGame>, count: Int = 5): List<SavedGame> =
    games.filter { it.moves.isNotBlank() }.sortedByDescending { it.updated }.take(count)

/** The most recent finished games, newest first. */
fun recentFinishedGames(games: List<SavedGame>, count: Int = 3): List<SavedGame> =
    games.filter { it.result!="*" && it.moves.isNotBlank() }.sortedByDescending { it.updated }.take(count)
