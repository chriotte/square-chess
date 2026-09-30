package com.dataespresso.squarechess

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val highlightMove: String? = null,
    val engineError: String? = null,
    val clock: ClockState? = null,
    val resultEvent: ResultEvent? = null,
    val ready: Boolean = false,
    val hint: Hint? = null,
    val hintBusy: Boolean = false,
    val hintError: String? = null
)
data class ResultEvent(val result: String, val reason: String)
class GameViewModel private constructor(
    app: Application,
    private val engine: EngineController,
    private val db: ChessDatabase
): AndroidViewModel(app) {
    constructor(app: Application): this(app,StockfishController(app),openChessDatabase(app))
    internal constructor(app: Application, engine: EngineController): this(app,engine,openChessDatabase(app))
    val state = MutableStateFlow(GameUi())
    val history = db.games().observeGames()
    private var revision = 0L
    private val commitLock = Mutex()
    private var foreground = true
    private var engineJob: Job? = null
    private var hintJob: Job? = null
    private var clockCheckpointJob: Job? = null
    init {
        viewModelScope.launch {
            val saved = db.games().latest()
            runCatching { saved?.let { load(it) } }.onFailure { state.value=GameUi(message="Saved game could not be recovered: ${it.message}") }
            state.value = state.value.copy(ready=true)
        }
    }
    private fun invalidate() {
        revision++
        engine.stop()
        engineJob?.cancel()
        engineJob=null
        hintJob?.cancel()
        hintJob=null
        clockCheckpointJob?.cancel()
        clockCheckpointJob=null
        // A shown hint stays: visibleHint() hides it once the position is different.
        if(state.value.busy || state.value.hintBusy) state.value=state.value.copy(busy=false,hintBusy=false)
    }
    suspend fun load(game: SavedGame, recoverClock: Boolean = true) {
        invalidate()
        var loadedGame = game
        var clock = game.clockState()
        if (recoverClock && clock?.phase == ClockPhase.RUNNING) {
            clock = clock.copy(phase=ClockPhase.PAUSED,anchorMs=0,interrupted=true)
            loadedGame = game.withClock(clock, SystemClock.elapsedRealtime())
            db.games().save(loadedGame)
        } else if (!recoverClock && clock?.phase == ClockPhase.RUNNING) {
            clock = clock.copy(anchorMs=SystemClock.elapsedRealtime())
        }
        val p = ChessPosition(loadedGame.initialFen,loadedGame.moves.split(" ").filter(String::isNotBlank))
        val message = when {
            loadedGame.result != "*" -> "Game finished · ${loadedGame.result}"
            clock?.phase == ClockPhase.PAUSED -> clockPauseMessage(clock)
            else -> "${p.board.sideToMove.name.lowercase().replaceFirstChar(Char::titlecase)} to move"
        }
        state.value=GameUi(loadedGame,p,clock=clock,message=message,ready=true)
    }
    fun resume(game: SavedGame) = viewModelScope.launch { foreground=true; commitLock.withLock { load(game) }; maybeEngine() }
    fun newGame(
        mode: GameMode,
        level: Int,
        humanWhite: Boolean,
        clockConfig: ClockConfig? = null,
        initialFen: String = START_FEN,
        hintsEnabled: Boolean = false
    ) = viewModelScope.launch {
        foreground=true
        commitLock.withLock {
            invalidate()
            val now = SystemClock.elapsedRealtime()
            val initialPosition=ChessPosition(initialFen)
            val clockSide=if(initialPosition.board.sideToMove==Side.WHITE) ClockSide.WHITE else ClockSide.BLACK
            val result=initialPosition.automaticResult() ?: "*"
            val clock = clockConfig?.let { ClockState(it,active=clockSide).start(now) }
                ?.let { if(result!="*") it.finish(now) else it }
            val game=SavedGame(UUID.randomUUID().toString(),mode.name,initialFen=initialFen,level=level,humanWhite=humanWhite,
                white=if(mode==GameMode.COMPUTER && !humanWhite) "Computer (${difficultyLabel(level)})" else "White",
                black=if(mode==GameMode.COMPUTER && humanWhite) "Computer (${difficultyLabel(level)})" else "Black",
                result=result,resultReason=if(result!="*") initialPosition.automaticResultReason().orEmpty() else "",
                hintsEnabled=hintsEnabled && mode==GameMode.COMPUTER)
                .withClock(clock, now)
            db.games().save(game)
            load(game, recoverClock=false)
            if (result != "*") {
                state.value = state.value.copy(
                    resultEvent = ResultEvent(result, initialPosition.automaticResultReason().orEmpty())
                )
            }
        }
        ensureClockCheckpointing()
        maybeEngine()
    }
    fun enter(text: String, expectedPosition: String = state.value.positionKey(), onResult: (String?) -> Unit = {}) = viewModelScope.launch {
        commitLock.withLock {
            val s=state.value
            if(s.positionKey()!=expectedPosition || !s.canEnterMove()) { onResult("Position changed — enter your move again"); return@withLock }
            val move=s.position.resolve(text)
            if(move==null) { state.value=s.copy(message="Not a legal move: $text"); onResult("Illegal or incomplete move") }
            else {
                if (commit(move.toString())) onResult(null)
                else onResult(if (state.value.clock?.phase == ClockPhase.PAUSED) "Clock is paused" else "Time expired")
            }
        }
        maybeEngine()
    }
    private suspend fun commit(uci: String): Boolean {
        val s=state.value; val g=s.game ?: return false
        val move=s.position.resolve(uci) ?: error("Engine returned an illegal move: $uci")
        val now = SystemClock.elapsedRealtime()
        if (s.clock != null && s.clock.phase != ClockPhase.RUNNING) return false
        val mover = if (s.position.board.sideToMove == Side.WHITE) ClockSide.WHITE else ClockSide.BLACK
        var clock = s.clock?.let { current ->
            if (current.phase == ClockPhase.RUNNING) current.switch(mover, now) else current
        }
        if (clock?.phase == ClockPhase.FLAGGED) {
            finishOnTime(s, clock)
            return false
        }
        val p=s.position.append(move)
        val result=p.automaticResult() ?: "*"
        if (result != "*" && clock != null) clock = clock.finish(now)
        val saved=g.copy(moves=p.moves.joinToString(" "),result=result,
            resultReason=if(result!="*") p.automaticResultReason() ?: "Game finished" else "",updated=System.currentTimeMillis())
            .withClock(clock, now)
        db.games().save(saved)
        revision++
        if(s.hintBusy) { hintJob?.cancel(); hintJob=null; engine.stop() }
        val highlightRevision=revision
        state.value=s.copy(
            game=saved,
            position=p,
            busy=false,
            engineError=null,
            hint=null,
            hintBusy=false,
            hintError=null,
            clock=clock,
            highlightMove=uci,
            resultEvent=if(result!="*") ResultEvent(result,p.automaticResultReason() ?: "Game finished") else null,
            message=if(result!="*") "Game finished · $result" else "${p.board.sideToMove.name.lowercase().replaceFirstChar(Char::titlecase)} to move${if(p.board.isKingAttacked) " · Check" else ""}"
        )
        viewModelScope.launch {
            delay(1200)
            if(revision==highlightRevision) state.value=state.value.copy(highlightMove=null)
        }
        Log.i("SquareChess","Committed ${p.moves.size}: $uci; FEN=${p.board.fen}")
        return true
    }
    private fun humanTurn(s: GameUi) = (s.position.board.sideToMove==Side.WHITE)==s.game!!.humanWhite
    fun maybeEngine() {
        val s=state.value; val g=s.game ?: return
        if(!foreground || s.busy || g.result!="*" || g.mode!=GameMode.COMPUTER.name || humanTurn(s) ||
            s.clock?.phase?.let { it != ClockPhase.RUNNING } == true) return
        val token=revision
        state.value=s.copy(busy=true,engineError=null,message="Computer is thinking…")
        engineJob=viewModelScope.launch {
            try {
                val best=engine.search(g.initialFen,s.position.moves,g.level)
                commitLock.withLock { if(token==revision && foreground) commit(best) }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                if(token==revision) state.value=state.value.copy(
                    busy=false,
                    engineError=e.message ?: "Unknown engine error",
                    message="Computer unavailable. Retry from the menu."
                )
                Log.e("SquareChess","Engine failed",e)
            }
        }
    }
    /**
     * Asks the engine for a suggested move on the player's turn. The move is only shown, never
     * played, and the clock keeps running. A result for a position that has since changed is dropped.
     */
    fun requestHint() {
        val s=state.value; val g=s.game ?: return
        if(!foreground || !s.hintsAvailable() || !s.canEnterMove() || s.hintBusy) return
        val key=s.positionKey()
        val token=revision
        state.value=s.copy(hint=null,hintBusy=true,hintError=null)
        hintJob=viewModelScope.launch {
            val best=try { engine.hint(g.initialFen,s.position.moves) }
                catch(e: CancellationException) { throw e }
                catch(e: Exception) { Log.e("SquareChess","Hint failed",e); null }
            commitLock.withLock {
                val now=state.value
                if(token!=revision || now.positionKey()!=key || !now.hintBusy) return@withLock
                val move=best?.let { now.position.resolve(it) }
                if(best!=null && move==null) Log.e("SquareChess","Engine hint is not legal here: $best")
                state.value=if(move!=null) now.copy(hint=Hint(move.toString(),key),hintBusy=false)
                    else now.copy(hintBusy=false,hintError="Hint unavailable · Try again")
            }
        }
    }
    fun clearHint() {
        if(state.value.hintBusy) { hintJob?.cancel(); engine.stop() }
        hintJob=null
        state.value=state.value.copy(hint=null,hintBusy=false,hintError=null)
    }
    fun undo() = viewModelScope.launch { commitLock.withLock {
        val s=state.value; val g=s.game ?: return@withLock
        invalidate()
        val now = SystemClock.elapsedRealtime()
        val settledClock = s.clock?.settled(now)
        if (settledClock?.phase == ClockPhase.FLAGGED) {
            finishOnTime(s, settledClock)
            return@withLock
        }
        val n=if(g.mode==GameMode.COMPUTER.name && humanTurn(s) && s.position.moves.size>=2) 2 else 1
        val moves = s.position.moves.dropLast(n)
        val position = ChessPosition(s.position.initialFen, moves)
        val clock = settledClock?.copy(
            active = if (position.board.sideToMove == Side.WHITE) ClockSide.WHITE else ClockSide.BLACK,
            phase = ClockPhase.PAUSED,
            anchorMs = 0,
            interrupted = false
        )
        val saved=g.copy(moves=moves.joinToString(" "),result="*",resultReason="",updated=System.currentTimeMillis())
            .withClock(clock, now)
        db.games().save(saved)
        load(saved, recoverClock=false)
    }; maybeEngine() }
    fun end(result: String, reason: String = "Result recorded") = viewModelScope.launch { commitLock.withLock {
        val s=state.value
        val g=s.game ?: return@withLock
        if(g.result!="*") return@withLock
        val now = SystemClock.elapsedRealtime()
        val currentClock = s.clock?.settled(now)
        if (currentClock?.phase == ClockPhase.FLAGGED) {
            finishOnTime(s, currentClock)
            return@withLock
        }
        invalidate()
        val clock = currentClock?.finish(now)
        val saved=g.copy(result=result,resultReason=reason,updated=System.currentTimeMillis()).withClock(clock, now)
        db.games().save(saved)
        state.value=s.copy(game=saved,clock=clock,busy=false,engineError=null,resultEvent=ResultEvent(result,reason),message="Game finished · $result")
    } }
    fun setOrientation(flipped: Boolean) = viewModelScope.launch { commitLock.withLock {
        val g=state.value.game ?: return@withLock
        val saved=g.copy(orientationFlipped=flipped).withClock(state.value.clock,SystemClock.elapsedRealtime())
        db.games().save(saved)
        state.value=state.value.copy(game=saved)
    } }
    fun acknowledgeResult() { state.value=state.value.copy(resultEvent=null) }
    data class ImportSummary(val imported: Int, val duplicates: Int, val failures: List<String>)
    /** Adds the games in [pgn] to the history. The open game is not changed. */
    suspend fun importGames(pgn: String): ImportSummary = withContext(Dispatchers.Default) {
        val parsed = parsePgnLibrary(pgn)
        val fresh = withoutDuplicates(parsed.games, db.games().allOldestFirst())
        fresh.forEach { db.games().save(it) }
        ImportSummary(fresh.size, parsed.games.size - fresh.size, parsed.failures)
    }
    suspend fun gamesForExport(mode: ExportModeFilter, period: ExportPeriod) =
        selectForExport(db.games().allOldestFirst(), mode, period)
    fun delete(game: SavedGame) = viewModelScope.launch { commitLock.withLock {
        val open=state.value.game?.id==game.id
        if(open) invalidate()
        db.games().delete(game.id)
        if(open) {
            // Show the next most recent game, as a fresh start would.
            val next=db.games().latest()
            if(next!=null) load(next) else state.value=GameUi(ready=true)
        }
    } }
    fun pauseClock() = viewModelScope.launch { commitLock.withLock { pauseClockLocked(interrupted=false) } }
    fun resumeClock() = viewModelScope.launch {
        commitLock.withLock {
            val s = state.value
            val clock = s.clock ?: return@withLock
            if (s.game?.result != "*" || clock.phase != ClockPhase.PAUSED) return@withLock
            val now = SystemClock.elapsedRealtime()
            val resumed = clock.resume(now)
            val saved = requireNotNull(s.game).withClock(resumed, now)
            db.games().save(saved)
            val side = if (resumed.active == ClockSide.WHITE) "White" else "Black"
            state.value = s.copy(game=saved, clock=resumed, message="$side to move")
        }
        ensureClockCheckpointing()
        maybeEngine()
    }
    fun clockExpired() = viewModelScope.launch { commitLock.withLock {
        val s = state.value
        val game = s.game ?: return@withLock
        if (game.result != "*") return@withLock
        val clock = s.clock?.settled(SystemClock.elapsedRealtime()) ?: return@withLock
        if (clock.phase == ClockPhase.FLAGGED) finishOnTime(s, clock)
    } }
    fun background() {
        foreground=false
        invalidate()
        viewModelScope.launch { commitLock.withLock { pauseClockLocked(interrupted=true) } }
    }
    fun pauseForNavigation() {
        foreground=false
        invalidate()
        viewModelScope.launch { commitLock.withLock { pauseClockLocked(interrupted=false) } }
    }
    fun foreground() { foreground=true; ensureClockCheckpointing(); maybeEngine() }
    private fun ensureClockCheckpointing() {
        if (!foreground || state.value.clock?.phase != ClockPhase.RUNNING) {
            clockCheckpointJob?.cancel()
            clockCheckpointJob=null
            return
        }
        if (clockCheckpointJob?.isActive == true) return
        clockCheckpointJob=viewModelScope.launch {
            while (isActive) {
                delay(CLOCK_CHECKPOINT_INTERVAL_MS)
                val keepRunning=commitLock.withLock {
                    val s=state.value
                    val game=s.game ?: return@withLock false
                    val clock=s.clock ?: return@withLock false
                    if (!foreground || clock.phase != ClockPhase.RUNNING || game.result != "*") return@withLock false
                    val now=SystemClock.elapsedRealtime()
                    val settled=clock.settled(now)
                    if (settled.phase == ClockPhase.FLAGGED) finishOnTime(s,settled)
                    else {
                        val checkpoint=game.withClock(settled,now)
                        db.games().save(checkpoint)
                        // Persist recovery data without invalidating the board every second.
                        // Other saves settle the live clock instead of reusing this snapshot.
                    }
                    settled.phase == ClockPhase.RUNNING
                }
                if (!keepRunning) break
            }
        }
    }
    private suspend fun pauseClockLocked(interrupted: Boolean) {
        val s = state.value
        val game = s.game ?: return
        val current = s.clock ?: return
        if (current.phase != ClockPhase.RUNNING) return
        invalidate()
        val now = SystemClock.elapsedRealtime()
        val paused = current.pause(now, interrupted)
        if (paused.phase == ClockPhase.FLAGGED) {
            finishOnTime(s, paused)
            return
        }
        val saved = game.withClock(paused, now)
        db.games().save(saved)
        state.value = s.copy(game=saved, clock=paused, busy=false, message=clockPauseMessage(paused))
    }
    private suspend fun finishOnTime(s: GameUi, clock: ClockState) {
        val game = s.game ?: return
        if (game.result != "*") return
        clockCheckpointJob=null
        revision++
        engine.stop()
        engineJob = null
        hintJob?.cancel()
        hintJob = null
        val flagged = clock.active
        val opponent=if(flagged==ClockSide.WHITE) Side.BLACK else Side.WHITE
        val result = if (s.position.provenUnableToMate(opponent)) "1/2-1/2"
            else if (flagged == ClockSide.WHITE) "0-1" else "1-0"
        val reason = "${flagged.name.lowercase().replaceFirstChar(Char::titlecase)} ran out of time"
        val saved = game.copy(result=result,resultReason=reason,updated=System.currentTimeMillis()).withClock(clock,SystemClock.elapsedRealtime())
        db.games().save(saved)
        state.value=s.copy(game=saved,clock=clock,busy=false,engineError=null,hint=null,hintBusy=false,hintError=null,
            resultEvent=ResultEvent(result,reason),message="Time expired · $result")
    }
    override fun onCleared() { engine.stop(); db.close(); super.onCleared() }
}

private const val CLOCK_CHECKPOINT_INTERVAL_MS = 1_000L

fun clockPauseMessage(clock: ClockState) =
    if (clock.interrupted) "Clock paused · App was interrupted" else "Clock paused · Resume when ready"
