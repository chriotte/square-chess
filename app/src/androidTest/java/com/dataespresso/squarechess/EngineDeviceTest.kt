package com.dataespresso.squarechess

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineDeviceTest {
    @Test fun offlineStockfishReturnsLegalMoveAfterE4() {
        runBlocking {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val engine=StockfishController(context)
            try {
                engine.start(); engine.newGame()
                val best=engine.search(START_FEN,listOf("e2e4"),4)
                val p=ChessPosition(moves=listOf("e2e4"))
                assertNotNull("Stockfish response must be legal: $best",p.resolve(best))
                val result=p.append(p.resolve(best)!!)
                assertEquals(2,result.moves.size)
                android.util.Log.i("SquareChessTest","REAL STOCKFISH bestmove=$best FEN=${result.board.fen}")
            } finally {engine.close()}
        }
    }
}
