package com.dataespresso.squarechess

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

enum class PuzzleMode { TRAINING, QUICK, REVIEW, THEME }
enum class PuzzleFeedback { NONE, CORRECT, WRONG, SOLVED }
/** Why the landing screen shows a note instead of a puzzle. */
enum class PuzzleNote { NONE, NO_REVIEW_DUE, REVIEW_DONE, NO_PUZZLE, PACK_MISSING }

data class PuzzleUi(
    val loading: Boolean = true,
    val level: Int = START_TRAINING_LEVEL,
    val totals: PuzzleTotals = PuzzleTotals(0,0,0,0),
    val puzzleCount: Int = 0,
    /** Null on the landing screen. */
    val mode: PuzzleMode? = null,
    val group: ThemeGroup? = null,
    val session: PuzzleSession? = null,
    val feedback: PuzzleFeedback = PuzzleFeedback.NONE,
    val hintShown: Boolean = false,
    /** Results of the running Quick 5 set; the summary shows when it holds five. */
    val quick: List<QuickResult> = emptyList(),
    val note: PuzzleNote = PuzzleNote.NONE
) {
    val quickFinished: Boolean get() = mode==PuzzleMode.QUICK && quick.size>=QUICK_SET_SIZE && session==null
}

/** The notation draft's key: a typed move belongs to one puzzle position. */
fun PuzzleUi.entryKey(): String? = session?.let { "puzzle:${it.puzzle.id}:${it.ply}" }
fun PuzzleUi.canEnterMove(): Boolean = session?.state==PuzzleState.SOLVING

/**
 * Offline puzzle training. Puzzles come from the bundled pack and are judged by their scripted
 * solution; the engine is not used. Progress is kept in the puzzle_progress table, never as games.
 */
class PuzzleViewModel internal constructor(
    app: Application,
    private val saved: SavedStateHandle,
    private val db: ChessDatabase,
    private val catalogue: suspend () -> List<Puzzle>,
    private val random: Random,
    private val now: () -> Long,
    private val replyDelayMs: Long
): AndroidViewModel(app) {
    constructor(app: Application, saved: SavedStateHandle): this(app,saved,appChessDatabase(app),
        { PuzzleCatalog.puzzles(app) },Random.Default,System::currentTimeMillis,REPLY_DELAY_MS)

    val state=MutableStateFlow(PuzzleUi())
    private val prefs=app.getSharedPreferences("puzzles",Context.MODE_PRIVATE)
    private val lock=Mutex()
    private var puzzles: List<Puzzle> = emptyList()
    private var progress: Map<String,PuzzleProgress> = emptyMap()
    private val recent=ArrayDeque<String>()
    private var replyJob: Job?=null
    private var opened=false

    /** Loads the pack and progress the first time the Puzzles screen opens. */
    fun open() {
        if(opened) { refreshTotals(); return }
        opened=true
        viewModelScope.launch { lock.withLock {
            puzzles=runCatching { catalogue() }.onFailure { Log.e("SquareChess","Puzzle pack not loaded",it) }.getOrDefault(emptyList())
            progress=db.puzzleProgress().all().associateBy { it.puzzleId }
            state.value=state.value.copy(loading=false,level=prefs.getInt(LEVEL_KEY,START_TRAINING_LEVEL),puzzleCount=puzzles.size,
                totals=puzzleTotals(progress.values,now()),note=if(puzzles.isEmpty()) PuzzleNote.PACK_MISSING else PuzzleNote.NONE)
            restoreSaved()
        } }
    }

    fun startTraining() = begin(PuzzleMode.TRAINING,null)
    fun startQuick() = begin(PuzzleMode.QUICK,null)
    fun startReview() = begin(PuzzleMode.REVIEW,null)
    fun startTheme(group: ThemeGroup) = begin(PuzzleMode.THEME,group)

    private fun begin(mode: PuzzleMode, group: ThemeGroup?) = viewModelScope.launch { lock.withLock {
        state.value=state.value.copy(mode=mode,group=group,quick=emptyList(),note=PuzzleNote.NONE)
        nextLocked()
    } }

    /** Starts a specific puzzle; used by tests and by a restored session. */
    internal fun startPuzzle(id: String, mode: PuzzleMode = PuzzleMode.TRAINING) = viewModelScope.launch { lock.withLock {
        val puzzle=puzzles.firstOrNull { it.id==id } ?: return@withLock
        state.value=state.value.copy(mode=mode,group=null,quick=emptyList(),note=PuzzleNote.NONE)
        show(puzzle)
    } }

    /** The next puzzle of the current mode, or the Quick 5 summary, or back to the landing screen. */
    fun next() = viewModelScope.launch { lock.withLock { nextLocked() } }

    private fun nextLocked() {
        replyJob?.cancel()
        val s=state.value
        val mode=s.mode ?: return
        if(mode==PuzzleMode.QUICK && s.quick.size>=QUICK_SET_SIZE) { update(s.copy(session=null,feedback=PuzzleFeedback.NONE,hintShown=false)); return }
        val start=android.os.SystemClock.elapsedRealtimeNanos()
        val puzzle=when(mode) {
            PuzzleMode.REVIEW -> dueForReview(progress.values,now()).firstOrNull { it!=s.session?.puzzle?.id }
                ?.let { id -> puzzles.firstOrNull { it.id==id } }
            else -> choosePuzzle(puzzles,s.level,progress,recent.toSet()+s.quick.map { it.puzzleId },s.group,random)
        }
        Log.i("SquareChess","Chose puzzle ${puzzle?.id} in ${(android.os.SystemClock.elapsedRealtimeNanos()-start)/1000} µs")
        if(puzzle==null) {
            val note=when {
                mode!=PuzzleMode.REVIEW -> PuzzleNote.NO_PUZZLE
                s.session==null -> PuzzleNote.NO_REVIEW_DUE
                else -> PuzzleNote.REVIEW_DONE
            }
            update(PuzzleUi(loading=false,level=s.level,totals=s.totals,puzzleCount=s.puzzleCount,note=note))
            return
        }
        show(puzzle)
    }

    private fun show(puzzle: Puzzle) {
        val session=PuzzleSession.start(puzzle)
        if(session==null) {
            // A damaged line: skip it for this run and try another.
            Log.e("SquareChess","Puzzle ${puzzle.id} has illegal moves; skipped")
            puzzles=puzzles.filter { it.id!=puzzle.id }
            nextLocked(); return
        }
        recent.addLast(puzzle.id); while(recent.size>RECENT_LIMIT) recent.removeFirst()
        update(state.value.copy(session=session,feedback=PuzzleFeedback.NONE,hintShown=false,note=PuzzleNote.NONE))
    }

    /**
     * The solver's move, from the board (UCI) or the keyboard (SAN or UCI). Returns false when
     * the text is not a legal move here, so the notation entry can show an error.
     */
    fun move(text: String, expectedKey: String? = state.value.entryKey()): Boolean {
        val s=state.value
        val session=s.session ?: return false
        if(expectedKey!=s.entryKey()) return false
        val (verdict,next)=session.judge(text)
        when(verdict) {
            MoveVerdict.ILLEGAL,MoveVerdict.NOT_NOW -> return false
            MoveVerdict.WRONG -> update(s.copy(session=next,feedback=PuzzleFeedback.WRONG))
            MoveVerdict.CORRECT -> {
                update(s.copy(session=next,feedback=PuzzleFeedback.CORRECT,hintShown=false))
                scheduleReply()
            }
            MoveVerdict.SOLVED -> {
                update(s.copy(session=next,feedback=PuzzleFeedback.SOLVED,hintShown=false))
                finish(next,if(next.firstTry) AttemptResult.FIRST_TRY else AttemptResult.WITH_HELP)
            }
        }
        return true
    }

    /** The opponent's scripted reply follows a short pause, so the solver sees their move land. */
    private fun scheduleReply() {
        replyJob?.cancel()
        replyJob=viewModelScope.launch {
            delay(replyDelayMs)
            val s=state.value
            val session=s.session ?: return@launch
            if(session.state==PuzzleState.OPPONENT_TO_MOVE) update(s.copy(session=session.reply()))
        }
    }

    fun showHint() {
        val s=state.value
        val session=s.session ?: return
        if(session.state!=PuzzleState.SOLVING) return
        update(s.copy(session=session.withHint(),hintShown=true))
    }
    fun hideHint() { update(state.value.copy(hintShown=false)) }

    /**
     * Leaves the puzzle for the landing screen. An unsolved puzzle that the player tried (a move
     * or a hint) counts as not solved and comes back in review; one only looked at is not counted.
     */
    fun exit() = viewModelScope.launch { lock.withLock {
        replyJob?.cancel()
        val s=state.value
        val session=s.session
        if(session!=null && session.state!=PuzzleState.SOLVED && (session.mistakes>0 || session.hintUsed || session.ply>1))
            finishLocked(session,AttemptResult.ABANDONED)
        update(PuzzleUi(loading=false,level=state.value.level,totals=state.value.totals,puzzleCount=s.puzzleCount))
    } }

    private fun finish(session: PuzzleSession, result: AttemptResult) = viewModelScope.launch { lock.withLock { finishLocked(session,result) } }

    private suspend fun finishLocked(session: PuzzleSession, result: AttemptResult) {
        val id=session.puzzle.id
        val record=recordAttempt(progress[id],id,result,session.mistakes,session.hintUsed,now())
        db.puzzleProgress().save(record)
        progress=progress+(id to record)
        val s=state.value
        // Review puzzles are old ones; only new puzzles move the training level.
        val level=if(s.mode==PuzzleMode.REVIEW) s.level else nextTrainingLevel(s.level,result,session.puzzle.rating)
        prefs.edit().putInt(LEVEL_KEY,level).apply()
        val quick=if(s.mode==PuzzleMode.QUICK) s.quick+QuickResult(id,result,session.hintUsed) else s.quick
        update(s.copy(level=level,quick=quick,totals=puzzleTotals(progress.values,now())))
    }

    private fun refreshTotals() { update(state.value.copy(totals=puzzleTotals(progress.values,now()))) }

    private fun update(value: PuzzleUi) {
        state.value=value
        val session=value.session
        saved[SAVED_ID]=session?.puzzle?.id
        saved[SAVED_PLY]=session?.ply
        saved[SAVED_MISTAKES]=session?.mistakes
        saved[SAVED_HINT]=session?.hintUsed
        saved[SAVED_MODE]=value.mode?.name
        saved[SAVED_GROUP]=value.group?.name
        saved[SAVED_QUICK]=value.quick.joinToString(" ") { "${it.puzzleId}:${it.result.name}:${it.hint}" }
    }

    /**
     * After the process was ended in the background, returns to the same puzzle and move. A
     * reply that was waiting is played at once, so the board is never left between two moves.
     */
    private fun restoreSaved() {
        val mode=saved.get<String>(SAVED_MODE)?.let { runCatching { PuzzleMode.valueOf(it) }.getOrNull() } ?: return
        val quick=saved.get<String>(SAVED_QUICK).orEmpty().split(' ').filter(String::isNotBlank).mapNotNull {
            val (id,result,hint)=it.split(':').takeIf { parts -> parts.size==3 } ?: return@mapNotNull null
            runCatching { QuickResult(id,AttemptResult.valueOf(result),hint.toBoolean()) }.getOrNull()
        }
        val group=saved.get<String>(SAVED_GROUP)?.let { runCatching { ThemeGroup.valueOf(it) }.getOrNull() }
        val puzzle=saved.get<String>(SAVED_ID)?.let { id -> puzzles.firstOrNull { it.id==id } }
        var session=puzzle?.let { PuzzleSession.restore(it,saved[SAVED_PLY] ?: 1,saved[SAVED_MISTAKES] ?: 0,saved[SAVED_HINT] ?: false) }
        if(session?.state==PuzzleState.OPPONENT_TO_MOVE) session=session.reply()
        update(state.value.copy(mode=mode,group=group,quick=quick,session=session,
            feedback=if(session?.state==PuzzleState.SOLVED) PuzzleFeedback.SOLVED else PuzzleFeedback.NONE))
    }

    companion object {
        const val REPLY_DELAY_MS=450L
        private const val RECENT_LIMIT=30
        private const val LEVEL_KEY="trainingLevel"
        private const val SAVED_ID="puzzle.id"
        private const val SAVED_PLY="puzzle.ply"
        private const val SAVED_MISTAKES="puzzle.mistakes"
        private const val SAVED_HINT="puzzle.hint"
        private const val SAVED_MODE="puzzle.mode"
        private const val SAVED_GROUP="puzzle.group"
        private const val SAVED_QUICK="puzzle.quick"
    }
}
