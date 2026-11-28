package com.dataespresso.squarechess

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
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

/**
 * Ratings on the real screen: a new puzzle moves the puzzle rating and a seen one does not, a
 * resigned rated game lowers the game rating, a game with a hint used is unrated, and the chart
 * on the progress page. Runs only in the .dev package: it clears games and puzzle progress.
 */
@RunWith(AndroidJUnit4::class)
class RatingDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val prefs get()=context.getSharedPreferences("puzzles",Context.MODE_PRIVATE)
    private lateinit var before: AppSettings

    @Before fun clean() {
        check(isIsolatedTestPackage(context.packageName))
        before=SettingsStore(context).load()
        SettingsStore(context).save(before.copy(eink=false,playful=false))
        openChessDatabase(context).apply { clearAllTables(); close() }
        prefs.edit().clear().commit()
    }
    @After fun restore() { SettingsStore(context).save(before) }

    private fun find(node: AccessibilityNodeInfo?, match: (String) -> Boolean): AccessibilityNodeInfo? {
        if(node==null || !node.refresh()) return null
        val text=node.text?.toString(); val description=node.contentDescription?.toString()
        if((text!=null && match(text)) || (description!=null && match(description))) return node
        for(i in 0 until node.childCount) find(node.getChild(i),match)?.let { return it }
        return null
    }
    private fun node(description: String, timeoutMs: Long=10_000, match: (String) -> Boolean): AccessibilityNodeInfo {
        val deadline=SystemClock.uptimeMillis()+timeoutMs
        var attempts=0
        while(SystemClock.uptimeMillis()<deadline) {
            dismissImmersiveModePrompt()
            find(activeRoot(),match)?.let { return it }
            if(++attempts%15==0) scrollForward()
            SystemClock.sleep(100)
        }
        error("Missing UI node: $description")
    }
    private fun node(text: String)=node(text) { it==text }
    private fun nodeWith(part: String, timeoutMs: Long=10_000)=node(part,timeoutMs) { it.contains(part) }
    private fun tap(target: AccessibilityNodeInfo) {
        var n=target
        while(!n.isClickable && n.parent!=null) n=n.parent
        assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun tap(text: String)=tap(node(text))
    private fun type(move: String) {
        instrumentation.sendStringSync(move)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        instrumentation.waitForIdleSync()
    }
    private fun solve(scenario: ActivityScenario<MainActivity>, id: String) {
        scenario.onActivity { it.puzzles.startPuzzle(id) }
        var session: PuzzleSession?=null
        val deadline=SystemClock.uptimeMillis()+10_000
        while(session?.puzzle?.id!=id && SystemClock.uptimeMillis()<deadline) {
            scenario.onActivity { session=it.puzzles.state.value.session }
            SystemClock.sleep(100)
        }
        val moves=session!!.puzzle.moves
        for(ply in 1 until moves.size step 2) {
            type(moves[ply])
            if(ply+2<moves.size) SystemClock.sleep(PuzzleViewModel.REPLY_DELAY_MS+300)
        }
        nodeWith("Puzzle solved")
    }

    @Test fun puzzleRatingCountsOnlyTheFirstAttempt() { run {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tap("Puzzles")
            nodeWith("Puzzle rating 1100?")
            solve(scenario,"0Xh1Y")
            // Solved first try against a harder puzzle (1803): up, provisional.
            nodeWith("Puzzle rating 11")
            val after=prefs.getInt("trainingLevel",0)
            assertTrue("Rating went up: $after",after>1100)
            nodeWith("(+")
            tap("Exit")
            // The same puzzle again: seen before, so it does not count.
            solve(scenario,"0Xh1Y")
            nodeWith("Not rated: only the first try at a puzzle counts.")
            assertEquals(after,prefs.getInt("trainingLevel",0))
        }
    } }

    @Test fun resignedRatedGameLowersTheRatingAndAHintMakesAGameUnrated() = runBlocking<Unit> {
        val db=openChessDatabase(context)
        try {
            db.games().save(SavedGame("rated",GameMode.COMPUTER.name,white="You",black="Computer (Level 1)",humanWhite=true,level=1))
            ActivityScenario.launch(MainActivity::class.java).use {
                tap("Continue game")
                nodeWith("Rated game")
                type("e2e4")
                // Wait for the computer's reply: two plies, so the game is not treated as aborted.
                val deadline=SystemClock.uptimeMillis()+20_000
                while(db.games().latest()!!.moves.split(' ').count { it.isNotBlank() }<2 && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(200)
                tap("Menu"); tap("Resign")
                node("Resign this game?")
                tap(node("Resign"))
                nodeWith("Game rating 9")
                nodeWith("(−")
            }
            val rated=db.games().latest()!!
            assertNotNull(rated.rating)
            assertTrue(rated.rating!!.change<0)
            assertEquals(1,gameRatingFromJson(org.json.JSONObject(prefs.getString("gameRating",null)!!))!!.games)

            db.games().save(SavedGame("hinted",GameMode.COMPUTER.name,white="You",black="Computer (Level 1)",humanWhite=true,level=1,
                hintsEnabled=true,updated=System.currentTimeMillis()+1_000))
            ActivityScenario.launch(MainActivity::class.java).use {
                tap("Continue game")
                nodeWith("Rated game")
                tap("Hint")
                type("e2e4")
                val deadline=SystemClock.uptimeMillis()+20_000
                while(db.games().latest()!!.moves.split(' ').count { it.isNotBlank() }<2 && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(200)
                // The hint has gone with the move; the header now says the game no longer counts.
                nodeWith("Unrated game")
                tap("Menu"); tap("Resign")
                node("Resign this game?")
                tap(node("Resign"))
                node("This game was not rated.")
            }
            val hinted=db.games().latest()!!
            assertTrue(hinted.unrated)
            assertNull(hinted.rating)
            assertEquals(1,gameRatingFromJson(org.json.JSONObject(prefs.getString("gameRating",null)!!))!!.games)

            // The progress page shows both ratings and the chart, read as its values.
            ActivityScenario.launch(MainActivity::class.java).use {
                tap("Puzzles")
                tap("Progress by theme")
                node("Ratings")
                nodeWith("1 rated game · 0 won · 0 drawn · 1 lost")
                nodeWith("Game rating: 9")
            }
        } finally { db.close() }
    }
}
