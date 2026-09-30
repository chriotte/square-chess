package com.dataespresso.squarechess

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Holding a key types its Alt character ("hold E for 2"), the way PhysiBoard users enter
 * numbers. The key map of the test device decides the character. Runs only in the .dev package.
 */
@RunWith(AndroidJUnit4::class)
class HeldKeyDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()

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
            find(instrumentation.uiAutomation.rootInActiveWindow,text)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Missing UI node: $text")
    }
    private fun tap(text: String) {
        var target=node(text)
        while(!target.isClickable && target.parent!=null) target=target.parent
        assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    /** A real keyboard's device id when there is one, so its own key map is used. */
    private val keyboard: Int by lazy { InputDevice.getDeviceIds().firstOrNull { id ->
        InputDevice.getDevice(id)?.let { it.keyboardType==InputDevice.KEYBOARD_TYPE_ALPHABETIC && !it.isVirtual } == true
    } ?: KeyCharacterMap.VIRTUAL_KEYBOARD }

    private fun press(keyCode: Int, held: Boolean) {
        val down=SystemClock.uptimeMillis()
        fun event(action: Int, repeat: Int, flags: Int)=KeyEvent(down,SystemClock.uptimeMillis(),action,keyCode,repeat,0,
            keyboard,0,flags or KeyEvent.FLAG_FROM_SYSTEM,InputDevice.SOURCE_KEYBOARD)
        instrumentation.sendKeySync(event(KeyEvent.ACTION_DOWN,0,0))
        if(held) instrumentation.sendKeySync(event(KeyEvent.ACTION_DOWN,1,KeyEvent.FLAG_LONG_PRESS))
        instrumentation.sendKeySync(event(KeyEvent.ACTION_UP,0,0))
        instrumentation.waitForIdleSync()
    }

    @Test fun heldKeyReplacesItsLetterWithTheAltCharacter() = runBlocking<Unit> {
        val context=instrumentation.targetContext
        check(isIsolatedTestPackage(context.packageName))
        val map=KeyCharacterMap.load(keyboard)
        // A letter key that gives a digit with Alt on this keyboard (on a Titan: E -> 2).
        val key=(KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z).firstOrNull { code ->
            listOf(KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON,KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON)
                .any { map.get(code,it).toChar().isDigit() }
        }
        assumeTrue("This keyboard has no Alt digits on letter keys",key!=null)
        val letter=map.get(key!!,0).toChar()
        val digit=listOf(KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON,KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON)
            .map { map.get(key,it).toChar() }.first { it.isDigit() }
        val db=openChessDatabase(context)
        db.clearAllTables()
        db.games().save(SavedGame("held-key-fixture",GameMode.LOCAL_TWO_PLAYER.name))
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                tap("Continue game")
                node("e4, empty")
                press(KeyEvent.KEYCODE_E,held=false)
                node("Move entry e")
                // A tap then a hold on the same key: the second letter becomes the digit.
                press(key,held=false)
                node("Move entry e$letter")
                press(key,held=true)
                node("Move entry e$letter$digit")
            }
        } finally { db.close() }
    }
}
