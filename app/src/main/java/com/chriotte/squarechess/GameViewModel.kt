package com.chriotte.squarechess

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.github.bhlangonijr.chesslib.Side
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class GameUi(
    val game: SavedGame? = null,
    val position: ChessPosition = ChessPosition(),
    val busy: Boolean = false,
    val message: String = "Your board. Your pace.",
    val ready: Boolean = false
)
class GameViewModel(app: Application): AndroidViewModel(app) {
    private val db = Room.databaseBuilder(app,ChessDatabase::class.java,"square-chess.db").build()
    private val engine: EngineController = StockfishController(app)
    val state = MutableStateFlow(GameUi())
    val history = db.games().observeGames()
    private var revision = 0L
    private val commitLock = Mutex()
    private var foreground = true
    private var engineJob: Job? = null
    init {
        viewModelScope.launch {
            val saved = db.games().latest()
            runCatching { saved?.let { load(it) } }.onFailure { state.value=GameUi(message="Saved game could not be recovered: ${it.message}") }
            state.value = state.value.copy(ready=true)
        }
    }
    private fun invalidate() { revision++; engine.stop(); engineJob?.cancel(); engineJob=null }
    suspend fun load(game: SavedGame) {
        invalidate()
        val p = ChessPosition(game.initialFen,game.moves.split(" ").filter(String::isNotBlank))
        state.value=GameUi(game,p,message="${p.board.sideToMove.name.lowercase().replaceFirstChar(Char::titlecase)} to move",ready=true)
    }
    fun resume(game: SavedGame) = viewModelScope.launch { foreground=true; commitLock.withLock { load(game) }; maybeEngine() }
    fun newGame(mode: GameMode, level: Int, humanWhite: Boolean) = viewModelScope.launch {
        foreground=true
        commitLock.withLock {
            invalidate()
            val game=SavedGame(UUID.randomUUID().toString(),mode.name,level=level,humanWhite=humanWhite,
                white=if(mode==GameMode.COMPUTER && !humanWhite) "Stockfish" else "White",
                black=if(mode==GameMode.COMPUTER && humanWhite) "Stockfish" else "Black")
            db.games().save(game)
            load(game)
        }
        maybeEngine()
    }
    fun enter(text: String) = viewModelScope.launch {
        commitLock.withLock {
            val s=state.value; val g=s.game ?: return@withLock
            if(s.busy || g.result != "*" || (g.mode==GameMode.COMPUTER.name && !humanTurn(s))) return@withLock
            val move=s.position.resolve(text)
            if(move==null) state.value=s.copy(message="Not a legal move: $text") else commit(move.toString())
        }
        maybeEngine()
    }
    private suspend fun commit(uci: String) {
        val s=state.value; val g=s.game ?: return
        val move=s.position.resolve(uci) ?: error("Engine returned an illegal move: $uci")
        val p=s.position.append(move)
        val saved=g.copy(moves=p.moves.joinToString(" "),result=p.automaticResult() ?: "*",updated=System.currentTimeMillis())
        db.games().save(saved)
        revision++
        state.value=s.copy(game=saved,position=p,busy=false,message=if(saved.result!="*") "Game finished · ${saved.result}" else "${p.board.sideToMove.name.lowercase().replaceFirstChar(Char::titlecase)} to move${if(p.board.isKingAttacked) " · Check" else ""}")
        Log.i("SquareChess","Committed ${p.moves.size}: $uci; FEN=${p.board.fen}")
    }
    private fun humanTurn(s: GameUi) = (s.position.board.sideToMove==Side.WHITE)==s.game!!.humanWhite
    fun maybeEngine() {
        val s=state.value; val g=s.game ?: return
        if(!foreground || s.busy || g.result!="*" || g.mode!=GameMode.COMPUTER.name || humanTurn(s)) return
        val token=revision
        state.value=s.copy(busy=true,message="Stockfish is thinking…")
        engineJob=viewModelScope.launch {
            try {
                val best=engine.search(g.initialFen,s.position.moves,g.level)
                commitLock.withLock { if(token==revision && foreground) commit(best) }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(token==revision) state.value=state.value.copy(busy=false,message="Engine unavailable: ${e.message}. Use Retry in the menu."); Log.e("SquareChess","Engine failed",e) }
        }
    }
    fun undo() = viewModelScope.launch { commitLock.withLock {
        val s=state.value; val g=s.game ?: return@withLock
        invalidate()
        val n=if(g.mode==GameMode.COMPUTER.name && humanTurn(s) && s.position.moves.size>=2) 2 else 1
        val saved=g.copy(moves=s.position.moves.dropLast(n).joinToString(" "),result="*",updated=System.currentTimeMillis())
        db.games().save(saved); load(saved)
    }; maybeEngine() }
    fun end(result: String) = viewModelScope.launch { commitLock.withLock {
        val g=state.value.game ?: return@withLock; invalidate()
        val saved=g.copy(result=result,updated=System.currentTimeMillis()); db.games().save(saved)
        state.value=state.value.copy(game=saved,busy=false,message="Game finished · $result")
    } }
    fun background() { foreground=false; invalidate(); state.value=state.value.copy(busy=false) }
    fun foreground() { foreground=true; maybeEngine() }
    override fun onCleared() { engine.stop(); super.onCleared() }
}
