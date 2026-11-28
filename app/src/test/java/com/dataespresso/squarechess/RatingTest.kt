package com.dataespresso.squarechess

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** The same numbers as the web version's tests (qa/logic.test.mjs in the web repository). */
class RatingTest {
    private fun progress(vararg attempts: Pair<String, Int>) =
        attempts.associate { (id, n) -> id to PuzzleProgress(id, n, 0, 0, 0, 0, "", 0, null, null) }
    private val start = SavedGame("g", GameMode.COMPUTER.name, initialFen = START_FEN, evaluationEnabled = false)

    @Test fun elo() {
        assertEquals(20, eloChange(1200, 1200, 1.0, 0)); assertEquals(-20, eloChange(1200, 1200, 0.0, 0))
        assertEquals(10, eloChange(1200, 1200, 1.0, 25)); assertEquals(0, eloChange(1200, 1200, 0.5, 25))
        assertEquals(18, eloChange(1200, 1600, 1.0, 25)); assertEquals(-18, eloChange(1600, 1200, 0.0, 25))
    }

    @Test fun puzzleChangeFirstTryWithHelpGivenUp() {
        assertEquals(10, puzzleChange(1500, AttemptResult.FIRST_TRY, 1500, 30))
        assertEquals(-5, puzzleChange(1500, AttemptResult.WITH_HELP, 1500, 30))
        assertEquals(-10, puzzleChange(1500, AttemptResult.ABANDONED, 1500, 30))
        assertTrue(puzzleChange(1500, AttemptResult.WITH_HELP, 2200, 30) <= 0)
        // A loss of 15 halves to −7.5, which rounds up to −7 as JavaScript's Math.round does.
        assertEquals(-15, puzzleChange(1300, AttemptResult.ABANDONED, 1100, 30))
        assertEquals(-7, puzzleChange(1300, AttemptResult.WITH_HELP, 1100, 30))
        assertEquals(2500, applyPuzzle(2495, 20)); assertEquals(600, applyPuzzle(605, -20))
        assertEquals(2, puzzleCount(progress("a" to 2, "b" to 0, "c" to 1)))
    }

    @Test fun onlyTheFirstAttemptCounts() {
        assertTrue(isFirstAttempt(null))
        assertTrue(isFirstAttempt(progress("a" to 0)["a"]))
        assertFalse(isFirstAttempt(progress("a" to 1)["a"]))
    }

    @Test fun ratedRules() {
        assertTrue(isRatedGame(start))
        assertFalse(isRatedGame(start.copy(unrated = true)))
        assertFalse(isRatedGame(start.copy(evaluationEnabled = true)))
        assertFalse(isRatedGame(start.copy(mode = GameMode.LOCAL_TWO_PLAYER.name)))
        assertFalse(isRatedGame(start.copy(initialFen = "8/8/8/8/8/8/k7/K7 w - - 0 1")))
        assertEquals(1.0, humanScore(start.copy(result = "1-0", humanWhite = true))!!, 0.0)
        assertEquals(0.0, humanScore(start.copy(result = "1-0", humanWhite = false))!!, 0.0)
        assertEquals(0.5, humanScore(start.copy(result = "1/2-1/2", humanWhite = false))!!, 0.0)
        assertNull(humanScore(start.copy(result = "*")))
        assertTrue(countsForRating(start.copy(result = "0-1", humanWhite = true, moves = "e2e4 e7e5")))
        assertFalse(countsForRating(start.copy(result = "0-1", humanWhite = true, moves = "e2e4")))
    }

    @Test fun gameRatingProvisionalRecordAndMerge() {
        val (a, change, opponent) = rateGame(GameRating(), 5, 1.0) // 1000 against level 5 (1000): +20 while provisional
        assertEquals(20, change); assertEquals(1000, opponent)
        assertEquals(GameRating(1020, 1, 1, 0, 0), a)
        val (b, lost, _) = rateGame(a, 1, 0.0)
        assertTrue(lost < -30); assertEquals(1, b.losses)
        assertEquals(10, rateGame(GameRating(1500, 40), 10, 0.5).second)
        assertEquals(b, mergeGameRating(a, b)); assertEquals(b, mergeGameRating(b, a))
        assertEquals(a, mergeGameRating(null, a))
        // Same number of games: the higher rating, whichever way round.
        val c = a.copy(rating = 1100)
        assertEquals(c, mergeGameRating(a, c)); assertEquals(c, mergeGameRating(c, a))
        assertEquals("1200?", ratingText(1200, 3)); assertEquals("1200", ratingText(1200, 20))
        assertEquals("+5", changeText(5)); assertEquals("−5", changeText(-5)); assertEquals("±0", changeText(0))
    }

    @Test fun ratingDaysLatestPerDayAndMergeBothWays() {
        var h1 = recordRatingDay(emptyMap(), PUZZLE_SERIES, 1200, "2026-11-27", 1)
        h1 = recordRatingDay(h1, PUZZLE_SERIES, 1210, "2026-11-28", 5, "2026-08-01")
        val h2 = recordRatingDay(mapOf("2026-01-01" to mapOf(PUZZLE_SERIES to RatingPoint(900, 0))), PUZZLE_SERIES, 1190, "2026-11-28", 9, "2026-08-01")
        assertEquals(mapOf("2026-11-28" to mapOf(PUZZLE_SERIES to RatingPoint(1190, 9))), h2)
        val m1 = mergeRatingDays(h1, h2); val m2 = mergeRatingDays(h2, h1)
        assertEquals(m1, m2)
        assertEquals(RatingPoint(1190, 9), m1["2026-11-28"]!![PUZZLE_SERIES])
        assertEquals(RatingPoint(1200, 1), m1["2026-11-27"]!![PUZZLE_SERIES])
        // Equal times: the higher value, from either side.
        val x = mapOf("d" to mapOf(GAME_SERIES to RatingPoint(1000, 5))); val y = mapOf("d" to mapOf(GAME_SERIES to RatingPoint(1010, 5)))
        assertEquals(mergeRatingDays(x, y), mergeRatingDays(y, x))
        assertEquals(1010, mergeRatingDays(x, y)["d"]!![GAME_SERIES]!!.value)
    }

    @Test fun jsonRoundTrips() {
        val days = mapOf("2026-11-28" to mapOf(PUZZLE_SERIES to RatingPoint(1243, 7), GAME_SERIES to RatingPoint(1180, 9)))
        assertEquals(days, ratingDaysFromJson(JSONObject(ratingDaysToJson(days).toString())))
        assertEquals("[1243,7]", ratingDaysToJson(days).getJSONObject("2026-11-28").getJSONArray("p").toString())
        val g = GameRating(1180, 12, 5, 2, 5)
        assertEquals(g, gameRatingFromJson(JSONObject(g.toJson().toString())))
        assertNull(gameRatingFromJson(JSONObject().put("games", 3)))
    }
}
