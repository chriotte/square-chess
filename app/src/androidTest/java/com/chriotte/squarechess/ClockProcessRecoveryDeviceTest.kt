package com.chriotte.squarechess

import android.app.Application
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClockProcessRecoveryDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val arguments get()=InstrumentationRegistry.getArguments()

    private object IdleEngine: EngineController {
        override suspend fun start() {}
        override suspend fun newGame() {}
        override suspend fun search(fen: String, moves: List<String>, level: Int): String =
            error("The local-game recovery fixture must not start the engine")
        override fun stop() {}
        override suspend fun close() {}
    }

    private fun node(text: String): AccessibilityNodeInfo {
        val deadline=SystemClock.uptimeMillis()+10_000
        while(SystemClock.uptimeMillis()<deadline) {
            dismissImmersiveModePrompt()
            val root=instrumentation.uiAutomation.rootInActiveWindow
            find(root,text)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Missing UI node: $text")
    }

    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if(node==null || !node.refresh()) return null
        if(node.text?.toString()==text || node.contentDescription?.toString()==text) return node
        for(index in 0 until node.childCount) find(node.getChild(index),text)?.let { return it }
        return null
    }

    private fun tap(text: String) {
        var target=node(text)
        while(!target.isClickable && target.parent!=null) target=target.parent
        assertTrue("Could not tap $text",target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    @Test fun prepareRunningClockCheckpoint() {
        assumeTrue("Run through scripts/test-clock-process-recovery.ps1",
            arguments.getString("processRecoveryStage")=="prepare")
        runBlocking {
            val context=instrumentation.targetContext
            check(context.packageName.endsWith(".v11review"))
            val db=openChessDatabase(context)
            val viewModel=GameViewModel(context.applicationContext as Application,IdleEngine)
            val store=ViewModelStore().apply { put("process-recovery-fixture",viewModel) }
            try {
                db.clearAllTables()
                withTimeout(5_000) {
                    while(!viewModel.state.value.ready) kotlinx.coroutines.delay(20)
                }
                viewModel.newGame(GameMode.LOCAL_TWO_PLAYER,4,true,ClockConfig(180_000,2_000))
                withTimeout(5_000) {
                    while(viewModel.state.value.clock?.phase!=ClockPhase.RUNNING ||
                        viewModel.state.value.game?.mode!=GameMode.LOCAL_TWO_PLAYER.name) {
                        kotlinx.coroutines.delay(20)
                    }
                }
                val initial=requireNotNull(db.games().latest()!!.clockWhiteMs)
                withTimeout(5_000) {
                    while(requireNotNull(db.games().latest()!!.clockWhiteMs)>=initial-500) {
                        kotlinx.coroutines.delay(50)
                    }
                }
                val saved=db.games().latest()!!
                assertEquals("RUNNING",saved.clockPhase)
                assertTrue(requireNotNull(saved.clockWhiteMs)<initial)
            } finally {
                store.clear()
                db.close()
            }
        }
    }

    @Test fun verifyRelaunchedProcessKeepsClockPausedUntilResume() {
        assumeTrue("Run through scripts/test-clock-process-recovery.ps1",
            arguments.getString("processRecoveryStage")=="verify")
        runBlocking {
            val context=instrumentation.targetContext
            check(context.packageName.endsWith(".v11review"))
            val db=openChessDatabase(context)
            try {
                ActivityScenario.launch(MainActivity::class.java).use {
                    tap("Continue game")
                    node("Clock paused · App was interrupted")
                    val recovered=db.games().latest()!!
                    assertEquals("PAUSED",recovered.clockPhase)
                    assertEquals(true,recovered.clockInterrupted)
                    val savedWhite=requireNotNull(recovered.clockWhiteMs)
                    kotlinx.coroutines.delay(1_200)
                    assertEquals(savedWhite,db.games().latest()!!.clockWhiteMs)

                    tap("Resume clock")
                    node("Pause clock")
                    val resumed=db.games().latest()!!
                    assertEquals("RUNNING",resumed.clockPhase)
                    assertEquals(false,resumed.clockInterrupted)
                }
            } finally {
                db.close()
            }
        }
    }
}
