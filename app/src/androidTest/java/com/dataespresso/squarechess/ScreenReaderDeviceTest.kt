package com.dataespresso.squarechess

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Requested by a screen-reader user: selection, the last move and each new move are spoken. Runs in the .dev package. */
@RunWith(AndroidJUnit4::class)
class ScreenReaderDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()

    @Before fun clean() {
        check(isIsolatedTestPackage(instrumentation.targetContext.packageName))
        openChessDatabase(instrumentation.targetContext).apply { clearAllTables(); close() }
    }

    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if(node==null || !node.refresh()) return null
        if(node.text?.toString()==text || labelMatches(node.contentDescription?.toString(),text)) return node
        for(i in 0 until node.childCount) find(node.getChild(i),text)?.let { return it }
        return null
    }
    private fun node(text: String): AccessibilityNodeInfo {
        val deadline=SystemClock.uptimeMillis()+10_000
        var attempts=0
        while(SystemClock.uptimeMillis()<deadline) {
            dismissImmersiveModePrompt()
            find(activeRoot(),text)?.let { return it }
            if(++attempts%20==0) scrollForward()
            SystemClock.sleep(100)
        }
        error("Missing UI node: $text")
    }
    private fun tap(text: String) {
        // A board square becomes clickable only when the board accepts moves; wait for that
        // instead of clicking a parent.
        val deadline=SystemClock.uptimeMillis()+5_000
        var target=node(text)
        while(!target.isClickable && SystemClock.uptimeMillis()<deadline && text.contains(", ")) { SystemClock.sleep(100); target=node(text) }
        while(!target.isClickable && target.parent!=null) target=target.parent
        assertTrue("Could not tap $text",target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    @Test fun selectionLastMoveAndNewMovesAreSpoken() {
        val store=SettingsStore(instrumentation.targetContext)
        store.save(store.load().copy(spokenMovesInWords=true))
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                tap("Over the board")
                tap("Start game")
                tap("e2, white pawn")
                node("e2, white pawn, selected")
                tap("e4, empty, legal destination")
                node("White played Pawn from e2 to e4")
                node("e4, white pawn, last move")
                node("e2, empty, last move")
            }
            // The setting: chess notation instead of words.
            store.save(store.load().copy(spokenMovesInWords=false))
            ActivityScenario.launch(MainActivity::class.java).use {
                tap("Continue game")
                node("White played e4")
            }
        } finally { store.save(store.load().copy(spokenMovesInWords=true)) }
    }
}
