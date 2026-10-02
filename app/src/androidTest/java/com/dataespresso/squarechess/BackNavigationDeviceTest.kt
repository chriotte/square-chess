package com.dataespresso.squarechess

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Back goes one step up and never leaves the app from an inner screen. Runs only in the .dev
 * package: it clears saved games.
 */
@RunWith(AndroidJUnit4::class)
class BackNavigationDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()

    @Before fun clean() {
        val context=instrumentation.targetContext
        check(isIsolatedTestPackage(context.packageName))
        openChessDatabase(context).apply { clearAllTables(); close() }
    }

    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if(node==null || !node.refresh()) return null
        if(node.text?.toString()==text || node.contentDescription?.toString()==text) return node
        for(i in 0 until node.childCount) find(node.getChild(i),text)?.let { return it }
        return null
    }
    private fun node(text: String): AccessibilityNodeInfo {
        val deadline=SystemClock.uptimeMillis()+10_000
        while(SystemClock.uptimeMillis()<deadline) {
            dismissImmersiveModePrompt()
            find(activeRoot(),text)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Missing UI node: $text")
    }
    private fun gone(text: String) {
        val deadline=SystemClock.uptimeMillis()+5_000
        while(SystemClock.uptimeMillis()<deadline) {
            if(find(activeRoot(),text)==null) return
            SystemClock.sleep(100)
        }
        error("Still shown: $text")
    }
    private fun tap(text: String) {
        var target=node(text)
        while(!target.isClickable && target.parent!=null) target=target.parent
        assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun back() {
        assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
    }

    @Test fun settingsAndHistoryReturnHome() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tap("Settings")
            node("E-ink mode")
            back()
            node("Play against computer")
            tap("Game history")
            node("Your games")
            back()
            node("Play against computer")
            scenario.onActivity { assertFalse("The app is still open",it.isFinishing) }
        }
    }

    @Test fun gameBackClearsTypingThenLeavesReviewThenSavesAndGoesHome() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tap("Over the board")
            tap("Start game")
            node("e4, empty")
            instrumentation.sendStringSync("e4")
            node("Move: e4")
            back()
            gone("Move: e4")
            node("e2, white pawn")
            instrumentation.sendStringSync("e4"); instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ENTER)
            node("e4, white pawn")
            tap("Review")
            node("Return")
            back()
            gone("Return")
            node("e4, white pawn")
            back()
            node("Continue game")
            scenario.onActivity { assertFalse(it.isFinishing) }
            tap("Continue game")
            node("e4, white pawn")
        }
    }
}
