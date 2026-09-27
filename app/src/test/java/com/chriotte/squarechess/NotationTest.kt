package com.chriotte.squarechess

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors

class NotationTest {
    @Test fun firstCharacterRepeatAndExactlyOnceSubmit() {
        val first=NotationDraft().type("e", "position")
        assertEquals("e",first.text)
        assertEquals(first,first.type("e","position",repeated=true))
        val complete=first.type("2e4","position")
        assertEquals("e2e4",complete.text)
        val submitted=complete.submit("position")
        assertTrue(submitted.submitting)
        assertEquals(submitted,submitted.submit("position"))
        assertEquals(submitted,submitted.type("e","position"))
    }
    @Test fun invalidDraftRemainsEditableAndStaleDraftCannotSubmit() {
        val draft=NotationDraft().type("e2e5","a").submit("a").rejected("Illegal")
        assertEquals("e2e5",draft.text)
        assertEquals("e2e4",draft.backspace().type("4","a").text)
        assertFalse(draft.submit("b").submitting)
        assertEquals("N",draft.type("N","b").text)
    }
    @Test fun sanUsesItsOwnPositionEvenOnFreshThread() {
        val executor=Executors.newSingleThreadExecutor()
        try {
            assertEquals("b8c6",executor.submit<String> {
                ChessPosition(moves=listOf("e2e4","c7c5","g1f3")).resolve("Nc6").toString()
            }.get())
        } finally { executor.shutdownNow() }
    }
    @Test fun coordinateSanCastleAndPromotionUseLegalDomain() {
        assertEquals("e2e4",ChessPosition().resolve("e2e4").toString())
        assertEquals("g1f3",ChessPosition().resolve("Nf3").toString())
        assertNull(ChessPosition().resolve("O-O"))
        val castle=ChessPosition("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertEquals("e1g1",castle.resolve("0-0").toString())
        assertEquals("e1c1",castle.resolve("O-O-O").toString())
        val promotion=ChessPosition("7k/P7/8/8/8/8/8/7K w - - 0 1")
        assertEquals("a7a8n",promotion.resolve("a7a8n").toString())
        assertNull(promotion.resolve("a7a8"))
    }
}
