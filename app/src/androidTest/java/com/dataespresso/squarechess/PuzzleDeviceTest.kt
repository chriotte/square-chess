package com.dataespresso.squarechess

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Puzzles on the real screen: touch, drag, keyboard notation, hints, orientation, review, Quick 5
 * and recreation. Uses fixed puzzles from the bundled pack. Runs only in the .dev package: it
 * clears saved games and puzzle progress.
 */
@RunWith(AndroidJUnit4::class)
class PuzzleDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext

    @Before fun clean() {
        check(isIsolatedTestPackage(context.packageName))
        openChessDatabase(context).apply { clearAllTables(); close() }
        context.getSharedPreferences("puzzles",Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun find(node: AccessibilityNodeInfo?, match: (String) -> Boolean): AccessibilityNodeInfo? {
        if(node==null || !node.refresh()) return null
        val text=node.text?.toString(); val description=node.contentDescription?.toString()
        if((text!=null && match(text)) || (description!=null && match(description))) return node
        for(i in 0 until node.childCount) find(node.getChild(i),match)?.let { return it }
        return null
    }
    private fun node(description: String, match: (String) -> Boolean): AccessibilityNodeInfo {
        val deadline=SystemClock.uptimeMillis()+10_000
        while(SystemClock.uptimeMillis()<deadline) {
            dismissImmersiveModePrompt()
            find(instrumentation.uiAutomation.rootInActiveWindow,match)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Missing UI node: $description")
    }
    private fun node(text: String)=node(text) { it==text }
    private fun nodeStarting(prefix: String)=node(prefix) { it.startsWith(prefix) }
    private fun gone(text: String) {
        val deadline=SystemClock.uptimeMillis()+5_000
        while(SystemClock.uptimeMillis()<deadline) {
            if(find(instrumentation.uiAutomation.rootInActiveWindow) { it==text }==null) return
            SystemClock.sleep(100)
        }
        error("Still shown: $text")
    }
    private fun tap(target: AccessibilityNodeInfo) {
        var node=target
        while(!node.isClickable && node.parent!=null) node=node.parent
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun tap(text: String)=tap(node(text))
    /** A board square by its accessibility name, for example "h6" in "h6, white rook". */
    private fun square(name: String)=nodeStarting("$name, ")
    private fun bounds(node: AccessibilityNodeInfo)=Rect().also { node.getBoundsInScreen(it) }
    private fun type(text: String) {
        instrumentation.sendStringSync(text)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        instrumentation.waitForIdleSync()
    }
    private fun drag(from: String, to: String) {
        val a=bounds(square(from)); val b=bounds(square(to))
        instrumentation.uiAutomation.executeShellCommand("input swipe ${a.centerX()} ${a.centerY()} ${b.centerX()} ${b.centerY()} 500").close()
        SystemClock.sleep(900)
        instrumentation.waitForIdleSync()
    }

    /** Opens Puzzles from Home and starts [id] directly, so the test knows the solution. */
    private fun withPuzzle(id: String, block: (ActivityScenario<MainActivity>) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tap("Puzzles")
            node("Continue training")
            scenario.onActivity { it.puzzles.startPuzzle(id) }
            block(scenario)
        }
    }

    @Test fun wrongTapThenKeyboardSolutionWithReplyAndPromotion() = runBlocking<Unit> {
        withPuzzle("0Xh1Y") {
            node("Puzzle · White to move\nTraining level 1100")
            // White solves, so a1 is at the bottom left.
            assertTrue(bounds(square("a1")).top>bounds(square("a8")).top)
            assertTrue(bounds(square("a1")).left<bounds(square("h1")).left)
            // Rxa6 is legal but wrong: the position stays.
            tap(square("h6")); tap(square("a6"))
            nodeStarting("Not quite")
            nodeStarting("h6, white rook")
            type("Rxh2")
            // The scripted reply ...Rxh2 comes by itself.
            nodeStarting("h2, black rook")
            nodeStarting("Correct · find the next move")
            // UCI without the piece: the promotion choice opens.
            type("f7f8")
            tap(node(context.getString(R.string.piece_queen)))
            nodeStarting("Solved · puzzle rating 1803")
            node("Next")
        }
        val db=openChessDatabase(context)
        try {
            val progress=db.puzzleProgress().get("0Xh1Y")!!
            assertEquals(1,progress.mistakes)
            assertEquals(0,progress.firstTrySolved)
            assertEquals(0,progress.reviewStep)
            assertNull("A puzzle is never a saved game",db.games().latest())
        } finally { db.close() }
    }

    @Test fun blackPuzzleIsFlippedAndDragWorks() = runBlocking<Unit> {
        withPuzzle("00B3B") {
            node("Puzzle · Black to move\nTraining level 1100")
            // Black solves, so a1 is at the top right.
            assertTrue(bounds(square("a1")).top<bounds(square("a8")).top)
            assertTrue(bounds(square("a1")).left>bounds(square("h1")).left)
            drag("f6","d8")
            nodeStarting("d8, white king")        // the reply Kxd8
            type("Kc4")
            nodeStarting("Solved")
        }
        val db=openChessDatabase(context)
        try { assertEquals(1,db.puzzleProgress().get("00B3B")!!.firstTrySolved) } finally { db.close() }
    }

    @Test fun hintShowsTheMoveAndCounts() = runBlocking<Unit> {
        withPuzzle("0Xuss") {
            tap("Hint")
            node("Hint: Queen from d1 to h1, Qh1#")
            node("Hide")
            type("Qh1#")
            nodeStarting("Solved")
        }
        val db=openChessDatabase(context)
        try { assertEquals(1,db.puzzleProgress().get("0Xuss")!!.hints) } finally { db.close() }
    }

    @Test fun backClearsTypedMoveThenLeavesAndMistakesComeBackInReview() = runBlocking<Unit> {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tap("Puzzles")
            tap("Review mistakes")
            nodeStarting("No puzzles are due for review")
            scenario.onActivity { it.puzzles.startPuzzle("0Xh1Y") }
            nodeStarting("Puzzle · White to move")
            instrumentation.sendStringSync("Rxh")
            node("Move: Rxh")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DEL)
            node("Move: Rx")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE)
            gone("Move: Rx")
            tap(square("h6")); tap(square("a6"))
            nodeStarting("Not quite")
            // Back leaves the unsolved puzzle; the mistake makes it due for review.
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            node("Continue training")
            node("1 puzzle is due")
            tap("Review mistakes")
            nodeStarting("Puzzle · White to move")
            node("Puzzle · White to move\nReview mistakes")
        }
    }

    @Test fun recreationKeepsThePuzzleAndMove() = runBlocking<Unit> {
        withPuzzle("0Xh1Y") { scenario ->
            type("Rxh2")
            nodeStarting("h2, black rook")
            scenario.recreate()
            nodeStarting("Correct · find the next move")
            nodeStarting("h2, black rook")
            nodeStarting("f7, white pawn")
            type("f8=Q")
            nodeStarting("Solved")
        }
    }

    @Test fun quickFiveEndsWithASummary() = runBlocking<Unit> {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tap("Puzzles")
            tap("Quick 5")
            repeat(QUICK_SET_SIZE) { index ->
                nodeStarting("Puzzle · ")
                node("Quick 5 · puzzle ${index+1} of 5") { it.endsWith("Quick 5 · puzzle ${index+1} of 5") }
                // Solve by playing the expected moves; replies come by themselves.
                while(true) {
                    var session: PuzzleSession?=null
                    scenario.onActivity { session=it.puzzles.state.value.session }
                    val s=session ?: break
                    if(s.state==PuzzleState.SOLVED) break
                    val move=s.expectedMove
                    if(move!=null) scenario.onActivity { it.puzzles.move(move) }
                    SystemClock.sleep(700)
                }
                tap(if(index==QUICK_SET_SIZE-1) "Results" else "Next")
            }
            node("Quick 5 done")
            node("5 puzzles")
            node("Solved first try: 5")
            node("Hints used: 0")
            tap("Done")
            node("Continue training")
            node("Puzzles tried: 5")
        }
    }
}
