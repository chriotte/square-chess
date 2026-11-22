package com.dataespresso.squarechess

import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square
import org.junit.Assert.*
import org.junit.Test

class PositionEditorTest {
    @Test fun theStartingPositionIsTheUsualFen() {
        assertEquals(START_FEN, EditorPosition.Start.toFen())
        assertNull(editorProblem(EditorPosition.Start))
    }

    @Test fun castlingFollowsKingsAndRooksOnTheirSquares() {
        val noRookH1 = EditorPosition.Start.with(Square.H1, null)
        assertTrue(noRookH1.toFen().contains(" w Qkq - 0 1"))
        val kingMoved = EditorPosition.Start.with(Square.E8, null).with(Square.D8, Piece.BLACK_KING).with(Square.D7, null)
        assertTrue(kingMoved.toFen().contains(" w KQ - 0 1"))
    }

    @Test fun halfSetUpBoardsSurviveTheRoundTrip() {
        val empty = EditorPosition.Empty
        assertEquals(empty, EditorPosition.fromFen(empty.toFen()))
        val one = empty.with(Square.E4, Piece.WHITE_KNIGHT).copy(whiteToMove = false)
        assertEquals(one, EditorPosition.fromFen(one.toFen()))
        assertEquals(EditorProblem.KINGS, editorProblem(one))
    }

    @Test fun problemsInPlainWords() {
        val kings = EditorPosition.Empty.with(Square.E1, Piece.WHITE_KING).with(Square.E8, Piece.BLACK_KING)
        assertNull(editorProblem(kings.with(Square.D2, Piece.WHITE_PAWN)))
        assertEquals(EditorProblem.PAWN_ON_EDGE, editorProblem(kings.with(Square.A8, Piece.WHITE_PAWN)))
        // White to move while Black's king is attacked by a white rook: Black would be in check.
        assertEquals(EditorProblem.WAITING_SIDE_IN_CHECK, editorProblem(kings.with(Square.E5, Piece.WHITE_ROOK)))
        // The same with Black to move is fine: Black is in check and must answer it.
        assertNull(editorProblem(kings.with(Square.E5, Piece.WHITE_ROOK).copy(whiteToMove = false)))
        // Black to move, stalemated in the corner.
        val stalemate = EditorPosition.Empty.with(Square.H8, Piece.BLACK_KING).with(Square.F7, Piece.WHITE_QUEEN)
            .with(Square.G6, Piece.WHITE_KING).copy(whiteToMove = false)
        assertEquals(EditorProblem.GAME_OVER, editorProblem(stalemate))
    }

    @Test fun aPlayablePositionPassesTheImportCheck() {
        val fen = EditorPosition.Start.with(Square.E2, null).with(Square.E4, Piece.WHITE_PAWN).copy(whiteToMove = false).toFen()
        assertEquals(fen, normalizeFenContent(fen))
    }
}
