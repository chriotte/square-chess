package com.chriotte.squarechess

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

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
    external fun search(fen: String, moves: String, level: Int, millis: Int): String
    external fun stop()
    external fun newGame()
    external fun close()
    external fun capabilities(): String
    external fun metrics(): String
    external fun configuration(): String
}
class StockfishController(private val context: Context) : EngineController {
    private val mutex = Mutex()
    @Volatile private var started = false
    override suspend fun start() = withContext(Dispatchers.IO) { mutex.withLock { ensureStarted() } }
    private fun ensureStarted() {
        if (started) return
        if(BuildConfig.FAIRY_ENGINE) {
            NativeEngine.start("")
            started=true
            return
        }
        val name = "nn-1a298aa575a0.nnue"
        val net = File(context.filesDir, name)
        val expected = "1a298aa575a085434d29027978dc36867fe9c5bcea9376654b7a8eba1e52dfc2"
        if (!net.exists()) {
            val temp = File(context.filesDir, "$name.tmp")
            context.assets.open(name).use { input -> temp.outputStream().use { input.copyTo(it) } }
            check(temp.renameTo(net)) { "Could not prepare the engine network" }
        }
        val hash = MessageDigest.getInstance("SHA-256")
        net.inputStream().use { input -> val b = ByteArray(65536); var n = input.read(b); while(n > 0) { hash.update(b,0,n); n=input.read(b) } }
        check(hash.digest().joinToString("") { "%02x".format(it) } == expected) { "Engine network integrity check failed" }
        NativeEngine.start(net.absolutePath)
        started = true
    }
    override suspend fun newGame() = withContext(Dispatchers.IO) { mutex.withLock { if(started) NativeEngine.newGame() } }
    override suspend fun search(fen: String, moves: List<String>, level: Int): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureStarted()
            // A background/cancel event may arrive while the network is loading.
            currentCoroutineContext().ensureActive()
            NativeEngine.search(fen, moves.joinToString(" "), level, if(BuildConfig.FAIRY_ENGINE) 500 else 100 + level * 80)
        }
    }
    override fun stop() { if(started) NativeEngine.stop() }
    override suspend fun close() = withContext(Dispatchers.IO) { mutex.withLock { if(started) NativeEngine.close(); started=false } }
}
