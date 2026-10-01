package com.dataespresso.squarechess

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The Puzzles landing screen: ways to train, progress and the training level. */
@Composable fun PuzzleLanding(
    ui: PuzzleUi, onHome: () -> Unit, onTraining: () -> Unit, onQuick: () -> Unit,
    onReview: () -> Unit, onTheme: (ThemeGroup) -> Unit
) {
    val palette=LocalPalette.current
    var themes by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        TextButton(onClick=onHome,contentPadding=PaddingValues(0.dp)) { Text(stringResource(R.string.home_back)) }
        Text(stringResource(R.string.puzzles),fontFamily=FontFamily.Serif,fontSize=30.sp)
        if(ui.loading) { Text(stringResource(R.string.puzzle_loading)); return@Column }
        Text(stringResource(R.string.puzzles_count,ui.puzzleCount,ui.level),fontSize=13.sp,color=palette.muted)
        noteText(ui.note)?.let { Text(stringResource(it),fontWeight=FontWeight.Medium,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite }) }
        if(ui.puzzleCount==0) return@Column
        LandingOption(stringResource(R.string.puzzle_continue),stringResource(R.string.puzzle_continue_help),onTraining)
        LandingOption(stringResource(R.string.puzzle_quick),stringResource(R.string.puzzle_quick_help),onQuick)
        LandingOption(stringResource(R.string.puzzle_review),
            if(ui.totals.due>0) pluralStringResource(R.plurals.puzzle_review_due,ui.totals.due,ui.totals.due) else stringResource(R.string.puzzle_review_none),onReview)
        LandingOption(stringResource(R.string.puzzle_theme),stringResource(R.string.puzzle_theme_help)) { themes=!themes }
        if(themes) Column(Modifier.padding(start=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            ThemeGroup.entries.forEach { group -> LandingOption(stringResource(groupName(group))) { onTheme(group) } }
        }
        MenuSection(stringResource(R.string.puzzle_progress))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).card(palette).padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.puzzle_progress_attempted,ui.totals.attempted))
            Text(stringResource(R.string.puzzle_progress_first,ui.totals.solvedFirstTry))
            Text(stringResource(R.string.puzzle_progress_help,ui.totals.solvedWithHelp))
            Text(stringResource(R.string.puzzle_level_note),fontSize=12.sp,color=palette.muted,modifier=Modifier.padding(top=4.dp))
        }
    }
}

/**
 * One puzzle: the status in the header, the board as large as the screen allows, and the typed
 * move over the board. Wrong moves are only reported; the board keeps the puzzle position.
 */
@Composable fun PuzzleBoardScreen(
    ui: PuzzleUi, prefs: AppSettings, draft: NotationDraft,
    onMove: (String) -> Unit, onHint: () -> Unit, onHideHint: () -> Unit, onNext: () -> Unit, onExit: () -> Unit,
    onClearDraft: () -> Unit, onPlayDraft: () -> Unit, cancelDraft: () -> Boolean
) {
    val session=ui.session ?: return
    val res=appResources()
    val flip=!session.solverWhite
    Column(Modifier.fillMaxSize()) {
        GameHeader {
            Column(Modifier.weight(1f).padding(end=4.dp)) {
                val hint=if(ui.hintShown) session.expectedMove?.let { hintMove(session.position,it) }?.let { hintText(res,it) } else null
                val first=when {
                    hint!=null -> stringResource(R.string.hint_line,hint.san,hint.detail)
                    session.state==PuzzleState.SOLVED -> stringResource(R.string.puzzle_solved_detail,session.puzzle.rating)
                    ui.feedback==PuzzleFeedback.WRONG -> stringResource(R.string.puzzle_wrong)
                    ui.feedback==PuzzleFeedback.CORRECT && session.state==PuzzleState.OPPONENT_TO_MOVE -> stringResource(R.string.puzzle_correct)
                    ui.feedback==PuzzleFeedback.CORRECT -> stringResource(R.string.puzzle_correct_continue)
                    else -> stringResource(if(session.solverWhite) R.string.puzzle_white_to_move else R.string.puzzle_black_to_move)
                }
                // Themes only after the solve, so they do not give the idea away; a chosen theme is known.
                val second=when {
                    session.state==PuzzleState.SOLVED -> session.puzzle.group.joinToString(" · ") { res.getString(groupName(it)) }.ifEmpty { null }
                    ui.mode==PuzzleMode.QUICK -> stringResource(R.string.puzzle_mode_quick,ui.quick.size+1,QUICK_SET_SIZE)
                    ui.mode==PuzzleMode.REVIEW -> stringResource(R.string.puzzle_review)
                    ui.mode==PuzzleMode.THEME && ui.group!=null -> stringResource(groupName(ui.group))
                    else -> stringResource(R.string.puzzle_mode_training,ui.level)
                }
                // Two 14 sp lines fit the 56 dp header below font scale 1.25 (measured on the Titan); from
                // that only the first line shows, so a larger font never makes the board smaller.
                val oneLine=LocalDensity.current.fontScale>=1.25f
                Text(if(oneLine) first else listOfNotNull(first,second).joinToString("\n"),fontSize=14.sp,fontWeight=FontWeight.Medium,
                    maxLines=if(oneLine) 1 else 2,overflow=TextOverflow.Ellipsis,
                    modifier=Modifier.semantics {
                        liveRegion=LiveRegionMode.Polite
                        if(hint!=null) contentDescription=hint.spoken
                    })
            }
            if(session.state==PuzzleState.SOLVED) {
                val last=ui.mode==PuzzleMode.QUICK && ui.quick.size>=QUICK_SET_SIZE
                Button(onClick=onNext,modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=12.dp)) {
                    Text(stringResource(if(last) R.string.puzzle_results else R.string.puzzle_next),maxLines=1)
                }
            } else {
                val shown=ui.hintShown
                FilledTonalButton(onClick=if(shown) onHideHint else onHint,enabled=session.state==PuzzleState.SOLVING || shown,
                    modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=10.dp)) {
                    Text(stringResource(if(shown) R.string.hide else R.string.hint),maxLines=1)
                }
            }
            Spacer(Modifier.width(4.dp))
            FilledTonalButton(onClick=onExit,modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=10.dp)) {
                Text(stringResource(R.string.puzzle_exit),maxLines=1)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.TopCenter) {
            val side=minOf(maxWidth-4.dp,maxHeight)
            Box(Modifier.size(side)) {
                ChessBoard(session.position,flip,session.state==PuzzleState.SOLVING,session.lastMove,Modifier.fillMaxSize(),prefs,
                    cancelDraft=cancelDraft,onMove=onMove)
                if(ui.hintShown) session.expectedMove?.let { HintOverlay(it,flip,Modifier.fillMaxSize()) }
                if(draft.text.isNotEmpty()) MoveEntryCard(draft,onClearDraft,onPlayDraft,Modifier.align(Alignment.TopCenter))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The end of a Quick 5 set: counts only, no score. */
@Composable fun QuickSummary(ui: PuzzleUi, onAgain: () -> Unit, onDone: () -> Unit) {
    val firstTry=ui.quick.count { it.result==AttemptResult.FIRST_TRY }
    val afterMistake=ui.quick.count { it.result==AttemptResult.WITH_HELP && !it.hint }
    val hints=ui.quick.count { it.hint }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.quick_done_title),fontFamily=FontFamily.Serif,fontSize=28.sp)
        Text(pluralStringResource(R.plurals.quick_puzzles,ui.quick.size,ui.quick.size),fontWeight=FontWeight.Medium)
        Text(stringResource(R.string.quick_first_try,firstTry))
        Text(stringResource(R.string.quick_after_mistake,afterMistake))
        Text(stringResource(R.string.quick_hints,hints))
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.padding(top=8.dp)) {
            Button(onClick=onAgain) { Text(stringResource(R.string.quick_another)) }
            OutlinedButton(onClick=onDone) { Text(stringResource(R.string.quick_done)) }
        }
    }
}

@androidx.annotation.StringRes fun groupName(group: ThemeGroup): Int = when(group) {
    ThemeGroup.CHECKMATE -> R.string.theme_checkmate
    ThemeGroup.FORKS -> R.string.theme_forks
    ThemeGroup.PINS -> R.string.theme_pins
    ThemeGroup.MOTIFS -> R.string.theme_motifs
    ThemeGroup.ENDGAMES -> R.string.theme_endgames
}

@androidx.annotation.StringRes private fun noteText(note: PuzzleNote): Int? = when(note) {
    PuzzleNote.NONE -> null
    PuzzleNote.NO_REVIEW_DUE -> R.string.note_no_review_due
    PuzzleNote.REVIEW_DONE -> R.string.note_review_done
    PuzzleNote.NO_PUZZLE -> R.string.note_no_puzzle
    PuzzleNote.PACK_MISSING -> R.string.note_pack_missing
}
