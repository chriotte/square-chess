package com.chriotte.squarechess

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LayoutDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun find(n: AccessibilityNodeInfo?,text: String): AccessibilityNodeInfo? {
        if(n==null || !n.refresh()) return null
        if(n.text?.toString()==text || n.contentDescription?.toString()==text) return n
        for(i in 0 until n.childCount) find(n.getChild(i),text)?.let {return it}
        return null
    }
    private fun node(text: String): AccessibilityNodeInfo {
        repeat(70) {
            dismissImmersiveModePrompt()
            find(instrumentation.uiAutomation.rootInActiveWindow,text)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Missing $text")
    }
    private fun tap(text: String) {
        var n=node(text)
        n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        while(!n.isClickable && n.parent!=null) n=n.parent
        assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()!!
        File(instrumentation.targetContext.filesDir,"layout-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun captureCoreScreensAndCheckBoardAndControls() {
        check(instrumentation.targetContext.packageName.matches(Regex("com[.]chriotte[.]squarechess[.](fairyexperiment|stockfishbaseline)")))
        ActivityScenario.launch(MainActivity::class.java).use {
            node("Play against computer");capture("home")
            tap("Help & About");node("Report a bug");capture("help")
            tap("Report a bug");node("What happened? (required)");capture("bug")
            tap("Back");tap("Back")
            tap("Over the board");tap("Time control: Untimed");tap("3+2");tap("Start game")
            node("Pause clock");node("e4, empty");capture("clock")
            val first=Rect().also {node("a8, black rook").getBoundsInScreen(it)}
            val last=Rect().also {node("h1, white rook").getBoundsInScreen(it)}
            assertTrue("Board has positive area",first.width()>0 && last.height()>0)
            assertTrue("Squares stay square",kotlin.math.abs(first.width()-first.height())<=2)
            tap("Menu");tap("Review game");node("Return to game");capture("review")
            tap("Return to game");node("Resume clock")
        }
    }
}
