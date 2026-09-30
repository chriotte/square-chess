package com.dataespresso.squarechess

import androidx.room.*
import kotlin.random.Random

/** How an attempt ended. Only FIRST_TRY counts as solved without help. */
enum class AttemptResult { FIRST_TRY, WITH_HELP, ABANDONED }

/**
 * What the player has done with one puzzle. The catalogue itself is a read-only asset; only
 * this progress is stored. [reviewStep] is null for puzzles outside the review cycle.
 */
@Entity(tableName="puzzle_progress")
data class PuzzleProgress(
    @PrimaryKey val puzzleId: String,
    val attempts: Int,
    val solved: Int,
    val firstTrySolved: Int,
    val mistakes: Int,
    val hints: Int,
    val lastResult: String,
    val lastAttemptAt: Long,
    val reviewStep: Int?,
    val nextReviewAt: Long?
)

@Dao interface PuzzleProgressDao {
    @Query("SELECT * FROM puzzle_progress") suspend fun all(): List<PuzzleProgress>
    @Query("SELECT * FROM puzzle_progress WHERE puzzleId = :id") suspend fun get(id: String): PuzzleProgress?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(progress: PuzzleProgress)
}

private const val DAY_MS=24*60*60*1000L
/**
 * Review delays by step. A puzzle solved with a mistake or a hint, or left unsolved, is due at
 * once (step 0), so "Review mistakes" can bring it back in the same sitting; each later solve
 * without help moves it on, and after the last step it leaves the review cycle.
 */
val REVIEW_DELAYS_MS=listOf(0L,3*DAY_MS,7*DAY_MS,30*DAY_MS)

/** The progress after one attempt, including the next review. */
fun recordAttempt(old: PuzzleProgress?, puzzleId: String, result: AttemptResult, mistakes: Int, hint: Boolean, now: Long): PuzzleProgress {
    val (step,due)=when {
        result!=AttemptResult.FIRST_TRY -> 0 to now+REVIEW_DELAYS_MS[0]
        old?.reviewStep==null -> null to null
        old.reviewStep+1<REVIEW_DELAYS_MS.size -> (old.reviewStep+1).let { it to now+REVIEW_DELAYS_MS[it] }
        else -> null to null
    }
    return PuzzleProgress(
        puzzleId=puzzleId,
        attempts=(old?.attempts ?: 0)+1,
        solved=(old?.solved ?: 0)+if(result!=AttemptResult.ABANDONED) 1 else 0,
        firstTrySolved=(old?.firstTrySolved ?: 0)+if(result==AttemptResult.FIRST_TRY) 1 else 0,
        mistakes=(old?.mistakes ?: 0)+mistakes,
        hints=(old?.hints ?: 0)+if(hint) 1 else 0,
        lastResult=result.name,
        lastAttemptAt=now,
        reviewStep=step,
        nextReviewAt=due
    )
}

const val START_TRAINING_LEVEL=1100
const val MIN_TRAINING_LEVEL=600
const val MAX_TRAINING_LEVEL=2500

/**
 * The next training level: the puzzle rating the selection aims at. It is not a chess rating.
 * Small steps: up after a solve without help (more for a puzzle above the level), a little
 * down after a solve with help, and more after a puzzle left unsolved.
 */
fun nextTrainingLevel(level: Int, result: AttemptResult, puzzleRating: Int): Int {
    val change=when(result) {
        AttemptResult.FIRST_TRY -> (15+(puzzleRating-level)/20).coerceIn(5,25)
        AttemptResult.WITH_HELP -> -5
        AttemptResult.ABANDONED -> -20
    }
    return (level+change).coerceIn(MIN_TRAINING_LEVEL,MAX_TRAINING_LEVEL)
}

/**
 * A new puzzle near [level]: first unseen puzzles within 150 points, then within 300, then any
 * unseen one, then the least recently seen. [group] limits the themes; [avoid] (for example the
 * last few puzzles, or a Quick 5 set) is never chosen while anything else is left.
 */
fun choosePuzzle(pool: List<Puzzle>, level: Int, progress: Map<String,PuzzleProgress>, avoid: Set<String>,
                 group: ThemeGroup?, random: Random): Puzzle? {
    val matching=pool.filter { (group==null || group in it.group) && it.id !in avoid }
    if(matching.isEmpty()) return null
    val unseen=matching.filter { it.id !in progress }
    for(band in listOf(150,300)) {
        val near=unseen.filter { kotlin.math.abs(it.rating-level)<=band }
        if(near.isNotEmpty()) return near[random.nextInt(near.size)]
    }
    if(unseen.isNotEmpty()) return unseen.minBy { kotlin.math.abs(it.rating-level) }
    return matching.minBy { progress[it.id]?.lastAttemptAt ?: 0L }
}

/** Puzzles due for review at [now], the longest overdue first. */
fun dueForReview(progress: Collection<PuzzleProgress>, now: Long): List<String> =
    progress.filter { it.nextReviewAt!=null && it.nextReviewAt<=now }.sortedBy { it.nextReviewAt }.map { it.puzzleId }

/** Totals for the Progress panel. */
data class PuzzleTotals(val attempted: Int, val solvedFirstTry: Int, val solvedWithHelp: Int, val due: Int)

fun puzzleTotals(progress: Collection<PuzzleProgress>, now: Long): PuzzleTotals = PuzzleTotals(
    attempted=progress.size,
    solvedFirstTry=progress.count { it.firstTrySolved>0 },
    solvedWithHelp=progress.count { it.firstTrySolved==0 && it.solved>0 },
    due=dueForReview(progress,now).size
)

/** One finished puzzle in a Quick 5 set. */
data class QuickResult(val puzzleId: String, val result: AttemptResult, val hint: Boolean)
const val QUICK_SET_SIZE=5
