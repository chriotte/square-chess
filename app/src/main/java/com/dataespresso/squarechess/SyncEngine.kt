package com.dataespresso.squarechess

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
                       val trainingLevel: Boolean = false, val playroom: Boolean = false) {
    val any: Boolean get() = games.isNotEmpty() || puzzles || trainingLevel || playroom
    operator fun plus(other: SyncChanges) = SyncChanges(games + other.games, puzzles || other.puzzles,
        trainingLevel || other.trainingLevel, playroom || other.playroom)
}

/** Last write wins per record, by `updated`, as on the website (site/play/js/account.js). */
object SyncMerge {
    val KV_KEYS = listOf(SyncRecord.TRAINING_LEVEL, SyncRecord.PLAYROOM)

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
            val data = when (key) {
                SyncRecord.TRAINING_LEVEL -> store.trainingLevel()?.let { trainingLevelToSyncData(it, base?.data) }
                else -> store.playroom().toSyncData(base?.data)
            } ?: continue
            out += SyncRecord(SyncRecord.KV, key, updated, data = data)
        }
        return out
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
                    else -> SyncChanges()
                }
            }
            else -> SyncChanges()
        }
    }
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
            if (key == SyncRecord.TRAINING_LEVEL && store.trainingLevel() == null) continue
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
