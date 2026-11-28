package com.dataespresso.squarechess

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.random.Random

class TrainingTest {
    private val pack by lazy { File("src/main/assets/puzzles/puzzles-v1.tsv").useLines { parsePuzzlePack(it) } }

    private fun puzzle(id: String, rating: Int, vararg themes: String) =
        Puzzle(id, "8/8/8/8/8/8/8/8 w - - 0 1", listOf("e2e4", "e7e5"), rating, 90, themes.toSet())
    private fun record(id: String, attempts: Int = 1, solved: Int = 1, clean: Int = 0, at: Long = 0) =
        PuzzleProgress(id, attempts, solved, clean, 0, 0, "", at, null, null)

    @Test fun courseMatchesTheWebsite() {
        val course = buildCourse(pack)
        assertEquals(27, course.size)
        // Reference ids from the web's buildCourse, run over the same pack (MD5 7b0f8c4d…).
        assertEquals(listOf("PklSl", "jeC91", "YBfLC", "bnCwB", "qto9m", "NzC8a", "6r2EJ", "8mUEd", "n7u0F", "lKb4m"), course[0].ids)
        assertEquals(listOf("tWhsq", "F0WVQ", "TKVQI", "jXtZO", "MXOsE", "oyE56", "nMGkY", "AU8wM", "wZ4yt", "3MdXJ"), course[1].ids)
        assertEquals("zugzwang", course.last().id)
        assertEquals(listOf("zko2e", "YoAXb", "p9MvM", "EaxfS", "U7Cpu", "KZ9Ni", "dW2qr", "9fhGx", "Bv8ZW", "2Mfwc"), course.last().ids)
        val all = course.joinToString("\n") { it.id + ":" + it.ids.joinToString(",") }
        val md5 = MessageDigest.getInstance("MD5").digest(all.toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals("9534d582cbea407f0d9311ee5c72f0b8", md5)
        assertEquals(course.size * CHAPTER_SIZE, course.flatMap { it.ids }.toSet().size)
        assertEquals((1..27).toList(), course.map { it.number })
    }

    @Test fun chapterPicksEvenlySpacedPuzzlesAndSkipsUsedOnes() {
        val pool = (1..30).map { puzzle("p%02d".format(it), 600 + it, "mateIn1") }
        val course = buildCourse(pool)
        // floor((i + 0.5) * 30 / 10) = 1, 4, 7 …
        assertEquals(listOf(1, 4, 7, 10, 13, 16, 19, 22, 25, 28).map { pool[it].id }, course[0].ids)
        // mate1b (900..1500) has nothing left; the next chapter with puzzles is numbered 2.
        assertTrue(course.drop(1).none { it.id == "mate1" })
    }

    @Test fun starsAndQueue() {
        val chapter = Chapter("c", "s", 1, (1..10).map { "p$it" })
        assertEquals(ChapterStatus(0, 0, 10, 0, false), chapterStatus(chapter, emptyMap()))
        assertEquals(chapter.ids, chapterQueue(chapter, emptyMap()))
        val nine = chapter.ids.take(9).associateWith { record(it, clean = 1) }
        assertEquals(0, chapterStatus(chapter, nine).stars)
        assertEquals(listOf("p10"), chapterQueue(chapter, nine))
        // All solved, 8 clean: two stars, and the two not clean come back.
        val eight = chapter.ids.associateWith { record(it, clean = if (it == "p9" || it == "p10") 0 else 1) }
        assertEquals(2, chapterStatus(chapter, eight).stars)
        assertEquals(listOf("p9", "p10"), chapterQueue(chapter, eight))
        val seven = eight + ("p8" to record("p8"))
        assertEquals(1, chapterStatus(chapter, seven).stars)
        val all = chapter.ids.associateWith { record(it, clean = 1) }
        assertEquals(3, chapterStatus(chapter, all).stars)
        assertEquals(chapter.ids, chapterQueue(chapter, all))
        // An abandoned attempt is not solved.
        assertFalse(chapterStatus(chapter, all + ("p1" to record("p1", solved = 0))).done)
    }

    @Test fun nextChapterIsFirstUnfinishedThenFirstWithoutThreeStars() {
        val a = Chapter("a", "s", 1, listOf("a1")); val b = Chapter("b", "s", 2, listOf("b1"))
        assertEquals(a, nextChapter(listOf(a, b), emptyMap()))
        assertEquals(b, nextChapter(listOf(a, b), mapOf("a1" to record("a1", clean = 1))))
        assertEquals(a, nextChapter(listOf(a, b), mapOf("a1" to record("a1"), "b1" to record("b1", clean = 1))))
        assertEquals(a, nextChapter(listOf(a, b), mapOf("a1" to record("a1", clean = 1), "b1" to record("b1", clean = 1))))
        assertNull(nextChapter(emptyList(), emptyMap()))
    }

    @Test fun themeStatsAndWeakest() {
        val pool = (1..6).map { puzzle("f$it", 1000, "fork") } + (1..6).map { puzzle("k$it", 1000, "skewer", "pin") } +
            (1..3).map { puzzle("m$it", 1000, "mateIn1") }
        val progress = (1..6).associate { "f$it" to record("f$it", clean = if (it <= 3) 1 else 0) } +
            (1..6).associate { "k$it" to record("k$it", clean = if (it <= 3) 1 else 0) } +
            (1..3).associate { "m$it" to record("m$it") } + ("x" to record("x"))
        val stats = themeStats(pool, progress).associateBy { it.tag }
        assertEquals(ThemeStat("fork", 6, 3), stats["fork"])
        assertEquals(ThemeStat("pin", 6, 3), stats["pin"])
        assertEquals(0, stats["zugzwang"]!!.attempted)
        assertEquals(TRAINING_TAGS.size, stats.size)
        // mateIn1 has only three tries; the rest tie at 50% and keep TAGS order.
        assertEquals(listOf("fork", "pin", "skewer"), weakestThemes(themeStats(pool, progress)).map { it.tag })
        val more = progress + (7..10).associate { "f$it" to record("f$it") }
        val bigger = pool + (7..10).map { puzzle("f$it", 1000, "fork") }
        assertEquals("fork", weakestThemes(themeStats(bigger, more)).first().tag)
    }

    @Test fun streakEdgeCases() {
        val today = LocalDate.of(2026, 3, 1)
        fun d(back: Long) = dayKey(today.minusDays(back))
        // Today still open: count from yesterday.
        assertEquals(2, streak(mapOf(d(0) to 3, d(1) to 10, d(2) to 12, d(4) to 10), 10, today))
        // Today met.
        assertEquals(3, streak(mapOf(d(0) to 10, d(1) to 10, d(2) to 12), 10, today))
        // A broken day ends it.
        assertEquals(1, streak(mapOf(d(0) to 10, d(1) to 9, d(2) to 12), 10, today))
        assertEquals(0, streak(mapOf(d(2) to 12), 10, today))
        // Goal off: any puzzle counts. Across the end of February.
        assertEquals(3, streak(mapOf(d(0) to 1, d(1) to 1, d(2) to 1), 0, today))
        assertEquals("2026-02-27", d(2))
    }

    @Test fun daysAddMergeAndKeep() {
        val today = LocalDate.of(2026, 11, 28)
        val old = mapOf("2026-07-30" to 4, "2026-07-31" to 2, "2026-11-28" to 1)
        val added = addToDay(old, today)
        assertEquals(2, added["2026-11-28"])
        assertEquals(2, added["2026-07-31"])
        assertNull("Older than 120 days", added["2026-07-30"])
        assertEquals(mapOf("a" to 3, "b" to 5, "c" to 1), mergeDays(mapOf("a" to 3, "b" to 2), mapOf("b" to 5, "c" to 1, "a" to 1)))
        val last = lastDays(mapOf("2026-11-27" to 4), today, 3)
        assertEquals(listOf(LocalDate.of(2026, 11, 26) to 0, LocalDate.of(2026, 11, 27) to 4, today to 0), last)
    }

    @Test fun repeatRoundsAreRecorded() {
        var set = RepeatSet("r1", 1, RepeatSource.NEAR, listOf("a", "b"))
        assertEquals("a", set.nextId)
        set = recordRepeat(set, "a", true, 4_000, 10)
        assertEquals(RepeatRound(1, listOf(RepeatResult("a", true, 4_000))), set.current)
        assertEquals("b", set.nextId)
        set = recordRepeat(set, "b", false, -5, 20)
        assertNull(set.current)
        assertEquals(listOf(RepeatCycle(1, 20, 1, 2, 4_000)), set.cycles)
        assertEquals(2, set.round.index)
        assertEquals("a", set.nextId)
        assertEquals("4:05", cycleTimeText(245_000))
        assertEquals("1:02:03", cycleTimeText(3_723_000))
        assertEquals("0:00", cycleTimeText(0))
    }

    @Test fun newRepeatSets() {
        val pool = (0 until 100).map { puzzle("p$it", 600 + it * 20) }
        val near = newRepeatSet(pool, emptyMap(), 1200, 10, RepeatSource.NEAR, 1_000, Random(1))!!
        assertEquals(10, near.ids.size)
        val ratings = near.ids.map { id -> pool.first { it.id == id }.rating }
        assertEquals(ratings.sorted(), ratings)
        assertTrue(ratings.all { it in 1000..1400 })
        assertEquals("r" + 1_000L.toString(36), near.id)
        // Not enough within 200: the closest size × 3.
        val far = newRepeatSet(pool, emptyMap(), 2600, 10, RepeatSource.NEAR, 1, Random(1))!!
        assertTrue(far.ids.all { id -> pool.first { it.id == id }.rating >= 2600 - 30 * 20 })
        val progress = mapOf("p1" to record("p1", attempts = 2, clean = 1, at = 5), "p2" to record("p2", clean = 1, at = 9),
            "p3" to record("p3", attempts = 1, clean = 0, at = 7), "gone" to record("gone", clean = 0, at = 99))
        assertEquals(listOf("p3", "p1"), newRepeatSet(pool, progress, 1200, 10, RepeatSource.MISTAKES, 1, Random(1))!!.ids)
        assertNull(newRepeatSet(pool, emptyMap(), 1200, 10, RepeatSource.MISTAKES, 1, Random(1)))
    }

    @Test fun repeatSetJsonKeepsUnknownFields() {
        val set = recordRepeat(RepeatSet("r1", 5, RepeatSource.MISTAKES, listOf("a", "b")), "a", true, 1_500, 9)
        val base = JSONObject().put("id", "r1").put("futureField", 42)
        val json = set.toJson(base)
        assertEquals(42, json.getInt("futureField"))
        assertEquals("mistakes", json.getString("source"))
        assertEquals(set, repeatSetFromJson(JSONObject(json.toString())))
        val done = recordRepeat(set, "b", false, 500, 10)
        val back = repeatSetFromJson(JSONObject(done.toJson().toString()))!!
        assertTrue(JSONObject(done.toJson().toString()).isNull("current"))
        assertEquals(done, back)
        assertNull(repeatSetFromJson(JSONObject().put("ids", org.json.JSONArray())))
        assertEquals(mapOf("2026-11-28" to 7), daysFromJson(JSONObject().put("2026-11-28", 7).put("junk", 3).put("2026-11-27", "x")))
    }
}
