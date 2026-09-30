package com.dataespresso.squarechess

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EvaluationTest {
    @Test fun scoresAreTurnedToWhitesSide() {
        assertEquals(Eval(31,null,"e2e4"),evalFromScore("cp",31,whiteToMove=true,best="e2e4"))
        assertEquals(Eval(-31),evalFromScore("cp",31,whiteToMove=false,best=null))
        // Black to move and mating in 2 is good for Black.
        assertEquals(Eval(-MATE_SCORE,2),evalFromScore("mate",2,whiteToMove=false,best=null))
        // White to move and mated in 3 is also good for Black.
        assertEquals(Eval(-MATE_SCORE,3),evalFromScore("mate",-3,whiteToMove=true,best=null))
    }
    @Test fun transcriptGivesTheLastFullScoreOfTheFirstLine() {
        val transcript="""
            info depth 1 seldepth 1 multipv 1 score cp 12 nodes 20 pv e2e4
            info depth 2 seldepth 2 multipv 1 score cp 40 nodes 90 pv d2d4
            info depth 3 seldepth 3 multipv 2 score cp -5 nodes 300 pv a2a3
            info depth 4 seldepth 4 multipv 1 score cp 55 lowerbound nodes 400 pv d2d4
            bestmove d2d4 ponder d7d5
        """.trimIndent()
        assertEquals(Eval(40,null,"d2d4"),parseSearchScore(transcript,whiteToMove=true,best="d2d4"))
        assertNull(parseSearchScore("bestmove e2e4",true,"e2e4"))
    }
    @Test fun theEnginesMoveIsBest() {
        assertEquals(MoveQuality.BEST,moveQuality(Eval(30,null,"e2e4"),Eval(-200),"e2e4",whiteMoved=true))
    }
    @Test fun lostWinningChanceGivesTheLabel() {
        val before=Eval(30,null,"e2e4")
        assertEquals(MoveQuality.GOOD,moveQuality(before,Eval(10),"d2d4",true))
        assertEquals(MoveQuality.INACCURACY,moveQuality(before,Eval(-90),"a2a3",true))
        assertEquals(MoveQuality.MISTAKE,moveQuality(before,Eval(-200),"g2g4",true))
        assertEquals(MoveQuality.BLUNDER,moveQuality(before,Eval(-600),"f2f3",true))
        // For Black the same scores mean the opposite.
        assertEquals(MoveQuality.GOOD,moveQuality(Eval(-30,null,"e7e5"),Eval(-600),"c7c5",false))
    }
    @Test fun matingMoveIsBest() {
        assertEquals(MoveQuality.BEST,moveQuality(Eval(900,null,"d1h5"),Eval(MATE_SCORE,0),"d8h4",true))
    }
    @Test fun leadWords() {
        assertEquals(Lead.EQUAL,lead(Eval(-40)))
        assertEquals(Lead.WHITE_SLIGHTLY,lead(Eval(80)))
        assertEquals(Lead.BLACK_BETTER,lead(Eval(-200)))
        assertEquals(Lead.WHITE_WINNING,lead(Eval(450)))
        assertEquals(Lead.BLACK_MATES,lead(Eval(-MATE_SCORE,3)))
        assertEquals("+1.8",scoreText(Eval(180)))
        assertEquals("−0.4",scoreText(Eval(-40)))
        assertEquals("0.0",scoreText(Eval(0)))
        assertEquals("",scoreText(Eval(MATE_SCORE,2)))
    }
    @Test fun terminalPositionsNeedNoSearch() {
        // Fool's mate: White is checkmated.
        val mated=ChessPosition(moves=listOf("f2f3","e7e5","g2g4","d8h4"))
        assertEquals(Eval(-MATE_SCORE,0),terminalEval(mated))
        assertNull(terminalEval(ChessPosition()))
    }
    @Test fun moverFollowsTheStartingSide() {
        assertTrue(whiteMovedAt(START_FEN,1))
        assertFalse(whiteMovedAt(START_FEN,2))
        assertFalse(whiteMovedAt("4k3/8/8/8/8/8/8/4K3 b - - 0 1",1))
    }
    @Test fun summaryCountsEachSide() {
        val moves=listOf("e2e4","e7e5","g1f3")
        // 1.e4 is the engine's move, 1...e5 drops Black from 47 % to 25 %, 2.Nf3 White from 75 % to 19 %.
        val evals=mapOf(0 to Eval(30,null,"e2e4"),1 to Eval(30,null,"c7c5"),2 to Eval(300,null,"d2d4"),3 to Eval(-400))
        val (white,black)=qualitySummary(evals,START_FEN,moves)
        assertEquals(QualityCount(0,0,1),white)
        assertEquals(QualityCount(0,1,0),black)
        assertNull(qualityAt(evals,START_FEN,moves,4))
    }
    @Test fun savedFormRoundTrips() {
        val evals=mapOf(0 to Eval(30,null,"e2e4"),1 to Eval(-MATE_SCORE,3,"d8h4"),2 to Eval(MATE_SCORE,0),5 to Eval(-12,null,"e7e8q"))
        val text=encodeEvaluations(evals)
        assertEquals("0=c30/e2e4 1=m-3/d8h4 2=m+0/ 5=c-12/e7e8q",text)
        assertEquals(evals,decodeEvaluations(text))
        assertEquals(emptyMap<Int,Eval>(),decodeEvaluations(""))
        assertEquals(mapOf(0 to Eval(1)),decodeEvaluations("0=c1/ junk 1=x/"))
    }
}

class OpeningsTest {
    private val table: Map<String,Opening> by lazy {
        File("src/main/assets/openings.tsv").useLines { parseOpenings(it) }
    }
    @Test fun namesFollowTheGame() {
        val ruyLopez=listOf("e2e4","e7e5","g1f3","b8c6","f1b5")
        assertEquals(Opening("C60","Ruy Lopez"),openingAt(table,START_FEN,ruyLopez))
        assertEquals("C20",openingAt(table,START_FEN,ruyLopez,2)?.eco)
        assertNull(openingAt(table,START_FEN,emptyList()))
    }
    @Test fun nameStaysWhenPlayLeavesTheList() {
        val moves=listOf("e2e4","e7e5","g1f3","b8c6","f1b5","a7a6","b5a4","g8f6","e1g1","f8e7","h2h3","h7h6","a2a3")
        assertTrue(openingAt(table,START_FEN,moves)!!.name.startsWith("Ruy Lopez"))
    }
    @Test fun transpositionsFindTheOpening() {
        // 1.Nf3 Nf6 2.c4 e6 3.Nc3 d5 4.d4 reaches a Queen's Gambit Declined position.
        val moves=listOf("g1f3","g8f6","c2c4","e7e6","b1c3","d7d5","d2d4")
        assertTrue(openingAt(table,START_FEN,moves)!!.name.startsWith("Queen's Gambit Declined"))
    }
}

class HeaderTextTest {
    private fun header(quality: String?, lead: String?, opening: String?=null, oneLine: Boolean=false, statusFirst: Boolean=false, review: Boolean=false, move: String="12. Nf3 · ") =
        headerText(move,if(review) "Move 3 of 9" else "Your move"," · Level 3",quality,lead,opening,oneLine,statusFirst,review)

    @Test fun withoutEvaluationTheOpeningTakesTheSecondLine() {
        assertEquals("12. Nf3 · Your move · Level 3\nC60 Ruy Lopez",header(null,null,"C60 Ruy Lopez"))
        assertEquals("12. Nf3 · Your move · Level 3",header(null,null,"C60 Ruy Lopez",oneLine=true))
    }
    @Test fun evaluationReplacesTheStatus() {
        assertEquals("12. Nf3 · Good move\n+0.6 · White slightly better",header("Good move","+0.6 · White slightly better","C60"))
        assertEquals("12. Nf3 · Good move · +0.6 · White slightly better",header("Good move","+0.6 · White slightly better",oneLine=true))
        // Before the move is rated, the status stays with the previous lead.
        assertEquals("12. Nf3 · Your move\nEqual",header(null,"Equal"))
    }
    @Test fun importantStatesComeFirst() {
        assertEquals("12. Nf3 · Your move · Level 3",header("Blunder","Black winning",oneLine=true,statusFirst=true))
    }
    @Test fun reviewKeepsThePosition() {
        assertEquals("Move 3 of 9 · Mistake\n+1.2 · White slightly better",header("Mistake","+1.2 · White slightly better",review=true,move=""))
        assertEquals("Move 3 of 9 · Mistake · Equal",header("Mistake","Equal",review=true,oneLine=true,move=""))
    }
}
