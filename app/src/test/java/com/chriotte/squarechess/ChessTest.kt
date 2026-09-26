package com.chriotte.squarechess
import org.junit.Assert.*
import org.junit.Test

class ChessTest {
    @Test fun startPositionPerft() { val p=ChessPosition(); assertEquals(20,p.legal.size); assertEquals(400L,p.perft(2)); assertEquals(8902L,p.perft(3)) }
    @Test fun sanAndReplay() { val p=ChessPosition(moves=listOf("e2e4","e7e5","g1f3")); assertEquals(listOf("e4","e5","Nf3"),p.san); assertEquals("b8c6",p.resolve("Nc6").toString()) }
    @Test fun illegalMoveRejected() { assertNull(ChessPosition().resolve("e2e5")) }
    @Test fun foolsMate() { val p=ChessPosition(moves=listOf("f2f3","e7e5","g2g4","d8h4")); assertEquals("0-1",p.automaticResult()) }
    @Test fun checkmateHasResultReason() { val p=ChessPosition(moves=listOf("f2f3","e7e5","g2g4","d8h4")); assertEquals("Checkmate",p.automaticResultReason()) }
    @Test fun insufficientMaterialHasResultReason() { assertEquals("Draw by insufficient material",ChessPosition("7k/8/8/8/8/8/8/7K w - - 0 1").automaticResultReason()) }
    @Test fun underpromotion() { val p=ChessPosition("7k/P7/8/8/8/8/8/7K w - - 0 1"); assertEquals(4,p.legal.count {it.from.name=="A7"}); assertNotNull(p.resolve("a7a8n")) }
    @Test fun threefoldIsClaimNotAutomatic() { val cycle=listOf("g1f3","g8f6","f3g1","f6g8"); val p=ChessPosition(moves=cycle+cycle); assertTrue(p.canClaimDraw()); assertNull(p.automaticResult()); assertEquals("1/2-1/2",ChessPosition(moves=cycle+cycle+cycle+cycle).automaticResult()) }
    @Test fun knightsOnBothSidesAreNotAutomaticallyDead() { val p=ChessPosition("7k/8/7n/8/8/8/N7/K7 w - - 0 1"); assertFalse(p.provenDeadMaterial()); assertNull(p.automaticResult()) }
    @Test fun fiftyMoveClaimAndSeventyFiveAutomatic() { assertTrue(ChessPosition("7k/8/8/8/8/8/R7/K7 w - - 100 60").canClaimDraw()); assertNull(ChessPosition("7k/8/8/8/8/8/R7/K7 w - - 100 60").automaticResult()); assertEquals("1/2-1/2",ChessPosition("7k/8/8/8/8/8/R7/K7 w - - 150 80").automaticResult()) }
}
