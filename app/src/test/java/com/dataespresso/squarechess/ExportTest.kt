package com.dataespresso.squarechess

import org.junit.Assert.*
import org.junit.Test

class ExportTest {
    private val day=86_400_000L
    private val now=1_800_000_000_000L
    private val games=listOf(
        SavedGame("a",GameMode.COMPUTER.name,moves="e2e4 e7e5",white="White",black="Computer (Level 3)",result="1-0",updated=now-2*day,level=3,resultReason="Checkmate"),
        SavedGame("b",GameMode.LOCAL_TWO_PLAYER.name,moves="d2d4",updated=now-40*day,clockBaseMs=300_000,clockIncrementMs=2_000),
        SavedGame("c",GameMode.PHYSICAL_BOARD_RECORDING.name,updated=now-400*day)
    )
    @Test fun filtersByModeAndPeriod() {
        assertEquals(listOf("a","b","c"),selectForExport(games,ExportModeFilter.ALL,ExportPeriod.ALL_TIME,now).map { it.id })
        assertEquals(listOf("a"),selectForExport(games,ExportModeFilter.COMPUTER,ExportPeriod.ALL_TIME,now).map { it.id })
        assertEquals(listOf("a"),selectForExport(games,ExportModeFilter.ALL,ExportPeriod.LAST_30_DAYS,now).map { it.id })
        assertEquals(listOf("a","b"),selectForExport(games,ExportModeFilter.ALL,ExportPeriod.LAST_YEAR,now).map { it.id })
        assertTrue(selectForExport(games,ExportModeFilter.RECORDED,ExportPeriod.LAST_YEAR,now).isEmpty())
    }
    @Test fun libraryIsOldestFirstWithOneGamePerBlock() {
        val pgn=libraryPgn(games)
        assertEquals(3,Regex("^\\[Event ",RegexOption.MULTILINE).findAll(pgn).count())
        assertTrue(pgn.indexOf("[Event \"Recorded physical game\"]")<pgn.indexOf("[Event \"Game against computer\"]"))
        // PGN games are separated by a blank line; each ends with its result.
        assertTrue(pgn.contains("1. d4 *\n\n[Event \"Game against computer\"]"))
        assertTrue(pgn.endsWith("1. e4 e5 1-0\n"))
    }
    @Test fun headersCarryDateModeLevelClockAndTermination() {
        val computer=games[0].toPgn()
        assertTrue(computer.contains("[Difficulty \"Level 3\"]"))
        assertTrue(computer.contains("[Termination \"Checkmate\"]"))
        assertTrue(Regex("\\[Date \"\\d{4}\\.\\d{2}\\.\\d{2}\"]").containsMatchIn(computer))
        assertTrue(games[1].toPgn().contains("[TimeControl \"300+2\"]"))
        assertFalse(games[1].toPgn().contains("[Difficulty"))
    }
    @Test fun fileNameIsDatedPgn() {
        assertTrue(exportFileName(now).matches(Regex("square-chess-games-\\d{4}-\\d{2}-\\d{2}\\.pgn")))
    }
}
