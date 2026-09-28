package com.chriotte.squarechess

import android.content.Intent
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ClockDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private class BlockingEngine: EngineController {
        val requests=Channel<CompletableDeferred<String>>(Channel.UNLIMITED)
        val cancellations=AtomicInteger()
        override suspend fun start() {}
        override suspend fun newGame() {}
        override suspend fun search(fen: String, moves: List<String>, level: Int): String {
            val result=CompletableDeferred<String>()
            requests.send(result)
            return try { result.await() }
            catch(e: CancellationException) { cancellations.incrementAndGet(); throw e }
        }
        override fun stop() {}
        override suspend fun close() {}
    }

    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if(node==null || !node.refresh()) return null
        if(node.text?.toString()==text || node.contentDescription?.toString()==text) return node
        for(index in 0 until node.childCount) find(node.getChild(index),text)?.let { return it }
        return null
    }

    private fun describe(node: AccessibilityNodeInfo?, depth: Int=0): String {
        if(node==null || depth>12) return ""
        val label=listOfNotNull(node.text?.toString(),node.contentDescription?.toString())
            .distinct().joinToString(" | ")
        return (if(label.isBlank()) "" else "${" ".repeat(depth)}$label\n")+
            (0 until node.childCount).joinToString("") { describe(node.getChild(it),depth+1) }
    }

    private fun node(text: String): AccessibilityNodeInfo {
        val deadline=SystemClock.uptimeMillis()+10_000
        var root: AccessibilityNodeInfo? = null
        while(SystemClock.uptimeMillis()<deadline) {
            dismissImmersiveModePrompt()
            root=instrumentation.uiAutomation.rootInActiveWindow
            find(root,text)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Missing UI node: $text\n${describe(root)}")
    }

    private fun tap(text: String) {
        var target=node(text)
        while(!target.isClickable && target.parent!=null) target=target.parent
        assertTrue("Could not tap $text",target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    private fun type(text: String) {
        val events=KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray())
        requireNotNull(events).forEach { instrumentation.sendKeySync(it) }
        instrumentation.waitForIdleSync()
    }

    private fun enter() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        instrumentation.waitForIdleSync()
    }

    @Test fun startingWithUntimedPresetDoesNotCrash() {
        runBlocking {
            val context=instrumentation.targetContext
            check(isIsolatedTestPackage(context.packageName))
            val db=openChessDatabase(context)
            try {
                db.clearAllTables()
                ActivityScenario.launch(MainActivity::class.java).use {
                    tap("Play against computer")
                    tap("Time control: Untimed")
                    tap("Untimed")
                    tap("Start game")
                    node("e4, empty")
                    val saved=db.games().latest()!!
                    assertEquals(GameMode.COMPUTER.name,saved.mode)
                    assertNull(saved.clockBaseMs)
                    assertNull(saved.clockPhase)
                }
            } finally {
                db.close()
            }
        }
    }

    @Test fun physicalBoardRecordingCanEnterAndSharePgn() {
        runBlocking {
            val context=instrumentation.targetContext
            check(isIsolatedTestPackage(context.packageName))
            val db=openChessDatabase(context)
            try {
                db.clearAllTables()
                ActivityScenario.launch(MainActivity::class.java).use {
                    tap("Record physical game")
                    tap("Start game")
                    node("e4, empty")
                    type("e2e4")
                    enter()
                    withTimeout(5_000) {
                        while(db.games().latest()!!.moves!="e2e4") kotlinx.coroutines.delay(50)
                    }
                    val game=db.games().latest()!!
                    assertEquals(GameMode.PHYSICAL_BOARD_RECORDING.name,game.mode)
                    val shareIntent=pgnShareIntent(game)
                    assertEquals(Intent.ACTION_SEND,shareIntent.action)
                    assertEquals("text/plain",shareIntent.type)
                    assertTrue(requireNotNull(shareIntent.getStringExtra(Intent.EXTRA_TEXT)).endsWith("1. e4 *"))
                    tap("Menu")
                    tap("Share PGN")
                    node("Sharing text")
                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    val currentPosition=ChessPosition(game.initialFen,game.moves.split(" ").filter(String::isNotBlank))
                    val fenIntent=fenShareIntent(currentPosition)
                    assertEquals(currentPosition.board.fen,fenIntent.getStringExtra(Intent.EXTRA_TEXT))
                    tap("Menu")
                    tap("Share FEN")
                    node("Sharing text")
                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                }
            } finally {
                db.close()
            }
        }
    }

    @Test fun importedFenPersistsAndStartsTheClockForSideToMove() {
        runBlocking {
            val context=instrumentation.targetContext
            check(isIsolatedTestPackage(context.packageName))
            val db=openChessDatabase(context)
            val engine=BlockingEngine()
            val app=context.applicationContext as android.app.Application
            val viewModel=GameViewModel(app,engine)
            val store=ViewModelStore().apply { put("fen-import-test",viewModel) }
            val fen=normalizeFenContent("r6k/8/8/8/8/8/8/7K b - - 0 17")
            try {
                db.clearAllTables()
                withTimeout(5_000) { while(!viewModel.state.value.ready) kotlinx.coroutines.delay(20) }
                viewModel.newGame(GameMode.LOCAL_TWO_PLAYER,4,true,ClockConfig(180_000,0),fen)
                withTimeout(5_000) {
                    while(viewModel.state.value.game?.initialFen!=fen ||
                        viewModel.state.value.clock?.active!=ClockSide.BLACK) kotlinx.coroutines.delay(20)
                }
                val saved=db.games().latest()!!
                assertEquals(fen,saved.initialFen)
                assertEquals("BLACK",saved.clockActive)
                viewModel.load(saved,recoverClock=false)
                assertEquals("BLACK",viewModel.state.value.position.board.sideToMove.name)
                assertEquals(ClockSide.BLACK,viewModel.state.value.clock?.active)
                viewModel.newGame(GameMode.LOCAL_TWO_PLAYER,4,true,ClockConfig(180_000,0),
                    "7k/8/8/8/8/8/8/7K b - - 0 17").join()
                assertEquals("1/2-1/2",viewModel.state.value.game?.result)
                assertEquals(ClockPhase.FINISHED,viewModel.state.value.clock?.phase)
                assertFalse(viewModel.state.value.canEnterMove())
                assertEquals("1/2-1/2",viewModel.state.value.resultEvent?.result)
                assertEquals(viewModel.state.value.game?.resultReason,viewModel.state.value.resultEvent?.reason)
            } finally {
                store.clear()
                db.close()
            }
        }
    }

    @Test fun localClockPausesOnRequestAndInterruptionAndSwitchesOnMove() {
        runBlocking {
            val context=instrumentation.targetContext
            check(isIsolatedTestPackage(context.packageName))
            val db=openChessDatabase(context)
            try {
                db.clearAllTables()
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    tap("Over the board")
                    tap("Time control: Untimed")
                    tap("3+2")
                    tap("Start game")
                    node("Pause clock")
                    var saved=db.games().latest()!!
                    assertEquals(180_000L,saved.clockBaseMs)
                    assertEquals(2_000L,saved.clockIncrementMs)
                    assertEquals("WHITE",saved.clockActive)
                    assertEquals("RUNNING",saved.clockPhase)

                    tap("Pause clock")
                    node("Clock paused · Resume when ready")
                    type("e2e4")
                    enter()
                    assertEquals("",db.games().latest()!!.moves)
                    val paused=db.games().latest()!!
                    assertEquals("PAUSED",paused.clockPhase)
                    val pausedWhiteMs=requireNotNull(paused.clockWhiteMs)

                    val resumedAt=SystemClock.elapsedRealtime()
                    tap("Resume clock")
                    node("Pause clock")
                    type("e2e4")
                    enter()
                    node("e4, white pawn")
                    withTimeout(5_000) {
                        while(db.games().latest()!!.moves!="e2e4") kotlinx.coroutines.delay(50)
                    }
                    saved=db.games().latest()!!
                    assertEquals("BLACK",saved.clockActive)
                    assertEquals("RUNNING",saved.clockPhase)
                    val elapsedSinceResume=SystemClock.elapsedRealtime()-resumedAt
                    val expectedMinimum=pausedWhiteMs-elapsedSinceResume+2_000L-250L
                    assertTrue("White's increment was not applied",requireNotNull(saved.clockWhiteMs)>=expectedMinimum)

                    scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                    withTimeout(5_000) {
                        while(db.games().latest()!!.clockPhase!="PAUSED" ||
                            db.games().latest()!!.clockInterrupted!=true) kotlinx.coroutines.delay(50)
                    }
                    scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                    node("Clock paused · App was interrupted")
                    tap("Resume clock")
                    node("Pause clock")
                }
            } finally {
                db.close()
            }
        }
    }

    @Test fun pauseDuringEngineSearchCancelsAndResumeStartsFreshSearch() {
        runBlocking {
            val context=instrumentation.targetContext
            check(isIsolatedTestPackage(context.packageName))
            val db=openChessDatabase(context)
            val engine=BlockingEngine()
            val app=context.applicationContext as android.app.Application
            val viewModel=GameViewModel(app,engine)
            val store=ViewModelStore().apply { put("clock-edge-test",viewModel) }
            try {
                db.clearAllTables()
                withTimeout(5_000) { while(!viewModel.state.value.ready) kotlinx.coroutines.delay(20) }
                viewModel.newGame(GameMode.COMPUTER,4,true,ClockConfig(180_000,0))
                withTimeout(5_000) {
                    while(viewModel.state.value.game?.mode!=GameMode.COMPUTER.name ||
                        viewModel.state.value.clock?.config!=ClockConfig(180_000,0)) kotlinx.coroutines.delay(20)
                }
                viewModel.enter("e2e4")
                val cancelledSearch=withTimeout(5_000) { engine.requests.receive() }
                viewModel.pauseClock()
                withTimeout(5_000) {
                    while(viewModel.state.value.clock?.phase!=ClockPhase.PAUSED ||
                        viewModel.state.value.busy) kotlinx.coroutines.delay(20)
                }
                assertEquals(1,engine.cancellations.get())
                assertEquals("e2e4",viewModel.state.value.game!!.moves)

                viewModel.resumeClock()
                val restartedSearch=withTimeout(5_000) { engine.requests.receive() }
                assertNotSame(cancelledSearch,restartedSearch)
                restartedSearch.complete("e7e5")
                withTimeout(5_000) {
                    while(viewModel.state.value.position.moves.size<2) kotlinx.coroutines.delay(20)
                }
                assertEquals(listOf("e2e4","e7e5"),viewModel.state.value.position.moves)
                assertEquals(ClockPhase.RUNNING,viewModel.state.value.clock?.phase)
                assertEquals(ClockSide.WHITE,viewModel.state.value.clock?.active)
            } finally {
                store.clear()
                db.close()
            }
        }
    }

    @Test fun runningClockIsCheckpointedAndRecoveryRequiresResume() {
            runBlocking {
                val context=instrumentation.targetContext
                check(isIsolatedTestPackage(context.packageName))
                val db=openChessDatabase(context)
                try {
                    db.clearAllTables()
                    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                        tap("Over the board")
                        tap("Time control: Untimed")
                        tap("3+2")
                        tap("Start game")
                        node("Pause clock")
                        val before=requireNotNull(db.games().latest()!!.clockWhiteMs)
                        kotlinx.coroutines.delay(1_400)
                        val checkpoint=db.games().latest()!!
                        assertEquals("RUNNING",checkpoint.clockPhase)
                        val checkpointedWhite=requireNotNull(checkpoint.clockWhiteMs)
                        assertTrue("Active time was not checkpointed",checkpointedWhite<before-500)
                        tap("Menu")
                        tap("Flip board")
                        withTimeout(5_000) {
                            while(db.games().latest()!!.orientationFlipped!=true) kotlinx.coroutines.delay(50)
                        }
                        assertTrue("Saving orientation restored an old clock snapshot",
                            requireNotNull(db.games().latest()!!.clockWhiteMs)<=checkpointedWhite)

                        scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                        withTimeout(5_000) {
                            while(db.games().latest()!!.clockPhase!="PAUSED") kotlinx.coroutines.delay(50)
                        }
                        val stopped=db.games().latest()!!
                        val remaining=requireNotNull(stopped.clockWhiteMs)
                        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                        ActivityScenario.launch(MainActivity::class.java).use {
                            withTimeout(5_000) {
                                while(db.games().latest()!!.clockInterrupted!=true) kotlinx.coroutines.delay(50)
                            }
                            val recovered=db.games().latest()!!
                            assertEquals("PAUSED",recovered.clockPhase)
                            assertEquals(remaining,recovered.clockWhiteMs)
                            tap("Continue game")
                            node("Clock paused · App was interrupted")
                        }
                    }
                } finally {
                    db.close()
                }
            }
        }

    @Test fun flagFallAwardsDrawWhenNoMatingMaterialIsPossible() {
        runBlocking {
            val context=instrumentation.targetContext
            check(isIsolatedTestPackage(context.packageName))
            val db=openChessDatabase(context)
            try {
                db.clearAllTables()
                val config=ClockConfig(180_000,0)
                val clock=ClockState(config,whiteMs=150,active=ClockSide.WHITE,phase=ClockPhase.PAUSED)
                db.games().save(
                    SavedGame("timeout-dead-material",GameMode.LOCAL_TWO_PLAYER.name,
                        initialFen="7k/8/8/8/8/8/8/R6K w - - 0 1").withClock(clock,SystemClock.elapsedRealtime())
                )
                ActivityScenario.launch(MainActivity::class.java).use {
                    tap("Continue game")
                    tap("Resume clock")
                    withTimeout(5_000) {
                        while(db.games().latest()!!.result=="*") kotlinx.coroutines.delay(50)
                    }
                    val finished=db.games().latest()!!
                    assertEquals("1/2-1/2",finished.result)
                    assertEquals("White ran out of time",finished.resultReason)
                    assertEquals("FLAGGED",finished.clockPhase)
                }
            } finally {
                db.close()
            }
        }
    }

    @Test fun flagFallAwardsWinWhenOpponentHasMatingMaterial() {
        runBlocking {
            val context=instrumentation.targetContext
            check(isIsolatedTestPackage(context.packageName))
            val db=openChessDatabase(context)
            try {
                db.clearAllTables()
                val clock=ClockState(
                    ClockConfig(180_000,0),
                    whiteMs=150,
                    active=ClockSide.WHITE,
                    phase=ClockPhase.PAUSED
                )
                db.games().save(
                    SavedGame("timeout-mating-material",GameMode.LOCAL_TWO_PLAYER.name,
                        initialFen="r6k/8/8/8/8/8/8/6K1 w - - 0 1")
                        .withClock(clock,SystemClock.elapsedRealtime())
                )
                ActivityScenario.launch(MainActivity::class.java).use {
                    tap("Continue game")
                    tap("Resume clock")
                    withTimeout(5_000) {
                        while(db.games().latest()!!.result=="*") kotlinx.coroutines.delay(50)
                    }
                    val finished=db.games().latest()!!
                    assertEquals("0-1",finished.result)
                    assertEquals("White ran out of time",finished.resultReason)
                    assertEquals("FLAGGED",finished.clockPhase)
                }
            } finally {
                db.close()
            }
        }
    }

    @Test fun undoingTimedMovesPausesClockAndRestoresMover() {
        runBlocking {
            val context=instrumentation.targetContext
            check(isIsolatedTestPackage(context.packageName))
            val db=openChessDatabase(context)
            try {
                db.clearAllTables()
                ActivityScenario.launch(MainActivity::class.java).use {
                    tap("Over the board")
                    tap("Time control: Untimed")
                    tap("3+2")
                    tap("Start game")
                    node("Pause clock")

                    type("e2e4")
                    enter()
                    withTimeout(5_000) {
                        while(db.games().latest()!!.moves!="e2e4") kotlinx.coroutines.delay(50)
                    }
                    type("e7e5")
                    enter()
                    withTimeout(5_000) {
                        while(db.games().latest()!!.moves!="e2e4 e7e5") kotlinx.coroutines.delay(50)
                    }
                    val whiteBeforeUndo=requireNotNull(db.games().latest()!!.clockWhiteMs)

                    tap("Menu")
                    tap("Undo / take back")
                    tap("Take back")
                    withTimeout(5_000) {
                        while(db.games().latest()!!.moves!="e2e4" ||
                            db.games().latest()!!.clockPhase!="PAUSED") kotlinx.coroutines.delay(50)
                    }
                    val undone=db.games().latest()!!
                    assertEquals("BLACK",undone.clockActive)
                    val whiteAfterUndo=requireNotNull(undone.clockWhiteMs)
                    assertTrue(whiteAfterUndo<=whiteBeforeUndo)
                    assertEquals("*",undone.result)
                    kotlinx.coroutines.delay(500)
                    assertEquals(whiteAfterUndo,db.games().latest()!!.clockWhiteMs)
                }
            } finally {
                db.close()
            }
        }
    }

    @Test fun manuallyEndingTimedGameFinishesAndPersistsClock() {
        runBlocking {
            val context=instrumentation.targetContext
            check(isIsolatedTestPackage(context.packageName))
            val db=openChessDatabase(context)
            try {
                db.clearAllTables()
                ActivityScenario.launch(MainActivity::class.java).use {
                    tap("Over the board")
                    tap("Time control: Untimed")
                    tap("3+2")
                    tap("Start game")
                    node("Pause clock")
                    tap("Menu")
                    tap("End game")
                    tap("Draw")

                    withTimeout(5_000) {
                        while(db.games().latest()!!.result=="*") kotlinx.coroutines.delay(50)
                    }
                    val finished=db.games().latest()!!
                    assertEquals("1/2-1/2",finished.result)
                    assertEquals("Result recorded by the players",finished.resultReason)
                    assertEquals("FINISHED",finished.clockPhase)
                    assertNotNull(finished.clockWhiteMs)
                }
            } finally {
                db.close()
            }
        }
    }

    @Test fun claimingFiftyMoveDrawEndsGameAndFinishesClock() {
        runBlocking {
            val context=instrumentation.targetContext
            check(isIsolatedTestPackage(context.packageName))
            val db=openChessDatabase(context)
            try {
                db.clearAllTables()
                val clock=ClockState(
                    ClockConfig(180_000,0),
                    active=ClockSide.WHITE,
                    phase=ClockPhase.PAUSED
                )
                db.games().save(
                    SavedGame("claim-fifty-move",GameMode.LOCAL_TWO_PLAYER.name,
                        initialFen="7k/8/8/8/8/8/R7/K7 w - - 100 60")
                        .withClock(clock,SystemClock.elapsedRealtime())
                )
                ActivityScenario.launch(MainActivity::class.java).use {
                    tap("Continue game")
                    tap("Menu")
                    tap("Claim draw")
                    withTimeout(5_000) {
                        while(db.games().latest()!!.result=="*") kotlinx.coroutines.delay(50)
                    }
                    val finished=db.games().latest()!!
                    assertEquals("1/2-1/2",finished.result)
                    assertEquals("Draw claimed",finished.resultReason)
                    assertEquals("FINISHED",finished.clockPhase)
                }
            } finally {
                db.close()
            }
        }
    }

}
