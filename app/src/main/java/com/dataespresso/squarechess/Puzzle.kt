package com.dataespresso.squarechess

import com.github.bhlangonijr.chesslib.Side

/**
 * A puzzle from the bundled Lichess pack (see docs/puzzles). [fen] is the position before the
 * opponent's setup move; [moves] (UCI) start with that setup move, then alternate between the
 * solver and the opponent, and end with the solver's last move.
 */
data class Puzzle(
    val id: String,
    val fen: String,
    val moves: List<String>,
    val rating: Int,
    val popularity: Int,
    val themes: Set<String>
) {
    val group: Set<ThemeGroup> = themeGroups(themes)
}

/** Parses one pack line: "id fen moves rating popularity themes", tab separated. Null when unreadable. */
fun parsePuzzleLine(line: String): Puzzle? {
    val parts=line.split('\t')
    if(parts.size!=6) return null
    val (id,fen,moves,rating,popularity,themes)=parts
    val list=moves.split(' ').filter(String::isNotBlank)
    if(id.isBlank() || fen.split(' ').size!=6 || list.size<2 || list.any { !UCI.matches(it) }) return null
    return Puzzle(id,fen,list,rating.toIntOrNull() ?: return null,popularity.toIntOrNull() ?: return null,
        themes.split(' ').filter(String::isNotBlank).toSet())
}
private val UCI=Regex("[a-h][1-8][a-h][1-8][qrbn]?")
private operator fun <T> List<T>.component6(): T = this[5]

/** Small theme groups for choosing what to train; the pack keeps the Lichess tags themselves. */
enum class ThemeGroup(val tags: Set<String>) {
    CHECKMATE(setOf("mate","mateIn1","mateIn2","mateIn3","mateIn4","mateIn5","backRankMate","smotheredMate",
        "anastasiaMate","arabianMate","bodenMate","doubleBishopMate","dovetailMate","hookMate")),
    FORKS(setOf("fork","doubleCheck","discoveredAttack")),
    PINS(setOf("pin","skewer","xRayAttack")),
    MOTIFS(setOf("sacrifice","deflection","attraction","clearance","interference","intermezzo","quietMove",
        "trappedPiece","hangingPiece","capturingDefender","exposedKing","kingsideAttack","queensideAttack","zugzwang","defensiveMove")),
    ENDGAMES(setOf("endgame","pawnEndgame","rookEndgame","bishopEndgame","knightEndgame","queenEndgame",
        "queenRookEndgame","promotion","underPromotion","advancedPawn"))
}

fun themeGroups(themes: Set<String>): Set<ThemeGroup> = ThemeGroup.entries.filter { group -> themes.any { it in group.tags } }.toSet()

enum class PuzzleState { SOLVING, OPPONENT_TO_MOVE, SOLVED }
enum class MoveVerdict { CORRECT, SOLVED, WRONG, ILLEGAL, NOT_NOW }

/**
 * One attempt at a puzzle, as data. [ply] is how many of the puzzle's moves are on the board
 * (1 after the setup move). The session never plays a wrong move: a wrong try is only counted.
 */
data class PuzzleSession(
    val puzzle: Puzzle,
    val ply: Int,
    val mistakes: Int = 0,
    val hintUsed: Boolean = false,
    /** The solver's own last move when it differs from the script (an alternative mate). */
    val finalMove: String? = null
) {
    val position: ChessPosition by lazy { ChessPosition(puzzle.fen,puzzle.moves.take(ply)+listOfNotNull(finalMove)) }
    val state: PuzzleState get() = when {
        finalMove!=null || ply>=puzzle.moves.size -> PuzzleState.SOLVED
        ply%2==0 -> PuzzleState.OPPONENT_TO_MOVE
        else -> PuzzleState.SOLVING
    }
    /** The solver's colour: the side to move after the setup move. */
    val solverWhite: Boolean get() = puzzle.fen.split(' ')[1]=="b"
    /** The move the solver should find now, or null when it is not the solver's turn. */
    val expectedMove: String? get() = if(state==PuzzleState.SOLVING) puzzle.moves[ply] else null
    /** The opponent's scripted reply waiting to be played, or null. */
    val pendingReply: String? get() = if(state==PuzzleState.OPPONENT_TO_MOVE) puzzle.moves[ply] else null
    /** The last move on the board, for the board's last-move marks. */
    val lastMove: String? get() = finalMove ?: puzzle.moves.getOrNull(ply-1)
    val firstTry: Boolean get() = mistakes==0 && !hintUsed

    /**
     * Judges the solver's [move], in UCI or SAN as ChessPosition.resolve reads them. The scripted
     * move is correct; so is any other move that mates at once, since a mate ends every line.
     * Another legal move is wrong and changes nothing but the mistake count. Text that is not a
     * legal move here (a typing error) is ILLEGAL and does not count as a mistake.
     */
    fun judge(move: String): Pair<MoveVerdict,PuzzleSession> {
        val expected=expectedMove ?: return MoveVerdict.NOT_NOW to this
        val legal=position.resolve(move) ?: return MoveVerdict.ILLEGAL to this
        if(legal.toString()==expected) {
            val next=copy(ply=ply+1)
            return (if(next.state==PuzzleState.SOLVED) MoveVerdict.SOLVED else MoveVerdict.CORRECT) to next
        }
        if(position.append(legal).board.isMated) return MoveVerdict.SOLVED to copy(finalMove=legal.toString())
        return MoveVerdict.WRONG to copy(mistakes=mistakes+1)
    }

    /** Plays the opponent's scripted reply. */
    fun reply(): PuzzleSession = if(state==PuzzleState.OPPONENT_TO_MOVE) copy(ply=ply+1) else this
    fun withHint(): PuzzleSession = copy(hintUsed=true)

    companion object {
        /**
         * Starts [puzzle] after its setup move, or returns null when the puzzle's moves are not
         * legal in sequence (a damaged pack line must never break the screen).
         */
        fun start(puzzle: Puzzle): PuzzleSession? = runCatching {
            val position=ChessPosition(puzzle.fen,puzzle.moves)
            check(position.moves.size==puzzle.moves.size)
            PuzzleSession(puzzle,1)
        }.getOrNull()

        /** Rebuilds a session saved as its fields, for example after the app was recreated. */
        fun restore(puzzle: Puzzle, ply: Int, mistakes: Int, hintUsed: Boolean): PuzzleSession? =
            start(puzzle)?.copy(ply=ply.coerceIn(1,puzzle.moves.size),mistakes=mistakes,hintUsed=hintUsed)
    }
}

fun PuzzleSession.solverSide(): Side = if(solverWhite) Side.WHITE else Side.BLACK
