package com.dataespresso.squarechess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FenImportTest {
    @Test fun rejectsImpossibleCheckPawnsCastlingAndEnPassant() {
        listOf("8/8/8/8/8/8/6k1/7K w - - 0 1",
            "P6k/8/8/8/8/8/8/7K w - - 0 1",
            "7k/8/8/8/8/8/8/7K w K - 0 1",
            "7k/8/8/8/8/8/8/7K w - e6 0 1").forEach {
            assertThrows(IllegalArgumentException::class.java) { normalizeFenContent(it) }
        }
    }
    @Test fun acceptsAndNormalizesSinglePositionWithBomAndWhitespace() {
        val fen="7k/8/8/8/8/8/8/7K b - - 0 17"

        assertEquals(fen,normalizeFenContent("\uFEFF  $fen \r\n"))
    }

    @Test fun rejectsFilesContainingMoreThanOnePosition() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeFenContent("$START_FEN\n$START_FEN")
        }
    }

    @Test fun rejectsInvalidRanksPiecesAndMissingKings() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeFenContent(START_FEN.replace("RNBQKBNR","RNBQKBNR/"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeFenContent("7k/8/8/8/8/8/8/6XK w - - 0 1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeFenContent("7k/8/8/8/8/8/8/8 w - - 0 1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeFenContent("7k/8/8/8/8/8/8/6KK w - - 0 1")
        }
    }

    @Test fun rejectsInvalidSideRightsEnPassantAndCounters() {
        val valid="7k/8/8/8/8/8/8/7K w - - 0 1"
        listOf(
            valid.replace(" w ", " x "),
            valid.replace(" - - ", " KK - "),
            valid.replace(" - - ", " - e3 "),
            valid.replace(" 0 1", " -1 1"),
            valid.replace(" 0 1", " 0 0")
        ).forEach { fen ->
            assertThrows(IllegalArgumentException::class.java) { normalizeFenContent(fen) }
        }
    }

    @Test fun rejectsOversizedInput() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeFenContent("x".repeat(MAX_FEN_FILE_SIZE_BYTES + 1))
        }
    }
}
