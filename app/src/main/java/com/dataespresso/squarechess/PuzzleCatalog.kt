package com.dataespresso.squarechess

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

const val PUZZLE_PACK="puzzles/puzzles-v1.tsv"

/**
 * The bundled puzzles, read once from assets when the player first opens Puzzles (never at app
 * start). Lines that cannot be read are skipped, so a damaged pack gives fewer puzzles, not a crash.
 */
object PuzzleCatalog {
    private val lock=Mutex()
    @Volatile private var loaded: List<Puzzle>?=null
    /** Milliseconds the first load took, for the performance notes. */
    @Volatile var loadMillis: Long=0; private set

    suspend fun puzzles(context: Context): List<Puzzle> = loaded ?: lock.withLock {
        loaded ?: withContext(Dispatchers.IO) {
            val start=SystemClock.elapsedRealtime()
            val list=context.assets.open(PUZZLE_PACK).bufferedReader().useLines { parsePuzzlePack(it) }
            loadMillis=SystemClock.elapsedRealtime()-start
            Log.i("SquareChess","Loaded ${list.size} puzzles in $loadMillis ms")
            list
        }.also { loaded=it }
    }
}

/** Parses pack lines, skipping unreadable ones and repeated IDs. */
fun parsePuzzlePack(lines: Sequence<String>): List<Puzzle> {
    val seen=HashSet<String>()
    return lines.mapNotNull(::parsePuzzleLine).filter { seen.add(it.id) }.toList()
}
