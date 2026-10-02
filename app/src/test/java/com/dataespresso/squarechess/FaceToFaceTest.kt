package com.dataespresso.squarechess

import com.github.bhlangonijr.chesslib.Side
import org.junit.Assert.*
import org.junit.Test

class FaceToFaceTest {
    @Test fun offTurnsNothing() {
        for(side in listOf(Side.WHITE,Side.BLACK)) for(flip in listOf(false,true))
            assertFalse(pieceTurnedRound(side,flip,faceToFace=false))
    }
    @Test fun onlyThePiecesAtTheTopTurn() {
        // White at the bottom: Black sits across the table.
        assertTrue(pieceTurnedRound(Side.BLACK,flip=false,faceToFace=true))
        assertFalse(pieceTurnedRound(Side.WHITE,flip=false,faceToFace=true))
        // Board flipped: White is at the top.
        assertTrue(pieceTurnedRound(Side.WHITE,flip=true,faceToFace=true))
        assertFalse(pieceTurnedRound(Side.BLACK,flip=true,faceToFace=true))
    }
    @Test fun faceToFaceIsOnAndGreySquaresOffByDefault() {
        val settings=AppSettings()
        assertTrue(settings.faceToFace)
        assertFalse(settings.einkGreySquares)
    }
}
