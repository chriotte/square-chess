package com.dataespresso.squarechess

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

data class ToolbarLayout(val review: Boolean, val undo: Boolean, val difficulty: Boolean, val hint: Boolean = false)
/**
 * Buttons that fit beside the status text. The Hint button ranks after Menu: it takes its
 * space from Review, Undo and the level label, which stay in the menu. The board never shrinks.
 * With hints, Undo ranks before Review: a player who asks for hints also takes moves back.
 */
fun toolbarLayout(widthDp: Float, fontScale: Float, hints: Boolean = false): ToolbarLayout {
    val effective=widthDp/fontScale.coerceAtLeast(1f)
    val hintWidth=if(hints) HINT_BUTTON_SPACE else 0f
    val (reviewWidth,undoWidth)=if(hints) 410f to 270f else 270f to 410f
    return ToolbarLayout(review=effective>=reviewWidth+hintWidth,undo=effective>=undoWidth+hintWidth,
        difficulty=effective>=400+hintWidth,hint=hints && effective>=200)
}
private const val HINT_BUTTON_SPACE=72f

/** Header text while a hint is involved, or null for the normal status. */
fun hintStatus(res: android.content.res.Resources, s: GameUi): HintText? {
    s.visibleHint()?.let { hint -> return hintMove(s.position,hint.move)?.let { hintText(res,it) } }
    if(s.hintBusy) return HintText("",res.getString(R.string.finding_hint),res.getString(R.string.finding_hint))
    if(s.hintFailed) return HintText("",res.getString(R.string.hint_unavailable),res.getString(R.string.hint_unavailable))
    return null
}

enum class GameStatus { GAME_OVER, COMPUTER_THINKING, CLOCK_PAUSED, CLOCK_PAUSED_INTERRUPTED, TIME_EXPIRED, CHECK,
    COMPUTER_UNAVAILABLE, YOUR_MOVE, COMPUTER_TO_MOVE, WHITE_TO_MOVE, BLACK_TO_MOVE }

fun gameStatus(s: GameUi): GameStatus = when {
    s.game?.result!="*" -> GameStatus.GAME_OVER
    s.busy -> GameStatus.COMPUTER_THINKING
    s.clock?.phase == ClockPhase.PAUSED -> if(requireNotNull(s.clock).interrupted) GameStatus.CLOCK_PAUSED_INTERRUPTED else GameStatus.CLOCK_PAUSED
    s.clock?.phase == ClockPhase.FLAGGED -> GameStatus.TIME_EXPIRED
    s.position.board.isKingAttacked -> GameStatus.CHECK
    s.engineError!=null -> GameStatus.COMPUTER_UNAVAILABLE
    s.game?.mode==GameMode.COMPUTER.name -> if(s.canEnterMove()) GameStatus.YOUR_MOVE else GameStatus.COMPUTER_TO_MOVE
    else -> if(s.position.board.sideToMove==com.github.bhlangonijr.chesslib.Side.WHITE) GameStatus.WHITE_TO_MOVE else GameStatus.BLACK_TO_MOVE
}

/** States the player must see even when the header shows an evaluation. */
val STATUS_BEFORE_EVALUATION=setOf(GameStatus.GAME_OVER,GameStatus.CLOCK_PAUSED,GameStatus.CLOCK_PAUSED_INTERRUPTED,
    GameStatus.TIME_EXPIRED,GameStatus.COMPUTER_UNAVAILABLE)

/**
 * The status text. [move] is the last move with its separator ("12. Nf3 · ") or empty.
 * Without an evaluation it is the usual status, with the opening name on a second line when
 * there is room. With one, the move's label replaces the status (the move tells whose turn it
 * is) and the lead takes the second line; a single line (a clock is shown) keeps the lead only.
 * [statusFirst] keeps the status for states the player must see.
 */
fun headerText(move: String, status: String, difficulty: String, quality: String?, lead: String?, opening: String?,
               oneLine: Boolean, statusFirst: Boolean, review: Boolean): String {
    val usual=move+status+difficulty
    if(statusFirst || lead==null && quality==null) return if(!oneLine && opening!=null) "$usual\n$opening" else usual
    val first=when {
        review -> listOfNotNull(status,quality).joinToString(" · ")
        quality!=null -> move+quality
        else -> move+status
    }
    return when {
        lead==null -> first
        oneLine -> if(review) "$first · $lead" else move+listOfNotNull(quality,lead).joinToString(" · ")
        else -> "$first\n$lead"
    }
}

/** Review arrows shown in the header when no review bar fits below the board. */
class HeaderReviewNav(val onFirst: ()->Unit, val onPrevious: ()->Unit, val onNext: ()->Unit, val onLast: ()->Unit)

/** One-line button label: a longer translation must not wrap, or the header (and so the board) would change size. */
@Composable private fun ButtonLabel(text: String) = Text(text,maxLines=1,softWrap=false,overflow=TextOverflow.Ellipsis)

/** Cutout-safe bounds are supplied by the existing GameHeader container. */
@Composable fun GameToolbar(
    s: GameUi, reviewPly: Int?, clock: ClockState? = null,
    onReview: ()->Unit, onReturn: ()->Unit, onUndo: ()->Unit, onMenu: ()->Unit,
    onPauseClock: ()->Unit = {}, onResumeClock: ()->Unit = {}, onClockExpired: ()->Unit = {},
    reviewNav: HeaderReviewNav? = null, lastMove: String? = null,
    onHint: ()->Unit = {}, onHideHint: ()->Unit = {}
) {
    val res=appResources()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val hints=reviewPly==null && s.hintsAvailable() && s.game?.result=="*"
        val layout=toolbarLayout(maxWidth.value,LocalDensity.current.fontScale,hints)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f).padding(end=4.dp)) {
                val hint=if(hints) remember(s.hint,s.hintBusy,s.hintFailed,s.position,res) { hintStatus(res,s) } else null
                val difficulty=if(layout.difficulty && s.game?.mode==GameMode.COMPUTER.name) " · ${levelLabel(s.game.level)}" else ""
                // Two 14 sp lines fit the 56 dp minimum header up to font scale 1.3, so the
                // extra text cannot make the header (and so the board) change size.
                val move=if(reviewPly==null && lastMove!=null && LocalDensity.current.fontScale<=1.3f) "$lastMove · " else ""
                val shownPly=reviewPly ?: s.position.moves.size
                val evaluation=s.evaluationOn()
                val quality=if(evaluation) qualityAt(s.evals,s.position.initialFen,s.position.moves,shownPly)?.let { stringResource(qualityText(it)) } else null
                // While the newest position is still being rated, the previous lead stands in.
                val leadEval=if(!evaluation) null else s.evals[shownPly] ?: if(reviewPly==null) s.evals[shownPly-1] else null
                val lead=leadEval?.let { leadText(res,it) }
                val table=Openings.table
                val opening=remember(table,s.position,shownPly) {
                    table?.let { openingAt(it,s.position.initialFen,s.position.moves,shownPly) }
                }?.let { stringResource(R.string.opening_line,it.eco,it.name) }
                val status=gameStatus(s)
                val statusFirst=reviewPly==null && status in STATUS_BEFORE_EVALUATION
                // A hint replaces the status in the same text line, so the header keeps its height.
                val text=when {
                    hint==null -> headerText(move,
                        if(reviewPly!=null) stringResource(R.string.status_review,reviewPly,s.position.moves.size) else stringResource(statusText(status)),
                        difficulty,quality,lead,opening,oneLine=clock!=null,statusFirst=statusFirst,review=reviewPly!=null)
                    hint.san.isEmpty() -> hint.detail
                    else -> stringResource(R.string.hint_line,hint.san,hint.detail)
                }
                // With a clock the header already holds two rows; one status line keeps its height unchanged.
                Text(text,fontSize=14.sp,fontWeight=FontWeight.Medium,
                    maxLines=if(clock!=null) 1 else 2,overflow=TextOverflow.Ellipsis,
                    modifier=if(hint!=null) Modifier.semantics { contentDescription=hint.spoken } else Modifier)
                if(clock!=null) ClockReadout(
                    clock,onPauseClock,onResumeClock,onClockExpired,
                    canToggle=reviewPly==null && s.game?.result=="*"
                )
            }
            if(reviewPly!=null && reviewNav!=null) {
                val total=s.position.moves.size
                val edges=layout.undo
                @Composable fun Arrow(label: String, description: String, enabled: Boolean, onClick: ()->Unit) =
                    TextButton(onClick=onClick,enabled=enabled,
                        modifier=Modifier.sizeIn(minWidth=40.dp,minHeight=48.dp).semantics { contentDescription=description },
                        contentPadding=PaddingValues(horizontal=4.dp)) { Text(label,fontSize=18.sp) }
                if(edges) Arrow("|‹",stringResource(R.string.first_position),reviewPly>0,reviewNav.onFirst)
                Arrow("‹",stringResource(R.string.previous_move),reviewPly>0,reviewNav.onPrevious)
                Arrow("›",stringResource(R.string.next_move),reviewPly<total,reviewNav.onNext)
                if(edges) Arrow("›|",stringResource(R.string.last_position),reviewPly<total,reviewNav.onLast)
            }
            if(layout.hint) {
                val shown=s.visibleHint()!=null
                val description=stringResource(if(shown) R.string.hide_hint else R.string.show_hint)
                FilledTonalButton(onClick=if(shown) onHideHint else onHint,
                    enabled=shown || (s.canEnterMove() && !s.hintBusy),
                    modifier=Modifier.sizeIn(minWidth=64.dp,minHeight=48.dp)
                        .semantics { contentDescription=description },
                    contentPadding=PaddingValues(horizontal=10.dp)) { ButtonLabel(stringResource(if(shown) R.string.hide else R.string.hint)) }
            }
            if(reviewPly!=null) {
                val description=stringResource(R.string.return_to_game)
                FilledTonalButton(onClick=onReturn,
                    modifier=Modifier.heightIn(min=48.dp).semantics { contentDescription=description },
                    contentPadding=PaddingValues(horizontal=10.dp)) { ButtonLabel(stringResource(R.string.return_action)) }
            }
            else if(layout.review) FilledTonalButton(onClick=onReview,enabled=!s.busy,
                modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=10.dp)) { ButtonLabel(stringResource(R.string.review)) }
            if(layout.undo && reviewPly==null && s.game?.result=="*" && s.position.moves.isNotEmpty()) {
                val description=stringResource(R.string.undo_description)
                FilledTonalButton(onClick=onUndo,enabled=!s.busy,
                    modifier=Modifier.sizeIn(minWidth=64.dp,minHeight=48.dp).semantics { contentDescription=description },
                    contentPadding=PaddingValues(horizontal=10.dp)) { ButtonLabel(stringResource(R.string.undo)) }
            }
            val menuDescription=stringResource(R.string.menu_description)
            Button(onClick=onMenu,modifier=Modifier.heightIn(min=48.dp).semantics { contentDescription=menuDescription },
                contentPadding=PaddingValues(horizontal=12.dp)) { ButtonLabel(stringResource(R.string.menu)) }
        }
    }
}

/** Only this small subtree observes the clock tick; the board never does. */
@Composable private fun ClockReadout(
    clock: ClockState,
    onPause: ()->Unit,
    onResume: ()->Unit,
    onExpired: ()->Unit,
    canToggle: Boolean
) {
    var now by remember(clock) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(clock) {
        while(clock.phase==ClockPhase.RUNNING) {
            now=SystemClock.elapsedRealtime()
            val settled=clock.settled(now)
            if(settled.phase!=ClockPhase.RUNNING) break
            val remaining=if(settled.active==ClockSide.WHITE) settled.whiteMs else settled.blackMs
            delay(clockTickDelayMs(remaining))
        }
    }
    val display=clock.settled(now)
    LaunchedEffect(display.phase) {
        if(display.phase==ClockPhase.FLAGGED) onExpired()
    }
    Column {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            ClockSide.entries.forEach { side ->
                val active=display.active==side && display.phase==ClockPhase.RUNNING
                val value=clockText(if(side==ClockSide.WHITE) display.whiteMs else display.blackMs)
                val description=stringResource(if(side==ClockSide.WHITE) R.string.clock_description_white else R.string.clock_description_black,value)+
                    if(active) stringResource(R.string.clock_running_suffix) else ""
                Text("${stringResource(if(side==ClockSide.WHITE) R.string.clock_white_short else R.string.clock_black_short)} $value",
                    fontFamily=FontFamily.Monospace,fontSize=12.sp,
                    fontWeight=if(active) FontWeight.Bold else FontWeight.Normal,
                    color=if(active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier=Modifier.background(if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(4.dp)).padding(horizontal=3.dp,vertical=1.dp)
                        .semantics { contentDescription=description })
            }
            when {
                !canToggle -> Unit
                display.phase == ClockPhase.RUNNING -> {
                    val description=stringResource(R.string.pause_clock)
                    TextButton(
                        onClick=onPause,
                        modifier=Modifier.heightIn(min=40.dp).semantics { contentDescription=description },
                        contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)
                    ) { ButtonLabel(stringResource(R.string.pause)) }
                }
                display.phase == ClockPhase.PAUSED -> {
                    val description=stringResource(R.string.resume_clock)
                    TextButton(
                        onClick=onResume,
                        modifier=Modifier.heightIn(min=40.dp).semantics { contentDescription=description },
                        contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)
                    ) { ButtonLabel(stringResource(R.string.resume)) }
                }
                else -> Unit
            }
        }
        if(display.phase==ClockPhase.PAUSED) {
            Text(stringResource(clockPauseText(display)),fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        } else if(display.phase==ClockPhase.FLAGGED) {
            Text(stringResource(if(display.active==ClockSide.WHITE) R.string.white_ran_out else R.string.black_ran_out),
                fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}
