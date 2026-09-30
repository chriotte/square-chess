package com.dataespresso.squarechess

import kotlin.math.abs
import kotlin.math.exp

/**
 * The engine's opinion of one position, from White's side: [score] in centipawns (positive is
 * good for White). When a side can force mate, [mate] is the number of its moves to mate and the
 * sign of [score] names the side; 0 means the position is already checkmate. [best] is the move
 * the engine would play there, in UCI form, and is missing for positions where the game ended.
 */
data class Eval(val score: Int, val mate: Int? = null, val best: String? = null)

const val MATE_SCORE=100_000
/** Search time for one evaluation: short enough to keep up with play, long enough to be useful. */
const val EVALUATION_TIME_MS=300

enum class MoveQuality { BEST, GOOD, INACCURACY, MISTAKE, BLUNDER }
enum class Lead { EQUAL, WHITE_SLIGHTLY, BLACK_SLIGHTLY, WHITE_BETTER, BLACK_BETTER, WHITE_WINNING, BLACK_WINNING, WHITE_MATES, BLACK_MATES }

fun GameUi.evaluationOn(): Boolean = game?.evaluationEnabled == true

/**
 * An engine score line ("score cp 31", "score mate -2") is from the side to move; [whiteToMove]
 * turns it to White's side. Returns null for a line without a usable score.
 */
fun evalFromScore(kind: String, value: Int, whiteToMove: Boolean, best: String?): Eval? {
    val sign=if(whiteToMove) 1 else -1
    return when(kind) {
        "cp" -> Eval(value*sign,null,best)
        "mate" -> {
            // "mate -2": the side to move is mated in two; "mate 0" does not occur in a search.
            val winnerSign=if(value>0) sign else -sign
            Eval(winnerSign*MATE_SCORE,abs(value),best)
        }
        else -> null
    }
}

/**
 * Reads the final full-width score from a UCI search transcript. Bound-only lines (from a
 * search cut short) and lines for other MultiPV entries are skipped.
 */
fun parseSearchScore(transcript: String, whiteToMove: Boolean, best: String?): Eval? {
    var found: Eval?=null
    for(line in transcript.lineSequence()) {
        if(!line.startsWith("info ") || " score " !in line || "bound" in line) continue
        val words=line.split(' ')
        val multiPv=words.indexOf("multipv").takeIf { it>=0 }?.let { words.getOrNull(it+1) }
        if(multiPv!=null && multiPv!="1") continue
        val at=words.indexOf("score")
        val value=words.getOrNull(at+2)?.toIntOrNull() ?: continue
        evalFromScore(words.getOrNull(at+1) ?: continue,value,whiteToMove,best)?.let { found=it }
    }
    return found
}

/** A finished position: the side to move is checkmated, or it is a draw (0). */
fun terminalEval(position: ChessPosition): Eval? = when {
    position.board.isMated -> Eval(if(position.board.sideToMove==com.github.bhlangonijr.chesslib.Side.WHITE) -MATE_SCORE else MATE_SCORE,0)
    position.automaticResult()=="1/2-1/2" -> Eval(0)
    else -> null
}

/** Chance of winning, 0..100, for the side with [cp]; the curve Lichess uses for its move labels. */
fun winChance(cp: Int): Double {
    val clamped=cp.coerceIn(-1000,1000)
    return 50+50*(2/(1+exp(-0.00368208*clamped))-1)
}

/**
 * How good [move] was, from the evaluations before and after it. The engine's own choice is
 * the best move; other moves lose some of the mover's winning chance, and the size of the
 * loss gives the label (the Lichess limits of 10, 20 and 30 percentage points).
 */
fun moveQuality(before: Eval, after: Eval, move: String, whiteMoved: Boolean): MoveQuality {
    if(before.best!=null && before.best==move) return MoveQuality.BEST
    // A mate on the board is as good as a move can be.
    if(after.mate==0 && (after.score>0)==whiteMoved) return MoveQuality.BEST
    val sign=if(whiteMoved) 1 else -1
    val loss=winChance(before.score*sign)-winChance(after.score*sign)
    return when {
        loss>=30 -> MoveQuality.BLUNDER
        loss>=20 -> MoveQuality.MISTAKE
        loss>=10 -> MoveQuality.INACCURACY
        else -> MoveQuality.GOOD
    }
}

fun lead(eval: Eval): Lead {
    val white=eval.score>0
    return when {
        eval.mate!=null -> if(white) Lead.WHITE_MATES else Lead.BLACK_MATES
        abs(eval.score)<50 -> Lead.EQUAL
        abs(eval.score)<150 -> if(white) Lead.WHITE_SLIGHTLY else Lead.BLACK_SLIGHTLY
        abs(eval.score)<300 -> if(white) Lead.WHITE_BETTER else Lead.BLACK_BETTER
        else -> if(white) Lead.WHITE_WINNING else Lead.BLACK_WINNING
    }
}

/** "+0.3", "−1.8" (pawns, from White's side), or "" for a mate. */
fun scoreText(eval: Eval): String {
    if(eval.mate!=null) return ""
    val pawns=eval.score/100.0
    val text=String.format(java.util.Locale.ROOT,"%.1f",abs(pawns))
    return if(text=="0.0") "0.0" else (if(pawns>0) "+" else "−")+text
}

/** Whether the move that leads to position [ply] (1-based) was White's. */
fun whiteMovedAt(initialFen: String, ply: Int): Boolean {
    val whiteFirst=initialFen.split(" ").getOrNull(1)!="b"
    return ((ply-1)%2==0)==whiteFirst
}

/** The label of the move that leads to position [ply], when both positions are evaluated. */
fun qualityAt(evals: Map<Int,Eval>, initialFen: String, moves: List<String>, ply: Int): MoveQuality? {
    if(ply<1 || ply>moves.size) return null
    val before=evals[ply-1] ?: return null
    val after=evals[ply] ?: return null
    return moveQuality(before,after,moves[ply-1],whiteMovedAt(initialFen,ply))
}

data class QualityCount(val inaccuracies: Int, val mistakes: Int, val blunders: Int)

/** Inaccuracies, mistakes and blunders of each side: first White, then Black. */
fun qualitySummary(evals: Map<Int,Eval>, initialFen: String, moves: List<String>): Pair<QualityCount,QualityCount> {
    val white=IntArray(3); val black=IntArray(3)
    for(ply in 1..moves.size) {
        val quality=qualityAt(evals,initialFen,moves,ply) ?: continue
        val slot=when(quality) { MoveQuality.INACCURACY -> 0; MoveQuality.MISTAKE -> 1; MoveQuality.BLUNDER -> 2; else -> continue }
        (if(whiteMovedAt(initialFen,ply)) white else black)[slot]++
    }
    return QualityCount(white[0],white[1],white[2]) to QualityCount(black[0],black[1],black[2])
}

/*
 * Saved form: one token per evaluated position, "ply=score/best". The score is "c31" for
 * centipawns or "m+3"/"m-3" for a mate by White or Black; the best move may be empty.
 */
fun encodeEvaluations(evals: Map<Int,Eval>): String = evals.toSortedMap().entries.joinToString(" ") { (ply,e) ->
    val score=if(e.mate!=null) "m${if(e.score>0) "+" else "-"}${e.mate}" else "c${e.score}"
    "$ply=$score/${e.best.orEmpty()}"
}

fun decodeEvaluations(text: String): Map<Int,Eval> = text.split(' ').filter(String::isNotBlank).mapNotNull { token ->
    runCatching {
        val ply=token.substringBefore('=').toInt()
        val score=token.substringAfter('=').substringBefore('/')
        val best=token.substringAfter('/',"").ifEmpty { null }
        val eval=when(score.first()) {
            'c' -> Eval(score.drop(1).toInt(),null,best)
            'm' -> Eval(if(score[1]=='+') MATE_SCORE else -MATE_SCORE,score.drop(2).toInt(),best)
            else -> error("Unknown score")
        }
        ply to eval
    }.getOrNull()
}.toMap()
