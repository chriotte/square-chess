package com.dataespresso.squarechess

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random

/*
 * Structured puzzle training: the course, single themes, progress by theme, the daily goal and
 * repeat sets. Pure functions over the puzzle pack and progress records. The web version
 * (squarechess.org/play, training.js) works out exactly the same chapters, stars and streaks,
 * so a player who signs in sees one set of progress on both.
 */

/** Single themes worth training on their own (the pack has enough of each). Labels: tag_<name>. */
val TRAINING_TAGS = listOf(
    "mateIn1", "mateIn2", "mateIn3", "backRankMate", "fork", "pin", "skewer", "discoveredAttack", "doubleCheck",
    "hangingPiece", "trappedPiece", "capturingDefender", "deflection", "attraction", "sacrifice", "clearance",
    "intermezzo", "quietMove", "defensiveMove", "kingsideAttack", "exposedKing", "promotion", "advancedPawn",
    "pawnEndgame", "rookEndgame", "bishopEndgame", "knightEndgame", "queenEndgame", "zugzwang",
)

private val MATE_PATTERNS = listOf("smotheredMate", "anastasiaMate", "hookMate", "arabianMate", "bodenMate", "doubleBishopMate", "dovetailMate")

/** One chapter as defined: puzzles with any of [tags], rated [min]..[max] (both included). */
data class ChapterDef(val id: String, val tags: List<String>, val min: Int, val max: Int)
data class CourseSection(val id: String, val chapters: List<ChapterDef>)

/** The course, in order. The ids name the strings: chapter_<id>, chapter_<id>_tip, course_sec_<id>. */
val COURSE_SECTIONS = listOf(
    CourseSection("mate", listOf(
        ChapterDef("mate1", listOf("mateIn1"), 600, 900), ChapterDef("mate1b", listOf("mateIn1"), 900, 1500),
        ChapterDef("backrank", listOf("backRankMate"), 600, 1700), ChapterDef("patterns", MATE_PATTERNS, 600, 2600),
        ChapterDef("mate2", listOf("mateIn2"), 600, 1100), ChapterDef("mate2b", listOf("mateIn2"), 1100, 1800),
        ChapterDef("mate3", listOf("mateIn3"), 600, 2100))),
    CourseSection("material", listOf(
        ChapterDef("hanging", listOf("hangingPiece"), 600, 1200), ChapterDef("fork", listOf("fork"), 600, 1200),
        ChapterDef("forkb", listOf("fork"), 1200, 1900), ChapterDef("pin", listOf("pin"), 600, 1600),
        ChapterDef("skewer", listOf("skewer", "xRayAttack"), 600, 1800),
        ChapterDef("discovered", listOf("discoveredAttack", "doubleCheck"), 600, 1700),
        ChapterDef("trapped", listOf("trappedPiece"), 600, 2000))),
    CourseSection("ideas", listOf(
        ChapterDef("deflection", listOf("deflection", "capturingDefender"), 800, 1800),
        ChapterDef("attraction", listOf("attraction"), 800, 1800), ChapterDef("sacrifice", listOf("sacrifice"), 1000, 2000),
        ChapterDef("clearance", listOf("clearance", "interference"), 800, 2200),
        ChapterDef("intermezzo", listOf("intermezzo"), 800, 2200), ChapterDef("quiet", listOf("quietMove"), 1000, 2200),
        ChapterDef("defence", listOf("defensiveMove"), 800, 2000))),
    CourseSection("endgames", listOf(
        ChapterDef("promotion", listOf("promotion", "underPromotion"), 600, 1600), ChapterDef("pawn", listOf("pawnEndgame"), 600, 1800),
        ChapterDef("rook", listOf("rookEndgame"), 600, 1800), ChapterDef("minor", listOf("bishopEndgame", "knightEndgame"), 600, 2000),
        ChapterDef("queen", listOf("queenEndgame", "queenRookEndgame"), 600, 2200), ChapterDef("zugzwang", listOf("zugzwang"), 800, 2400))),
)
const val CHAPTER_SIZE = 10

/** A chapter with its puzzles, easiest first. [number] counts from 1 through the whole course. */
data class Chapter(val id: String, val section: String, val number: Int, val ids: List<String>)

/**
 * The chapters with their puzzle ids. No puzzle is in two chapters. Each chapter takes puzzles
 * evenly spaced through its rating range, so it gets a little harder as it goes.
 */
fun buildCourse(pool: List<Puzzle>): List<Chapter> {
    val used = HashSet<String>()
    val chapters = ArrayList<Chapter>()
    for (section in COURSE_SECTIONS) for (def in section.chapters) {
        val fits = pool.filter { it.id !in used && it.rating in def.min..def.max && def.tags.any { tag -> tag in it.themes } }
            .sortedWith(compareBy<Puzzle> { it.rating }.thenBy { it.id })
        val n = minOf(CHAPTER_SIZE, fits.size)
        val ids = (0 until n).map { i -> fits[((i + 0.5) * fits.size / n).toInt()].id }
        used += ids
        if (ids.isNotEmpty()) chapters += Chapter(def.id, section.id, chapters.size + 1, ids)
    }
    return chapters
}

/** A puzzle counts as clean once it has been solved without a mistake or a hint. */
private fun PuzzleProgress?.clean() = this != null && firstTrySolved > 0
private fun PuzzleProgress?.isSolved() = this != null && solved > 0

data class ChapterStatus(val solved: Int, val clean: Int, val total: Int, val stars: Int, val done: Boolean)

/** Every puzzle solved finishes a chapter. Three stars: all clean; two: at least 80% clean. */
fun chapterStatus(chapter: Chapter, progress: Map<String, PuzzleProgress>): ChapterStatus {
    val total = chapter.ids.size
    val solved = chapter.ids.count { progress[it].isSolved() }
    val clean = chapter.ids.count { progress[it].clean() }
    val done = solved == total
    val stars = when {
        !done -> 0
        clean == total -> 3
        clean >= ceil(total * 0.8).toInt() -> 2
        else -> 1
    }
    return ChapterStatus(solved, clean, total, stars, done)
}

/** The puzzles to play when a chapter is opened: unsolved first, then the ones not yet clean, else all. */
fun chapterQueue(chapter: Chapter, progress: Map<String, PuzzleProgress>): List<String> {
    chapter.ids.filter { !progress[it].isSolved() }.takeIf { it.isNotEmpty() }?.let { return it }
    return chapter.ids.filter { !progress[it].clean() }.ifEmpty { chapter.ids }
}

/** The chapter to continue with: the first not done, then the first without three stars. */
fun nextChapter(chapters: List<Chapter>, progress: Map<String, PuzzleProgress>): Chapter? =
    chapters.firstOrNull { !chapterStatus(it, progress).done }
        ?: chapters.firstOrNull { chapterStatus(it, progress).stars < 3 } ?: chapters.firstOrNull()

/** Accuracy for one single theme: puzzles with the tag tried at least once, and how many of them are clean. */
data class ThemeStat(val tag: String, val attempted: Int, val clean: Int) {
    val accuracy: Double get() = if (attempted > 0) clean.toDouble() / attempted else 0.0
}

fun themeStats(pool: List<Puzzle>, progress: Map<String, PuzzleProgress>): List<ThemeStat> {
    val tried = IntArray(TRAINING_TAGS.size)
    val clean = IntArray(TRAINING_TAGS.size)
    val index = TRAINING_TAGS.withIndex().associate { (i, tag) -> tag to i }
    for (puzzle in pool) {
        val record = progress[puzzle.id] ?: continue
        if (record.attempts <= 0) continue
        for (tag in puzzle.themes) {
            val i = index[tag] ?: continue
            tried[i]++
            if (record.clean()) clean[i]++
        }
    }
    return TRAINING_TAGS.mapIndexed { i, tag -> ThemeStat(tag, tried[i], clean[i]) }
}

const val MIN_FOR_WEAKEST = 5
/** Themes with enough tries, lowest accuracy first; ties go to the theme tried more. */
fun weakestThemes(stats: List<ThemeStat>, count: Int = 3): List<ThemeStat> =
    stats.filter { it.attempted >= MIN_FOR_WEAKEST }
        .sortedWith(compareBy<ThemeStat> { it.accuracy }.thenByDescending { it.attempted }).take(count)

// ---- Daily goal ----------------------------------------------------------------------------

val PUZZLE_GOALS = listOf(0, 5, 10, 20, 30)
const val DEFAULT_PUZZLE_GOAL = 10
private const val KEEP_DAYS = 120L

/** The local calendar day as the web writes it: "2026-11-28". */
fun dayKey(day: LocalDate): String = day.toString()

/** Adds one finished puzzle to [today]'s count and drops days older than 120 days. */
fun addToDay(days: Map<String, Int>, today: LocalDate): Map<String, Int> {
    val key = dayKey(today)
    val oldest = dayKey(today.minusDays(KEEP_DAYS))
    return (days + (key to (days[key] ?: 0) + 1)).filterKeys { it >= oldest }
}

/** Two devices' counts together: the larger count for each day (each device counts its own). */
fun mergeDays(a: Map<String, Int>, b: Map<String, Int>): Map<String, Int> =
    (a.keys + b.keys).associateWith { maxOf(a[it] ?: 0, b[it] ?: 0) }

/** Days in a row that met the goal, up to today, or up to yesterday while today is still open. */
fun streak(days: Map<String, Int>, goal: Int, today: LocalDate): Int {
    val need = maxOf(goal, 1)
    var day = today
    if ((days[dayKey(day)] ?: 0) < need) day = day.minusDays(1)
    var count = 0
    while ((days[dayKey(day)] ?: 0) >= need) { count++; day = day.minusDays(1) }
    return count
}

/** The last [n] days, oldest first, with their counts. */
fun lastDays(days: Map<String, Int>, today: LocalDate, n: Int = 14): List<Pair<LocalDate, Int>> =
    (n - 1 downTo 0).map { back -> today.minusDays(back.toLong()).let { it to (days[dayKey(it)] ?: 0) } }

// ---- Repeat sets ---------------------------------------------------------------------------

val REPEAT_SIZES = listOf(10, 20, 50)
enum class RepeatSource(val key: String) { NEAR("near"), MISTAKES("mistakes") }

data class RepeatResult(val id: String, val clean: Boolean, val ms: Long)
/** A finished cycle: when it ended, puzzles solved on the first try, puzzles, and the time on the board. */
data class RepeatCycle(val index: Int, val finished: Long, val clean: Int, val total: Int, val ms: Long)
data class RepeatRound(val index: Int, val results: List<RepeatResult>)

/** The same puzzles, cycle after cycle, getting faster and cleaner. [current] is null between cycles. */
data class RepeatSet(
    val id: String,
    val created: Long,
    val source: RepeatSource,
    val ids: List<String>,
    val cycles: List<RepeatCycle> = emptyList(),
    val current: RepeatRound? = null,
) {
    /** The cycle in progress, started if needed. */
    val round: RepeatRound get() = current ?: RepeatRound(cycles.size + 1, emptyList())
    /** The next puzzle of the cycle. */
    val nextId: String? get() = ids.getOrNull(round.results.size)
}

/** A new set of [size] puzzles near [level], or from puzzles that went wrong before; null if none fit. */
fun newRepeatSet(pool: List<Puzzle>, progress: Map<String, PuzzleProgress>, level: Int, size: Int,
                 source: RepeatSource, now: Long, random: Random): RepeatSet? {
    val ids = when (source) {
        RepeatSource.MISTAKES -> {
            val known = pool.mapTo(HashSet()) { it.id }
            progress.values.filter { it.attempts > it.firstTrySolved }.sortedByDescending { it.lastAttemptAt }
                .map { it.puzzleId }.filter { it in known }.take(size)
        }
        RepeatSource.NEAR -> {
            val near = pool.filter { abs(it.rating - level) <= 200 }
            val list = if (near.size >= size) near else pool.sortedBy { abs(it.rating - level) }.take(size * 3)
            list.shuffled(random).take(size).sortedBy { it.rating }.map { it.id }
        }
    }
    if (ids.isEmpty()) return null
    return RepeatSet("r" + now.toString(36), now, source, ids)
}

/** Records one puzzle of the cycle; a full cycle moves into [RepeatSet.cycles]. */
fun recordRepeat(set: RepeatSet, id: String, clean: Boolean, ms: Long, now: Long): RepeatSet {
    val round = set.round
    val results = round.results + RepeatResult(id, clean, maxOf(0L, ms))
    if (results.size < set.ids.size) return set.copy(current = round.copy(results = results))
    val summary = RepeatCycle(round.index, now, results.count { it.clean }, results.size, results.sumOf { it.ms })
    return set.copy(current = null, cycles = set.cycles + summary)
}

/** "4:05" or "1:02:03". */
fun cycleTimeText(ms: Long): String {
    val s = Math.round(ms / 1000.0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(java.util.Locale.ROOT, h, m, sec) else "%d:%02d".format(java.util.Locale.ROOT, m, sec)
}
