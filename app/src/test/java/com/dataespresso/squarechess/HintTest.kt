package com.dataespresso.squarechess

import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Square
import org.junit.Assert.*
import org.junit.Test

class HintTest {
    @Test fun normalMoveNamesPieceSquaresAndSan() {
        assertEquals(HintMove("Nf3","g1","f3",PieceType.KNIGHT),hintMove(ChessPosition(),"g1f3"))
        assertEquals(HintMove("e4","e2","e4",PieceType.PAWN),hintMove(ChessPosition(),"e2e4"))
    }
    @Test fun captureAndCheckAppearInSan() {
        // 1.e4 e5 2.Bc4 Nc6: Bxf7+ is a capture with check.
        val p=ChessPosition(moves=listOf("e2e4","e7e5","f1c4","b8c6"))
        assertEquals(HintMove("Bxf7+","c4","f7",PieceType.BISHOP),hintMove(p,"c4f7"))
    }
    @Test fun castlingIsDescribedAsCastling() {
        val p=ChessPosition("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertEquals(HintMove("O-O","e1","g1",PieceType.KING,Castle.KINGSIDE),hintMove(p,"e1g1"))
        assertEquals(Castle.QUEENSIDE,hintMove(p,"e1c1")!!.castle)
        assertNull("A one-square king move is not castling",hintMove(p,"e1f1")!!.castle)
    }
    @Test fun promotionNamesTheNewPiece() {
        val p=ChessPosition("7k/4P3/8/8/8/8/8/K7 w - - 0 1")
        assertEquals(HintMove("e8=Q+","e7","e8",PieceType.PAWN,promotion=PieceType.QUEEN),hintMove(p,"e7e8q"))
        assertEquals(PieceType.KNIGHT,hintMove(p,"e7e8n")!!.promotion)
    }
    @Test fun illegalOrMissingMoveGivesNoText() {
        assertNull(hintMove(ChessPosition(),"e2e5"))
        assertNull(hintMove(ChessPosition(),"(none)"))
    }    @Test fun boardMappingMatchesBothOrientations() {
        assertEquals(0 to 7,cellOf(Square.A1,false))
        assertEquals(7 to 0,cellOf(Square.A1,true))
        assertEquals(6 to 7,cellOf(Square.G1,false))
        assertEquals(1 to 0,cellOf(Square.G1,true))
        for(col in 0..7) for(row in 0..7) for(flipped in listOf(false,true)) {
            assertEquals(col to row,cellOf(squareAtCell(col,row,flipped),flipped))
        }
    }
    @Test fun hintIsShownOnlyForItsOwnPosition() {
        val game=SavedGame("h",GameMode.COMPUTER.name,hintsEnabled=true)
        val state=GameUi(game=game)
        val shown=state.copy(hint=Hint("e2e4",state.positionKey()))
        assertNotNull(shown.visibleHint())
        assertNull(shown.copy(position=ChessPosition(moves=listOf("e2e4"))).visibleHint())
        assertNull(shown.copy(game=game.copy(result="0-1")).visibleHint())
        assertTrue(state.hintsAvailable())
        assertFalse(state.copy(game=game.copy(hintsEnabled=false)).hintsAvailable())
        assertFalse(state.copy(game=game.copy(mode=GameMode.LOCAL_TWO_PLAYER.name)).hintsAvailable())
    }
    @Test fun hintButtonTakesSpaceFromSecondaryActionsFirst() {
        // With hints, Undo stays before Review.
        assertEquals(ToolbarLayout(false,true,false,true),toolbarLayout(411f,1f,hints=true))
        assertEquals(ToolbarLayout(true,true,true,true),toolbarLayout(500f,1f,hints=true))
        assertEquals(ToolbarLayout(false,false,false,true),toolbarLayout(300f,1f,hints=true))
        assertEquals(ToolbarLayout(false,false,false,false),toolbarLayout(360f,2f,hints=true))
        assertFalse(toolbarLayout(500f,1f).hint)
    }
    @Test fun newGamesHaveHintsOff() {
        assertFalse(SavedGame("x",GameMode.COMPUTER.name).hintsEnabled)
    }
}
