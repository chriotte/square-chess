package com.dataespresso.squarechess

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Playroom: a world, the buddy on Home, Recent games and the buddy screen. Runs in the .dev package. */
@RunWith(AndroidJUnit4::class)
class PlayroomDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private lateinit var before: AppSettings

    @Before fun clean() {
        check(isIsolatedTestPackage(context.packageName))
        before=SettingsStore(context).load()
        openChessDatabase(context).apply {
            clearAllTables()
            runBlocking {
                games().save(SavedGame(id="playroom-1",mode=GameMode.COMPUTER.name,moves="e2e4 e7e5 f1c4 b8c6 d1h5 g8f6 h5f7",
                    white="You",black="Computer (Level 3)",humanWhite=true,level=3,result="1-0",resultReason="Checkmate",updated=1_000L))
            }
            close()
        }
    }
    @After fun restore() { SettingsStore(context).save(before) }

    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if(node==null || !node.refresh()) return null
        // Merged rows (the buddy card, a recent game) read several texts as one label.
        if(node.text?.toString()?.contains(text)==true || node.contentDescription?.toString()?.contains(text)==true) return node
        for(i in 0 until node.childCount) find(node.getChild(i),text)?.let { return it }
        return null
    }
    private fun node(text: String, timeoutMs: Long=10_000): AccessibilityNodeInfo? {
        val deadline=SystemClock.uptimeMillis()+timeoutMs
        var attempts=0
        while(SystemClock.uptimeMillis()<deadline) {
            dismissImmersiveModePrompt()
            find(activeRoot(),text)?.let { return it }
            if(++attempts%20==0) scrollForward()
            SystemClock.sleep(100)
        }
        return null
    }
    private fun tap(text: String) {
        var target=node(text) ?: error("Missing UI node: $text")
        while(!target.isClickable && target.parent!=null) target=target.parent
        assertTrue("Could not tap $text",target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    @Test fun buddyRecentGamesAndStickers() {
        SettingsStore(context).save(before.copy(eink=false,world=World.FOREST,playful=true,buddy=Buddy.ROBOT,
            outfit=Outfit(),seenStickers=emptySet()))
        ActivityScenario.launch(MainActivity::class.java).use {
            assertNotNull("Buddy hello",node("Beep boop. That means hello."))
            // The finished game earned stickers that are new until the buddy screen is opened.
            assertNotNull("New sticker",node("New sticker: First game"))
            // Recent games sit at the bottom and scroll sideways. Swipe the page up by hand: the shared
            // scroll helper would also scroll the sideways row and move the first game out of view.
            val metrics=context.resources.displayMetrics
            val x=metrics.widthPixels/2; val from=metrics.heightPixels*8/10; val to=metrics.heightPixels*2/10
            repeat(4) { instrumentation.uiAutomation.executeShellCommand("input swipe $x $from $x $to 300").close(); SystemClock.sleep(600) }
            assertNotNull("Recent games heading",node("RECENT GAMES",timeoutMs=1_900))
            assertNotNull("Recent game",node("You won · Level 3",timeoutMs=1_900))
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            tap("Beep boop. That means hello.")
            assertNotNull(node("Your buddy"))
            assertNotNull(node("Grey, boxy and pancake-powered."))
            // Last: finding it scrolls the screen down to the sticker book.
            assertNotNull(node("STICKER BOOK",timeoutMs=40_000))
        }
        // Opening the buddy screen marked the stickers as seen.
        val seen=SettingsStore(context).load().seenStickers
        assertTrue(Sticker.FIRST_GAME in seen)
        assertTrue(Sticker.FIRST_CHECKMATE in seen)
    }

    @Test fun playfulExtrasOffShowsNoBuddy() {
        SettingsStore(context).save(before.copy(eink=false,world=World.CLASSIC,playful=false))
        ActivityScenario.launch(MainActivity::class.java).use {
            assertNotNull(node("Play against computer"))
            assertNull("No buddy when Playful extras is off",node("Beep boop. That means hello.",timeoutMs=1_500))
        }
    }
}
