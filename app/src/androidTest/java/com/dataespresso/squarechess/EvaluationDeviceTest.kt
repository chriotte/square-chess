package com.dataespresso.squarechess

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Evaluation during play. Runs only in the separate .dev package: it clears saved games. */
@RunWith(AndroidJUnit4::class)
class EvaluationDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()

    private class Request(val moves: List<String>, val answer: CompletableDeferred<Eval?> = CompletableDeferred())

    /** Each evaluation or move search waits until the test answers it. */
    private class ScriptedEngine: EngineController {
        val evaluations=Channel<Request>(Channel.UNLIMITED)
        val moves=Channel<CompletableDeferred<String>>(Channel.UNLIMITED)
        override suspend fun start() {}
        override suspend fun newGame() {}
        override suspend fun search(fen: String, moves: List<String>, level: Int): String =
            CompletableDeferred<String>().also { this.moves.send(it) }.await()
        override suspend fun evaluate(fen: String, moves: List<String>): Eval? =
            Request(moves).also { evaluations.send(it) }.answer.await()
        override fun stop() {}
        override suspend fun close() {}
    }

    private suspend fun until(message: String, condition: () -> Boolean) {
        try { withTimeout(5_000) { while(!condition()) delay(20) } }
        catch(e: Exception) { throw AssertionError("Timed out: $message",e) }
    }

    private suspend fun ScriptedEngine.answer(ply: Int, eval: Eval) {
        val request=withTimeout(5_000) { evaluations.receive() }
        assertEquals("Evaluated position",ply,request.moves.size)
        request.answer.complete(eval)
    }

    private fun withViewModel(block: suspend (GameViewModel, ScriptedEngine, ChessDatabase) -> Unit) = runBlocking {
        val context=instrumentation.targetContext
        check(isIsolatedTestPackage(context.packageName))
        val db=openChessDatabase(context)
        db.clearAllTables()
        val engine=ScriptedEngine()
        val viewModel=GameViewModel(context.applicationContext as Application,engine)
        val store=ViewModelStore().apply { put("evaluation-test",viewModel) }
        try {
            until("ready") { viewModel.state.value.ready }
            block(viewModel,engine,db)
        } finally { store.clear(); db.close() }
    }

    @Test fun movesAreRatedBeforeTheComputerReplies() = withViewModel { vm, engine, db ->
        vm.newGame(GameMode.COMPUTER,1,true,evaluationEnabled=true).join()
        assertTrue(db.games().latest()!!.evaluationEnabled)
        engine.answer(0,Eval(30,null,"e2e4"))
        until("start rated") { 0 in vm.state.value.evals }
        vm.enter("e2e4").join()
        // The computer waits until the player's move is rated.
        val request=withTimeout(5_000) { engine.evaluations.receive() }
        assertEquals(1,request.moves.size)
        delay(100)
        assertTrue("No search before the rating",engine.moves.isEmpty)
        request.answer.complete(Eval(25,null,"e7e5"))
        withTimeout(5_000) { engine.moves.receive() }.complete("e7e5")
        engine.answer(2,Eval(35,null,"g1f3"))
        until("reply rated") { 2 in vm.state.value.evals }
        val s=vm.state.value
        assertEquals(MoveQuality.BEST,qualityAt(s.evals,START_FEN,s.position.moves,1))
        assertEquals(MoveQuality.BEST,qualityAt(s.evals,START_FEN,s.position.moves,2))
        assertEquals("0=c30/e2e4 1=c25/e7e5 2=c35/g1f3",db.games().latest()!!.evaluations)
    }

    @Test fun undoForgetsTheRemovedPositions() = withViewModel { vm, engine, db ->
        vm.newGame(GameMode.LOCAL_TWO_PLAYER,1,true,evaluationEnabled=true).join()
        engine.answer(0,Eval(30,null,"e2e4"))
        until("start rated") { 0 in vm.state.value.evals }
        vm.enter("e2e4").join()
        engine.answer(1,Eval(25,null,"e7e5"))
        until("move rated") { 1 in vm.state.value.evals }
        vm.undo().join()
        assertEquals(setOf(0),vm.state.value.evals.keys)
        assertEquals("0=c30/e2e4",db.games().latest()!!.evaluations)
        // The new move is rated again, not taken from the removed line.
        vm.enter("d2d4").join()
        engine.answer(1,Eval(20,null,"d7d5"))
        until("new move rated") { vm.state.value.evals[1]?.best=="d7d5" }
    }

    @Test fun ratingOfAnOldPositionIsDropped() = withViewModel { vm, engine, _ ->
        vm.newGame(GameMode.LOCAL_TWO_PLAYER,1,true,evaluationEnabled=true).join()
        engine.answer(0,Eval(30,null,"e2e4"))
        until("start rated") { 0 in vm.state.value.evals }
        vm.enter("e2e4").join()
        val pending=withTimeout(5_000) { engine.evaluations.receive() }
        vm.undo().join()
        pending.answer.complete(Eval(-500,null,"a7a6"))
        delay(200)
        assertEquals(setOf(0),vm.state.value.evals.keys)
    }

    @Test fun gamesWithoutEvaluationNeverAskForOne() = withViewModel { vm, engine, db ->
        vm.newGame(GameMode.COMPUTER,1,true).join()
        assertFalse(db.games().latest()!!.evaluationEnabled)
        vm.enter("e2e4").join()
        withTimeout(5_000) { engine.moves.receive() }.complete("e7e5")
        until("computer moved") { vm.state.value.position.moves.size==2 }
        delay(200)
        assertTrue(engine.evaluations.isEmpty)
        assertTrue(vm.state.value.evals.isEmpty())
    }

    @Test fun checkmateIsRatedWithoutTheEngine() = withViewModel { vm, engine, _ ->
        vm.newGame(GameMode.LOCAL_TWO_PLAYER,1,true,evaluationEnabled=true).join()
        engine.answer(0,Eval(30,null,"e2e4"))
        for((ply,move) in listOf("f2f3","e7e5","g2g4").withIndex()) {
            vm.enter(move).join()
            engine.answer(ply+1,Eval(if(ply==2) -MATE_SCORE else -50,if(ply==2) 1 else null,if(ply==2) "d8h4" else null))
            until("rated $move") { ply+1 in vm.state.value.evals }
        }
        vm.enter("d8h4").join()
        until("mate rated") { 4 in vm.state.value.evals }
        assertEquals(Eval(-MATE_SCORE,0),vm.state.value.evals[4])
        val s=vm.state.value
        assertEquals(MoveQuality.BEST,qualityAt(s.evals,START_FEN,s.position.moves,4))
        assertEquals(MoveQuality.BLUNDER,qualityAt(s.evals,START_FEN,s.position.moves,3))
    }
}
