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
import kotlinx.coroutines.delay

data class ToolbarLayout(val review: Boolean, val undo: Boolean, val difficulty: Boolean, val hint: Boolean = false)
/**
 * Buttons that fit beside the status text. The Hint button ranks after Menu: it takes its
 * space from Review, Undo and the level label, which stay in the menu. The board never shrinks.
 */
fun toolbarLayout(widthDp: Float, fontScale: Float, hints: Boolean = false): ToolbarLayout {
    val effective=widthDp/fontScale.coerceAtLeast(1f)
    val hintWidth=if(hints) HINT_BUTTON_SPACE else 0f
    return ToolbarLayout(review=effective>=270+hintWidth,undo=effective>=410+hintWidth,
        difficulty=effective>=400+hintWidth,hint=hints && effective>=200)
}
private const val HINT_BUTTON_SPACE=72f

/** Header text while a hint is involved, or null for the normal status. */
fun hintStatus(s: GameUi): HintText? {
    s.visibleHint()?.let { hint -> return hintText(s.position,hint.move) }
    if(s.hintBusy) return HintText("","Finding hint…","Finding hint")
    s.hintError?.let { return HintText("",it,it) }
    return null
}

fun gameStatus(s: GameUi): String = when {
    s.game?.result!="*" -> "Game over"
    s.busy -> "Computer thinking"
    s.clock?.phase == ClockPhase.PAUSED -> clockPauseMessage(requireNotNull(s.clock))
    s.clock?.phase == ClockPhase.FLAGGED -> "Time expired"
    s.position.board.isKingAttacked -> "Check"
    s.engineError!=null -> "Computer unavailable"
    s.game?.mode==GameMode.COMPUTER.name -> if(s.canEnterMove()) "Your move" else "Computer to move"
    else -> if(s.position.board.sideToMove==com.github.bhlangonijr.chesslib.Side.WHITE) "White to move" else "Black to move"
}

/** Review arrows shown in the header when no review bar fits below the board. */
class HeaderReviewNav(val onFirst: ()->Unit, val onPrevious: ()->Unit, val onNext: ()->Unit, val onLast: ()->Unit)

/** Cutout-safe bounds are supplied by the existing GameHeader container. */
@Composable fun GameToolbar(
    s: GameUi, reviewPly: Int?, clock: ClockState? = null,
    onReview: ()->Unit, onReturn: ()->Unit, onUndo: ()->Unit, onMenu: ()->Unit,
    onPauseClock: ()->Unit = {}, onResumeClock: ()->Unit = {}, onClockExpired: ()->Unit = {},
    reviewNav: HeaderReviewNav? = null, lastMove: String? = null,
    onHint: ()->Unit = {}, onHideHint: ()->Unit = {}
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val hints=reviewPly==null && s.hintsAvailable() && s.game?.result=="*"
        val layout=toolbarLayout(maxWidth.value,LocalDensity.current.fontScale,hints)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f).padding(end=4.dp)) {
                val hint=if(hints) remember(s.hint,s.hintBusy,s.hintError,s.position) { hintStatus(s) } else null
                val status=if(reviewPly!=null) "Review · $reviewPly/${s.position.moves.size}" else gameStatus(s)
                val difficulty=if(layout.difficulty && s.game?.mode==GameMode.COMPUTER.name) " · ${difficultyLabel(s.game.level)}" else ""
                // Two 14 sp lines fit the 56 dp minimum header up to font scale 1.3, so the
                // extra text cannot make the header (and so the board) change size.
                val move=if(reviewPly==null && lastMove!=null && LocalDensity.current.fontScale<=1.3f) "$lastMove · " else ""
                // A hint replaces the status in the same text line, so the header keeps its height.
                val text=when {
                    hint==null -> move+status+difficulty
                    hint.san.isEmpty() -> hint.detail
                    else -> "Hint: ${hint.san} · ${hint.detail}"
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
                if(edges) Arrow("|‹","First position",reviewPly>0,reviewNav.onFirst)
                Arrow("‹","Previous move",reviewPly>0,reviewNav.onPrevious)
                Arrow("›","Next move",reviewPly<total,reviewNav.onNext)
                if(edges) Arrow("›|","Last position",reviewPly<total,reviewNav.onLast)
            }
            if(layout.hint) {
                val shown=s.visibleHint()!=null
                FilledTonalButton(onClick=if(shown) onHideHint else onHint,
                    enabled=shown || (s.canEnterMove() && !s.hintBusy),
                    modifier=Modifier.sizeIn(minWidth=64.dp,minHeight=48.dp)
                        .semantics { contentDescription=if(shown) "Hide hint" else "Show hint" },
                    contentPadding=PaddingValues(horizontal=10.dp)) { Text(if(shown) "Hide" else "Hint") }
            }
            if(reviewPly!=null) FilledTonalButton(onClick=onReturn,
                modifier=Modifier.heightIn(min=48.dp).semantics { contentDescription="Return to game" },
                contentPadding=PaddingValues(horizontal=10.dp)) { Text("Return") }
            else if(layout.review) FilledTonalButton(onClick=onReview,enabled=!s.busy,
                modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=10.dp)) { Text("Review") }
            if(layout.undo && reviewPly==null && s.game?.result=="*" && s.position.moves.isNotEmpty()) {
                FilledTonalButton(onClick=onUndo,enabled=!s.busy,
                    modifier=Modifier.sizeIn(minWidth=64.dp,minHeight=48.dp).semantics { contentDescription="Undo last turn" },
                    contentPadding=PaddingValues(horizontal=10.dp)) { Text("Undo") }
            }
            Button(onClick=onMenu,modifier=Modifier.heightIn(min=48.dp).semantics { contentDescription="Game menu" },
                contentPadding=PaddingValues(horizontal=12.dp)) { Text("Menu") }
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
                Text("${if(side==ClockSide.WHITE) "W" else "B"} $value",fontFamily=FontFamily.Monospace,fontSize=12.sp,
                    fontWeight=if(active) FontWeight.Bold else FontWeight.Normal,
                    color=if(active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier=Modifier.background(if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(4.dp)).padding(horizontal=3.dp,vertical=1.dp)
                        .semantics { contentDescription="${side.name.lowercase()} $value${if(active) ", running" else ""}" })
            }
            when {
                !canToggle -> Unit
                display.phase == ClockPhase.RUNNING -> {
                    TextButton(
                        onClick=onPause,
                        modifier=Modifier.heightIn(min=40.dp).semantics { contentDescription="Pause clock" },
                        contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)
                    ) { Text("Pause") }
                }
                display.phase == ClockPhase.PAUSED -> {
                    TextButton(
                        onClick=onResume,
                        modifier=Modifier.heightIn(min=40.dp).semantics { contentDescription="Resume clock" },
                        contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)
                    ) { Text("Resume") }
                }
                else -> Unit
            }
        }
        if(display.phase==ClockPhase.PAUSED) {
            Text(clockPauseMessage(display),fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        } else if(display.phase==ClockPhase.FLAGGED) {
            Text("${display.active.name.lowercase().replaceFirstChar(Char::titlecase)} ran out of time",fontSize=11.sp)
        }
    }
}
