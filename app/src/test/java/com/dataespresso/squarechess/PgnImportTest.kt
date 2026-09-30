package com.dataespresso.squarechess

import org.junit.Assert.*
import org.junit.Test

class PgnImportTest {
    private val now = 1_800_000_000_000L

    @Test fun exportedLibraryImportsBackIdentically() {
        val originals = listOf(
            SavedGame("a", GameMode.COMPUTER.name, moves = "e2e4 e7e5 g1f3 b8c6 f1b5", white = "White",
                black = "Computer (Level 3)", level = 3, result = "*", updated = now - 86_400_000L),
            SavedGame("b", GameMode.LOCAL_TWO_PLAYER.name, moves = "f2f3 e7e5 g2g4 d8h4", result = "0-1",
                resultReason = "Checkmate", updated = now - 3_600_000L),
            SavedGame("c", GameMode.PHYSICAL_BOARD_RECORDING.name, initialFen = "7k/P7/8/8/8/8/8/K7 w - - 0 1",
                moves = "a7a8q", updated = now)
        )
        val parsed = parsePgnLibrary(libraryPgn(originals), now)
        assertTrue(parsed.failures.toString(), parsed.failures.isEmpty())
        val byMoves = parsed.games.associateBy { it.moves }
        for (original in originals) {
            val back = requireNotNull(byMoves[original.moves]) { "missing ${original.id}" }
            assertEquals(original.mode, back.mode)
            assertEquals(original.initialFen, back.initialFen)
            assertEquals(original.white, back.white)
            assertEquals(original.black, back.black)
            assertEquals(original.result, back.result)
        }
        val computer = byMoves.getValue("e2e4 e7e5 g1f3 b8c6 f1b5")
        assertEquals(3, computer.level)
        assertTrue("Human played White against the computer", computer.humanWhite)
        assertEquals("Checkmate", byMoves.getValue("f2f3 e7e5 g2g4 d8h4").resultReason)
    }

    @Test fun commentsVariationsAndAnnotationsAreIgnored() {
        val pgn = """
            [Event "Casual"]
            [White "Anna"]
            [Black "Ben"]
            [Result "1-0"]

            1. e4 {best by test} e5 2. Nf3!? (2. f4 exf4 3. Nf3) 2... Nc6 $1 3. Bb5 ; Spanish
            3... a6 4.Ba4 1-0
        """.trimIndent()
        val game = parsePgnLibrary(pgn, now).games.single()
        assertEquals("e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4", game.moves)
        assertEquals("1-0", game.result)
        assertEquals("Anna", game.white)
        assertEquals(GameMode.PHYSICAL_BOARD_RECORDING.name, game.mode)
    }

    @Test fun badGameIsReportedAndOthersStillImport() {
        val pgn = """
            [White "Good"]
            [Black "Game"]

            1. d4 d5 *

            [White "Bad"]
            [Black "Game"]

            1. e4 e4 *
        """.trimIndent()
        val result = parsePgnLibrary(pgn, now)
        assertEquals(listOf("d2d4 d7d5"), result.games.map { it.moves })
        assertEquals(1, result.failures.size)
        assertTrue(result.failures.single(), result.failures.single().contains("move 2"))
    }

    @Test fun castlingPromotionAndBlackToMoveFromFen() {
        val castle = parsePgnLibrary("[SetUp \"1\"]\n[FEN \"r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1\"]\n\n1... O-O-O 2. O-O *", now).games.single()
        assertEquals("e8c8 e1g1", castle.moves)
        val promo = parsePgnLibrary("[FEN \"8/P6k/8/8/8/8/8/K7 w - - 0 1\"]\n\n1. a8=N *", now).games.single()
        assertEquals("a7a8n", promo.moves.lowercase())
    }

    @Test fun duplicatesAreSkipped() {
        val game = parsePgnLibrary("1. e4 e5 *", now).games.single()
        assertTrue(withoutDuplicates(listOf(game), listOf(game.copy(id = "existing"))).isEmpty())
        assertEquals(1, withoutDuplicates(listOf(game, game.copy(id = "twin")), emptyList()).size)
    }

    // importSummaryText needs Android resources: see LocalizationDeviceTest.
}
