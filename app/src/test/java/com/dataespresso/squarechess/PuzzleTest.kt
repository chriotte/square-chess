package com.dataespresso.squarechess

import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.random.Random

private fun puzzle(line: String) = requireNotNull(parsePuzzleLine(line)) { "unreadable: $line" }

/** Rows from the bundled pack, and small positions made for one rule each. */
private val PROMOTION_LINE="0Xh1Y\t6K1/1p1k1P2/p6R/8/PP6/8/5r1p/8 b - - 4 62\td7e7 h6h2 f2h2 f7f8q\t1803\t95\tadvancedPawn deflection endgame promotion rookEndgame"
private val BLACK_LINE="00B3B\t2K5/3P4/5b2/p1B5/P7/3k4/6p1/8 w - - 7 77\td7d8q f6d8 c8d8 d3c4\t1429\t94\tendgame"
private val MATE_IN_ONE="0Xuss\t8/2p5/ppk3p1/5pN1/P1P2P1p/5PnP/5QK1/2Bq4 w - - 0 45\tc1e3 d1h1\t952\t98\tendgame mate mateIn1"
// Back rank: after ...c6 both Ra8# and Rb8# mate; the script says Ra8#.
private val TWO_MATES="twoMates\t7k/2p3pp/8/8/8/8/8/RR4K1 b - - 0 1\tc7c6 a1a8\t800\t90\tmate mateIn1 backRankMate"
private val CASTLING="castle\t4k3/8/8/8/8/8/8/4K2R b K - 0 1\te8d8 e1g1\t900\t90\tcastling"

class PuzzleParsingTest {
    @Test fun readsAPackRow() {
        val p=puzzle(PROMOTION_LINE)
        assertEquals("0Xh1Y",p.id)
        assertEquals(listOf("d7e7","h6h2","f2h2","f7f8q"),p.moves)
        assertEquals(1803,p.rating)
        assertEquals(95,p.popularity)
        assertTrue("promotion" in p.themes)
        assertEquals(setOf(ThemeGroup.MOTIFS,ThemeGroup.ENDGAMES),p.group)
    }
    @Test fun rejectsBrokenRows() {
        assertNull(parsePuzzleLine(""))
        assertNull(parsePuzzleLine("id\tfen"))
        assertNull(parsePuzzleLine("x\t8/8/8/8/8/8/8/8 w - - 0 1\te2e4\t1000\t90\t"))            // no solver move
        assertNull(parsePuzzleLine("x\t8/8/8/8/8/8/8/8 w - - 0 1\te2e4 Nf3\t1000\t90\t"))         // not UCI
        assertNull(parsePuzzleLine("x\t8/8/8/8/8/8/8/8 w - - 0 1\te2e4 e7e5\tabc\t90\t"))         // rating
        assertNull(parsePuzzleLine("x\t8/8/8 w\te2e4 e7e5\t1000\t90\t"))                           // FEN fields
        assertNull(parsePuzzleLine("\t8/8/8/8/8/8/8/8 w - - 0 1\te2e4 e7e5\t1000\t90\t"))          // no ID
    }
    @Test fun packSkipsBadLinesAndRepeatedIds() {
        val list=parsePuzzlePack(sequenceOf(MATE_IN_ONE,"garbage",MATE_IN_ONE,TWO_MATES))
        assertEquals(listOf("0Xuss","twoMates"),list.map { it.id })
    }
    @Test fun illegalScriptCannotStart() {
        // Legal-looking UCI that is not legal here: the pawn on e2 cannot jump to e5.
        val broken=puzzle("bad\t4k3/8/8/8/8/8/4P3/4K3 b - - 0 1\te8d8 e2e5\t900\t90\t")
        assertNull(PuzzleSession.start(broken))
    }
    @Test fun wholeBundledPackIsPlayable() {
        val file=File("src/main/assets/puzzles/puzzles-v1.tsv")
        val lines=file.readLines()
        val puzzles=parsePuzzlePack(lines.asSequence())
        assertEquals("every line reads",lines.size,puzzles.size)
        assertTrue("pack size",puzzles.size in 1000..20000)
        val broken=puzzles.filter { PuzzleSession.start(it)==null }.map { it.id }
        assertEquals(emptyList<String>(),broken)
        assertTrue(puzzles.all { it.rating in 400..3200 })
    }
}

class PuzzleSessionTest {
    @Test fun setupMoveIsPlayedBeforeThePuzzleIsShown() {
        val s=PuzzleSession.start(puzzle(PROMOTION_LINE))!!
        assertEquals(1,s.ply)
        assertEquals(listOf("d7e7"),s.position.moves)
        // Black set up the position; White solves.
        assertTrue(s.solverWhite)
        assertEquals(PuzzleState.SOLVING,s.state)
        assertEquals("h6h2",s.expectedMove)
        assertEquals("d7e7",s.lastMove)
        assertEquals(Piece.BLACK_KING,s.position.board.getPiece(Square.E7))
    }
    @Test fun correctMoveThenScriptedReplyThenSolve() {
        var s=PuzzleSession.start(puzzle(PROMOTION_LINE))!!
        val (first,afterFirst)=s.judge("h6h2")
        assertEquals(MoveVerdict.CORRECT,first)
        assertEquals(PuzzleState.OPPONENT_TO_MOVE,afterFirst.state)
        assertEquals("f2h2",afterFirst.pendingReply)
        s=afterFirst.reply()
        assertEquals(listOf("d7e7","h6h2","f2h2"),s.position.moves)
        assertEquals(PuzzleState.SOLVING,s.state)
        // Promotion is part of the move: f7f8 without q is a different, legal-looking entry.
        val (last,done)=s.judge("f7f8q")
        assertEquals(MoveVerdict.SOLVED,last)
        assertEquals(PuzzleState.SOLVED,done.state)
        assertTrue(done.firstTry)
        assertEquals(Piece.WHITE_QUEEN,done.position.board.getPiece(Square.F8))
    }
    @Test fun wrongMoveChangesNothingButTheMistakeCount() {
        val s=PuzzleSession.start(puzzle(PROMOTION_LINE))!!
        val (verdict,after)=s.judge("h6a6")      // Rxa6: legal, not the solution
        assertEquals(MoveVerdict.WRONG,verdict)
        assertEquals(s.position.board.fen,after.position.board.fen)
        assertEquals(1,after.mistakes)
        assertFalse(after.firstTry)
        assertEquals("h6h2",after.expectedMove)
    }
    @Test fun textThatIsNotAMoveIsNotAMistake() {
        val s=PuzzleSession.start(puzzle(PROMOTION_LINE))!!
        val (verdict,after)=s.judge("Qz9")
        assertEquals(MoveVerdict.ILLEGAL,verdict)
        assertEquals(0,after.mistakes)
    }
    @Test fun sanAndUciAreBothAccepted() {
        val s=PuzzleSession.start(puzzle(PROMOTION_LINE))!!
        assertEquals(MoveVerdict.CORRECT,s.judge("Rxh2").first)
        assertEquals(MoveVerdict.CORRECT,s.judge("h6h2").first)
    }
    @Test fun blackToSolve() {
        val s=PuzzleSession.start(puzzle(BLACK_LINE))!!
        assertFalse(s.solverWhite)
        assertEquals(com.github.bhlangonijr.chesslib.Side.BLACK,s.position.board.sideToMove)
        assertEquals("f6d8",s.expectedMove)
        // The setup move was a promotion; the solver's first move captures the new queen.
        val (verdict,next)=s.judge("Bxd8")
        assertEquals(MoveVerdict.CORRECT,verdict)
        assertEquals(MoveVerdict.SOLVED,next.reply().judge("d3c4").first)
    }
    @Test fun anotherMateInOneIsAlsoCorrect() {
        val s=PuzzleSession.start(puzzle(TWO_MATES))!!
        val (verdict,done)=s.judge("b1b8")
        assertEquals(MoveVerdict.SOLVED,verdict)
        assertEquals(PuzzleState.SOLVED,done.state)
        assertTrue(done.position.board.isMated)
        assertEquals("b1b8",done.lastMove)
        assertEquals(MoveVerdict.SOLVED,s.judge("Ra8#").first)
    }
    @Test fun scriptedMateInOne() {
        val s=PuzzleSession.start(puzzle(MATE_IN_ONE))!!
        assertFalse(s.solverWhite)
        assertEquals(MoveVerdict.SOLVED,s.judge("Qh1#").first)
    }
    @Test fun castlingByNotationOrKingMove() {
        val s=PuzzleSession.start(puzzle(CASTLING))!!
        assertEquals(MoveVerdict.SOLVED,s.judge("O-O").first)
        assertEquals(MoveVerdict.SOLVED,s.judge("e1g1").first)
        assertEquals(MoveVerdict.WRONG,s.judge("Kf1").first)
    }
    @Test fun hintMarksTheAttempt() {
        val s=PuzzleSession.start(puzzle(MATE_IN_ONE))!!.withHint()
        assertTrue(s.hintUsed)
        assertFalse(s.judge("Qh1#").second.firstTry)
    }
    @Test fun movesAreIgnoredWhenItIsNotTheSolversTurn() {
        val waiting=PuzzleSession.start(puzzle(PROMOTION_LINE))!!.judge("h6h2").second
        assertEquals(MoveVerdict.NOT_NOW,waiting.judge("f7f8q").first)
    }
    @Test fun restoreReturnsToTheSameMove() {
        val p=puzzle(PROMOTION_LINE)
        val restored=PuzzleSession.restore(p,3,1,true)!!
        assertEquals(listOf("d7e7","h6h2","f2h2"),restored.position.moves)
        assertEquals(1,restored.mistakes)
        assertTrue(restored.hintUsed)
        assertEquals(PuzzleState.SOLVING,restored.state)
    }
}

class PuzzleTrainingTest {
    private val day=24*60*60*1000L

    @Test fun reviewScheduleBringsMistakesBack() {
        val now=1_000_000L
        val failed=recordAttempt(null,"p",AttemptResult.WITH_HELP,1,false,now)
        assertEquals(0,failed.reviewStep)
        assertEquals(now,failed.nextReviewAt)
        assertEquals(listOf("p"),dueForReview(listOf(failed),now))
        val second=recordAttempt(failed,"p",AttemptResult.FIRST_TRY,0,false,now+10)
        assertEquals(1,second.reviewStep)
        assertEquals(now+10+3*day,second.nextReviewAt)
        assertEquals(emptyList<String>(),dueForReview(listOf(second),now+day))
        val third=recordAttempt(second,"p",AttemptResult.FIRST_TRY,0,false,now+4*day)
        assertEquals(now+4*day+7*day,third.nextReviewAt)
        val fourth=recordAttempt(third,"p",AttemptResult.FIRST_TRY,0,false,now+12*day)
        assertEquals(now+12*day+30*day,fourth.nextReviewAt)
        val retired=recordAttempt(fourth,"p",AttemptResult.FIRST_TRY,0,false,now+50*day)
        assertNull(retired.reviewStep)
        assertNull(retired.nextReviewAt)
        // A new mistake starts the cycle again.
        assertEquals(0,recordAttempt(third,"p",AttemptResult.ABANDONED,0,true,now).reviewStep)
        assertEquals(5,retired.attempts)
        assertEquals(4,retired.firstTrySolved)
    }
    @Test fun firstTrySolveOfANewPuzzleIsNotReviewed() {
        val p=recordAttempt(null,"p",AttemptResult.FIRST_TRY,0,false,5)
        assertNull(p.nextReviewAt)
        assertEquals(1,p.solved)
    }
    @Test fun hintOrAbandonGoesToReview() {
        assertEquals(0,recordAttempt(null,"p",AttemptResult.WITH_HELP,0,true,5).reviewStep)
        val left=recordAttempt(null,"p",AttemptResult.ABANDONED,2,false,5)
        assertEquals(0,left.solved)
        assertEquals(2,left.mistakes)
        assertEquals(5L,left.nextReviewAt)
    }
    @Test fun dueListIsOldestFirst() {
        val a=recordAttempt(null,"a",AttemptResult.WITH_HELP,1,false,300)
        val b=recordAttempt(null,"b",AttemptResult.WITH_HELP,1,false,100)
        val c=recordAttempt(null,"c",AttemptResult.WITH_HELP,1,false,900)
        assertEquals(listOf("b","a"),dueForReview(listOf(a,b,c),500))
    }
    @Test fun trainingLevelMovesInSmallSteps() {
        assertEquals(1115,nextTrainingLevel(1100,AttemptResult.FIRST_TRY,1100))
        assertEquals(1125,nextTrainingLevel(1100,AttemptResult.FIRST_TRY,1500))   // harder puzzle: capped step
        assertEquals(1105,nextTrainingLevel(1100,AttemptResult.FIRST_TRY,900))    // easier puzzle: small step
        assertEquals(1095,nextTrainingLevel(1100,AttemptResult.WITH_HELP,1100))
        assertEquals(1080,nextTrainingLevel(1100,AttemptResult.ABANDONED,1100))
        assertEquals(MIN_TRAINING_LEVEL,nextTrainingLevel(MIN_TRAINING_LEVEL,AttemptResult.ABANDONED,700))
        assertEquals(MAX_TRAINING_LEVEL,nextTrainingLevel(MAX_TRAINING_LEVEL,AttemptResult.FIRST_TRY,2600))
    }

    private fun p(id: String, rating: Int, vararg themes: String) = Puzzle(id,"8/8/8/8/8/8/8/K6k w - - 0 1",listOf("a1a2","h1h2"),rating,90,themes.toSet())
    private val pool=listOf(p("a",1000,"fork"),p("b",1100,"pin"),p("c",1120,"mateIn1"),p("d",1500,"fork"),p("e",2200,"endgame"))

    @Test fun choosesUnseenPuzzlesNearTheLevel() {
        repeat(20) { seed ->
            val chosen=choosePuzzle(pool,1100,emptyMap(),emptySet(),null,Random(seed))!!
            assertTrue(chosen.id in setOf("a","b","c"))
        }
        val seen=mapOf("a" to recordAttempt(null,"a",AttemptResult.FIRST_TRY,0,false,1),
            "b" to recordAttempt(null,"b",AttemptResult.FIRST_TRY,0,false,1),"c" to recordAttempt(null,"c",AttemptResult.FIRST_TRY,0,false,1))
        // Nothing unseen within 300: the nearest unseen one.
        assertEquals("d",choosePuzzle(pool,1100,seen,emptySet(),null,Random(1))!!.id)
    }
    @Test fun themeAndAvoidListAreRespected() {
        repeat(10) { seed ->
            assertEquals("a",choosePuzzle(pool,1100,emptyMap(),setOf("d"),ThemeGroup.FORKS,Random(seed))!!.id)
        }
        assertNull(choosePuzzle(pool,1100,emptyMap(),setOf("a","d"),ThemeGroup.FORKS,Random(1)))
        assertEquals("e",choosePuzzle(pool,1100,emptyMap(),emptySet(),ThemeGroup.ENDGAMES,Random(1))!!.id)
    }
    @Test fun whenAllAreSeenTheLongestUnseenComesFirst() {
        val progress=pool.mapIndexed { i,it -> it.id to recordAttempt(null,it.id,AttemptResult.FIRST_TRY,0,false,100L-i) }.toMap()
        assertEquals("e",choosePuzzle(pool,1100,progress,emptySet(),null,Random(1))!!.id)
        assertEquals("d",choosePuzzle(pool,1100,progress,setOf("e"),null,Random(1))!!.id)
    }
    @Test fun aQuickSetHasFiveDifferentPuzzles() {
        val big=(1..40).map { p("p$it",900+it*10) }
        val chosen=mutableListOf<String>()
        repeat(QUICK_SET_SIZE) { chosen+=choosePuzzle(big,1100,emptyMap(),chosen.toSet(),null,Random(7))!!.id }
        assertEquals(QUICK_SET_SIZE,chosen.toSet().size)
    }
    @Test fun totalsCountEachPuzzleOnce() {
        val progress=listOf(
            recordAttempt(null,"a",AttemptResult.FIRST_TRY,0,false,1),
            recordAttempt(null,"b",AttemptResult.WITH_HELP,1,false,1),
            recordAttempt(null,"c",AttemptResult.ABANDONED,0,true,1))
        assertEquals(PuzzleTotals(3,1,1,2),puzzleTotals(progress,10))
    }
}
