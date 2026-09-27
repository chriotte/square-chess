package com.chriotte.squarechess

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

data class ToolbarLayout(val review: Boolean, val undo: Boolean, val difficulty: Boolean)
fun toolbarLayout(widthDp: Float, fontScale: Float): ToolbarLayout {
    val effective=widthDp/fontScale.coerceAtLeast(1f)
    return ToolbarLayout(review=effective>=270,undo=effective>=410,difficulty=effective>=400)
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

/** Cutout-safe bounds are supplied by the existing GameHeader container. */
@Composable fun GameToolbar(
    s: GameUi, reviewPly: Int?, clock: ClockState? = null,
    onReview: ()->Unit, onReturn: ()->Unit, onUndo: ()->Unit, onMenu: ()->Unit,
    onPauseClock: ()->Unit = {}, onResumeClock: ()->Unit = {}, onClockExpired: ()->Unit = {}
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val layout=toolbarLayout(maxWidth.value,LocalDensity.current.fontScale)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f).padding(end=4.dp)) {
                val status=if(reviewPly!=null) "Review · $reviewPly/${s.position.moves.size}" else gameStatus(s)
                val difficulty=if(layout.difficulty && s.game?.mode==GameMode.COMPUTER.name) " · Level ${s.game.level}" else ""
                Text(status+difficulty,fontSize=14.sp,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(clock!=null) ClockReadout(
                    clock,onPauseClock,onResumeClock,onClockExpired,
                    canToggle=reviewPly==null && s.game?.result=="*"
                )
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
