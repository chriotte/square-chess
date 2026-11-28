package com.dataespresso.squarechess

import kotlin.math.pow

/*
 * Ratings: a puzzle rating and a game rating, both worked out with the Elo formula, so points
 * depend on how strong the puzzle or the computer level is. Pure functions, the same as the web
 * version (site/play/js/rating.js), so a player who signs in sees the same numbers on both.
 */

/** The first results move a rating faster (K 40), then it settles (K 20). Shown as "1200?" until then. */
const val PROVISIONAL = 20
private const val K_NEW = 40
private const val K_SETTLED = 20

/**
 * A fixed rating for each computer level (1 to 10). These are estimates: the levels are engine
 * settings and have never been measured against rated players.
 */
val LEVEL_RATINGS = listOf(400, 550, 700, 850, 1000, 1200, 1400, 1650, 1900, 2400)
const val START_GAME_RATING = 1000
/** The puzzle rating is stored as the training level, so existing players keep their number. */
const val START_PUZZLE_RATING = START_TRAINING_LEVEL
private const val MIN_GAME = 100
private const val MAX_GAME = 3000
private const val MIN_PUZZLE = MIN_TRAINING_LEVEL
private const val MAX_PUZZLE = MAX_TRAINING_LEVEL
/** A game ended before both sides moved is treated as aborted: it is not rated. */
const val MIN_RATED_PLIES = 2

fun levelRating(level: Int): Int = LEVEL_RATINGS[level.coerceIn(1, LEVEL_RATINGS.size) - 1]

/** The expected score (0–1) of a player rated [rating] against [opponent]. */
fun expectedScore(rating: Int, opponent: Int): Double = 1.0 / (1.0 + 10.0.pow((opponent - rating) / 400.0))
fun kFactor(count: Int): Int = if (count < PROVISIONAL) K_NEW else K_SETTLED
/** JavaScript's Math.round: halves go up, so −2.5 becomes −2 (as Math.round does here too). */
private fun jsRound(x: Double): Int = Math.round(x).toInt()
/** Points won or lost: K × (score − expected), rounded. [score] is 1, 0.5 or 0. */
fun eloChange(rating: Int, opponent: Int, score: Double, count: Int): Int =
    jsRound(kFactor(count) * (score - expectedScore(rating, opponent)))

// ---- Puzzles -------------------------------------------------------------------------------

/**
 * The change to the puzzle rating for a puzzle's first attempt. Solved first try: a win against
 * the puzzle's rating. Solved after a mistake or a hint: half of what a loss would cost. Given up:
 * a loss. [count] is how many puzzles were rated before.
 */
fun puzzleChange(rating: Int, result: AttemptResult, puzzleRating: Int, count: Int): Int {
    if (result == AttemptResult.FIRST_TRY) return eloChange(rating, puzzleRating, 1.0, count)
    val loss = eloChange(rating, puzzleRating, 0.0, count)
    return if (result == AttemptResult.WITH_HELP) jsRound(loss / 2.0) else loss
}
fun applyPuzzle(rating: Int, change: Int): Int = (rating + change).coerceIn(MIN_PUZZLE, MAX_PUZZLE)
/** Rated puzzles so far: puzzles attempted at least once (each counts once, its first attempt). */
fun puzzleCount(progress: Map<String, PuzzleProgress>): Int = progress.values.count { it.attempts > 0 }
/** Only a puzzle's first attempt is rated: no record yet, or one without attempts. */
fun isFirstAttempt(record: PuzzleProgress?): Boolean = record == null || record.attempts == 0

// ---- Games ---------------------------------------------------------------------------------

/** The game rating and the record behind it. */
data class GameRating(val rating: Int = START_GAME_RATING, val games: Int = 0, val wins: Int = 0, val draws: Int = 0, val losses: Int = 0)

/** What a rated game did to the rating, saved on the game. */
data class GameRatingChange(val before: Int, val after: Int, val change: Int, val opponent: Int, val provisional: Boolean)

/**
 * Whether a game counts for the game rating: against the computer, from the normal start,
 * without the evaluation, and without undo or hints ([SavedGame.unrated] is set when either is used).
 */
fun isRatedGame(g: SavedGame?): Boolean =
    g != null && g.mode == GameMode.COMPUTER.name && !g.evaluationEnabled && !g.unrated && (g.initialFen.isBlank() || g.initialFen == START_FEN)

/** The human's score in a finished game: 1, 0.5 or 0; null while it is in progress. */
fun humanScore(g: SavedGame): Double? = when (g.result) {
    "1/2-1/2" -> 0.5
    "1-0", "0-1" -> if ((g.result == "1-0") == g.humanWhite) 1.0 else 0.0
    else -> null
}

/** A finished game that changes the rating: rated, and long enough not to count as aborted. */
fun countsForRating(g: SavedGame): Boolean =
    isRatedGame(g) && g.moves.split(' ').count { it.isNotBlank() } >= MIN_RATED_PLIES && humanScore(g) != null

/** The game rating after one finished rated game, with the change and the opponent's rating. */
fun rateGame(state: GameRating, level: Int, score: Double): Triple<GameRating, Int, Int> {
    val opponent = levelRating(level)
    val change = eloChange(state.rating, opponent, score, state.games)
    val next = state.copy(rating = (state.rating + change).coerceIn(MIN_GAME, MAX_GAME), games = state.games + 1,
        wins = state.wins + if (score == 1.0) 1 else 0, draws = state.draws + if (score == 0.5) 1 else 0,
        losses = state.losses + if (score == 0.0) 1 else 0)
    return Triple(next, change, opponent)
}

/** Two devices' game ratings: the one with more games, then the higher rating (the same on both sides). */
fun mergeGameRating(a: GameRating?, b: GameRating?): GameRating? = when {
    a == null -> b
    b == null -> a
    a.games != b.games -> if (a.games > b.games) a else b
    else -> if (b.rating > a.rating) b else a
}

// ---- History -------------------------------------------------------------------------------

/** One rating at one time: the value and when it was set (milliseconds). */
data class RatingPoint(val value: Int, val ms: Long)
/** By day ("2026-11-28"), by rating ("p" puzzle, "g" game): the latest value of the day. */
typealias RatingDays = Map<String, Map<String, RatingPoint>>
const val PUZZLE_SERIES = "p"
const val GAME_SERIES = "g"
const val RATING_KEEP_DAYS = 120L

/** Records [value] as the latest of [which] on [day]; days before [oldestKey] are dropped. */
fun recordRatingDay(history: RatingDays, which: String, value: Int, day: String, time: Long, oldestKey: String? = null): RatingDays {
    val next = history + (day to ((history[day] ?: emptyMap()) + (which to RatingPoint(value, time))))
    return if (oldestKey == null) next else next.filterKeys { it >= oldestKey }
}

/** Two devices' histories: for each day and rating, the later value, then the higher if equal. */
fun mergeRatingDays(a: RatingDays, b: RatingDays): RatingDays {
    val out = a.toMutableMap()
    for ((day, values) in b) {
        val mine = out[day] ?: emptyMap()
        val merged = mine.toMutableMap()
        for ((which, v) in values) {
            val m = mine[which]
            if (m == null || v.ms > m.ms || (v.ms == m.ms && v.value > m.value)) merged[which] = v
        }
        out[day] = merged
    }
    return out
}

/** "1243" or "1243?" while provisional. */
fun ratingText(value: Int, count: Int): String = if (count < PROVISIONAL) "$value?" else "$value"
/** "+14", "−9" (a real minus sign) or "±0". */
fun changeText(change: Int): String = when {
    change > 0 -> "+$change"
    change < 0 -> "−${-change}"
    else -> "±0"
}
