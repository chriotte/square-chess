package com.dataespresso.squarechess

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** The daily goal, puzzles per day and repeat set records (docs/accounts.md in the web repository). */
class TrainingSyncTest {
    private fun kv(id: String, updated: Long, data: JSONObject) = SyncRecord(SyncRecord.KV, id, updated, data = data)

    @Test fun goalNewestWins() = runBlocking {
        val store = MemorySyncStore()
        store.goal = 5; store.kvStamps[SyncRecord.PUZZLE_GOAL] = 100
        assertTrue(SyncMerge.apply(store, kv(SyncRecord.PUZZLE_GOAL, 200, JSONObject().put("value", 20))).training)
        assertEquals(20, store.goal)
        assertFalse(SyncMerge.apply(store, kv(SyncRecord.PUZZLE_GOAL, 150, JSONObject().put("value", 30))).any)
        // A goal this version does not offer is skipped.
        assertFalse(SyncMerge.apply(store, kv(SyncRecord.PUZZLE_GOAL, 300, JSONObject().put("value", 7))).any)
        assertEquals(20, store.goal)
        store.goal = 0; store.kvStamps[SyncRecord.PUZZLE_GOAL] = 400
        val out = SyncMerge.localChanges(store).single { it.id == SyncRecord.PUZZLE_GOAL }
        assertEquals(0, out.data!!.getInt("value"))
    }

    @Test fun daysAreMergedAndSentBackWhenTheyDiffer() = runBlocking {
        val store = MemorySyncStore()
        store.days = mapOf("2026-11-27" to 3, "2026-11-28" to 1); store.kvStamps[SyncRecord.PUZZLE_DAYS] = 500
        // An older server copy still merges: counts are never replaced.
        val incoming = JSONObject().put("value", JSONObject().put("2026-11-27", 1).put("2026-11-26", 4))
        assertTrue(SyncMerge.apply(store, kv(SyncRecord.PUZZLE_DAYS, 100, incoming)).training)
        assertEquals(mapOf("2026-11-26" to 4, "2026-11-27" to 3, "2026-11-28" to 1), store.days)
        val back = SyncMerge.localChanges(store).single { it.id == SyncRecord.PUZZLE_DAYS }
        assertEquals(501, back.updated)
        assertEquals(4, back.data!!.getJSONObject("value").getInt("2026-11-26"))
        // A server copy that already holds everything: nothing to send back.
        val all = JSONObject().put("value", JSONObject().put("2026-11-26", 4).put("2026-11-27", 3).put("2026-11-28", 2))
        SyncMerge.apply(store, kv(SyncRecord.PUZZLE_DAYS, 700, all))
        assertEquals(2, store.days["2026-11-28"])
        assertTrue(SyncMerge.localChanges(store).none { it.id == SyncRecord.PUZZLE_DAYS })
    }

    @Test fun repeatSetKeepsUnknownFieldsAndClears() = runBlocking {
        val store = MemorySyncStore()
        val set = RepeatSet("r1", 5, RepeatSource.NEAR, listOf("a", "b")).toJson().put("futureField", "keep")
        assertTrue(SyncMerge.apply(store, kv(SyncRecord.REPEAT_SET, 100, JSONObject().put("value", set).put("other", 1))).training)
        assertEquals("keep", store.repeat!!.getString("futureField"))
        // A cycle recorded here is written over the stored set: the unknown field survives.
        val played = recordRepeat(repeatSetFromJson(store.repeat)!!, "a", true, 900, 200)
        store.repeat = played.toJson(store.repeat); store.kvStamps[SyncRecord.REPEAT_SET] = 200
        val out = SyncMerge.localChanges(store).single { it.id == SyncRecord.REPEAT_SET }
        assertEquals("keep", out.data!!.getJSONObject("value").getString("futureField"))
        assertEquals(1, out.data!!.getInt("other"))
        assertEquals(1, repeatSetFromJson(out.data!!.getJSONObject("value"))!!.round.results.size)
        // Cleared on another device.
        assertTrue(SyncMerge.apply(store, kv(SyncRecord.REPEAT_SET, 300, JSONObject().put("value", false))).training)
        assertNull(store.repeat)
        store.kvStamps[SyncRecord.REPEAT_SET] = 400
        assertFalse(SyncMerge.localChanges(store).single { it.id == SyncRecord.REPEAT_SET }.data!!.getBoolean("value"))
        // Something that is neither a set nor false is skipped.
        assertFalse(SyncMerge.apply(store, kv(SyncRecord.REPEAT_SET, 500, JSONObject().put("value", "x"))).any)
    }

    @Test fun backupCarriesTheTrainingValues() = runBlocking {
        val store = MemorySyncStore()
        store.goal = 20; store.days = mapOf("2026-11-28" to 7)
        store.repeat = RepeatSet("r1", 5, RepeatSource.MISTAKES, listOf("a")).toJson()
        val file = Backup.build(store, AppSettings(), 1_000)
        val other = MemorySyncStore()
        val restored = Backup.restore(other, file, AppSettings())
        assertTrue(restored.changes.training)
        assertEquals(20, other.goal)
        assertEquals(mapOf("2026-11-28" to 7), other.days)
        assertEquals("r1", other.repeat!!.getString("id"))
    }
}
