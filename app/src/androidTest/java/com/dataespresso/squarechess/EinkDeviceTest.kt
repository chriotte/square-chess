package com.dataespresso.squarechess

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** E-ink mode changes colours only: screens, board and settings keep working. Runs in the .dev package. */
@RunWith(AndroidJUnit4::class)
class EinkDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if(node==null || !node.refresh()) return null
        if(node.text?.toString()==text || node.contentDescription?.toString()==text) return node
        for(i in 0 until node.childCount) find(node.getChild(i),text)?.let { return it }
        return null
    }
    private fun node(text: String): AccessibilityNodeInfo {
        val deadline=SystemClock.uptimeMillis()+10_000
        var attempts=0
        while(SystemClock.uptimeMillis()<deadline) {
            dismissImmersiveModePrompt()
            find(activeRoot(),text)?.let { return it }
            // After 2 s, look further down (the board choice is at the end of Settings).
            if(++attempts%20==0) scrollForward()
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

    @Test fun switchingModeKeepsTheScreenAndTheBoardWorks() {
        val context=instrumentation.targetContext
        check(isIsolatedTestPackage(context.packageName))
        val store=SettingsStore(context)
        store.save(store.load().copy(eink=false))
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                tap("Settings")
                tap("E-ink mode")
                // The theme change must not reset navigation to the home screen.
                node("Move sounds")
                node("E-ink mode")
                assertTrue(store.load().eink)
                tap("‹ Home")
                tap("Over the board")
                tap("Start game")
                tap("e2, white pawn")
                tap("e4, empty, legal destination")
                node("e4, white pawn")
            }
            ActivityScenario.launch(MainActivity::class.java).use {
                tap("Settings")
                assertTrue(store.load().eink)
                tap("E-ink mode")
                node("Move sounds")
                assertFalse(store.load().eink)
            }
        } finally { store.save(store.load().copy(eink=false)) }
    }

    /** Requested by an e-ink user: grey dark squares instead of lines, and pieces facing the other player. */
    @Test fun greySquaresAndFaceToFaceAreSavedAndTheBoardWorks() {
        val context=instrumentation.targetContext
        check(isIsolatedTestPackage(context.packageName))
        openChessDatabase(context).apply { clearAllTables(); close() }
        val store=SettingsStore(context)
        store.save(store.load().copy(eink=true,einkGreySquares=false,faceToFace=false))
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                tap("Settings")
                tap("Grey board")
                node("Grey board, selected")
                assertTrue(store.load().einkGreySquares)
                tap("Lines board")
                node("Lines board, selected")
                assertFalse(store.load().einkGreySquares)
                tap("Grey board")
                tap("Face-to-face pieces")
                assertTrue(store.load().faceToFace)
                // Settings has scrolled down, past its Home button.
                instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                tap("Over the board")
                tap("Start game")
                tap("e2, white pawn")
                tap("e4, empty, legal destination")
                node("e4, white pawn")
                // Black's turn: the turned black pieces still move normally.
                tap("e7, black pawn")
                tap("e5, empty, legal destination")
                node("e5, black pawn")
            }
        } finally { store.save(store.load().copy(eink=false,einkGreySquares=false,faceToFace=false)) }
    }
}
