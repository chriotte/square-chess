package com.dataespresso.squarechess

import android.app.Application
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
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

/** Hint rules. Runs only in the separate .dev package: it clears saved games. */
@RunWith(AndroidJUnit4::class)
class HintDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()

    /** Each hint or move search waits until the test answers it. */
    private class ScriptedEngine: EngineController {
        val hints=Channel<CompletableDeferred<String>>(Channel.UNLIMITED)
        val moves=Channel<CompletableDeferred<String>>(Channel.UNLIMITED)
        var lastHintMoves: List<String>? = null
        override suspend fun start() {}
        override suspend fun newGame() {}
        override suspend fun search(fen: String, moves: List<String>, level: Int): String =
            CompletableDeferred<String>().also { this.moves.send(it) }.await()
        override suspend fun hint(fen: String, moves: List<String>): String {
            lastHintMoves=moves
            return CompletableDeferred<String>().also { hints.send(it) }.await()
        }
        override fun stop() {}
        override suspend fun close() {}
    }

    private suspend fun until(message: String, condition: () -> Boolean) {
        try { withTimeout(5_000) { while(!condition()) delay(20) } }
        catch(e: Exception) { throw AssertionError("Timed out: $message",e) }
    }

    private fun withViewModel(block: suspend (GameViewModel, ScriptedEngine, ChessDatabase) -> Unit) = runBlocking {
        val context=instrumentation.targetContext
        check(isIsolatedTestPackage(context.packageName))
        val db=openChessDatabase(context)
        db.clearAllTables()
        val engine=ScriptedEngine()
        val viewModel=GameViewModel(context.applicationContext as Application,engine)
        val store=ViewModelStore().apply { put("hint-test",viewModel) }
        try {
            until("ready") { viewModel.state.value.ready }
            block(viewModel,engine,db)
        } finally { store.clear(); db.close() }
    }

    @Test fun hintIsShownButNeverPlayedAndClearsAfterTheMove() = withViewModel { vm, engine, db ->
        vm.newGame(GameMode.COMPUTER,1,true,hintsEnabled=true).join()
        assertTrue(db.games().latest()!!.hintsEnabled)
        vm.requestHint()
        assertTrue(vm.state.value.hintBusy)
        engine.hints.receive().complete("g1f3")
        until("hint shown") { vm.state.value.visibleHint()?.move=="g1f3" }
        assertEquals(emptyList<String>(),engine.lastHintMoves)
        assertEquals("",db.games().latest()!!.moves)
        assertTrue(vm.state.value.canEnterMove())
        vm.enter("e2e4").join()
        assertNull(vm.state.value.hint)
        assertNull(vm.state.value.visibleHint())
        assertEquals("e2e4",db.games().latest()!!.moves)
        // Computer's turn: a hint request is ignored.
        vm.requestHint()
        assertFalse(vm.state.value.hintBusy)
        engine.moves.receive().complete("e7e5")
        until("computer moved") { vm.state.value.position.moves.size==2 && vm.state.value.canEnterMove() }
        vm.requestHint()
        engine.hints.receive().complete("g1f3")
        until("second hint") { vm.state.value.visibleHint()!=null }
        vm.clearHint()
        assertNull(vm.state.value.visibleHint())
    }

    @Test fun hintForAnOldPositionIsDropped() = withViewModel { vm, engine, _ ->
        vm.newGame(GameMode.COMPUTER,1,true,hintsEnabled=true).join()
        vm.requestHint()
        val pending=engine.hints.receive()
        vm.enter("d2d4").join()
        pending.complete("g1f3")
        engine.moves.receive().complete("d7d5")
        until("computer moved") { vm.state.value.position.moves.size==2 }
        delay(200)
        assertNull(vm.state.value.hint)
        assertFalse(vm.state.value.hintBusy)
    }

    @Test fun undoAndEndClearTheHint() = withViewModel { vm, engine, _ ->
        vm.newGame(GameMode.COMPUTER,1,true,hintsEnabled=true).join()
        vm.enter("e2e4").join()
        engine.moves.receive().complete("e7e5")
        until("computer moved") { vm.state.value.canEnterMove() }
        vm.requestHint(); engine.hints.receive().complete("g1f3")
        until("hint") { vm.state.value.visibleHint()!=null }
        vm.undo().join()
        assertNull(vm.state.value.visibleHint())
        vm.requestHint(); engine.hints.receive().complete("e2e4")
        until("hint again") { vm.state.value.visibleHint()?.move=="e2e4" }
        vm.end("0-1","You resigned").join()
        assertNull(vm.state.value.visibleHint())
        vm.requestHint()
        assertFalse(vm.state.value.hintBusy)
    }

    @Test fun engineFailureLeavesTheGamePlayable() = withViewModel { vm, engine, _ ->
        vm.newGame(GameMode.COMPUTER,1,true,hintsEnabled=true).join()
        vm.requestHint()
        engine.hints.receive().completeExceptionally(IllegalStateException("engine down"))
        until("error shown") { vm.state.value.hintFailed }
        assertTrue(vm.state.value.canEnterMove())
        vm.requestHint()
        engine.hints.receive().complete("e2e5") // not legal
        until("illegal result rejected") { !vm.state.value.hintBusy }
        assertNull(vm.state.value.hint)
        assertTrue(vm.state.value.hintFailed)
        vm.enter("e2e4").join()
        assertEquals(listOf("e2e4"),vm.state.value.position.moves)
        assertFalse(vm.state.value.hintFailed)
    }

    @Test fun hintsOnlyForComputerGamesThatAllowThem() = withViewModel { vm, _, db ->
        vm.newGame(GameMode.COMPUTER,1,true).join()
        assertFalse(db.games().latest()!!.hintsEnabled)
        vm.requestHint()
        assertFalse(vm.state.value.hintBusy)
        vm.newGame(GameMode.LOCAL_TWO_PLAYER,1,true,hintsEnabled=true).join()
        assertFalse(db.games().latest()!!.hintsEnabled)
        vm.requestHint()
        assertFalse(vm.state.value.hintBusy)
        vm.newGame(GameMode.PHYSICAL_BOARD_RECORDING,1,true,hintsEnabled=true).join()
        assertFalse(db.games().latest()!!.hintsEnabled)
    }

    // --- UI with the real engine ---

    private fun find(node: AccessibilityNodeInfo?, match: (String) -> Boolean): AccessibilityNodeInfo? {
        if(node==null || !node.refresh()) return null
        if(listOfNotNull(node.text?.toString(),node.contentDescription?.toString()).any(match)) return node
        for(i in 0 until node.childCount) find(node.getChild(i),match)?.let { return it }
        return null
    }
    private fun node(description: String, match: (String) -> Boolean = { it==description }): AccessibilityNodeInfo {
        val deadline=SystemClock.uptimeMillis()+15_000
        while(SystemClock.uptimeMillis()<deadline) {
            dismissImmersiveModePrompt()
            find(activeRoot(),match)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Missing UI node: $description")
    }
    private fun absent(text: String) = find(activeRoot()) { it==text }==null
    private fun tap(text: String) {
        var target=node(text)
        while(!target.isClickable && target.parent!=null) target=target.parent
        assertTrue("Could not tap $text",target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun bounds(text: String)=Rect().also { node(text).getBoundsInScreen(it) }
    private fun type(text: String) {
        KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray())!!.forEach { instrumentation.sendKeySync(it) }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        instrumentation.waitForIdleSync()
    }

    @Test fun hintButtonShowsTextAndKeepsTheBoardSize() = runBlocking {
        val context=instrumentation.targetContext
        check(isIsolatedTestPackage(context.packageName))
        val db=openChessDatabase(context)
        try {
            db.clearAllTables()
            ActivityScenario.launch(MainActivity::class.java).use {
                tap("Play against computer")
                tap("Allow hints")
                tap("Start game")
                val before=bounds("e4, empty")
                tap("Show hint")
                val hint=node("hint text") { it.startsWith("Hint: ") && it.contains(" from ") }
                val spoken=hint.contentDescription.toString()
                assertEquals("Hint does not resize the board",before,bounds("e4, empty"))
                assertEquals("",db.games().latest()!!.moves)
                node("Hide hint")
                tap("Hide hint")
                node("Show hint")
                assertTrue(absent(spoken))
                tap("Show hint")
                node("hint text") { it.startsWith("Hint: ") }
                // Play a normal move: the hint and its text go away.
                type("e2e4")
                node("e4, white pawn")
                node("computer reply") { it.contains("Your move") }
                assertTrue(find(activeRoot()) { it.startsWith("Hint: ") && it.contains(" from ") }==null)
                assertTrue(db.games().latest()!!.hintsEnabled)
            }
        } finally { db.close() }
    }

    @Test fun hintsOffByDefaultShowNoButton() = runBlocking {
        val context=instrumentation.targetContext
        check(isIsolatedTestPackage(context.packageName))
        val db=openChessDatabase(context)
        try {
            db.clearAllTables()
            ActivityScenario.launch(MainActivity::class.java).use {
                tap("Play against computer")
                tap("Start game")
                node("e4, empty")
                assertTrue(absent("Show hint"))
                assertFalse(db.games().latest()!!.hintsEnabled)
                tap("Menu")
                assertTrue(absent("Show hint"))
            }
        } finally { db.close() }
    }
}
