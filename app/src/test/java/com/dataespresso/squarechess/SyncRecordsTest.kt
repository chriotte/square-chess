package com.dataespresso.squarechess

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

internal fun syncGame(id: String = "g1", updated: Long = 1_000, moves: String = "e2e4 e7e5", result: String = "*") =
    SavedGame(id = id, mode = GameMode.COMPUTER.name, moves = moves, humanWhite = false, level = 6, result = result,
        resultReason = if (result == "*") "" else "Checkmate", updated = updated, orientationFlipped = true,
        hintsEnabled = true, evaluationEnabled = true, evaluations = "0:+20")

internal fun syncProgress(id: String = "00B3B", at: Long = 5_000) = PuzzleProgress(id, 2, 1, 0, 3, 1,
    AttemptResult.WITH_HELP.name, at, 0, at)

class SyncRecordsTest {
    @Test fun gameRoundTrip() {
        val game = syncGame()
        val data = game.toSyncData()
        assertEquals(JSONArray(listOf("e2e4", "e7e5")).toString(), data.getJSONArray("moves").toString())
        assertEquals("COMPUTER", data.getString("mode"))
        assertEquals(1_000L, data.getLong("updated"))
        assertTrue(data.isNull("clock"))
        assertEquals(game, savedGameFromSync("g1", 1_000, data))
    }

    @Test fun clockTravelsInTheWebShapeAndARunningClockComesBackPaused() {
        val clock = ClockState(ClockConfig(300_000, 2_000, 1_000), whiteMs = 250_000, blackMs = 240_000,
            active = ClockSide.BLACK, phase = ClockPhase.PAUSED, delayRemainingMs = 500)
        val game = syncGame().withClock(clock, 0)
        val json = game.toSyncData().getJSONObject("clock")
        assertEquals(300_000L, json.getJSONObject("config").getLong("baseMs"))
        assertEquals(2_000L, json.getJSONObject("config").getLong("incrementMs"))
        assertEquals("BLACK", json.getString("active"))
        assertEquals(0L, json.getLong("anchorMs"))
        assertEquals(clock, savedGameFromSync("g1", 1_000, game.toSyncData())!!.clockState())
        json.put("phase", "RUNNING")
        val back = savedGameFromSync("g1", 1_000, game.toSyncData().put("clock", json))!!.clockState()!!
        assertEquals(ClockPhase.PAUSED, back.phase)
        assertTrue(back.interrupted)
    }

    @Test fun unknownFieldsAreKept() {
        val base = syncGame().toSyncData().put("annotations", JSONArray(listOf("!!"))).put("clock",
            JSONObject().put("config", JSONObject().put("baseMs", 60_000).put("incrementMs", 0).put("bronstein", true))
                .put("whiteMs", 1).put("blackMs", 1).put("active", "WHITE").put("phase", "PAUSED").put("future", 3))
        val clock = ClockState(ClockConfig(60_000, 0), whiteMs = 50_000, blackMs = 40_000, phase = ClockPhase.PAUSED)
        val written = syncGame(moves = "e2e4 e7e5 g1f3", updated = 2_000).withClock(clock, 0).toSyncData(base)
        assertEquals("!!", written.getJSONArray("annotations").getString(0))
        assertEquals(3, written.getJSONObject("clock").getInt("future"))
        assertTrue(written.getJSONObject("clock").getJSONObject("config").getBoolean("bronstein"))
        assertEquals(50_000L, written.getJSONObject("clock").getLong("whiteMs"))
        assertEquals(3, written.getJSONArray("moves").length())
        // The base itself is not changed.
        assertEquals(2, base.getJSONArray("moves").length())
    }

    @Test fun damagedOrUnknownGamesAreNotLoaded() {
        assertNull(savedGameFromSync("g1", 1, syncGame().toSyncData().put("moves", JSONArray(listOf("e2e5")))))
        assertNull(savedGameFromSync("g1", 1, syncGame().toSyncData().put("mode", "ONLINE_BLITZ")))
        assertNull(savedGameFromSync("g1", 1, JSONObject()))
        // A clock that cannot be read is dropped; the game still loads.
        val noClock = savedGameFromSync("g1", 1, syncGame().toSyncData().put("clock", JSONObject().put("config", "x")))
        assertNotNull(noClock)
        assertNull(noClock!!.clockState())
        // Out-of-range values are clamped.
        assertEquals(10, savedGameFromSync("g1", 1, syncGame().toSyncData().put("level", 99))!!.level)
        assertEquals("*", savedGameFromSync("g1", 1, syncGame().toSyncData().put("result", "2-0"))!!.result)
    }

    @Test fun puzzleRoundTrip() {
        val p = syncProgress()
        val data = p.toSyncData()
        assertEquals("WITH_HELP", data.getString("lastResult"))
        assertEquals(p, puzzleProgressFromSync(p.puzzleId, p.lastAttemptAt, data))
        val noReview = p.copy(reviewStep = null, nextReviewAt = null)
        assertTrue(noReview.toSyncData().isNull("reviewStep"))
        assertEquals(noReview, puzzleProgressFromSync(p.puzzleId, p.lastAttemptAt, noReview.toSyncData()))
    }

    @Test fun trainingLevelValue() {
        assertEquals(1234, trainingLevelFromSync(trainingLevelToSyncData(1234)))
        assertNull(trainingLevelFromSync(JSONObject().put("value", 99_999)))
        assertNull(trainingLevelFromSync(JSONObject().put("value", "high")))
    }

    @Test fun outfitUsesTheAppTextForm() {
        val outfit = Outfit.decode("CROWN,skin:MEDIUM_DARK,hair:BLACK")
        val choices = PlayroomChoices(World.SPACE, Buddy.MAGNUS, outfit, unlockAll = true, seenStickers = setOf(Sticker.FIRST_GAME))
        val value = choices.toSyncData().getJSONObject("value")
        assertEquals("CROWN,skin:MEDIUM_DARK,hair:BLACK", value.getString("outfit"))
        assertEquals("SPACE", value.getString("world"))
        assertEquals("MAGNUS", value.getString("buddy"))
        assertTrue(value.getBoolean("unlockAll"))
        assertEquals("FIRST_GAME", value.getJSONArray("seenStickers").getString(0))
        assertEquals(choices, playroomFromSync(choices.toSyncData(), AppSettings().playroom))
    }

    @Test fun playroomKeepsWhatOnlyANewerVersionKnows() {
        val base = JSONObject().put("value", JSONObject()
            .put("world", "VOLCANO").put("buddy", "DRAGON").put("outfit", "CROWN,JETPACK,skin:MEDIUM,hair:PINK")
            .put("unlockAll", false).put("seenStickers", JSONArray(listOf("FIRST_GAME", "MOON_LANDING"))).put("theme", "night"))
            .put("note", "kept")
        val local = AppSettings().playroom
        val read = playroomFromSync(base, local)!!
        // Unknown world and buddy leave this device's; the known parts of the outfit are read.
        assertEquals(local.world, read.world)
        assertEquals(local.buddy, read.buddy)
        assertEquals(Outfit.decode("CROWN,skin:MEDIUM,hair:PINK"), read.outfit)
        assertEquals(setOf(Sticker.FIRST_GAME), read.seenStickers)
        // Written back unchanged: the unknown names survive.
        val same = read.toSyncData(base)
        assertEquals("kept", same.getString("note"))
        val value = same.getJSONObject("value")
        assertEquals("VOLCANO", value.getString("world"))
        assertEquals("DRAGON", value.getString("buddy"))
        assertEquals("CROWN,JETPACK,skin:MEDIUM,hair:PINK", value.getString("outfit"))
        assertEquals(2, value.getJSONArray("seenStickers").length())
        assertEquals("night", value.getString("theme"))
        // A change here replaces the known part; unknown names stay.
        val changed = read.copy(outfit = read.outfit.copy(hair = HairColour.BLONDE).with(Slot.HEAD, Item.BEANIE),
            seenStickers = setOf(Sticker.FIRST_GAME, Sticker.PROMOTION)).toSyncData(base).getJSONObject("value")
        assertEquals("BEANIE,JETPACK,skin:MEDIUM,hair:BLONDE", changed.getString("outfit"))
        assertEquals(listOf("FIRST_GAME", "PROMOTION", "MOON_LANDING"),
            changed.getJSONArray("seenStickers").let { a -> (0 until a.length()).map { a.getString(it) } })
        assertEquals("VOLCANO", changed.getString("world"))
        assertEquals("DRAGON", changed.getString("buddy"))
    }

    @Test fun aKnownWorldOrBuddyIsReplacedByTheChoiceHere() {
        val base = JSONObject().put("value", JSONObject().put("world", "FOREST").put("buddy", "ROBOT").put("outfit", ""))
        val value = PlayroomChoices(World.OCEAN, Buddy.SLOTH, Outfit(), false, emptySet()).toSyncData(base).getJSONObject("value")
        assertEquals("OCEAN", value.getString("world"))
        assertEquals("SLOTH", value.getString("buddy"))
        assertEquals("", value.getString("outfit"))
    }

    @Test fun recordJson() {
        val record = SyncRecord(SyncRecord.GAME, "g1", 5, deleted = true)
        val json = record.toJson()
        assertTrue(json.isNull("data"))
        assertEquals(record.copy(data = null), SyncRecord.fromJson(json))
        assertNull(SyncRecord.fromJson(JSONObject().put("kind", "secret").put("id", "x").put("updated", 1).put("data", JSONObject())))
        assertNull(SyncRecord.fromJson(JSONObject().put("kind", "game").put("id", "bad id!").put("updated", 1).put("data", JSONObject())))
        assertNull(SyncRecord.fromJson(JSONObject().put("kind", "game").put("id", "g").put("updated", 0).put("data", JSONObject())))
    }
}

class SyncMergeTest {
    private fun remote(game: SavedGame) = SyncRecord(SyncRecord.GAME, game.id, game.updated, data = game.toSyncData())

    @Test fun newerRemoteGameWinsOlderIsIgnored() = runBlocking {
        val store = MemorySyncStore()
        store.games["g1"] = syncGame(updated = 2_000)
        assertFalse(SyncMerge.apply(store, remote(syncGame(updated = 1_000, moves = "d2d4"))).any)
        assertEquals("e2e4 e7e5", store.games["g1"]!!.moves)
        assertEquals(setOf("g1"), SyncMerge.apply(store, remote(syncGame(updated = 3_000, moves = "d2d4"))).games)
        assertEquals("d2d4", store.games["g1"]!!.moves)
        assertEquals(3_000L, store.games["g1"]!!.updated)
    }

    @Test fun localChangesAreTheRecordsNewerThanTheirBase() = runBlocking {
        val store = MemorySyncStore()
        store.games["old"] = syncGame("old", 1_000)
        store.games["new"] = syncGame("new", 2_000)
        store.games["fresh"] = syncGame("fresh", 500)
        store.saveBase(remote(syncGame("old", 1_000)))
        store.saveBase(remote(syncGame("new", 1_500)))
        store.puzzles["p1"] = syncProgress("p1", 10)
        store.level = 1200; store.kvStamps[SyncRecord.TRAINING_LEVEL] = 77
        val keys = SyncMerge.localChanges(store).map { it.key }.toSet()
        assertEquals(setOf("game:new", "game:fresh", "puzzle:p1", "kv:trainingLevel"), keys)
    }

    @Test fun aValueWithoutAKnownTimeIsNotSent() = runBlocking {
        val store = MemorySyncStore()
        store.level = 1300
        assertTrue(SyncMerge.localChanges(store).isEmpty())
    }

    @Test fun localDeletionIsSentAsATombstoneUntilConfirmed() = runBlocking {
        val store = MemorySyncStore()
        store.saveBase(remote(syncGame("g1", 1_000)))
        store.tombs = mapOf("g1" to 4_000L)
        val tomb = SyncMerge.localChanges(store).single()
        assertTrue(tomb.deleted)
        assertNull(tomb.data)
        assertEquals(4_000L, tomb.updated)
        // The server echoes the tombstone back: confirmed, nothing more to send.
        SyncMerge.apply(store, tomb)
        assertEquals(emptyMap<String, Long>(), store.tombs)
        assertTrue(SyncMerge.localChanges(store).isEmpty())
    }

    @Test fun remoteDeletionRemovesTheGameUnlessItChangedLater() = runBlocking {
        val store = MemorySyncStore()
        store.games["g1"] = syncGame("g1", 1_000)
        store.games["g2"] = syncGame("g2", 9_000)
        assertEquals(setOf("g1"), SyncMerge.apply(store, SyncRecord(SyncRecord.GAME, "g1", 2_000, deleted = true)).games)
        assertNull(store.games["g1"])
        assertFalse(SyncMerge.apply(store, SyncRecord(SyncRecord.GAME, "g2", 2_000, deleted = true)).any)
        assertNotNull(store.games["g2"])
    }

    @Test fun aGameChangedElsewhereAfterItWasDeletedHereComesBack() = runBlocking {
        val store = MemorySyncStore()
        store.tombs = mapOf("g1" to 2_000L)
        assertFalse(SyncMerge.apply(store, remote(syncGame("g1", 1_500))).any)
        assertNull(store.games["g1"])
        SyncMerge.apply(store, remote(syncGame("g1", 3_000)))
        assertNotNull(store.games["g1"])
        assertEquals(emptyMap<String, Long>(), store.tombs)
    }

    @Test fun puzzleAndValuesFollowTheSameRule() = runBlocking {
        val store = MemorySyncStore()
        store.puzzles["p1"] = syncProgress("p1", 5_000)
        assertFalse(SyncMerge.apply(store, SyncRecord(SyncRecord.PUZZLE, "p1", 4_000, data = syncProgress("p1", 4_000).toSyncData())).any)
        assertTrue(SyncMerge.apply(store, SyncRecord(SyncRecord.PUZZLE, "p1", 6_000, data = syncProgress("p1", 6_000).toSyncData())).puzzles)
        assertEquals(6_000L, store.puzzles["p1"]!!.lastAttemptAt)

        store.level = 1100; store.kvStamps[SyncRecord.TRAINING_LEVEL] = 100
        assertTrue(SyncMerge.apply(store, SyncRecord(SyncRecord.KV, SyncRecord.TRAINING_LEVEL, 200, data = trainingLevelToSyncData(1500))).trainingLevel)
        assertEquals(1500, store.level)
        assertEquals(200L, store.kvStamps[SyncRecord.TRAINING_LEVEL])
        assertFalse(SyncMerge.apply(store, SyncRecord(SyncRecord.KV, SyncRecord.TRAINING_LEVEL, 150, data = trainingLevelToSyncData(900))).any)
        assertEquals(1500, store.level)

        val outfit = PlayroomChoices(World.JUNGLE, Buddy.PRINCESS, Outfit.decode("CROWN,skin:DARK"), false, emptySet())
        assertTrue(SyncMerge.apply(store, SyncRecord(SyncRecord.KV, SyncRecord.PLAYROOM, 300, data = outfit.toSyncData())).playroom)
        assertEquals(outfit, store.choices)
        // Unknown small values are stored as bases but change nothing.
        assertFalse(SyncMerge.apply(store, SyncRecord(SyncRecord.KV, "somethingNew", 400, data = JSONObject().put("value", 1))).any)
    }

    @Test fun unknownFieldsSurviveAWriteBack() = runBlocking {
        val store = MemorySyncStore()
        val fromNewerApp = syncGame("g1", 1_000).toSyncData().put("annotations", "keep me")
        SyncMerge.apply(store, SyncRecord(SyncRecord.GAME, "g1", 1_000, data = fromNewerApp))
        store.games["g1"] = store.games["g1"]!!.copy(moves = "e2e4 e7e5 g1f3", updated = 2_000)
        val out = SyncMerge.localChanges(store).single()
        assertEquals("keep me", out.data!!.getString("annotations"))
        assertEquals(3, out.data!!.getJSONArray("moves").length())
    }
}
