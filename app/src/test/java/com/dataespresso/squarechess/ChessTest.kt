package com.dataespresso.squarechess
import org.junit.Assert.*
import org.junit.Test

class ChessTest {
    @Test fun bareKingCannotWinOnTimeAgainstStrongerMaterial() {
        val p=ChessPosition("7k/8/8/8/8/8/R7/K7 w - - 0 1")
        org.junit.Assert.assertFalse(p.provenDeadMaterial())
        org.junit.Assert.assertTrue(p.provenUnableToMate(com.github.bhlangonijr.chesslib.Side.BLACK))
        org.junit.Assert.assertFalse(p.provenUnableToMate(com.github.bhlangonijr.chesslib.Side.WHITE))
    }
    @Test fun startPositionPerft() { val p=ChessPosition(); assertEquals(20,p.legal.size); assertEquals(400L,p.perft(2)); assertEquals(8902L,p.perft(3)) }
    @Test fun kiwipetePerftCoversCastlingAndTacticalLegality() {
        val position=ChessPosition("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(48,position.perft(1))
        assertEquals(2_039L,position.perft(2))
        assertEquals(97_862L,position.perft(3))
    }
    @Test fun perftPositionThreeCoversEnPassantAndCheckResponses() {
        val position=ChessPosition("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals(14,position.perft(1))
        assertEquals(191L,position.perft(2))
        assertEquals(2_812L,position.perft(3))
        assertEquals(43_238L,position.perft(4))
    }
    @Test fun perftPositionFourCoversPromotionsAndCastlingRights() {
        val position=ChessPosition("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")
        assertEquals(6,position.perft(1))
        assertEquals(264L,position.perft(2))
        assertEquals(9_467L,position.perft(3))
    }
    @Test fun sanAndReplay() { val p=ChessPosition(moves=listOf("e2e4","e7e5","g1f3")); assertEquals(listOf("e4","e5","Nf3"),p.san); assertEquals("b8c6",p.resolve("Nc6").toString()) }
    @Test fun illegalMoveRejected() { assertNull(ChessPosition().resolve("e2e5")) }
    @Test fun foolsMate() { val p=ChessPosition(moves=listOf("f2f3","e7e5","g2g4","d8h4")); assertEquals("0-1",p.automaticResult()) }
    @Test fun checkmateHasResultReason() { val p=ChessPosition(moves=listOf("f2f3","e7e5","g2g4","d8h4")); assertEquals("Checkmate",p.automaticResultReason()) }
    @Test fun insufficientMaterialHasResultReason() { assertEquals("Draw by insufficient material",ChessPosition("7k/8/8/8/8/8/8/7K w - - 0 1").automaticResultReason()) }
    @Test fun timeoutCannotAwardWinWhenFlaggedSideHasOnlyKingAndMinorPiece() {
        assertTrue(ChessPosition("7k/8/8/8/8/8/8/6BK w - - 0 1").provenDeadMaterial())
        assertTrue(ChessPosition("7k/8/8/8/8/8/8/5NK1 w - - 0 1").provenDeadMaterial())
    }
    @Test fun underpromotion() { val p=ChessPosition("7k/P7/8/8/8/8/8/7K w - - 0 1"); assertEquals(4,p.legal.count {it.from.name=="A7"}); assertNotNull(p.resolve("a7a8n")) }
    @Test fun threefoldIsClaimNotAutomatic() { val cycle=listOf("g1f3","g8f6","f3g1","f6g8"); val p=ChessPosition(moves=cycle+cycle); assertTrue(p.canClaimDraw()); assertNull(p.automaticResult()); assertEquals("1/2-1/2",ChessPosition(moves=cycle+cycle+cycle+cycle).automaticResult()) }
    @Test fun knightsOnBothSidesAreNotAutomaticallyDead() { val p=ChessPosition("7k/8/7n/8/8/8/N7/K7 w - - 0 1"); assertFalse(p.provenDeadMaterial()); assertNull(p.automaticResult()) }
    @Test fun bishopsOnSameColorSquaresAreDeadButOppositeColorsAreNot() {
        val sameColor=ChessPosition("7k/8/8/8/8/4b3/8/K1B5 w - - 0 1")
        val oppositeColors=ChessPosition("7k/8/8/8/8/3b4/8/K1B5 w - - 0 1")
        assertTrue(sameColor.provenDeadMaterial())
        assertEquals("1/2-1/2",sameColor.automaticResult())
        assertFalse(oppositeColors.provenDeadMaterial())
    }
    @Test fun fiftyMoveClaimAndSeventyFiveAutomatic() { assertTrue(ChessPosition("7k/8/8/8/8/8/R7/K7 w - - 100 60").canClaimDraw()); assertNull(ChessPosition("7k/8/8/8/8/8/R7/K7 w - - 100 60").automaticResult()); assertEquals("1/2-1/2",ChessPosition("7k/8/8/8/8/8/R7/K7 w - - 150 80").automaticResult()) }
    @Test fun castlingThroughAttackedSquareIsIllegal() {
        val position=ChessPosition("k4r2/8/8/8/8/8/8/4K2R w K - 0 1")
        assertNull(position.resolve("O-O"))
    }
    @Test fun castlingRemainsLegalWhenKingPathIsSafe() {
        val position=ChessPosition("4k3/8/8/8/8/8/8/4K2R w K - 0 1")
        val castle=position.resolve("O-O")
        assertNotNull(castle)
        assertEquals("WHITE_KING",position.append(requireNotNull(castle)).board.getPiece(
            com.github.bhlangonijr.chesslib.Square.G1
        ).name)
    }
    @Test fun enPassantThatUncoversCheckIsIllegal() {
        val position=ChessPosition("7k/8/8/r4pPK/8/8/8/8 w - f6 0 1")
        assertNull(position.resolve("g5f6"))
        assertNull(position.resolve("gxf6"))
    }
}
