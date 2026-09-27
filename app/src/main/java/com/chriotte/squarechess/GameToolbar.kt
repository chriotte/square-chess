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
    return ToolbarLayout(review=effective>=270,undo=effective>=410,difficulty=effective>=470)
}

fun gameStatus(s: GameUi): String = when {
    s.game?.result!="*" -> "Game over"
    s.busy -> "Computer thinking"
    s.position.board.isKingAttacked -> "Check"
    s.engineError!=null -> "Computer unavailable"
    s.game?.mode==GameMode.COMPUTER.name -> if(s.canEnterMove()) "Your move" else "Computer to move"
    else -> if(s.position.board.sideToMove==com.github.bhlangonijr.chesslib.Side.WHITE) "White to move" else "Black to move"
}

/** Cutout-safe bounds are supplied by the existing GameHeader container. */
@Composable fun GameToolbar(
    s: GameUi, reviewPly: Int?, clock: ClockState? = null,
    onReview: ()->Unit, onReturn: ()->Unit, onUndo: ()->Unit, onMenu: ()->Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val layout=toolbarLayout(maxWidth.value,LocalDensity.current.fontScale)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end=4.dp)) {
                val status=if(reviewPly!=null) "Review · $reviewPly/${s.position.moves.size}" else gameStatus(s)
                val difficulty=if(layout.difficulty && s.game?.mode==GameMode.COMPUTER.name) " · Level ${s.game.level}" else ""
                Text(status+difficulty,fontSize=14.sp,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(clock!=null) ClockReadout(clock)
            }
            if(reviewPly!=null) TextButton(onClick=onReturn,contentPadding=PaddingValues(horizontal=6.dp)) { Text("Return") }
            else if(layout.review) TextButton(onClick=onReview,enabled=!s.busy,contentPadding=PaddingValues(horizontal=6.dp)) { Text("Review") }
            if(layout.undo && reviewPly==null && s.game?.result=="*" && s.position.moves.isNotEmpty()) {
                TextButton(onClick=onUndo,enabled=!s.busy,
                    modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).semantics { contentDescription="Undo last turn" },
                    contentPadding=PaddingValues(0.dp)) { Text("↶",fontSize=22.sp) }
            }
            Button(onClick=onMenu,modifier=Modifier.heightIn(min=48.dp).semantics { contentDescription="Game menu" },
                contentPadding=PaddingValues(horizontal=12.dp)) { Text("Menu") }
        }
    }
}

/** Only this small subtree observes the clock tick; the board never does. */
@Composable private fun ClockReadout(clock: ClockState) {
    var now by remember(clock) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(clock) {
        while(clock.phase==ClockPhase.RUNNING) { now=SystemClock.elapsedRealtime(); delay(100) }
    }
    val display=clock.settled(now)
    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
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
    }
}
