package com.dataespresso.squarechess

import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Installs/runs against the opt-in .v11review package, never user game storage. */
@RunWith(AndroidJUnit4::class)
class InputDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if(node==null) return null
        // UiAutomation may retain a cached Compose node between content events.
        // Read its current semantics instead of comparing a previous frame.
        if(!node.refresh()) return null
        if(node.text?.toString()==text || node.contentDescription?.toString()==text) return node
        for(i in 0 until node.childCount) find(node.getChild(i),text)?.let { return it }
        return null
    }
    private fun waitNode(text: String): AccessibilityNodeInfo {
        val deadline=SystemClock.uptimeMillis()+10_000
        while(SystemClock.uptimeMillis()<deadline) {
            dismissImmersiveModePrompt()
            find(instrumentation.uiAutomation.rootInActiveWindow,text)?.let { return it }
            SystemClock.sleep(100)
        }
        fun visible(node: AccessibilityNodeInfo?): String {
            if(node==null) return ""
            return "[${node.text}/${node.contentDescription}]" + (0 until node.childCount).joinToString("") { visible(node.getChild(it)) }
        }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(instrumentation.targetContext.filesDir,"input-test-failure.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
        }
        error("Missing UI node: $text; tree=${visible(instrumentation.uiAutomation.rootInActiveWindow)}")
    }
    private fun tap(text: String) {
        var node=waitNode(text)
        while(!node.isClickable && node.parent!=null) node=node.parent
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
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
    @Test fun orientationAndDismissedResultSurviveRecreationAndReopening() = runBlocking {
        val context=instrumentation.targetContext
        check(isIsolatedTestPackage(context.packageName))
        val db=openChessDatabase(context)
        db.games().save(SavedGame("persistence-fixture",GameMode.PHYSICAL_BOARD_RECORDING.name))
        fun assertFlipped() {
            val a1=android.graphics.Rect().also { waitNode("a1, white rook").getBoundsInScreen(it) }
            val h8=android.graphics.Rect().also { waitNode("h8, black rook").getBoundsInScreen(it) }
            assertTrue("a1 is above and right of h8 when flipped",a1.top<h8.top && a1.left>h8.left)
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                tap("Continue game")
                tap("Menu"); tap("Flip board")
                kotlinx.coroutines.withTimeout(5_000) {
                    while(db.games().latest()!!.orientationFlipped!=true) kotlinx.coroutines.delay(50)
                }
                assertFlipped()
                scenario.recreate()
                assertFlipped()
                tap("Menu"); tap("End game"); tap("Draw")
                waitNode("Result recorded by the players")
                assertEquals("1/2-1/2",db.games().latest()!!.result)
                assertEquals("Result recorded by the players",db.games().latest()!!.resultReason)
                tap("Review board")
                scenario.recreate()
                waitNode("Game over")
                assertNull(find(instrumentation.uiAutomation.rootInActiveWindow,"Play again"))
                assertFlipped()
                tap("Menu"); tap("Save & home")
                tap("View last game")
                waitNode("Game over")
                assertFlipped()
            }
            // A fresh Activity/ViewModel loads the saved record rather than replaying the result event.
            ActivityScenario.launch(MainActivity::class.java).use {
                tap("View last game")
                waitNode("Game over")
                assertNull(find(instrumentation.uiAutomation.rootInActiveWindow,"Play again"))
                assertFlipped()
            }
        } finally { db.close() }
    }
    @Test fun coordinateEntryIsVisibleLegalOnceAndReviewCannotSubmit() = runBlocking {
        val context=instrumentation.targetContext
        check(isIsolatedTestPackage(context.packageName))
        val db=openChessDatabase(context)
        db.games().save(SavedGame("keyboard-fixture",GameMode.LOCAL_TWO_PLAYER.name))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                tap("Continue game")
                val before=android.graphics.Rect().also { waitNode("e4, empty").getBoundsInScreen(it) }
                type("e")
                waitNode("Move entry e")
                type("2e5")
                waitNode("Move entry e2e5")
                enter()
                waitNode("Illegal or incomplete move")
                assertEquals("",db.games().latest()!!.moves)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DEL)
                type("4")
                waitNode("Move entry e2e4")
                val after=android.graphics.Rect().also { waitNode("e4, empty").getBoundsInScreen(it) }
                assertEquals("Entry must not resize the board",before,after)
                scenario.onActivity { activity ->
                    val insets=activity.window.decorView.rootWindowInsets
                    assertFalse(WindowInsetsCompat.toWindowInsetsCompat(insets).isVisible(WindowInsetsCompat.Type.ime()))
                }
                enter(); enter()
                waitNode("e4, white pawn")
                assertEquals("e2e4",db.games().latest()!!.moves)
                tap("Review")
                tap("First position")
                waitNode("e4, empty")
                type("e7e5"); enter()
                assertEquals("e2e4",db.games().latest()!!.moves)
                tap("Return")
                type("e7e5"); enter()
                waitNode("e5, black pawn")
                type("Nf3"); enter()
                waitNode("f3, white knight")
                assertEquals("e2e4 e7e5 g1f3",db.games().latest()!!.moves)
                type("d7d5"); enter()
                waitNode("d5, black pawn")
                type("f1c4"); enter()
                waitNode("c4, white bishop")
                assertEquals("e2e4 e7e5 g1f3 d7d5 f1c4",db.games().latest()!!.moves)
            }
        } finally { db.close() }
    }
}
