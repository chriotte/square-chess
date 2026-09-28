package com.chriotte.squarechess

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameMigrationDeviceTest {
    @Test fun existingGamesSurviveUpgradeAndOrientationPersists() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(isIsolatedTestPackage(context.packageName))
        val name="migration-test-${java.util.UUID.randomUUID()}.db"
        context.openOrCreateDatabase(name,0,null).use { old ->
            old.execSQL("CREATE TABLE games (id TEXT NOT NULL PRIMARY KEY, mode TEXT NOT NULL, initialFen TEXT NOT NULL, moves TEXT NOT NULL, white TEXT NOT NULL, black TEXT NOT NULL, humanWhite INTEGER NOT NULL, level INTEGER NOT NULL, result TEXT NOT NULL, updated INTEGER NOT NULL)")
            old.execSQL("INSERT INTO games VALUES (?,?,?,?,?,?,?,?,?,?)",arrayOf<Any>("old-game",GameMode.COMPUTER.name,START_FEN,"e2e4 e7e5","Stockfish","Player",0,4,"*",123L))
            old.version=1
        }
        try {
            val db=openChessDatabase(context,name)
            try {
                val old=db.games().latest()!!
                assertEquals("old-game",old.id)
                assertEquals("e2e4 e7e5",old.moves)
                assertEquals(123L,old.updated)
                assertEquals("Player",old.black)
                assertNull(old.orientationFlipped)
                assertNull(old.clockBaseMs)
                assertNull(old.clockIncrementMs)
                assertNull(old.clockPhase)
                assertTrue(defaultFlipFor(old))
                assertNotNull(ChessPosition(moves=old.moves.split(" ")).resolve("Nf3"))
                val clock=ClockState(ClockConfig(300_000,5_000,3_000)).start(100).finish(200)
                db.games().save(old.copy(orientationFlipped=false,result="1-0",resultReason="Resignation")
                    .withClock(clock,200))
            } finally { db.close() }
            val reopened=openChessDatabase(context,name)
            try {
                val restored=reopened.games().latest()!!
                assertFalse(defaultFlipFor(restored))
                assertEquals("1-0",restored.result)
                assertEquals("Resignation",restored.resultReason)
                assertEquals("e2e4 e7e5",restored.moves)
                assertEquals(300_000L,restored.clockBaseMs)
                assertEquals(5_000L,restored.clockIncrementMs)
                assertEquals(3_000L,restored.clockDelayMs)
                assertEquals(2_900L,restored.clockDelayRemainingMs)
                assertEquals("FINISHED",restored.clockPhase)
                assertEquals(ClockSide.WHITE.name,restored.clockActive)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) } // Only this isolated test-created file.
    }
}
