package com.dataespresso.squarechess

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

enum class PuzzleMode { TRAINING, QUICK, REVIEW, THEME, COURSE, REPEAT }
enum class PuzzleFeedback { NONE, CORRECT, WRONG, SOLVED }
/** Why the landing screen shows a note instead of a puzzle. */
enum class PuzzleNote { NONE, NO_REVIEW_DUE, REVIEW_DONE, NO_PUZZLE, PACK_MISSING }
/** The page shown when no puzzle is on the board. */
enum class PuzzleView { HOME, COURSE, PROGRESS, REPEAT }

/** What a finished puzzle did to the puzzle rating: [change] is null when it was not the first attempt. */
data class PuzzleRated(val puzzleId: String, val change: Int?)

/** A finished pass through a chapter: the chapter and how each puzzle of this pass went. */
data class ChapterDone(val chapterId: String, val round: List<AttemptResult>)

/**
 * Why a wrong move fails. [reply] is the engine's best answer to [wrong] (null while it thinks or
 * when it gave none), [lead] where that leaves the game; [endsReason] is set when the wrong move
 * itself ends the game. [failed] means no explanation came, so the plain text shows.
 */
data class Refutation(
    val puzzleId: String,
    val ply: Int,
    val base: ChessPosition,
    val wrong: String,
    val wrongSan: String,
    val sideToReply: Boolean,
    val reply: String? = null,
    val replySan: String? = null,
    val lead: Eval? = null,
    val endsReason: String? = null,
    val failed: Boolean = false,
) {
    val thinking: Boolean get() = reply == null && endsReason == null && !failed
}

data class PuzzleUi(
    val loading: Boolean = true,
    /** The puzzle rating; it also chooses which puzzles come next. */
    val level: Int = START_PUZZLE_RATING,
    val totals: PuzzleTotals = PuzzleTotals(0,0,0,0),
    val puzzleCount: Int = 0,
    /** Null on the landing screen. */
    val mode: PuzzleMode? = null,
    val group: ThemeGroup? = null,
    /** A single theme (a pack tag such as "fork") instead of a [group]. */
    val tag: String? = null,
    val session: PuzzleSession? = null,
    val feedback: PuzzleFeedback = PuzzleFeedback.NONE,
    val hintShown: Boolean = false,
    /** Results of the running Quick 5 set; the summary shows when it holds five. */
    val quick: List<QuickResult> = emptyList(),
    val note: PuzzleNote = PuzzleNote.NONE,
    val view: PuzzleView = PuzzleView.HOME,
    val course: List<Chapter> = emptyList(),
    val progress: Map<String,PuzzleProgress> = emptyMap(),
    val stats: List<ThemeStat> = emptyList(),
    val goal: Int = DEFAULT_PUZZLE_GOAL,
    val days: Map<String,Int> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
    val repeatSet: RepeatSet? = null,
    /** The chapter being played, the puzzles still to come in this pass, and how this pass went. */
    val chapter: String? = null,
    val queue: List<String> = emptyList(),
    val round: List<AttemptResult> = emptyList(),
    val chapterDone: ChapterDone? = null,
    /** The repeat cycle that just ended, for the notice on the repeat page. */
    val cycleDone: RepeatCycle? = null,
    val refute: Refutation? = null,
    /** "Show me": the board shows the wrong move and the reply, and takes no moves. */
    val preview: Boolean = false,
    val rated: PuzzleRated? = null,
    val gameRating: GameRating = GameRating(),
    val ratingDays: RatingDays = emptyMap(),
) {
    /** Rated puzzles so far, for the question mark of a provisional rating. */
    val ratedCount: Int get() = puzzleCount(progress)
    val quickFinished: Boolean get() = mode==PuzzleMode.QUICK && quick.size>=QUICK_SET_SIZE && session==null
    val currentChapter: Chapter? get() = chapter?.let { id -> course.firstOrNull { it.id==id } }
    /** The page under the puzzle: the summary of a chapter or a page of its own. */
    val summaryShown: Boolean get() = session==null && chapterDone!=null
}

/** The notation draft's key: a typed move belongs to one puzzle position. */
fun PuzzleUi.entryKey(): String? = session?.let { "puzzle:${it.puzzle.id}:${it.ply}" }
fun PuzzleUi.canEnterMove(): Boolean = session?.state==PuzzleState.SOLVING && !preview

/**
 * Offline puzzle training. Puzzles come from the bundled pack and are judged by their scripted
 * solution; the engine only explains why a wrong move fails. Progress is kept in the
 * puzzle_progress table, never as games; the goal, day counts and repeat set in preferences.
 */
class PuzzleViewModel internal constructor(
    app: Application,
    private val saved: SavedStateHandle,
    private val db: ChessDatabase,
    private val catalogue: suspend () -> List<Puzzle>,
    private val random: Random,
    private val now: () -> Long,
    private val replyDelayMs: Long,
    private val engine: EngineController,
): AndroidViewModel(app) {
    constructor(app: Application, saved: SavedStateHandle): this(app,saved,appChessDatabase(app),
        { PuzzleCatalog.puzzles(app) },Random.Default,System::currentTimeMillis,REPLY_DELAY_MS,StockfishController(app))

    val state=MutableStateFlow(PuzzleUi())
    private val prefs=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    private val training=TrainingPrefs(prefs,now)
    private val lock=Mutex()
    private var puzzles: List<Puzzle> = emptyList()
    private var byId: Map<String,Puzzle> = emptyMap()
    private var progress: Map<String,PuzzleProgress> = emptyMap()
    private val recent=ArrayDeque<String>()
    private var replyJob: Job?=null
    private var refuteJob: Job?=null
    private var opened=false
    /** When the puzzle on the board appeared, for repeat-set times. */
    private var shownAt=0L

    private fun today(): LocalDate = Instant.ofEpochMilli(now()).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Loads the pack and progress the first time the Puzzles screen opens. */
    fun open() {
        if(opened) { refresh(); return }
        opened=true
        viewModelScope.launch { lock.withLock {
            puzzles=runCatching { catalogue() }.onFailure { Log.e("SquareChess","Puzzle pack not loaded",it) }.getOrDefault(emptyList())
            byId=puzzles.associateBy { it.id }
            progress=db.puzzleProgress().all().associateBy { it.puzzleId }
            val course=buildCourse(puzzles)
            state.value=state.value.copy(loading=false,level=training.puzzleRating,puzzleCount=puzzles.size,
                note=if(puzzles.isEmpty()) PuzzleNote.PACK_MISSING else PuzzleNote.NONE,course=course)
            refreshLocked()
            restoreSaved()
        } }
    }

    fun startTraining() = begin(PuzzleMode.TRAINING,null,null)
    fun startQuick() = begin(PuzzleMode.QUICK,null,null)
    fun startReview() = begin(PuzzleMode.REVIEW,null,null)
    fun startTheme(group: ThemeGroup) = begin(PuzzleMode.THEME,group,null)
    fun startTag(tag: String) = begin(PuzzleMode.THEME,null,tag)

    private fun begin(mode: PuzzleMode, group: ThemeGroup?, tag: String?) = viewModelScope.launch { lock.withLock {
        state.value=state.value.copy(mode=mode,group=group,tag=tag,quick=emptyList(),note=PuzzleNote.NONE,chapterDone=null,cycleDone=null)
        nextLocked()
    } }

    /** Plays a chapter: the puzzles not solved yet, else those not clean yet, else all of them. */
    fun startChapter(id: String) = viewModelScope.launch { lock.withLock {
        val chapter=state.value.course.firstOrNull { it.id==id } ?: return@withLock
        state.value=state.value.copy(mode=PuzzleMode.COURSE,group=null,tag=null,quick=emptyList(),note=PuzzleNote.NONE,
            chapter=id,queue=chapterQueue(chapter,progress),round=emptyList(),chapterDone=null,view=PuzzleView.COURSE)
        nextLocked()
    } }

    fun startRepeat() = viewModelScope.launch { lock.withLock {
        state.value=state.value.copy(mode=PuzzleMode.REPEAT,group=null,tag=null,quick=emptyList(),note=PuzzleNote.NONE,
            cycleDone=null,view=PuzzleView.REPEAT)
        nextLocked()
    } }

    /** Opens a page of the landing screen (the course, progress by theme or the repeat set). */
    fun openView(view: PuzzleView) {
        update(state.value.copy(view=view,note=PuzzleNote.NONE,chapterDone=null,cycleDone=null))
    }

    fun setGoal(goal: Int) {
        if(goal !in PUZZLE_GOALS) return
        training.goal=goal
        update(state.value.copy(goal=goal))
    }

    fun makeRepeatSet(size: Int, source: RepeatSource) = viewModelScope.launch { lock.withLock {
        val set=newRepeatSet(puzzles,progress,state.value.level,size,source,now(),random) ?: return@withLock
        training.repeatSet=set
        update(state.value.copy(repeatSet=set,cycleDone=null))
    } }

    fun clearRepeatSet() {
        training.repeatSet=null
        update(state.value.copy(repeatSet=null,cycleDone=null))
    }

    /** Starts a specific puzzle; used by tests and by a restored session. */
    internal fun startPuzzle(id: String, mode: PuzzleMode = PuzzleMode.TRAINING) = viewModelScope.launch { lock.withLock {
        val puzzle=byId[id] ?: return@withLock
        state.value=state.value.copy(mode=mode,group=null,tag=null,quick=emptyList(),note=PuzzleNote.NONE)
        show(puzzle)
    } }

    /** The next puzzle of the current mode, or a summary, or back to the landing screen. */
    fun next() = viewModelScope.launch { lock.withLock { nextLocked() } }

    private fun nextLocked() {
        replyJob?.cancel(); refuteJob?.cancel()
        val s=state.value
        val mode=s.mode ?: return
        if(mode==PuzzleMode.QUICK && s.quick.size>=QUICK_SET_SIZE) { update(s.copy(session=null,feedback=PuzzleFeedback.NONE,hintShown=false,refute=null,preview=false)); return }
        if(mode==PuzzleMode.COURSE) {
            val chapter=s.currentChapter
            val queue=s.queue.dropWhile { it !in byId }
            if(chapter==null || queue.isEmpty()) {
                update(landing(s).copy(chapterDone=chapter?.let { ChapterDone(it.id,s.round) },view=PuzzleView.COURSE))
                return
            }
            state.value=s.copy(queue=queue.drop(1))
            show(byId.getValue(queue.first()))
            return
        }
        if(mode==PuzzleMode.REPEAT) {
            val set=s.repeatSet
            val id=set?.nextId
            val puzzle=id?.let { byId[it] }
            // A finished cycle ends here: the repeat page shows its time before the next cycle starts.
            val cycleJustEnded=s.cycleDone!=null && set?.current==null && s.session!=null
            if(set==null || id==null || cycleJustEnded) { update(landing(s).copy(view=PuzzleView.REPEAT)); return }
            if(puzzle==null) {
                // A puzzle missing from this pack counts as done, so the cycle can still finish.
                val updated=recordRepeat(set,id,false,0,now())
                training.repeatSet=updated
                state.value=s.copy(repeatSet=updated)
                nextLocked(); return
            }
            show(puzzle)
            return
        }
        val start=android.os.SystemClock.elapsedRealtimeNanos()
        val puzzle=when(mode) {
            PuzzleMode.REVIEW -> dueForReview(progress.values,now()).firstOrNull { it!=s.session?.puzzle?.id }?.let { byId[it] }
            else -> choosePuzzle(puzzles,s.level,progress,recent.toSet()+s.quick.map { it.puzzleId },s.group,random,s.tag)
        }
        Log.i("SquareChess","Chose puzzle ${puzzle?.id} in ${(android.os.SystemClock.elapsedRealtimeNanos()-start)/1000} µs")
        if(puzzle==null) {
            val note=when {
                mode!=PuzzleMode.REVIEW -> PuzzleNote.NO_PUZZLE
                s.session==null -> PuzzleNote.NO_REVIEW_DUE
                else -> PuzzleNote.REVIEW_DONE
            }
            update(landing(s).copy(note=note,view=PuzzleView.HOME))
            return
        }
        show(puzzle)
    }

    /** The landing screen: no puzzle and no mode, everything else kept. */
    private fun landing(s: PuzzleUi) = s.copy(mode=null,group=null,tag=null,session=null,feedback=PuzzleFeedback.NONE,hintShown=false,
        quick=emptyList(),note=PuzzleNote.NONE,chapter=null,queue=emptyList(),round=emptyList(),refute=null,preview=false)

    private fun show(puzzle: Puzzle) {
        val session=PuzzleSession.start(puzzle)
        if(session==null) {
            // A damaged line: skip it for this run and try another.
            Log.e("SquareChess","Puzzle ${puzzle.id} has illegal moves; skipped")
            puzzles=puzzles.filter { it.id!=puzzle.id }
            byId=byId-puzzle.id
            nextLocked(); return
        }
        recent.addLast(puzzle.id); while(recent.size>RECENT_LIMIT) recent.removeFirst()
        shownAt=now()
        update(state.value.copy(session=session,feedback=PuzzleFeedback.NONE,hintShown=false,note=PuzzleNote.NONE,refute=null,preview=false))
    }

    /**
     * The solver's move, from the board (UCI) or the keyboard (SAN or UCI). Returns false when
     * the text is not a legal move here, so the notation entry can show an error.
     */
    fun move(text: String, expectedKey: String? = state.value.entryKey()): Boolean {
        val s=state.value
        val session=s.session ?: return false
        if(expectedKey!=s.entryKey() || s.preview) return false
        val (verdict,next)=session.judge(text)
        when(verdict) {
            MoveVerdict.ILLEGAL,MoveVerdict.NOT_NOW -> return false
            MoveVerdict.WRONG -> {
                update(s.copy(session=next,feedback=PuzzleFeedback.WRONG))
                session.position.resolve(text)?.let { explain(session,it.toString()) }
            }
            MoveVerdict.CORRECT -> {
                refuteJob?.cancel()
                update(s.copy(session=next,feedback=PuzzleFeedback.CORRECT,hintShown=false,refute=null,preview=false))
                scheduleReply()
            }
            MoveVerdict.SOLVED -> {
                refuteJob?.cancel()
                update(s.copy(session=next,feedback=PuzzleFeedback.SOLVED,hintShown=false,refute=null,preview=false))
                finish(next,if(next.firstTry) AttemptResult.FIRST_TRY else AttemptResult.WITH_HELP)
            }
        }
        return true
    }

    /**
     * Why a wrong move fails: the engine's best answer to it (hint strength, about 300 ms), and
     * where that leaves the game. A result that arrives after the player moved on is dropped.
     */
    private fun explain(session: PuzzleSession, wrong: String) {
        refuteJob?.cancel()
        val base=session.position
        val after=ChessPosition(base.initialFen,base.moves+wrong)
        val wrongSan=after.san.lastOrNull() ?: wrong
        val white=after.board.sideToMove==com.github.bhlangonijr.chesslib.Side.WHITE
        val start=Refutation(session.puzzle.id,session.ply,base,wrong,wrongSan,white,endsReason=after.automaticResultReason())
        update(state.value.copy(refute=start,preview=false))
        if(start.endsReason!=null) return
        refuteJob=viewModelScope.launch {
            val result=try {
                val eval=engine.evaluate(after.initialFen,after.moves)
                val best=eval?.best
                if(eval==null || best==null) start.copy(failed=true)
                else start.copy(reply=best,replySan=ChessPosition(after.initialFen,after.moves+best).san.lastOrNull() ?: best,lead=eval)
            } catch(e: CancellationException) { throw e } catch(e: Exception) {
                Log.w("SquareChess","No explanation for the wrong move",e); start.copy(failed=true)
            }
            val s=state.value
            if(s.refute?.puzzleId==start.puzzleId && s.refute.ply==start.ply && s.refute.wrong==wrong) update(s.copy(refute=result))
        }
    }

    /** "Show me" and "Back to the puzzle". */
    fun togglePreview() {
        val s=state.value
        if(s.refute?.reply==null && !s.preview) return
        update(s.copy(preview=!s.preview))
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
        update(s.copy(session=session.withHint(),hintShown=true,preview=false))
    }
    fun hideHint() { update(state.value.copy(hintShown=false)) }

    /**
     * Leaves the puzzle for the landing screen. An unsolved puzzle that the player tried (a move
     * or a hint) counts as not solved and comes back in review; one only looked at is not counted.
     * From a chapter or a repeat set it returns to that page.
     */
    fun exit() = viewModelScope.launch { lock.withLock {
        replyJob?.cancel(); refuteJob?.cancel()
        val s=state.value
        val session=s.session
        if(session!=null && session.state!=PuzzleState.SOLVED && (session.mistakes>0 || session.hintUsed || session.ply>1))
            finishLocked(session,AttemptResult.ABANDONED)
        val view=when(s.mode) { PuzzleMode.COURSE -> PuzzleView.COURSE; PuzzleMode.REPEAT -> PuzzleView.REPEAT; else -> PuzzleView.HOME }
        update(landing(state.value).copy(view=view,chapterDone=null))
    } }

    private fun finish(session: PuzzleSession, result: AttemptResult) = viewModelScope.launch { lock.withLock { finishLocked(session,result) } }

    private suspend fun finishLocked(session: PuzzleSession, result: AttemptResult) {
        val id=session.puzzle.id
        var s=state.value
        // Only a puzzle's first attempt changes the puzzle rating (Elo against the puzzle's rating),
        // in every mode; a puzzle seen before is not a fair test. Checked before the attempt is recorded.
        val rated=if(isFirstAttempt(progress[id])) {
            val level=applyPuzzle(s.level,puzzleChange(s.level,result,session.puzzle.rating,puzzleCount(progress)))
            training.savePuzzleRating(level)
            s=s.copy(level=level)
            PuzzleRated(id,level-state.value.level)
        } else PuzzleRated(id,null)
        val record=recordAttempt(progress[id],id,result,session.mistakes,session.hintUsed,now())
        db.puzzleProgress().save(record)
        progress=progress+(id to record)
        if(result!=AttemptResult.ABANDONED) training.days=addToDay(training.days,today())
        s=s.copy(rated=rated)
        if(s.mode==PuzzleMode.QUICK) s=s.copy(quick=s.quick+QuickResult(id,result,session.hintUsed))
        if(s.mode==PuzzleMode.COURSE) s=s.copy(round=s.round+result)
        if(s.mode==PuzzleMode.REPEAT) {
            val set=s.repeatSet
            if(set!=null && set.nextId==id) {
                val updated=recordRepeat(set,id,result==AttemptResult.FIRST_TRY,now()-shownAt,now())
                training.repeatSet=updated
                s=s.copy(repeatSet=updated,cycleDone=if(updated.cycles.size>set.cycles.size) updated.cycles.last() else s.cycleDone)
            }
        }
        state.value=s
        refreshLocked()
    }

    /** After a sync brought progress, the training level or the training values from another device. A puzzle on screen stays. */
    fun reloadAfterSync() {
        if(!opened) return
        viewModelScope.launch { lock.withLock {
            progress=db.puzzleProgress().all().associateBy { it.puzzleId }
            state.value=state.value.copy(level=prefs.getInt(LEVEL_KEY,START_TRAINING_LEVEL))
            refreshLocked()
        } }
    }

    /** Totals, theme accuracy and the training values, read again (for example after another screen). */
    private fun refresh() = viewModelScope.launch { lock.withLock { refreshLocked() } }

    private fun refreshLocked() {
        update(state.value.copy(totals=puzzleTotals(progress.values,now()),progress=progress,stats=themeStats(puzzles,progress),
            goal=training.goal,days=training.days,today=today(),repeatSet=training.repeatSet,
            gameRating=training.gameRating,ratingDays=training.ratingDays))
    }

    private fun update(value: PuzzleUi) {
        state.value=value
        val session=value.session
        saved[SAVED_ID]=session?.puzzle?.id
        saved[SAVED_PLY]=session?.ply
        saved[SAVED_MISTAKES]=session?.mistakes
        saved[SAVED_HINT]=session?.hintUsed
        saved[SAVED_MODE]=value.mode?.name
        saved[SAVED_GROUP]=value.group?.name
        saved[SAVED_TAG]=value.tag
        saved[SAVED_QUICK]=value.quick.joinToString(" ") { "${it.puzzleId}:${it.result.name}:${it.hint}" }
        saved[SAVED_VIEW]=value.view.name
        saved[SAVED_CHAPTER]=value.chapter
        saved[SAVED_QUEUE]=value.queue.joinToString(" ")
        saved[SAVED_ROUND]=value.round.joinToString(" ") { it.name }
    }

    /**
     * After the process was ended in the background, returns to the same puzzle and move. A
     * reply that was waiting is played at once, so the board is never left between two moves.
     */
    private fun restoreSaved() {
        val view=saved.get<String>(SAVED_VIEW)?.let { runCatching { PuzzleView.valueOf(it) }.getOrNull() } ?: PuzzleView.HOME
        val mode=saved.get<String>(SAVED_MODE)?.let { runCatching { PuzzleMode.valueOf(it) }.getOrNull() }
        if(mode==null) { update(state.value.copy(view=view)); return }
        val quick=saved.get<String>(SAVED_QUICK).orEmpty().split(' ').filter(String::isNotBlank).mapNotNull {
            val (id,result,hint)=it.split(':').takeIf { parts -> parts.size==3 } ?: return@mapNotNull null
            runCatching { QuickResult(id,AttemptResult.valueOf(result),hint.toBoolean()) }.getOrNull()
        }
        val group=saved.get<String>(SAVED_GROUP)?.let { runCatching { ThemeGroup.valueOf(it) }.getOrNull() }
        val tag=saved.get<String>(SAVED_TAG)?.takeIf { it in TRAINING_TAGS }
        val queue=saved.get<String>(SAVED_QUEUE).orEmpty().split(' ').filter(String::isNotBlank)
        val round=saved.get<String>(SAVED_ROUND).orEmpty().split(' ').mapNotNull { runCatching { AttemptResult.valueOf(it) }.getOrNull() }
        val puzzle=saved.get<String>(SAVED_ID)?.let { byId[it] }
        var session=puzzle?.let { PuzzleSession.restore(it,saved[SAVED_PLY] ?: 1,saved[SAVED_MISTAKES] ?: 0,saved[SAVED_HINT] ?: false) }
        if(session?.state==PuzzleState.OPPONENT_TO_MOVE) session=session.reply()
        shownAt=now()
        update(state.value.copy(mode=mode,group=group,tag=tag,quick=quick,session=session,view=view,
            chapter=saved.get<String>(SAVED_CHAPTER),queue=queue,round=round,
            feedback=if(session?.state==PuzzleState.SOLVED) PuzzleFeedback.SOLVED else PuzzleFeedback.NONE))
    }

    companion object {
        const val REPLY_DELAY_MS=450L
        private const val RECENT_LIMIT=30
        internal const val PREFS="puzzles"
        internal const val LEVEL_KEY="trainingLevel"
        internal const val LEVEL_UPDATED_KEY="trainingLevel.updated"
        private const val SAVED_ID="puzzle.id"
        private const val SAVED_PLY="puzzle.ply"
        private const val SAVED_MISTAKES="puzzle.mistakes"
        private const val SAVED_HINT="puzzle.hint"
        private const val SAVED_MODE="puzzle.mode"
        private const val SAVED_GROUP="puzzle.group"
        private const val SAVED_TAG="puzzle.tag"
        private const val SAVED_QUICK="puzzle.quick"
        private const val SAVED_VIEW="puzzle.view"
        private const val SAVED_CHAPTER="puzzle.chapter"
        private const val SAVED_QUEUE="puzzle.queue"
        private const val SAVED_ROUND="puzzle.round"
    }
}
