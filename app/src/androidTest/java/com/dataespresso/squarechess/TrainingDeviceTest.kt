package com.dataespresso.squarechess

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Structured training on the real screen: chapter 1 of the course with one wrong move and its
 * explanation, the chapter summary, progress by theme, the daily goal and one repeat cycle.
 * Solves puzzles by their scripted solutions. Runs only in the .dev package: it clears puzzle progress.
 */
@RunWith(AndroidJUnit4::class)
class TrainingDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private lateinit var before: AppSettings

    @Before fun clean() {
        check(isIsolatedTestPackage(context.packageName))
        before=SettingsStore(context).load()
        SettingsStore(context).save(before.copy(eink=false,playful=false))
        openChessDatabase(context).apply { clearAllTables(); close() }
        context.getSharedPreferences("puzzles",Context.MODE_PRIVATE).edit().clear().commit()
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
            // Pages scroll; what is below the screen is not in the tree until it scrolls into view.
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

    private fun session(scenario: ActivityScenario<MainActivity>): PuzzleSession {
        var session: PuzzleSession?=null
        val deadline=SystemClock.uptimeMillis()+10_000
        while(session==null && SystemClock.uptimeMillis()<deadline) {
            scenario.onActivity { session=it.puzzles.state.value.session?.takeIf { s -> s.state==PuzzleState.SOLVING } }
            if(session==null) SystemClock.sleep(100)
        }
        return session ?: error("No puzzle to solve")
    }
    /** Plays the solver's moves of the puzzle on screen, waiting for each scripted reply. */
    private fun solve(scenario: ActivityScenario<MainActivity>) {
        val first=session(scenario)
        var ply=first.ply
        while(ply<first.puzzle.moves.size) {
            val now=session(scenario)
            assertEquals(first.puzzle.id,now.puzzle.id)
            if(now.ply!=ply) { ply=now.ply; continue }
            type(first.puzzle.moves[ply])
            ply+=2
            if(ply<first.puzzle.moves.size) SystemClock.sleep(PuzzleViewModel.REPLY_DELAY_MS+300)
        }
        nodeWith("Puzzle solved")
    }

    @Test fun courseChapterProgressGoalAndRepeatCycle() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tap("Puzzles")
            nodeWith("Today: 0 of 10 puzzles")
            tap("Course")
            tap("Continue the course")
            nodeWith("Chapter 1 · Mate in one · 1 of 10")
            // Tall screens have room below the board for the tip and the explanation card.
            val metrics=context.resources.displayMetrics
            val tall=metrics.heightPixels>metrics.widthPixels*1.4
            if(tall) nodeWith("Look at every check first.")

            // A wrong move: legal, not the solution and not a mate. The engine explains it.
            val s=session(scenario)
            val wrong=s.position.legal.map { it.toString() }.first { m ->
                m!=s.expectedMove && !ChessPosition(s.position.initialFen,s.position.moves+m).board.isMated
            }
            type(wrong)
            nodeWith("Not quite")
            val explanation=node("explanation",20_000) { it.contains("After ") || it.contains("does not work here. Try again.") || it.contains("That move ends the game") }
            if(tall && explanation.text.toString().contains("After ")) {
                tap("Show me")
                node("Back to the puzzle")
                tap("Back to the puzzle")
                node("Show me")
            }

            for(i in 1..10) {
                solve(scenario)
                tap(if(i<10) "Next" else "Finish the chapter")
            }
            node("Chapter finished")
            // Nine of ten without a mistake: two stars.
            node("2 of 3 stars")
            node("Solved without a mistake or a hint: 9 of 10")
            tap("Back to the course")
            tap("‹ Puzzles")
            node("Daily goal reached")
            nodeWith("1 day in a row")

            tap("Progress by theme")
            nodeWith("1/27")
            nodeWith("First try: 90% · Tried: 10")
            // Back from a page returns to the Puzzles landing screen.
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()

            tap("Repeat set")
            tap("10")
            tap("Make the set")
            tap("Start cycle 1")
            for(i in 1..10) {
                nodeWith("Repeat set · cycle 1 · $i of 10")
                solve(scenario)
                tap(if(i<10) "Next" else "Finish the cycle")
            }
            nodeWith("Cycle 1 finished in")
            node("Start cycle 2")
        }
        val prefs=context.getSharedPreferences("puzzles",Context.MODE_PRIVATE)
        // The course and repeat set leave the training level alone; the day count holds all 20.
        assertFalse(prefs.contains("trainingLevel"))
        assertTrue(prefs.getString("puzzleDays",null)!!.contains(":20"))
        val set=repeatSetFromJson(org.json.JSONObject(prefs.getString("repeatSet",null)!!))!!
        assertEquals(1,set.cycles.size)
        assertEquals(10,set.cycles[0].clean)
    }
}
