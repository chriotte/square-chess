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

    companion object {
        const val GOAL_KEY = "puzzleGoal"
        const val DAYS_KEY = "puzzleDays"
        const val REPEAT_KEY = "repeatSet"
        const val UPDATED = ".updated"
    }
}
