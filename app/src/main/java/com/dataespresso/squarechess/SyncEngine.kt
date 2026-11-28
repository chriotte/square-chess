package com.dataespresso.squarechess

import org.json.JSONObject

/**
 * What sync reads and writes on this device. The Room and SharedPreferences version is
 * [AppSyncStore]; tests use an in-memory one.
 *
 * A "base" is the last copy of a record seen from the server. It decides what has changed here
 * (a game whose `updated` is newer than its base) and keeps fields this version does not know,
 * so they are written back unchanged.
 */
interface SyncStore {
    suspend fun gameStamps(): Map<String, Long>
    suspend fun game(id: String): SavedGame?
    suspend fun saveGame(game: SavedGame)
    suspend fun deleteGame(id: String)
    suspend fun puzzleStamps(): Map<String, Long>
    suspend fun puzzle(id: String): PuzzleProgress?
    suspend fun savePuzzle(progress: PuzzleProgress)
    fun trainingLevel(): Int?
    fun setTrainingLevel(level: Int)
    fun playroom(): PlayroomChoices
    fun setPlayroom(choices: PlayroomChoices)
    /** The daily puzzle goal, or null when it was never chosen here. */
    fun puzzleGoal(): Int?
    fun setPuzzleGoal(goal: Int)
    fun puzzleDays(): Map<String, Int>
    fun setPuzzleDays(days: Map<String, Int>)
    /** The repeat set as stored, in the web's JSON format, or null when there is none. */
    fun repeatSet(): JSONObject?
    fun setRepeatSet(set: JSONObject?)
    /** The game rating, or null before the first rated game here. */
    fun gameRating(): GameRating?
    fun setGameRating(rating: GameRating)
    fun ratingDays(): RatingDays
    fun setRatingDays(days: RatingDays)
    /** When a small value last changed here; 0 when not known (for example before this version). */
    fun kvUpdated(key: String): Long
    fun setKvUpdated(key: String, updated: Long)
    /** Games deleted here that the server has not confirmed yet, with the time of deletion. */
    fun tombstones(): Map<String, Long>
    fun setTombstones(tombstones: Map<String, Long>)
    suspend fun bases(): Map<String, SyncRecord>
    suspend fun saveBase(record: SyncRecord)
}

/** What a sync changed on this device, so open screens can refresh. */
data class SyncChanges(val games: Set<String> = emptySet(), val puzzles: Boolean = false,
                       val trainingLevel: Boolean = false, val playroom: Boolean = false,
                       /** The daily goal, the puzzles per day or the repeat set. */
                       val training: Boolean = false) {
    val any: Boolean get() = games.isNotEmpty() || puzzles || trainingLevel || playroom || training
    operator fun plus(other: SyncChanges) = SyncChanges(games + other.games, puzzles || other.puzzles,
        trainingLevel || other.trainingLevel, playroom || other.playroom, training || other.training)
}

/** Last write wins per record, by `updated`, as on the website (site/play/js/account.js). */
object SyncMerge {
    val KV_KEYS = listOf(SyncRecord.TRAINING_LEVEL, SyncRecord.PLAYROOM, SyncRecord.PUZZLE_GOAL, SyncRecord.PUZZLE_DAYS, SyncRecord.REPEAT_SET,
        SyncRecord.GAME_RATING, SyncRecord.RATING_DAYS)
    /** Values merged with the server's copy rather than replaced by the newer one. */
    val MERGED = setOf(SyncRecord.PUZZLE_DAYS, SyncRecord.GAME_RATING, SyncRecord.RATING_DAYS)

    /** Records changed here since the server last saw them, written over their bases. */
    suspend fun localChanges(store: SyncStore): List<SyncRecord> {
        val bases = store.bases()
        val out = mutableListOf<SyncRecord>()
        val tombstones = store.tombstones()
        for ((id, updated) in store.gameStamps()) {
            if (id in tombstones) continue
            val base = bases["${SyncRecord.GAME}:$id"]
            if (base == null || updated > base.updated) {
                val game = store.game(id) ?: continue
                out += SyncRecord(SyncRecord.GAME, id, game.updated, data = game.toSyncData(base?.data))
            }
        }
        for ((id, deletedAt) in tombstones) {
            val base = bases["${SyncRecord.GAME}:$id"]
            if (base == null || !base.deleted || base.updated < deletedAt) out += SyncRecord(SyncRecord.GAME, id, deletedAt, deleted = true)
        }
        for ((id, updated) in store.puzzleStamps()) {
            val base = bases["${SyncRecord.PUZZLE}:$id"]
            if (base == null || updated > base.updated) {
                val progress = store.puzzle(id) ?: continue
                out += SyncRecord(SyncRecord.PUZZLE, id, progress.lastAttemptAt, data = progress.toSyncData(base?.data))
            }
        }
        for (key in KV_KEYS) {
            val updated = store.kvUpdated(key)
            val base = bases["${SyncRecord.KV}:$key"]
            if (updated <= 0 || (base != null && updated <= base.updated)) continue
            val data = kvData(store, key, base?.data) ?: continue
            out += SyncRecord(SyncRecord.KV, key, updated, data = data)
        }
        return out
    }

    /** A small value as record data, written over [base] so unknown fields survive; null when not set. */
    fun kvData(store: SyncStore, key: String, base: JSONObject?): JSONObject? = when (key) {
        SyncRecord.TRAINING_LEVEL -> store.trainingLevel()?.let { trainingLevelToSyncData(it, base) }
        SyncRecord.PLAYROOM -> store.playroom().toSyncData(base)
        SyncRecord.PUZZLE_GOAL -> store.puzzleGoal()?.let { puzzleGoalToSyncData(it, base) }
        SyncRecord.PUZZLE_DAYS -> store.puzzleDays().takeIf { it.isNotEmpty() }?.let { puzzleDaysToSyncData(it, base) }
        SyncRecord.REPEAT_SET -> repeatSetToSyncData(store.repeatSet(), base)
        SyncRecord.GAME_RATING -> store.gameRating()?.let { gameRatingToSyncData(it, base) }
        SyncRecord.RATING_DAYS -> store.ratingDays().takeIf { it.isNotEmpty() }?.let { ratingDaysToSyncData(it, base) }
        else -> null
    }

    /**
     * Applies one record from the server (or from a backup file): the newer copy wins. The
     * server's copy always becomes the base when [asBase] is set, even when this device's copy is
     * newer, because the base is what the server holds.
     */
    suspend fun apply(store: SyncStore, record: SyncRecord, asBase: Boolean = true): SyncChanges {
        if (asBase) store.saveBase(record)
        return when (record.kind) {
            SyncRecord.GAME -> {
                val tombstones = store.tombstones()
                val tomb = tombstones[record.id]
                val local = store.game(record.id)
                if (record.updated <= maxOf(tomb ?: 0, local?.updated ?: 0)) {
                    // The server confirmed this device's deletion: the tombstone is no longer needed.
                    if (tomb != null && record.deleted && record.updated >= tomb) store.setTombstones(tombstones - record.id)
                    return SyncChanges()
                }
                if (record.deleted) {
                    if (local == null && tomb == null) return SyncChanges()
                    store.deleteGame(record.id)
                } else {
                    val game = record.data?.let { savedGameFromSync(record.id, record.updated, it) } ?: return SyncChanges()
                    store.saveGame(game)
                }
                if (tomb != null) store.setTombstones(tombstones - record.id)
                SyncChanges(games = setOf(record.id))
            }
            SyncRecord.PUZZLE -> {
                val local = store.puzzle(record.id)
                if (record.deleted || (local != null && local.lastAttemptAt >= record.updated)) return SyncChanges()
                val progress = record.data?.let { puzzleProgressFromSync(record.id, record.updated, it) } ?: return SyncChanges()
                store.savePuzzle(progress)
                SyncChanges(puzzles = true)
            }
            SyncRecord.KV -> {
                if (record.id in MERGED && !record.deleted) return mergeValue(store, record)
                if (record.deleted || store.kvUpdated(record.id) >= record.updated) return SyncChanges()
                val data = record.data ?: return SyncChanges()
                when (record.id) {
                    SyncRecord.TRAINING_LEVEL -> {
                        store.setTrainingLevel(trainingLevelFromSync(data) ?: return SyncChanges())
                        store.setKvUpdated(record.id, record.updated)
                        SyncChanges(trainingLevel = true)
                    }
                    SyncRecord.PLAYROOM -> {
                        store.setPlayroom(playroomFromSync(data, store.playroom()) ?: return SyncChanges())
                        store.setKvUpdated(record.id, record.updated)
                        SyncChanges(playroom = true)
                    }
                    SyncRecord.PUZZLE_GOAL -> {
                        store.setPuzzleGoal(puzzleGoalFromSync(data) ?: return SyncChanges())
                        store.setKvUpdated(record.id, record.updated)
                        SyncChanges(training = true)
                    }
                    SyncRecord.REPEAT_SET -> {
                        val value = repeatSetFromSync(data)
                        if (!value.valid) return SyncChanges()
                        store.setRepeatSet(value.set)
                        store.setKvUpdated(record.id, record.updated)
                        SyncChanges(training = true)
                    }
                    else -> SyncChanges()
                }
            }
            else -> SyncChanges()
        }
    }
}

/**
 * Values merged, not replaced, the same way on every device: puzzles per day (the larger count for
 * each day), the game rating (the copy with more games, then the higher rating) and the rating
 * history (per day and rating, the later value). When the merged value differs from the server's,
 * the change time moves past the server's copy so the merged value is sent back.
 */
private fun mergeValue(store: SyncStore, record: SyncRecord): SyncChanges {
    val data = record.data ?: return SyncChanges()
    val (changedHere, sameAsServer) = when (record.id) {
        SyncRecord.PUZZLE_DAYS -> {
            val incoming = puzzleDaysFromSync(data); val local = store.puzzleDays()
            val merged = mergeDays(local, incoming)
            if (merged != local) store.setPuzzleDays(merged)
            (merged != local) to (merged == incoming)
        }
        SyncRecord.GAME_RATING -> {
            val incoming = gameRatingFromSync(data) ?: return SyncChanges()
            val local = store.gameRating()
            val merged = mergeGameRating(local, incoming)!!
            if (merged != local) store.setGameRating(merged)
            (merged != local) to (merged == incoming)
        }
        SyncRecord.RATING_DAYS -> {
            val incoming = ratingDaysFromSync(data); val local = store.ratingDays()
            val merged = mergeRatingDays(local, incoming)
            if (merged != local) store.setRatingDays(merged)
            (merged != local) to (merged == incoming)
        }
        else -> return SyncChanges()
    }
    store.setKvUpdated(record.id, if (!sameAsServer) maxOf(store.kvUpdated(record.id), record.updated) + 1 else record.updated)
    return SyncChanges(training = changedHere)
}

/** The server's position in its change list, and the account's token; kept by [AccountStore] in the app. */
interface SyncSession {
    val token: String?
    var cursor: Long
}

/**
 * One full sync: push local changes in batches, apply what the server sends back, and repeat
 * while either side has more. Throws [SyncError] on failure; nothing is lost, the next sync
 * sends the same changes again.
 */
class SyncEngine(private val api: SyncApi, private val store: SyncStore, private val session: SyncSession) {
    suspend fun run(): SyncChanges {
        val token = session.token ?: throw SyncError.SignedOut()
        var changes = SyncChanges()
        val pending = ArrayDeque(SyncMerge.localChanges(store))
        var rounds = 0
        while (true) {
            val batch = List(minOf(BATCH, pending.size)) { pending.removeFirst() }
            val reply = api.sync(token, session.cursor, batch)
            for (record in reply.records) changes += SyncMerge.apply(store, record)
            session.cursor = maxOf(session.cursor, reply.cursor)
            // A runaway server answer must not loop for ever; the rest comes at the next sync.
            if ((pending.isEmpty() && !reply.more) || ++rounds >= MAX_ROUNDS) break
        }
        // Values merged with the server's are sent back at once, so other devices see the merged copy.
        val merged = SyncMerge.localChanges(store).filter { it.kind == SyncRecord.KV && it.id in SyncMerge.MERGED }
        if (merged.isNotEmpty()) {
            val reply = api.sync(token, session.cursor, merged)
            for (record in reply.records) changes += SyncMerge.apply(store, record)
            session.cursor = maxOf(session.cursor, reply.cursor)
        }
        return changes
    }

    /**
     * After the first sync of a sign-in: when the account has no Playroom or training level yet,
     * this device's own values go up. When it has them, the account's values have already won.
     */
    suspend fun keepLocalValuesIfAccountHasNone(now: Long): Boolean {
        val bases = store.bases()
        var stamped = false
        for (key in SyncMerge.KV_KEYS) {
            if ("${SyncRecord.KV}:$key" in bases || store.kvUpdated(key) > 0) continue
            if (SyncMerge.kvData(store, key, null) == null) continue
            if (key == SyncRecord.REPEAT_SET && store.repeatSet() == null) continue
            store.setKvUpdated(key, now); stamped = true
        }
        return stamped
    }

    companion object {
        /** Under the server's limit of 500 records per request. */
        const val BATCH = 400
        private const val MAX_ROUNDS = 200
    }
}
