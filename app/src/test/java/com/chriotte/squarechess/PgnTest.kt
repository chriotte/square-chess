package com.chriotte.squarechess

import org.junit.Assert.assertTrue
import org.junit.Test

class PgnTest {
    @Test fun exportsSanAndFinalResult() {
        val game=SavedGame(
            id="mate",
            mode=GameMode.PHYSICAL_BOARD_RECORDING.name,
            moves="f2f3 e7e5 g2g4 d8h4",
            result="0-1"
        )

        val pgn=game.toPgn()

        assertTrue(pgn.contains("[White \"White\"]"))
        assertTrue(pgn.contains("[Black \"Black\"]"))
        assertTrue(pgn.contains("[Result \"0-1\"]"))
        assertTrue(pgn.endsWith("1. f3 e5 2. g4 Qh4# 0-1"))
    }

    @Test fun exportsCustomFenAndEscapesHeaderNames() {
        val fen="7k/8/8/8/8/8/8/7K w - - 0 1"
        val game=SavedGame(
            id="custom-fen",
            mode=GameMode.PHYSICAL_BOARD_RECORDING.name,
            initialFen=fen,
            white="A \"Quoted\" Player",
            black="Back\\Slash"
        )

        val pgn=game.toPgn()

        assertTrue(pgn.contains("[White \"A \\\"Quoted\\\" Player\"]"))
        assertTrue(pgn.contains("[Black \"Back\\\\Slash\"]"))
        assertTrue(pgn.contains("[SetUp \"1\"]"))
        assertTrue(pgn.contains("[FEN \"$fen\"]"))
    }

    @Test fun exportsBlackToMoveFromCustomFen() {
        val fen=START_FEN.replace(" w KQkq ", " b KQkq ").replace(" 0 1", " 0 17")
        val game=SavedGame(id="black-start",mode=GameMode.PHYSICAL_BOARD_RECORDING.name,
            initialFen=fen,moves="b8c6")

        assertTrue(game.toPgn().endsWith("17... Nc6 *"))
    }

    @Test fun wrapsLongMovetextAtReadableLineLengths() {
        val cycle=listOf("g1f3","g8f6","f3g1","f6g8")
        val game=SavedGame(id="long-game",mode=GameMode.PHYSICAL_BOARD_RECORDING.name,
            moves=List(12) { cycle }.flatten().joinToString(" "))

        assertTrue(game.toPgn().substringAfter("\n\n").lines().all { it.length<=80 })
    }
}
