package com.dataespresso.squarechess

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface EngineController {
    suspend fun start()
    suspend fun newGame()
    suspend fun search(fen: String, moves: List<String>, level: Int): String
    fun stop()
    suspend fun close()
}
object NativeEngine {
    init { System.loadLibrary("squarefish") }
    external fun start(path: String)
    /** [skill] is the UCI Skill Level (-20..20); see Difficulty.kt. */
    external fun search(fen: String, moves: String, skill: Int, multiPv: Int, millis: Int): String
    external fun stop()
    external fun newGame()
    external fun close()
    external fun capabilities(): String
    external fun metrics(): String
    external fun configuration(): String
}
/** Fairy-Stockfish through the JNI bridge in native/fairy_bridge.cpp. */
class StockfishController(@Suppress("unused") private val context: Context) : EngineController {
    private val mutex = Mutex()
    @Volatile private var started = false
    override suspend fun start() = withContext(Dispatchers.IO) { mutex.withLock { ensureStarted() } }
    private fun ensureStarted() {
        if (started) return
        // Classical evaluation: no network file to load.
        NativeEngine.start("")
        started = true
    }
    override suspend fun newGame() = withContext(Dispatchers.IO) { mutex.withLock { if(started) NativeEngine.newGame() } }
    override suspend fun search(fen: String, moves: List<String>, level: Int): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureStarted()
            // A background/cancel event may arrive while the engine is starting.
            currentCoroutineContext().ensureActive()
            val options=engineLevel(level)
            NativeEngine.search(fen, moves.joinToString(" "), options.skill, options.multiPv, ENGINE_MOVE_TIME_MS)
        }
    }
    override fun stop() { if(started) NativeEngine.stop() }
    override suspend fun close() = withContext(Dispatchers.IO) { mutex.withLock { if(started) NativeEngine.close(); started=false } }
}
