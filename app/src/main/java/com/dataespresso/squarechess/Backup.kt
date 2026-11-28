package com.dataespresso.squarechess

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A year of daily games is far below this; a larger file is not a backup from this app. */
const val MAX_BACKUP_BYTES = 32 * 1024 * 1024

/**
 * A full backup in one JSON file, without an account: games, puzzle progress, the training level,
 * the Playroom and this device's settings. Records have the same shape as the website's sync and
 * export (`{kind, id, updated, data}`), so a file downloaded from the account screen
 * (GET /api/export) can be restored too.
 */
object Backup {
    const val FORMAT = "square-chess-backup"
    const val VERSION = 1
    /** In backups only, never synced: sound, e-ink and the other choices that belong to one device. */
    const val DEVICE_SETTINGS = "deviceSettings"

    suspend fun build(store: SyncStore, settings: AppSettings, now: Long): JSONObject {
        val records = JSONArray()
        for (id in store.gameStamps().keys) store.game(id)?.let {
            records.put(SyncRecord(SyncRecord.GAME, id, it.updated, data = it.toSyncData()).toJson())
        }
        for (id in store.puzzleStamps().keys) store.puzzle(id)?.let {
            records.put(SyncRecord(SyncRecord.PUZZLE, id, it.lastAttemptAt, data = it.toSyncData()).toJson())
        }
        // A value without a known change time gets the oldest valid time, so any dated copy wins over it.
        store.trainingLevel()?.let {
            records.put(SyncRecord(SyncRecord.KV, SyncRecord.TRAINING_LEVEL, store.kvUpdated(SyncRecord.TRAINING_LEVEL).coerceAtLeast(1),
                data = trainingLevelToSyncData(it)).toJson())
        }
        records.put(SyncRecord(SyncRecord.KV, SyncRecord.PLAYROOM, store.kvUpdated(SyncRecord.PLAYROOM).coerceAtLeast(1),
            data = settings.playroom.toSyncData()).toJson())
        // The daily goal, puzzles per day and repeat set, when this device has them.
        for (key in listOf(SyncRecord.PUZZLE_GOAL, SyncRecord.PUZZLE_DAYS, SyncRecord.REPEAT_SET, SyncRecord.GAME_RATING, SyncRecord.RATING_DAYS)) {
            if (key == SyncRecord.REPEAT_SET && store.repeatSet() == null) continue
            val data = SyncMerge.kvData(store, key, null) ?: continue
            records.put(SyncRecord(SyncRecord.KV, key, store.kvUpdated(key).coerceAtLeast(1), data = data).toJson())
        }
        records.put(SyncRecord(SyncRecord.KV, DEVICE_SETTINGS, now, data = JSONObject().put("value", deviceSettingsJson(settings))).toJson())
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return JSONObject().put("format", FORMAT).put("version", VERSION).put("exported", iso.format(Date(now))).put("records", records)
    }

    data class Restored(val games: Int, val puzzles: Int, val settings: AppSettings?, val changes: SyncChanges)

    /**
     * Merges [backup] into this device: for each record the newer copy wins, as in sync, so a
     * restore never replaces a newer game with an older one. Device settings in the file are
     * returned for the caller to apply. Throws IllegalArgumentException for a file that is not a backup.
     */
    suspend fun restore(store: SyncStore, backup: JSONObject, current: AppSettings): Restored {
        val list = backup.optJSONArray("records") ?: throw IllegalArgumentException("Not a Square Chess backup")
        var changes = SyncChanges()
        var puzzles = 0
        var settings: AppSettings? = null
        for (i in 0 until list.length()) {
            val json = list.optJSONObject(i) ?: continue
            if (json.optString("kind") == SyncRecord.KV && json.optString("id") == DEVICE_SETTINGS) {
                settings = json.optJSONObject("data")?.optJSONObject("value")?.let { deviceSettingsFrom(it, current) }
                continue
            }
            val record = SyncRecord.fromJson(json) ?: continue
            val applied = SyncMerge.apply(store, record, asBase = false)
            if (applied.puzzles) puzzles++
            changes += applied
        }
        return Restored(changes.games.size, puzzles, settings, changes)
    }

    fun deviceSettingsJson(s: AppSettings): JSONObject = JSONObject()
        .put("sound", s.sound).put("haptics", s.haptics).put("coordinates", s.coordinates).put("legalMoves", s.legalMoves)
        .put("boardTheme", s.boardTheme.name).put("eink", s.eink).put("einkGreySquares", s.einkGreySquares)
        .put("faceToFace", s.faceToFace).put("spokenMovesInWords", s.spokenMovesInWords).put("playful", s.playful)

    fun deviceSettingsFrom(v: JSONObject, s: AppSettings): AppSettings = s.copy(
        sound = v.optBoolean("sound", s.sound), haptics = v.optBoolean("haptics", s.haptics),
        coordinates = v.optBoolean("coordinates", s.coordinates), legalMoves = v.optBoolean("legalMoves", s.legalMoves),
        boardTheme = BoardTheme.entries.firstOrNull { it.name == v.optString("boardTheme") } ?: s.boardTheme,
        eink = v.optBoolean("eink", s.eink), einkGreySquares = v.optBoolean("einkGreySquares", s.einkGreySquares),
        faceToFace = v.optBoolean("faceToFace", s.faceToFace),
        spokenMovesInWords = v.optBoolean("spokenMovesInWords", s.spokenMovesInWords),
        playful = v.optBoolean("playful", s.playful)
    )

    fun fileName(now: Long = System.currentTimeMillis()): String =
        "square-chess-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))}.json"
}
