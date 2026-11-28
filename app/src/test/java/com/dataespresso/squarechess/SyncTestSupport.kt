package com.dataespresso.squarechess

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64

/** Sync's storage in memory, for tests. */
class MemorySyncStore : SyncStore {
    val games = linkedMapOf<String, SavedGame>()
    val puzzles = linkedMapOf<String, PuzzleProgress>()
    var level: Int? = null
    var choices = AppSettings().playroom
    val kvStamps = mutableMapOf<String, Long>()
    var tombs: Map<String, Long> = emptyMap()
    val baseRecords = linkedMapOf<String, SyncRecord>()

    override suspend fun gameStamps() = games.mapValues { it.value.updated }
    override suspend fun game(id: String) = games[id]
    override suspend fun saveGame(game: SavedGame) { games[game.id] = game }
    override suspend fun deleteGame(id: String) { games.remove(id) }
    override suspend fun puzzleStamps() = puzzles.mapValues { it.value.lastAttemptAt }
    override suspend fun puzzle(id: String) = puzzles[id]
    override suspend fun savePuzzle(progress: PuzzleProgress) { puzzles[progress.puzzleId] = progress }
    override fun trainingLevel() = level
    override fun setTrainingLevel(level: Int) { this.level = level }
    override fun playroom() = choices
    override fun setPlayroom(choices: PlayroomChoices) { this.choices = choices }
    var goal: Int? = null
    var days: Map<String, Int> = emptyMap()
    var repeat: JSONObject? = null
    override fun puzzleGoal() = goal
    override fun setPuzzleGoal(goal: Int) { this.goal = goal }
    override fun puzzleDays() = days
    override fun setPuzzleDays(days: Map<String, Int>) { this.days = days }
    override fun repeatSet() = repeat
    override fun setRepeatSet(set: JSONObject?) { repeat = set }
    var game: GameRating? = null
    var history: RatingDays = emptyMap()
    override fun gameRating() = game
    override fun setGameRating(rating: GameRating) { game = rating }
    override fun ratingDays() = history
    override fun setRatingDays(days: RatingDays) { history = days }
    override fun kvUpdated(key: String) = kvStamps[key] ?: 0
    override fun setKvUpdated(key: String, updated: Long) { kvStamps[key] = updated }
    override fun tombstones() = tombs
    override fun setTombstones(tombstones: Map<String, Long>) { tombs = tombstones }
    override suspend fun bases() = baseRecords.toMap()
    override suspend fun saveBase(record: SyncRecord) { baseRecords[record.key] = record }
}

/** A plain in-memory SharedPreferences, enough for AccountStore. */
class MemoryPrefs : SharedPreferences {
    val values = mutableMapOf<String, Any?>()
    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = values[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        val changes = mutableMapOf<String, Any?>()
        var clear = false
        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { changes[key] = values }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun remove(key: String) = apply { changes[key] = null }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) values.clear()
            for ((k, v) in changes) if (v == null) values.remove(k) else values[k] = v
        }
    }
}

/**
 * The website's API in miniature, following site/api/index.php: the token endpoint with PKCE,
 * Bearer tokens, last-write-wins sync with a sequence cursor, logout and delete. Only test data.
 */
class FakeSyncServer(private val pageSize: Int = 2000) : AutoCloseable {
    data class Stored(val kind: String, val id: String, var updated: Long, var deleted: Boolean, var data: JSONObject?, var seq: Long)

    private val server = MiniHttpServer { handle(it) }
    val url: String get() = server.url
    val records = linkedMapOf<String, Stored>()
    private var seq = 0L
    /** code -> challenge and expiry. */
    val codes = mutableMapOf<String, Pair<String, Long>>()
    val tokens = mutableSetOf<String>()
    var username = "tester"
    var password = "correct horse"
    var now: () -> Long = System::currentTimeMillis
    val requests = mutableListOf<String>()
    var lastTokenForm: Map<String, String> = emptyMap()

    override fun close() = server.close()

    /** What the authorize page would issue after the player pressed Allow. */
    fun issueCode(challenge: String, lifetimeMs: Long = 120_000): String {
        val code = (1..64).map { "0123456789abcdef".random() }.joinToString("")
        codes[code] = challenge to now() + lifetimeMs
        return code
    }
    fun newToken(): String = (1..64).map { "0123456789abcdef".random() }.joinToString("").also { tokens += it }

    fun put(kind: String, id: String, updated: Long, data: JSONObject?, deleted: Boolean = false) {
        records["$kind:$id"] = Stored(kind, id, updated, deleted, data, ++seq)
    }

    private fun handle(x: MiniHttpServer.Request): MiniHttpServer.Response {
        var out = MiniHttpServer.Response(500, "{}")
        val path = x.path.removePrefix("/api/")
        requests += "${x.method} $path"
        val body = x.body
        val bearer = x.header("Authorization")?.removePrefix("Bearer ")?.takeIf { it in tokens }
        fun reply(status: Int, json: JSONObject) { out = MiniHttpServer.Response(status, json.toString()) }
        fun fail(status: Int, code: String) = reply(status, JSONObject().put("error", code).put("message", code))
        fun ok(extra: JSONObject = JSONObject()) = reply(200, extra.put("ok", true))
        when ("${x.method} $path") {
            "POST oauth/token" -> {
                val form = body.split('&').filter { it.isNotEmpty() }.associate {
                    val (k, v) = (it.split('=', limit = 2) + "").take(2); URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
                }
                lastTokenForm = form
                val code = form["code"].orEmpty()
                val entry = codes.remove(code) // single use, as on the server
                val verifier = form["code_verifier"].orEmpty()
                val expected = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
                if (entry == null || entry.second <= now() || entry.first != expected ||
                    form["client_id"] != "square-chess-android" || form["redirect_uri"] != "org.squarechess.app:/oauth") fail(400, "invalid_grant")
                else ok(JSONObject().put("access_token", newToken()).put("token_type", "Bearer").put("expires_in", 31536000))
            }
            "GET me" -> ok(JSONObject().put("user", if (bearer == null) JSONObject.NULL else JSONObject().put("username", username)))
            "POST logout" -> { bearer?.let { tokens -= it }; ok() }
            "POST delete" -> when {
                bearer == null -> fail(401, "signed_out")
                JSONObject(body).optString("password") != password -> fail(403, "wrong_password")
                else -> { tokens.clear(); records.clear(); ok() }
            }
            "POST sync" -> {
                if (bearer == null) { fail(401, "signed_out"); return out }
                val b = JSONObject(body)
                val list = b.optJSONArray("records") ?: JSONArray()
                if (list.length() > 500) { fail(400, "bad_records"); return out }
                for (i in 0 until list.length()) {
                    val r = list.getJSONObject(i)
                    val key = "${r.getString("kind")}:${r.getString("id")}"
                    val updated = r.getLong("updated")
                    val deleted = r.optBoolean("deleted")
                    val existing = records[key]
                    if (existing == null) put(r.getString("kind"), r.getString("id"), updated, if (deleted) null else r.getJSONObject("data"), deleted)
                    else if (updated > existing.updated) {
                        existing.updated = updated; existing.deleted = deleted
                        existing.data = if (deleted) null else r.getJSONObject("data"); existing.seq = ++seq
                    }
                }
                var cursor = b.optLong("cursor", 0)
                val page = JSONArray()
                records.values.filter { it.seq > cursor }.sortedBy { it.seq }.take(pageSize).forEach {
                    cursor = maxOf(cursor, it.seq)
                    page.put(JSONObject().put("kind", it.kind).put("id", it.id).put("updated", it.updated)
                        .put("deleted", it.deleted).put("data", it.data ?: JSONObject.NULL))
                }
                ok(JSONObject().put("cursor", cursor).put("records", page).put("more", page.length() == pageSize))
            }
            else -> fail(404, "not_found")
        }
        return out
    }
}
