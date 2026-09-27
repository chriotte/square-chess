package com.chriotte.squarechess

import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
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
        if(node.text?.toString()==text || node.contentDescription?.toString()==text) return node
        for(i in 0 until node.childCount) find(node.getChild(i),text)?.let { return it }
        return null
    }
    private fun waitNode(text: String): AccessibilityNodeInfo {
        repeat(50) {
            find(instrumentation.uiAutomation.rootInActiveWindow,text)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Missing UI node: $text")
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
    @Test fun coordinateEntryIsVisibleLegalOnceAndReviewCannotSubmit() = runBlocking {
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".v11review"))
        val db=Room.databaseBuilder(context,ChessDatabase::class.java,"square-chess.db").build()
        db.games().save(SavedGame("keyboard-fixture",GameMode.LOCAL_TWO_PLAYER.name))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                tap("Continue game")
                val before=android.graphics.Rect().also { waitNode("e4, empty").getBoundsInScreen(it) }
                type("e")
                waitNode("Move entry e")
                type("2e5")
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
                type("f2f4"); enter()
                waitNode("f4, white pawn")
                assertEquals("e2e4 e7e5 g1f3 d7d5 f2f4",db.games().latest()!!.moves)
            }
        } finally { db.close() }
    }
}
