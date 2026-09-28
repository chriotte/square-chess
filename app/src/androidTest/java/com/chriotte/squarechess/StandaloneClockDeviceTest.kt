package com.chriotte.squarechess

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StandaloneClockDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()

    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if(node==null || !node.refresh()) return null
        if(node.text?.toString()==text || node.contentDescription?.toString()==text) return node
        for(index in 0 until node.childCount) find(node.getChild(index),text)?.let { return it }
        return null
    }

    private fun node(text: String): AccessibilityNodeInfo {
        val deadline=SystemClock.uptimeMillis()+10_000
        while(SystemClock.uptimeMillis()<deadline) {
            dismissImmersiveModePrompt()
            find(instrumentation.uiAutomation.rootInActiveWindow,text)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Missing UI node: $text")
    }

    private fun tap(text: String) {
        var target=node(text)
        while(!target.isClickable && target.parent!=null) target=target.parent
        assertTrue("Could not tap $text",target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    @Test fun standaloneClockSwitchesSidesAndDoesNotCreateGameRecords() = runBlocking {
        val context=instrumentation.targetContext
        check(context.packageName.matches(Regex("com[.]chriotte[.]squarechess[.](fairyexperiment|stockfishbaseline)")))
        val db=openChessDatabase(context)
        try {
            db.clearAllTables()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                node("For games played on a physical board")
                tap("Chess clock")
                tap("Preset · 5+0")
                tap("1+0")
                tap("Start with Black")
                tap("Apply time control")
                tap("Start clock")

                tap("Black clock, active")
                node("White clock, active")

                tap("Pause clock")
                node("Resume clock")
                tap("Resume clock")
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                node("Resume clock")
                tap("Reset")
                tap("Reset")
                node("Start clock")
            }
            assertNull("Standalone clock must not create a saved chess game",db.games().latest())
        } finally {
            db.close()
        }
    }

    @Test fun computerSideUsesExplicitWhiteAndBlackChoices() = runBlocking {
        val context=instrumentation.targetContext
        check(context.packageName.matches(Regex("com[.]chriotte[.]squarechess[.](fairyexperiment|stockfishbaseline)")))
        val db=openChessDatabase(context)
        try {
            db.clearAllTables()
            ActivityScenario.launch(MainActivity::class.java).use {
                tap("Play against computer")
                node("Play as White")
                tap("Play as Black")
                tap("Start game")
                withTimeout(5_000) {
                    while(db.games().latest()?.humanWhite!=false) kotlinx.coroutines.delay(50)
                }
                assertFalse(db.games().latest()!!.humanWhite)
            }
        } finally {
            db.close()
        }
    }
}
