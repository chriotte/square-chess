package com.chriotte.squarechess

import android.os.Debug
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class FairyEngineDeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun checkIsolation()=check(context.packageName in listOf("com.chriotte.squarechess.fairyexperiment","com.chriotte.squarechess.stockfishbaseline"))
    @Test fun actualOptionsNegativeSkillsLegalMovesAndRestart() = runBlocking {
        assumeTrue(BuildConfig.FAIRY_ENGINE);checkIsolation()
        val engine=StockfishController(context)
        try {
            repeat(2) {
                engine.start()
                assertTrue(NativeEngine.capabilities().contains("Skill Level type spin default 20 min -20 max 20"))
                for(profile in FAIRY_PROFILES) {
                    engine.newGame()
                    val move=engine.search(START_FEN,listOf("e2e4"),profile.id)
                    assertNotNull(ChessPosition(moves=listOf("e2e4")).resolve(move))
                    assertEquals("skill=${profile.skill};multipv=8;threads=1;hash=16;nnue=0;limitStrength=0",NativeEngine.configuration())
                    assertTrue(NativeEngine.metrics().contains("info depth"))
                }
                engine.close()
            }
        } finally {engine.close()}
    }
    @Test fun cancellationNewSearchAndTerminalPositions() = runBlocking {
        assumeTrue(BuildConfig.FAIRY_ENGINE);checkIsolation()
        val engine=StockfishController(context)
        try {
            engine.start()
            val began=SystemClock.elapsedRealtime()
            val search=async(Dispatchers.IO) {NativeEngine.search(START_FEN,"",4,5000)}
            delay(100);engine.stop()
            val move=withTimeout(2500) {search.await()}
            assertTrue("Cancellation should stop a five-second search",SystemClock.elapsedRealtime()-began<3000)
            assertNotNull(ChessPosition().resolve(move))
            engine.newGame()
            assertNotNull(ChessPosition().resolve(engine.search(START_FEN,emptyList(),1)))
            // The host normally handles terminal positions; the native engine must also return cleanly.
            for(fen in listOf("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1","7k/5K2/6Q1/8/8/8/8/8 b - - 0 1")) {
                assertTrue(engine.search(fen,emptyList(),4) in listOf("(none)","0000"))
            }
            val promotion="8/P6k/8/8/8/8/7p/4K3 w - - 0 1"
            assertNotNull(ChessPosition(promotion).resolve(engine.search(promotion,emptyList(),4)))
            assertNotNull(ChessPosition(promotion,listOf("a7a8n")).resolve(engine.search(promotion,listOf("a7a8n"),4)))
            val castling="r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"
            assertNotNull(ChessPosition(castling).resolve(engine.search(castling,emptyList(),4)))
            assertNotNull(ChessPosition(castling,listOf("e1g1")).resolve(engine.search(castling,listOf("e1g1"),4)))
        } finally {engine.close()}
    }
    @Test fun recordOwnInstalledStorage() {
        checkIsolation()
        val folder=File(context.filesDir,"engine-experiment").apply {mkdirs()}
        val manager=context.getSystemService(android.app.usage.StorageStatsManager::class.java)
        val stats=manager.queryStatsForPackage(android.os.storage.StorageManager.UUID_DEFAULT,context.packageName,Process.myUserHandle())
        File(folder,"installed-storage.txt").writeText("app_bytes=${stats.appBytes}\ndata_bytes=${stats.dataBytes}\ncache_bytes=${stats.cacheBytes}\n")
    }
    @Test fun completeEngineSelfPlay() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("engineSelfPlay")=="true");checkIsolation()
        val engine=StockfishController(context)
        val folder=File(context.filesDir,"engine-experiment").apply {mkdirs()}
        val power=context.getSystemService(android.os.PowerManager::class.java)
        var position=ChessPosition()
        var thermalMax=power.currentThermalStatus
        val began=SystemClock.elapsedRealtime()
        try {
            engine.start();engine.newGame()
            while(position.automaticResult()==null && position.moves.size<600) {
                thermalMax=maxOf(thermalMax,power.currentThermalStatus)
                check(thermalMax<android.os.PowerManager.THERMAL_STATUS_SEVERE) {"Stopped at severe thermal status"}
                val profile=if(position.moves.size%2==0) 7 else 8
                val best=engine.search(START_FEN,position.moves,profile)
                val move=position.resolve(best)
                assertNotNull("Illegal self-play move",move)
                position=position.append(move!!)
                File(folder,"selfplay.txt").writeText("plies=${position.moves.size}\nresult=${position.automaticResult()}\nfen=${position.board.fen}\nseconds=${(SystemClock.elapsedRealtime()-began)/1000}\nmax_thermal_status=$thermalMax\nmoves=${position.moves.joinToString(" ")}\n")
            }
            assertNotNull("Self-play did not reach an automatic result within 600 plies",position.automaticResult())
        } finally {engine.close()}
    }
    @Test fun recordDifficultyAndPerformanceExperiment() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("engineBenchmark")=="true");checkIsolation()
        val engine=StockfishController(context)
        val folder=File(context.filesDir,"engine-experiment").apply {mkdirs()}
        val positions=listOf(
            "opening" to START_FEN,
            "after_e4" to ChessPosition(moves=listOf("e2e4")).board.fen,
            "kiwipete" to "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "hanging_queen" to "4k3/8/8/8/3q4/8/3R4/4K3 w - - 0 1",
            "mate_in_one" to "7k/5K2/6Q1/8/8/8/8/8 w - - 0 1"
        )
        val start=SystemClock.elapsedRealtime();engine.start()
        val startup=SystemClock.elapsedRealtime()-start
        File(folder,"options.txt").writeText(NativeEngine.capabilities())
        val cpuStart=Process.getElapsedCpuTime();val wallStart=SystemClock.elapsedRealtime()
        var peakPss=Debug.getPss();var legal=0
        try {
            File(folder,"results.csv").bufferedWriter().use {out ->
                out.appendLine("engine,profile,skill,multipv,budget_ms,position,fen,repeat,move,depth,nodes,elapsed_ms,pss_kb,error")
                val profiles=if(BuildConfig.FAIRY_ENGINE) listOf(4,6,7,8,9,5) else listOf(2)
                for(profile in profiles) for((name,fen) in positions) repeat(6) {repeat ->
                    engine.newGame()
                    val began=SystemClock.elapsedRealtime()
                    val budget=if(BuildConfig.FAIRY_ENGINE) 500 else 100+profile*80
                    val move=withContext(Dispatchers.IO) {NativeEngine.search(fen,"",profile,budget)}
                    val elapsed=SystemClock.elapsedRealtime()-began
                    val position=ChessPosition(fen)
                    assertNotNull("Illegal engine move $move in $name",position.resolve(move));legal++
                    val info=NativeEngine.metrics()
                    val depth=Regex("\\bdepth (\\d+)").findAll(info).map {it.groupValues[1].toInt()}.maxOrNull() ?: 0
                    val nodes=Regex("\\bnodes (\\d+)").findAll(info).map {it.groupValues[1].toLong()}.maxOrNull() ?: 0
                    val pss=Debug.getPss();peakPss=maxOf(peakPss,pss)
                    val skill=if(BuildConfig.FAIRY_ENGINE) (FAIRY_PROFILES.firstOrNull {it.id==profile}?.skill ?: 20) else 2
                    val label=if(BuildConfig.FAIRY_ENGINE) (FAIRY_PROFILES.firstOrNull {it.id==profile}?.label ?: "control20") else "Level2"
                    out.appendLine("${if(BuildConfig.FAIRY_ENGINE) "Fairy" else "Stockfish19"},$label,$skill,${if(BuildConfig.FAIRY_ENGINE) 8 else 1},$budget,$name,\"$fen\",$repeat,$move,$depth,$nodes,$elapsed,$pss,")
                    out.flush()
                    File(folder,"trace-$label-$name-$repeat.txt").writeText(info)
                }
            }
            File(folder,"summary.txt").writeText("startup_ms=$startup\nlegal_moves=$legal\ncpu_ms=${Process.getElapsedCpuTime()-cpuStart}\nwall_ms=${SystemClock.elapsedRealtime()-wallStart}\npeak_sampled_pss_kb=$peakPss\n"+File("/proc/self/status").readLines().filter {it.startsWith("VmHWM:") || it.startsWith("VmRSS:")}.joinToString("\n"))
        } finally {engine.close()}
    }
}
