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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The Puzzles landing screen: the daily goal, ways to train, progress and the training level. */
@Composable fun PuzzleLanding(
    ui: PuzzleUi, onHome: () -> Unit, onTraining: () -> Unit, onQuick: () -> Unit,
    onReview: () -> Unit, onTheme: (ThemeGroup) -> Unit, onTag: (String) -> Unit = {}, onView: (PuzzleView) -> Unit = {},
    onGoal: (Int) -> Unit = {}
) {
    val palette=LocalPalette.current
    var themes by rememberSaveable { mutableStateOf(false) }
    var single by rememberSaveable { mutableStateOf(false) }
    val scroll=rememberScrollState()
    // A note ("No puzzles are due for review") is at the top: bring it into view.
    LaunchedEffect(ui.note) { if(ui.note!=PuzzleNote.NONE) scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal=24.dp,vertical=16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        TextButton(onClick=onHome,contentPadding=PaddingValues(0.dp)) { Text(stringResource(R.string.home_back)) }
        Text(stringResource(R.string.puzzles),fontFamily=FontFamily.Serif,fontSize=30.sp)
        if(ui.loading) { Text(stringResource(R.string.puzzle_loading)); return@Column }
        Text(stringResource(R.string.puzzles_count,ui.puzzleCount,ui.level),fontSize=13.sp,color=palette.muted)
        noteText(ui.note)?.let { Text(stringResource(it),fontWeight=FontWeight.Medium,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite }) }
        if(ui.puzzleCount==0) return@Column
        GoalCard(ui,onGoal)
        LandingOption(stringResource(R.string.puzzle_continue),stringResource(R.string.puzzle_continue_help),onTraining)
        if(ui.course.isNotEmpty()) {
            val next=remember(ui.course,ui.progress) { nextChapter(ui.course,ui.progress) }
            val done=remember(ui.course,ui.progress) { ui.course.count { chapterStatus(it,ui.progress).done } }
            LandingOption(stringResource(R.string.course_title),
                next?.let { stringResource(R.string.course_progress,done,ui.course.size)+" · "+stringResource(chapterName(it.id)) }
                    ?: stringResource(R.string.course_help)) { onView(PuzzleView.COURSE) }
        }
        LandingOption(stringResource(R.string.puzzle_quick),stringResource(R.string.puzzle_quick_help),onQuick)
        LandingOption(stringResource(R.string.puzzle_review),
            if(ui.totals.due>0) pluralStringResource(R.plurals.puzzle_review_due,ui.totals.due,ui.totals.due) else stringResource(R.string.puzzle_review_none),onReview)
        LandingOption(stringResource(R.string.repeat_title),ui.repeatSet?.let { set ->
            stringResource(R.string.repeat_progress,set.round.index,set.round.results.size,set.ids.size) } ?: stringResource(R.string.repeat_help)) {
            onView(PuzzleView.REPEAT)
        }
        LandingOption(stringResource(R.string.puzzle_theme),stringResource(R.string.puzzle_theme_help)) { themes=!themes }
        if(themes) Column(Modifier.padding(start=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            ThemeGroup.entries.forEach { group -> LandingOption(stringResource(groupName(group))) { onTheme(group) } }
            LandingOption(stringResource(R.string.puzzle_more_themes)) { single=!single }
            if(single) Column(Modifier.padding(start=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                TRAINING_TAGS.forEach { tag -> LandingOption(tagName(tag)?.let { stringResource(it) } ?: tag) { onTag(tag) } }
            }
        }
        MenuSection(stringResource(R.string.puzzle_progress))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).card(palette).padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.puzzle_progress_attempted,ui.totals.attempted))
            Text(stringResource(R.string.puzzle_progress_first,ui.totals.solvedFirstTry))
            Text(stringResource(R.string.puzzle_progress_help,ui.totals.solvedWithHelp))
            Text(stringResource(R.string.puzzle_level_note),fontSize=12.sp,color=palette.muted,modifier=Modifier.padding(top=4.dp))
        }
        LandingOption(stringResource(R.string.progress_title)) { onView(PuzzleView.PROGRESS) }
    }
}

/**
 * One puzzle: the status in the header, the board as large as the screen allows, and the typed
 * move over the board. Wrong moves are only reported; the board keeps the puzzle position.
 */
@Composable fun PuzzleBoardScreen(
    ui: PuzzleUi, prefs: AppSettings, draft: NotationDraft, earned: Set<Sticker>,
    onMove: (String) -> Unit, onHint: () -> Unit, onHideHint: () -> Unit, onNext: () -> Unit, onExit: () -> Unit,
    onClearDraft: () -> Unit, onPlayDraft: () -> Unit, cancelDraft: () -> Boolean, onTogglePreview: () -> Unit = {}
) {
    val session=ui.session ?: return
    val res=appResources()
    val palette=LocalPalette.current
    val flip=!session.solverWhite
    val solved=session.state==PuzzleState.SOLVED
    val nextLabel=when {
        ui.mode==PuzzleMode.QUICK && ui.quick.size>=QUICK_SET_SIZE -> R.string.puzzle_results
        ui.mode==PuzzleMode.COURSE && ui.queue.isEmpty() -> R.string.course_finish
        ui.mode==PuzzleMode.REPEAT && ui.repeatSet?.current==null -> R.string.repeat_finish
        else -> R.string.puzzle_next
    }
    val chapter=if(ui.mode==PuzzleMode.COURSE) ui.currentChapter else null
    val refute=ui.refute?.takeIf { session.state==PuzzleState.SOLVING && it.puzzleId==session.puzzle.id }
    val refuteLine=refute?.let { refuteText(res,it) }
    // The course tip shows until the player has moved, and again whenever no feedback is showing.
    val tip=chapter?.takeIf { ui.feedback==PuzzleFeedback.NONE && refute==null && !solved }?.let { stringResource(chapterTip(it.id)) }
    val preview=ui.preview && refute?.reply!=null
    // Room for a note beside or below the board; without it the explanation takes the header's line.
    var noteRoom by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        GameHeader {
            Column(Modifier.weight(1f).padding(end=4.dp)) {
                val hint=if(ui.hintShown) session.expectedMove?.let { hintMove(session.position,it) }?.let { hintText(res,it) } else null
                val first=when {
                    hint!=null -> stringResource(R.string.hint_line,hint.san,hint.detail)
                    session.state==PuzzleState.SOLVED -> stringResource(R.string.puzzle_solved)
                    ui.feedback==PuzzleFeedback.WRONG -> stringResource(R.string.puzzle_wrong)
                    ui.feedback==PuzzleFeedback.CORRECT && session.state==PuzzleState.OPPONENT_TO_MOVE -> stringResource(R.string.puzzle_correct)
                    ui.feedback==PuzzleFeedback.CORRECT -> stringResource(R.string.puzzle_correct_continue)
                    else -> stringResource(if(session.solverWhite) R.string.puzzle_white_to_move else R.string.puzzle_black_to_move)
                }
                // Themes only after the solve, so they do not give the idea away; a chosen theme is known.
                val second=when {
                    // Without room for the note, the explanation of a wrong move takes the second line.
                    refuteLine!=null && !noteRoom && hint==null -> refuteLine
                    session.state==PuzzleState.SOLVED -> (listOf(res.getString(R.string.puzzle_rating,session.puzzle.rating))+
                        session.puzzle.group.map { res.getString(groupName(it)) }).joinToString(" · ")
                    ui.mode==PuzzleMode.QUICK -> stringResource(R.string.puzzle_mode_quick,ui.quick.size+1,QUICK_SET_SIZE)
                    ui.mode==PuzzleMode.REVIEW -> stringResource(R.string.puzzle_review)
                    chapter!=null -> listOf(stringResource(R.string.course_chapter_n,chapter.number),stringResource(chapterName(chapter.id)),
                        stringResource(R.string.course_round,chapter.ids.indexOf(session.puzzle.id)+1,chapter.ids.size)).joinToString(" · ")
                    ui.mode==PuzzleMode.REPEAT && ui.repeatSet!=null -> ui.repeatSet.round.let { round ->
                        stringResource(R.string.repeat_mode,round.index,minOf(round.results.size+1,ui.repeatSet.ids.size),ui.repeatSet.ids.size) }
                    ui.mode==PuzzleMode.THEME && ui.tag!=null -> tagName(ui.tag)?.let { stringResource(it) } ?: ui.tag
                    ui.mode==PuzzleMode.THEME && ui.group!=null -> stringResource(groupName(ui.group))
                    else -> stringResource(R.string.puzzle_mode_training,ui.level)
                }
                // Two 14 sp lines fit the 56 dp header below font scale 1.25 (measured on the Titan); from
                // that only the first line shows, so a larger font never makes the board smaller.
                val oneLine=LocalDensity.current.fontScale>=1.25f
                // The outcome of a move is the line to notice: solved and wrong stand out from the plain status.
                val outcome=hint==null && (session.state==PuzzleState.SOLVED || ui.feedback==PuzzleFeedback.WRONG)
                val shown=buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight=if(outcome) FontWeight.Bold else null,color=if(outcome) palette.accent else Color.Unspecified)) { append(first) }
                    if(!oneLine && second!=null) append("\n$second")
                }
                Text(shown,fontSize=14.sp,fontWeight=FontWeight.Medium,
                    maxLines=if(oneLine) 1 else 2,overflow=TextOverflow.Ellipsis,
                    modifier=Modifier.semantics {
                        liveRegion=LiveRegionMode.Polite
                        if(hint!=null) contentDescription=hint.spoken
                    })
            }
            if(solved) {
                Button(onClick=onNext,modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=12.dp)) {
                    Text(stringResource(nextLabel),maxLines=1)
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
                if(preview && refute!=null) {
                    // "Show me": the wrong move and the engine's answer, with no moves allowed.
                    val shownPosition=remember(refute) { ChessPosition(refute.base.initialFen,refute.base.moves+refute.wrong+refute.reply!!) }
                    ChessBoard(shownPosition,flip,false,refute.reply,Modifier.fillMaxSize(),prefs,cancelDraft=cancelDraft,onMove={})
                } else ChessBoard(session.position,flip,session.state==PuzzleState.SOLVING,session.lastMove,Modifier.fillMaxSize(),prefs,
                    cancelDraft=cancelDraft,onMove=onMove)
                if(ui.hintShown && !preview) session.expectedMove?.let { HintOverlay(it,flip,Modifier.fillMaxSize()) }
                if(draft.text.isNotEmpty()) MoveEntryCard(draft,onClearDraft,onPlayDraft,Modifier.align(Alignment.TopCenter))
            }
            // The buddy only uses space the square board cannot: beside it on wide screens, below it on
            // tall ones, and nowhere when neither is free (the header still says how the move went).
            val gutter=(maxWidth-side)/2
            val spareBelow=maxHeight-side
            val notePlace=when {
                gutter>=BUDDY_GUTTER_MIN -> Modifier.align(Alignment.TopEnd).padding(end=8.dp).width(gutter-16.dp)
                spareBelow>=NOTE_MIN -> Modifier.align(Alignment.TopCenter).padding(top=side+12.dp).width(minOf(side-16.dp,420.dp))
                else -> null
            }
            SideEffect { noteRoom=notePlace!=null }
            if(notePlace!=null && (refuteLine!=null || tip!=null)) {
                // The explanation of a wrong move, or the chapter's tip, takes the buddy's place for now.
                Column(notePlace.clip(RoundedCornerShape(12.dp)).card(palette).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(refuteLine!=null) {
                        Text(refuteLine,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
                        if(refute?.reply!=null) OutlinedButton(onClick=onTogglePreview,modifier=Modifier.heightIn(min=48.dp)) {
                            Text(stringResource(if(preview) R.string.refute_hide else R.string.refute_show))
                        }
                    } else if(tip!=null) Text(buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight=FontWeight.Bold)) { append(stringResource(R.string.course_tip)) }
                        append(" $tip")
                    },fontSize=14.sp)
                }
            } else if(prefs.playful) {
                val moment=when {
                    ui.hintShown -> BuddyMoment.HINT
                    ui.feedback==PuzzleFeedback.WRONG -> BuddyMoment.WRONG
                    ui.feedback==PuzzleFeedback.CORRECT || session.state==PuzzleState.SOLVED -> BuddyMoment.RIGHT
                    else -> BuddyMoment.THINK
                }
                // The buddy's own line is cheerful but general; the bubble also says the puzzle is done.
                val extra=if(solved) stringResource(R.string.puzzle_solved) else null
                if(gutter>=BUDDY_GUTTER_MIN) BuddyReaction(prefs,earned,moment,Modifier.align(Alignment.TopEnd).padding(end=8.dp).width(gutter-16.dp),extra,large=true)
                else if(spareBelow>=BUDDY_LARGE_BELOW) BuddyReaction(prefs,earned,moment,Modifier.align(Alignment.TopCenter).padding(top=side+8.dp).width(minOf(side,280.dp)),extra,large=true,maxHeight=spareBelow-8.dp)
                else if(spareBelow>=BUDDY_ROW_MIN) BuddyReaction(prefs,earned,moment,Modifier.align(Alignment.TopCenter).padding(top=side+8.dp).width(side),extra)
            } else if(solved) {
                // Without the buddy, the free space says it plainly, with a Next that is easy to reach.
                val place=when {
                    gutter>=BUDDY_GUTTER_MIN -> Modifier.align(Alignment.TopEnd).padding(end=8.dp).width(gutter-16.dp)
                    spareBelow>=SOLVED_CARD_MIN -> Modifier.align(Alignment.TopCenter).padding(top=side+12.dp).width(minOf(side-16.dp,360.dp))
                    else -> null
                }
                if(place!=null) Column(place.clip(RoundedCornerShape(12.dp)).card(palette).padding(16.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.puzzle_solved),fontFamily=FontFamily.Serif,fontSize=24.sp,color=palette.text)
                    Button(onClick=onNext,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                        Text(stringResource(nextLabel))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private val BUDDY_GUTTER_MIN=180.dp
private val BUDDY_ROW_MIN=112.dp
private val BUDDY_LARGE_BELOW=290.dp
private val SOLVED_CARD_MIN=140.dp
private val NOTE_MIN=100.dp

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
