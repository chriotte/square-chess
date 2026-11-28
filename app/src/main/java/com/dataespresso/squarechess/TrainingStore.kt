package com.dataespresso.squarechess

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/*
 * The daily goal, the puzzles per day and the repeat set, kept in the "puzzles" preferences next
 * to the training level. The day counts and the repeat set are stored as JSON in the format the
 * web version syncs (docs/accounts.md in the web repository), so they pass through sync unchanged.
 */

fun daysToJson(days: Map<String, Int>): JSONObject = JSONObject().apply { days.toSortedMap().forEach { (k, v) -> put(k, v) } }

/** Day counts from JSON; anything that is not a "yyyy-MM-dd" key with a count is skipped. */
fun daysFromJson(json: JSONObject?): Map<String, Int> {
    if (json == null) return emptyMap()
    val out = HashMap<String, Int>()
    for (key in json.keys()) {
        val count = (json.opt(key) as? Number)?.toInt() ?: continue
        if (DAY.matches(key) && count > 0) out[key] = count
    }
    return out
}
private val DAY = Regex("""\d{4}-\d{2}-\d{2}""")

/** The repeat set as JSON, written over [base] so fields this version does not know are kept. */
fun RepeatSet.toJson(base: JSONObject? = null): JSONObject {
    val json = if (base == null) JSONObject() else JSONObject(base.toString())
    json.put("id", id).put("created", created).put("source", source.key).put("ids", JSONArray(ids))
    json.put("cycles", JSONArray(cycles.map {
        JSONObject().put("index", it.index).put("finished", it.finished).put("clean", it.clean).put("total", it.total).put("ms", it.ms)
    }))
    json.put("current", current?.let { round ->
        JSONObject().put("index", round.index).put("results", JSONArray(round.results.map {
            JSONObject().put("id", it.id).put("clean", it.clean).put("ms", it.ms)
        }))
    } ?: JSONObject.NULL)
    return json
}

/** A repeat set from JSON, or null when it is missing, cleared (false) or unreadable. */
fun repeatSetFromJson(json: JSONObject?): RepeatSet? = json?.let {
    runCatching {
        val ids = it.getJSONArray("ids").let { a -> (0 until a.length()).map(a::getString) }
        val cycles = it.optJSONArray("cycles")?.let { a ->
            (0 until a.length()).map { i ->
                val c = a.getJSONObject(i)
                RepeatCycle(c.getInt("index"), c.optLong("finished"), c.optInt("clean"), c.optInt("total"), c.optLong("ms"))
            }
        }.orEmpty()
        val current = it.optJSONObject("current")?.let { c ->
            val results = c.optJSONArray("results")?.let { a ->
                (0 until a.length()).map { i -> a.getJSONObject(i).let { r -> RepeatResult(r.getString("id"), r.optBoolean("clean"), r.optLong("ms")) } }
            }.orEmpty()
            RepeatRound(c.optInt("index", cycles.size + 1), results)
        }
        val source = RepeatSource.entries.firstOrNull { s -> s.key == it.optString("source") } ?: RepeatSource.NEAR
        RepeatSet(it.getString("id"), it.optLong("created"), source, ids, cycles, current).takeIf { s -> s.ids.isNotEmpty() }
    }.getOrNull()
}

fun GameRating.toJson(): JSONObject = JSONObject().put("rating", rating).put("games", games).put("wins", wins)
    .put("draws", draws).put("losses", losses)
fun gameRatingFromJson(json: JSONObject?): GameRating? = json?.let {
    runCatching {
        GameRating(it.getInt("rating"), it.optInt("games"), it.optInt("wins"), it.optInt("draws"), it.optInt("losses"))
    }.getOrNull()
}

/** `{"2026-11-28": {"p": [1243, ms], "g": [1180, ms]}}`; anything unreadable is skipped. */
fun ratingDaysToJson(days: RatingDays): JSONObject = JSONObject().apply {
    days.toSortedMap().forEach { (day, values) ->
        put(day, JSONObject().apply { values.toSortedMap().forEach { (which, p) -> put(which, JSONArray().put(p.value).put(p.ms)) } })
    }
}
fun ratingDaysFromJson(json: JSONObject?): RatingDays {
    if (json == null) return emptyMap()
    val out = HashMap<String, Map<String, RatingPoint>>()
    for (day in json.keys()) {
        val values = json.optJSONObject(day) ?: continue
        if (!DAY.matches(day)) continue
        val points = HashMap<String, RatingPoint>()
        for (which in values.keys()) {
            val pair = values.optJSONArray(which) ?: continue
            val v = (pair.opt(0) as? Number)?.toInt() ?: continue
            val ms = (pair.opt(1) as? Number)?.toLong() ?: continue
            points[which] = RatingPoint(v, ms)
        }
        out[day] = points
    }
    return out
}

/**
 * The training values in the puzzle preferences. Each change also stores when it was made
 * (`<key>.updated`), which sync uses to keep the newer copy.
 */
class TrainingPrefs(private val prefs: SharedPreferences, private val now: () -> Long = System::currentTimeMillis) {
    var goal: Int
        get() = prefs.getInt(GOAL_KEY, DEFAULT_PUZZLE_GOAL)
        set(value) { prefs.edit().putInt(GOAL_KEY, value).putLong(GOAL_KEY + UPDATED, now()).apply() }

    var days: Map<String, Int>
        get() = daysFromJson(prefs.getString(DAYS_KEY, null)?.let { runCatching { JSONObject(it) }.getOrNull() })
        set(value) { prefs.edit().putString(DAYS_KEY, daysToJson(value).toString()).putLong(DAYS_KEY + UPDATED, now()).apply() }

    /** The stored JSON, kept whole so fields from a newer version survive a rewrite. */
    private fun repeatJson(): JSONObject? = prefs.getString(REPEAT_KEY, null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    var repeatSet: RepeatSet?
        get() = repeatSetFromJson(repeatJson())
        set(value) {
            val edit = prefs.edit().putLong(REPEAT_KEY + UPDATED, now())
            if (value == null) edit.remove(REPEAT_KEY) else edit.putString(REPEAT_KEY, value.toJson(repeatJson()?.takeIf { it.optString("id") == value.id }).toString())
            edit.apply()
        }

    /** The puzzle rating, stored as the training level so existing players keep their number. */
    val puzzleRating: Int get() = prefs.getInt(LEVEL_KEY, START_PUZZLE_RATING)

    var gameRating: GameRating
        get() = gameRatingFromJson(prefs.getString(GAME_RATING_KEY, null)?.let { runCatching { JSONObject(it) }.getOrNull() }) ?: GameRating()
        set(value) { prefs.edit().putString(GAME_RATING_KEY, value.toJson().toString()).putLong(GAME_RATING_KEY + UPDATED, now()).apply() }

    var ratingDays: RatingDays
        get() = ratingDaysFromJson(prefs.getString(RATING_DAYS_KEY, null)?.let { runCatching { JSONObject(it) }.getOrNull() })
        set(value) { prefs.edit().putString(RATING_DAYS_KEY, ratingDaysToJson(value).toString()).putLong(RATING_DAYS_KEY + UPDATED, now()).apply() }

    private fun remember(which: String, value: Int) {
        val time = now()
        val today = java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        ratingDays = recordRatingDay(ratingDays, which, value, dayKey(today), time, dayKey(today.minusDays(RATING_KEEP_DAYS)))
    }

    /** Saves a new puzzle rating and today's point for the chart. */
    fun savePuzzleRating(value: Int) {
        prefs.edit().putInt(LEVEL_KEY, value).putLong(LEVEL_KEY + UPDATED, now()).apply()
        remember(PUZZLE_SERIES, value)
    }

    /**
     * Rates a finished game, once: returns it with its rating change, or unchanged when it does not
     * count (not rated, too short, still in progress, or rated already).
     */
    fun rateFinishedGame(g: SavedGame): SavedGame {
        if (!countsForRating(g) || g.rating != null) return g
        val score = humanScore(g) ?: return g
        val before = gameRating
        val (state, change, opponent) = rateGame(before, g.level, score)
        gameRating = state
        remember(GAME_SERIES, state.rating)
        return g.withRating(GameRatingChange(before.rating, state.rating, change, opponent, before.games < PROVISIONAL))
    }

    companion object {
        const val LEVEL_KEY = "trainingLevel"
        const val GAME_RATING_KEY = "gameRating"
        const val RATING_DAYS_KEY = "ratingDays"
        const val GOAL_KEY = "puzzleGoal"
        const val DAYS_KEY = "puzzleDays"
        const val REPEAT_KEY = "repeatSet"
        const val UPDATED = ".updated"
    }
}
