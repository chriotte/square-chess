package com.dataespresso.squarechess

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Ratings in sync: the game rating, the rating history and the rating fields on game records. */
class RatingSyncTest {
    private fun kv(id: String, updated: Long, value: Any) = SyncRecord(SyncRecord.KV, id, updated, data = JSONObject().put("value", value))

    @Test fun gameRecordsKeepUnratedAndRating() {
        val game = SavedGame("g1", GameMode.COMPUTER.name, moves = "e2e4 e7e5", result = "0-1", updated = 5, unrated = false)
            .withRating(GameRatingChange(1000, 979, -21, 400, true))
        val base = JSONObject().put("rating", JSONObject().put("serverNote", "keep"))
        val data = game.toSyncData(base)
        assertEquals(-21, data.getJSONObject("rating").getInt("change"))
        assertEquals("keep", data.getJSONObject("rating").getString("serverNote"))
        assertFalse(data.getBoolean("unrated"))
        val back = savedGameFromSync("g1", 5, JSONObject(data.toString()))!!
        assertEquals(game.rating, back.rating)
        val hinted = savedGameFromSync("g2", 5, JSONObject(SavedGame("g2", GameMode.COMPUTER.name, unrated = true).toSyncData().toString()))!!
        assertTrue(hinted.unrated)
        assertNull(hinted.rating)
        assertFalse(SavedGame("g3", GameMode.COMPUTER.name).toSyncData().has("rating"))
    }

    @Test fun gameRatingMergesTheSameBothWaysAndIsSentBack() = runBlocking {
        val a = GameRating(1040, 3, 2, 0, 1); val b = GameRating(990, 5, 2, 1, 2)
        val one = MemorySyncStore().apply { game = a; kvStamps[SyncRecord.GAME_RATING] = 900 }
        assertTrue(SyncMerge.apply(one, kv(SyncRecord.GAME_RATING, 100, b.toJson())).training)
        assertEquals(b, one.game)
        // The server's copy already won: nothing to send back.
        assertTrue(SyncMerge.localChanges(one).none { it.id == SyncRecord.GAME_RATING })
        val two = MemorySyncStore().apply { game = b; kvStamps[SyncRecord.GAME_RATING] = 50 }
        assertFalse(SyncMerge.apply(two, kv(SyncRecord.GAME_RATING, 100, a.toJson())).training)
        assertEquals(b, two.game)
        val back = SyncMerge.localChanges(two).single { it.id == SyncRecord.GAME_RATING }
        assertEquals(5, back.data!!.getJSONObject("value").getInt("games"))
        assertEquals(101, back.updated)
    }

    @Test fun ratingDaysMergeTheSameBothWays() = runBlocking {
        val mine = mapOf("2026-11-27" to mapOf(PUZZLE_SERIES to RatingPoint(1200, 1)), "2026-11-28" to mapOf(PUZZLE_SERIES to RatingPoint(1210, 5)))
        val theirs = mapOf("2026-11-28" to mapOf(PUZZLE_SERIES to RatingPoint(1190, 9), GAME_SERIES to RatingPoint(980, 8)))
        val one = MemorySyncStore().apply { history = mine }
        SyncMerge.apply(one, kv(SyncRecord.RATING_DAYS, 100, ratingDaysToJson(theirs)))
        val two = MemorySyncStore().apply { history = theirs }
        SyncMerge.apply(two, kv(SyncRecord.RATING_DAYS, 100, ratingDaysToJson(mine)))
        assertEquals(one.history, two.history)
        assertEquals(RatingPoint(1190, 9), one.history["2026-11-28"]!![PUZZLE_SERIES])
        assertEquals(RatingPoint(1200, 1), one.history["2026-11-27"]!![PUZZLE_SERIES])
        // Each side holds something the server did not: both send the merged history back.
        assertTrue(SyncMerge.localChanges(one).any { it.id == SyncRecord.RATING_DAYS })
        assertTrue(SyncMerge.localChanges(two).any { it.id == SyncRecord.RATING_DAYS })
    }

    @Test fun backupCarriesTheRatings() = runBlocking {
        val store = MemorySyncStore().apply {
            game = GameRating(1100, 4, 3, 0, 1); history = mapOf("2026-11-28" to mapOf(GAME_SERIES to RatingPoint(1100, 3)))
        }
        val other = MemorySyncStore()
        Backup.restore(other, Backup.build(store, AppSettings(), 1_000), AppSettings())
        assertEquals(store.game, other.game)
        assertEquals(store.history, other.history)
    }
}
