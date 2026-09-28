package com.dataespresso.squarechess

import com.github.bhlangonijr.chesslib.Piece
import org.junit.Assert.*
import org.junit.Test

class BoardExtrasTest {
    @Test fun capturesListTakenPiecesAndMaterialLead() {
        // 1. e4 d5 2. exd5 Qxd5 3. Nc3 Qxg2: each side took a pawn, then Black another.
        val p=ChessPosition(moves=listOf("e2e4","d7d5","e4d5","d8d5","b1c3","d5g2"))
        val c=captures(p)
        assertEquals(listOf(Piece.BLACK_PAWN),c.byWhite)
        assertEquals(listOf(Piece.WHITE_PAWN,Piece.WHITE_PAWN),c.byBlack)
        assertEquals(-1,c.whiteLead)
    }
    @Test fun promotionIsNotCountedAsACapturedPawn() {
        val p=ChessPosition("7k/P7/8/8/8/8/8/K7 w - - 0 1",listOf("a7a8q"))
        val c=captures(p)
        assertTrue(c.byWhite.isEmpty())
        assertTrue(c.byBlack.isEmpty())
        assertEquals(9,c.whiteLead)
    }
    @Test fun lastMoveSoundsDistinguishMoveCaptureCheckAndEnPassant() {
        assertNull(lastMoveSound(ChessPosition()))
        assertEquals(MoveSound.MOVE,lastMoveSound(ChessPosition(moves=listOf("e2e4"))))
        assertEquals(MoveSound.CAPTURE,lastMoveSound(ChessPosition(moves=listOf("e2e4","d7d5","e4d5"))))
        assertEquals(MoveSound.CHECK,lastMoveSound(ChessPosition(moves=listOf("f2f3","e7e5","g2g4","d8h4"))))
        assertEquals(MoveSound.CAPTURE,lastMoveSound(ChessPosition(moves=listOf("e2e4","a7a6","e4e5","d7d5","e5d6"))))
    }
    @Test fun lastMoveTextNumbersWhiteAndBlackMoves() {
        assertNull(lastMoveText(emptyList(),1,true))
        assertEquals("1. e4",lastMoveText(listOf("e4"),1,true))
        assertEquals("1… e5",lastMoveText(listOf("e4","e5"),1,true))
        assertEquals("17… Nc6",lastMoveText(listOf("Nc6"),17,false))
        assertEquals("18. Nf3",lastMoveText(listOf("Nc6","Nf3"),17,false))
    }
}
