package com.dataespresso.squarechess

import org.json.JSONArray
import org.json.JSONObject

/**
 * One record of the website's sync API (see docs/development/sign-in-sync.md): a game, the
 * progress on one puzzle, or a small value. [updated] is milliseconds since 1970; the newer copy
 * wins. A deleted game is a tombstone: [deleted] true and no [data].
 */
data class SyncRecord(val kind: String, val id: String, val updated: Long, val deleted: Boolean = false, val data: JSONObject? = null) {
    val key: String get() = "$kind:$id"
    fun toJson(): JSONObject = JSONObject()
        .put("kind", kind).put("id", id).put("updated", updated).put("deleted", deleted)
        .put("data", data ?: JSONObject.NULL)

    companion object {
        const val GAME = "game"
        const val PUZZLE = "puzzle"
        const val KV = "kv"
        const val TRAINING_LEVEL = "trainingLevel"
        const val PLAYROOM = "playroom"
        /** The server accepts these ids; anything else is skipped there, so it is skipped here too. */
        private val ID = Regex("^[A-Za-z0-9_.:-]{1,80}$")

        /** Null when [json] is not a usable record. */
        fun fromJson(json: JSONObject): SyncRecord? {
            val kind = json.optString("kind")
            val id = json.optString("id")
            val updated = json.optLong("updated", 0)
            if (kind !in setOf(GAME, PUZZLE, KV) || !ID.matches(id) || updated <= 0) return null
            val deleted = json.optBoolean("deleted", false)
            val data = if (deleted) null else json.optJSONObject("data")
            if (!deleted && data == null) return null
            return SyncRecord(kind, id, updated, deleted, data)
        }
    }
}

/** A copy of [base] (so fields this version does not know are kept), or a new object. */
private fun copyOf(base: JSONObject?): JSONObject = if (base == null) JSONObject() else JSONObject(base.toString())
private fun JSONObject.stringOrNull(name: String): String? = if (isNull(name)) null else opt(name)?.toString()
private fun JSONObject.longOrNull(name: String): Long? = if (isNull(name)) null else (opt(name) as? Number)?.toLong()
private fun JSONObject.intOrNull(name: String): Int? = if (isNull(name)) null else (opt(name) as? Number)?.toInt()
private fun JSONObject.boolOrNull(name: String): Boolean? = if (isNull(name)) null else opt(name) as? Boolean
private fun Any?.orNull(): Any = this ?: JSONObject.NULL

private val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*")

/** The game as a record's data, written over [base] so unknown fields survive. */
fun SavedGame.toSyncData(base: JSONObject? = null): JSONObject {
    val json = copyOf(base)
    val clock = runCatching { clockState() }.getOrNull()
    json.put("id", id).put("mode", mode).put("initialFen", initialFen)
        .put("moves", JSONArray(moves.split(' ').filter(String::isNotBlank)))
        .put("white", white).put("black", black).put("humanWhite", humanWhite).put("level", level)
        .put("result", result).put("resultReason", resultReason).put("updated", updated)
        .put("orientationFlipped", orientationFlipped.orNull())
        .put("clock", clock?.toSyncJson(base?.optJSONObject("clock")).orNull())
        .put("hintsEnabled", hintsEnabled).put("evaluationEnabled", evaluationEnabled)
        .put("evaluations", evaluations)
    return json
}

/** The web client's clock snapshot: settled values and no monotonic anchor. */
private fun ClockState.toSyncJson(base: JSONObject?): JSONObject {
    val json = copyOf(base)
    json.put("config", copyOf(base?.optJSONObject("config"))
        .put("baseMs", config.baseMs).put("incrementMs", config.incrementMs).put("delayMs", config.delayMs))
    return json.put("whiteMs", whiteMs).put("blackMs", blackMs).put("active", active.name).put("phase", phase.name)
        .put("anchorMs", 0).put("interrupted", interrupted).put("delayRemainingMs", delayRemainingMs)
}

private fun clockFromSync(json: JSONObject?): ClockState? = json?.let {
    runCatching {
        val config = it.getJSONObject("config")
        val clockConfig = ClockConfig(config.getLong("baseMs"), config.getLong("incrementMs"), config.optLong("delayMs", 0))
        ClockState(
            config = clockConfig,
            whiteMs = it.getLong("whiteMs").coerceAtLeast(0),
            blackMs = it.getLong("blackMs").coerceAtLeast(0),
            active = ClockSide.valueOf(it.getString("active")),
            phase = ClockPhase.valueOf(it.getString("phase")),
            interrupted = it.optBoolean("interrupted", false),
            delayRemainingMs = it.optLong("delayRemainingMs", clockConfig.delayMs).coerceIn(0, clockConfig.delayMs)
        )
    }.getOrNull()
}

/**
 * The game in a record, or null when it cannot be shown safely here: an unknown mode, or moves
 * that are not legal from the starting position. A damaged record must never break the app.
 */
fun savedGameFromSync(id: String, updated: Long, data: JSONObject): SavedGame? = runCatching {
    val mode = data.getString("mode")
    if (GameMode.entries.none { it.name == mode }) return null
    val initialFen = data.stringOrNull("initialFen") ?: START_FEN
    val movesJson = data.optJSONArray("moves") ?: JSONArray()
    val moves = (0 until movesJson.length()).map { movesJson.getString(it) }
    val position = ChessPosition(initialFen, moves)
    if (position.moves.size != moves.size) return null
    val result = data.stringOrNull("result")?.takeIf { it in RESULTS } ?: "*"
    // A running clock comes back paused, as after a restart of the app.
    val clock = clockFromSync(data.optJSONObject("clock"))?.let {
        if (it.phase == ClockPhase.RUNNING) it.copy(phase = ClockPhase.PAUSED, interrupted = true) else it
    }
    SavedGame(
        id = id, mode = mode, initialFen = initialFen, moves = moves.joinToString(" "),
        white = data.stringOrNull("white") ?: "White", black = data.stringOrNull("black") ?: "Black",
        humanWhite = data.boolOrNull("humanWhite") ?: true,
        level = (data.intOrNull("level") ?: DEFAULT_LEVEL).coerceIn(1, 10),
        result = result, resultReason = data.stringOrNull("resultReason").orEmpty(), updated = updated,
        orientationFlipped = data.boolOrNull("orientationFlipped"),
        hintsEnabled = data.boolOrNull("hintsEnabled") ?: false,
        evaluationEnabled = data.boolOrNull("evaluationEnabled") ?: false,
        evaluations = data.stringOrNull("evaluations").orEmpty()
    ).withClock(clock, 0)
}.getOrNull()

fun PuzzleProgress.toSyncData(base: JSONObject? = null): JSONObject = copyOf(base)
    .put("puzzleId", puzzleId).put("attempts", attempts).put("solved", solved).put("firstTrySolved", firstTrySolved)
    .put("mistakes", mistakes).put("hints", hints).put("lastResult", lastResult).put("lastAttemptAt", lastAttemptAt)
    .put("reviewStep", reviewStep.orNull()).put("nextReviewAt", nextReviewAt.orNull())

fun puzzleProgressFromSync(id: String, updated: Long, data: JSONObject): PuzzleProgress? = runCatching {
    PuzzleProgress(
        puzzleId = id,
        attempts = data.optInt("attempts", 0).coerceAtLeast(0),
        solved = data.optInt("solved", 0).coerceAtLeast(0),
        firstTrySolved = data.optInt("firstTrySolved", 0).coerceAtLeast(0),
        mistakes = data.optInt("mistakes", 0).coerceAtLeast(0),
        hints = data.optInt("hints", 0).coerceAtLeast(0),
        lastResult = data.stringOrNull("lastResult")?.takeIf { r -> AttemptResult.entries.any { it.name == r } }
            ?: AttemptResult.ABANDONED.name,
        lastAttemptAt = updated,
        reviewStep = data.intOrNull("reviewStep")?.coerceIn(0, REVIEW_DELAYS_MS.size - 1),
        nextReviewAt = data.longOrNull("nextReviewAt")
    )
}.getOrNull()

fun trainingLevelToSyncData(level: Int, base: JSONObject? = null): JSONObject = copyOf(base).put("value", level)
fun trainingLevelFromSync(data: JSONObject): Int? =
    data.intOrNull("value")?.takeIf { it in MIN_TRAINING_LEVEL..MAX_TRAINING_LEVEL }

/** The synced part of the Playroom. Device choices (sound, e-ink, Playful extras on or off) stay on each device. */
data class PlayroomChoices(
    val world: World,
    val buddy: Buddy,
    val outfit: Outfit,
    val unlockAll: Boolean,
    val seenStickers: Set<Sticker>
)
val AppSettings.playroom: PlayroomChoices get() = PlayroomChoices(world, buddy, outfit, unlockAll, seenStickers)
fun AppSettings.withPlayroom(p: PlayroomChoices): AppSettings =
    copy(world = p.world, buddy = p.buddy, outfit = p.outfit, unlockAll = p.unlockAll, seenStickers = p.seenStickers)

/**
 * The `playroom` value, written over the value in [base]. As the format asks, names this version
 * does not know (a world, buddy, outfit item, colour or sticker added by a newer version) are kept
 * when saving, so they are not lost just because this version cannot draw them.
 */
fun PlayroomChoices.toSyncData(base: JSONObject? = null): JSONObject {
    val data = copyOf(base)
    val old = base?.optJSONObject("value")
    val value = copyOf(old)
    val oldWorld = old?.stringOrNull("world")
    value.put("world", if (oldWorld != null && World.entries.none { it.name == oldWorld }) oldWorld else world.name)
    val oldBuddy = old?.stringOrNull("buddy")
    value.put("buddy", if (oldBuddy != null && Buddy.entries.none { it.name == oldBuddy }) oldBuddy else buddy.name)
    val oldParts = (old?.opt("outfit") as? String).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val unknownItems = oldParts.filter { p -> !p.startsWith("skin:") && !p.startsWith("hair:") && Item.entries.none { it.name == p } }
    val skinText = skin(outfit) ?: oldParts.firstOrNull { p -> p.startsWith("skin:") && SkinTone.entries.none { "skin:${it.name}" == p } }
    val hairText = hair(outfit) ?: oldParts.firstOrNull { p -> p.startsWith("hair:") && HairColour.entries.none { "hair:${it.name}" == p } }
    value.put("outfit", (outfit.items.values.sortedBy { it.slot.ordinal }.map { it.name } + unknownItems +
        listOfNotNull(skinText, hairText)).joinToString(","))
    value.put("unlockAll", unlockAll)
    val oldStickers = old?.optJSONArray("seenStickers")?.let { list -> (0 until list.length()).map { list.optString(it) } }.orEmpty()
    value.put("seenStickers", JSONArray(seenStickers.sortedBy { it.ordinal }.map { it.name } +
        oldStickers.filter { s -> Sticker.entries.none { it.name == s } }))
    return data.put("value", value)
}
private fun skin(outfit: Outfit) = outfit.skin?.let { "skin:${it.name}" }
private fun hair(outfit: Outfit) = outfit.hair?.let { "hair:${it.name}" }

/** The choices in a record's data; fields that are missing or unknown here keep [current]. */
fun playroomFromSync(data: JSONObject, current: PlayroomChoices): PlayroomChoices? =
    data.optJSONObject("value")?.let { playroomFromValue(it, current) }

private fun playroomFromValue(value: JSONObject, current: PlayroomChoices): PlayroomChoices {
    val stickers = value.optJSONArray("seenStickers")?.let { list ->
        (0 until list.length()).mapNotNull { i -> Sticker.entries.firstOrNull { it.name == list.optString(i) } }.toSet()
    }
    val outfit = when (val raw = value.opt("outfit")) {
        is String -> Outfit.decode(raw)
        else -> null
    }
    return PlayroomChoices(
        world = World.entries.firstOrNull { it.name == value.stringOrNull("world") } ?: current.world,
        buddy = Buddy.entries.firstOrNull { it.name == value.stringOrNull("buddy") } ?: current.buddy,
        outfit = outfit ?: current.outfit,
        unlockAll = value.boolOrNull("unlockAll") ?: current.unlockAll,
        seenStickers = stickers ?: current.seenStickers
    )
}
