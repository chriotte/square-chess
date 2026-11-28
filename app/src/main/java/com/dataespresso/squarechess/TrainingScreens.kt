package com.dataespresso.squarechess

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The structured training pages: the daily goal card on the landing screen, the course, the
 * summary after a chapter, progress by theme and the repeat set. Plain lists on every display;
 * bars and stars are solid shapes with their values as text for TalkBack, and nothing animates.
 */

/** A page under Puzzles: "‹ Puzzles", a title, then scrolling content. */
@Composable private fun TrainingPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        TextButton(onClick=onBack,contentPadding=PaddingValues(0.dp)) { Text("‹ "+stringResource(R.string.puzzles)) }
        Text(title,fontFamily=FontFamily.Serif,fontSize=30.sp,modifier=Modifier.semantics { heading() })
        content()
    }
}

@Composable private fun SectionTitle(text: String) {
    Text(text,fontSize=18.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=8.dp).semantics { heading() })
}

/** A horizontal bar, 0..1 filled; solid on e-ink. */
@Composable private fun Meter(fraction: Float, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val palette=LocalPalette.current
    Box(modifier.height(height).clip(RoundedCornerShape(height/2))
        .then(if(palette.eink) Modifier.border(1.dp,palette.text,RoundedCornerShape(height/2)) else Modifier.background(palette.inactiveTrack))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f,1f)).background(if(palette.eink) palette.text else palette.accent))
    }
}

/** Three stars, filled up to [count], read as "2 of 3 stars". */
@Composable fun Stars(count: Int, modifier: Modifier = Modifier) {
    val palette=LocalPalette.current
    val label=stringResource(R.string.course_stars,count)
    Row(modifier.clearAndSetSemantics { contentDescription=label }) {
        (1..3).forEach { i ->
            val on=i<=count
            Text(if(on || !palette.eink) "★" else "☆",fontSize=18.sp,
                color=if(on) (if(palette.eink) palette.text else palette.accent) else palette.muted.copy(alpha=if(palette.eink) 1f else 0.45f))
        }
    }
}

/** Selectable chips in a wrapping row; one is chosen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun <T> Choice(options: List<Pair<T,String>>, selected: T, onPick: (T) -> Unit, spoken: (T) -> String? = { null }) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp),
        modifier=Modifier.semantics { selectableGroup() }) {
        options.forEach { (value,label) ->
            val description=spoken(value)
            FilterChip(selected=value==selected,onClick={ onPick(value) },label={ Text(label) },
                modifier=Modifier.semantics { role=Role.RadioButton; if(description!=null) contentDescription=description })
        }
    }
}

/** Today's puzzles against the daily goal, the streak, and the goal itself. */
@Composable fun GoalCard(ui: PuzzleUi, onGoal: (Int) -> Unit) {
    val palette=LocalPalette.current
    val today=ui.days[dayKey(ui.today)] ?: 0
    val goal=ui.goal
    val run=streak(ui.days,goal,ui.today)
    val met=goal>0 && today>=goal
    val headline=when {
        met -> stringResource(R.string.goal_done)
        goal>0 -> stringResource(R.string.goal_today,today,goal)
        else -> stringResource(R.string.goal_today_nogoal,today)
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).card(palette).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(headline,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
            if(run>0) Text(pluralStringResource(R.plurals.goal_streak,run,run),fontSize=13.sp,color=if(palette.eink) palette.text else palette.accent)
        }
        if(goal>0) Meter(today.toFloat()/goal,Modifier.fillMaxWidth().semantics {
            progressBarRangeInfo=ProgressBarRangeInfo(minOf(today,goal).toFloat(),0f..goal.toFloat())
            contentDescription=headline
        })
        Text(stringResource(R.string.goal_label),fontSize=12.sp,color=palette.muted)
        val spoken=PUZZLE_GOALS.associateWith { if(it==0) null else stringResource(R.string.goal_n,it) }
        Choice(PUZZLE_GOALS.map { it to if(it==0) stringResource(R.string.goal_off) else it.toString() },goal,onGoal) { spoken[it] }
    }
}

/** The course: every chapter by section, with stars, and a button for the next chapter. */
@Composable fun CourseScreen(ui: PuzzleUi, onBack: () -> Unit, onChapter: (String) -> Unit) {
    val palette=LocalPalette.current
    val statuses=remember(ui.course,ui.progress) { ui.course.associate { it.id to chapterStatus(it,ui.progress) } }
    val next=remember(ui.course,ui.progress) { nextChapter(ui.course,ui.progress) }
    val done=statuses.values.count { it.done }
    val stars=statuses.values.sumOf { it.stars }
    TrainingPage(stringResource(R.string.course_title),onBack) {
        Text(stringResource(R.string.course_intro))
        next?.let { Button(onClick={ onChapter(it.id) },modifier=Modifier.heightIn(min=48.dp)) { Text(stringResource(R.string.course_continue)) } }
        Text(stringResource(R.string.course_progress,done,ui.course.size)+" · "+stringResource(R.string.course_star_total,stars,ui.course.size*3),color=palette.muted)
        Text(stringResource(R.string.course_stars_help),fontSize=13.sp,color=palette.muted)
        COURSE_SECTIONS.forEach { section ->
            val chapters=ui.course.filter { it.section==section.id }
            if(chapters.isEmpty()) return@forEach
            SectionTitle(stringResource(sectionName(section.id)))
            chapters.forEach { chapter ->
                val status=statuses.getValue(chapter.id)
                val current=chapter==next
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).card(palette)
                    .then(if(current) Modifier.border(2.dp,if(palette.eink) palette.text else palette.accent,RoundedCornerShape(12.dp)) else Modifier)
                    .clickable(role=Role.Button) { onChapter(chapter.id) }.heightIn(min=56.dp).padding(horizontal=14.dp,vertical=10.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Text(chapter.number.toString(),fontWeight=FontWeight.Bold,color=if(status.done) palette.text else palette.muted,
                        modifier=Modifier.width(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(chapterName(chapter.id)),fontWeight=FontWeight.Medium)
                        Text(stringResource(R.string.course_solved,status.solved,status.total),fontSize=12.sp,color=palette.muted)
                    }
                    Stars(status.stars)
                }
            }
        }
    }
}

/** After a pass through a chapter: stars, how many are clean, and what to do next. */
@Composable fun ChapterSummary(ui: PuzzleUi, onChapter: (String) -> Unit, onBack: () -> Unit) {
    val palette=LocalPalette.current
    val done=ui.chapterDone ?: return
    val chapter=ui.course.firstOrNull { it.id==done.chapterId } ?: return
    val status=chapterStatus(chapter,ui.progress)
    val following=ui.course.getOrNull(ui.course.indexOf(chapter)+1)
    val roundClean=done.round.count { it==AttemptResult.FIRST_TRY }
    TrainingPage(stringResource(R.string.course_title),onBack) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).card(palette).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.course_chapter_n,chapter.number),color=palette.muted)
            Text(stringResource(if(status.done) R.string.course_done_title else chapterName(chapter.id)),fontFamily=FontFamily.Serif,fontSize=24.sp,
                modifier=Modifier.semantics { heading(); liveRegion=LiveRegionMode.Polite })
            if(status.done) Stars(status.stars)
            Text(stringResource(R.string.course_solved,status.solved,status.total))
            Text(stringResource(R.string.course_done_text,status.clean,status.total))
            if(done.round.isNotEmpty()) Text(stringResource(R.string.course_round_clean,roundClean,done.round.size),fontSize=13.sp,color=palette.muted)
            if(status.done && following!=null) Button(onClick={ onChapter(following.id) },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text(stringResource(R.string.course_next))
            }
            if(!status.done || status.stars<3) {
                val label=stringResource(if(status.done) R.string.course_retry else R.string.course_keep_going)
                if(!status.done) Button(onClick={ onChapter(chapter.id) },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(label) }
                else OutlinedButton(onClick={ onChapter(chapter.id) },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(label) }
            }
            OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.course_back)) }
        }
    }
}

private fun percent(x: Double): String = "${(x*100).roundToInt()}%"

/** Totals, puzzles per day, where to practise and accuracy for every theme tried. */
@Composable fun ProgressScreen(ui: PuzzleUi, onBack: () -> Unit, onTrain: (String) -> Unit) {
    val palette=LocalPalette.current
    val weakest=remember(ui.stats) { weakestThemes(ui.stats) }
    val tried=remember(ui.stats) { ui.stats.filter { it.attempted>0 }.sortedBy { it.accuracy } }
    val untried=ui.stats.filter { it.attempted==0 }
    val run=streak(ui.days,ui.goal,ui.today)
    val chaptersDone=remember(ui.course,ui.progress) { ui.course.count { chapterStatus(it,ui.progress).done } }
    val res=appResources()
    TrainingPage(stringResource(R.string.progress_title),onBack) {
        val tiles=listOf(
            ui.totals.attempted.toString() to stringResource(R.string.puzzle_progress_attempted,ui.totals.attempted).substringBefore(':').substringBefore('：').trim(),
            (if(ui.totals.attempted>0) percent(ui.totals.solvedFirstTry.toDouble()/ui.totals.attempted) else "—") to stringResource(R.string.progress_first),
            run.toString() to stringResource(R.string.progress_streak),
            "$chaptersDone/${ui.course.size}" to stringResource(R.string.progress_chapters))
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                row.forEach { (value,label) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).card(palette).padding(12.dp).semantics(mergeDescendants=true) {}) {
                        Text(value,fontSize=22.sp,fontWeight=FontWeight.Bold)
                        Text(label,fontSize=12.sp,color=palette.muted)
                    }
                }
            }
        }
        SectionTitle(stringResource(R.string.progress_days))
        DayBars(ui)
        if(ui.goal>0) Text(stringResource(R.string.goal_today,ui.days[dayKey(ui.today)] ?: 0,ui.goal),fontSize=13.sp,color=palette.muted)

        SectionTitle(stringResource(R.string.progress_weakest))
        if(weakest.isEmpty()) Text(stringResource(R.string.progress_weakest_none,MIN_FOR_WEAKEST),color=palette.muted)
        weakest.forEach { stat -> ThemeRow(stat,onTrain,primary=true) }

        if(tried.isNotEmpty()) {
            SectionTitle(stringResource(R.string.progress_title))
            tried.forEach { stat -> ThemeRow(stat,onTrain,primary=false) }
        }
        if(untried.isNotEmpty()) Text(stringResource(R.string.progress_not_tried)+": "+
            untried.joinToString(", ") { s -> tagName(s.tag)?.let { res.getString(it) } ?: s.tag },fontSize=13.sp,color=palette.muted)
    }
}

/** One theme: its name, tries, accuracy as a bar and a number, and Train. Fits a phone's width. */
@Composable private fun ThemeRow(stat: ThemeStat, onTrain: (String) -> Unit, primary: Boolean) {
    val palette=LocalPalette.current
    val name=tagName(stat.tag)?.let { stringResource(it) } ?: stat.tag
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).card(palette).padding(start=14.dp,end=6.dp,top=8.dp,bottom=8.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics(mergeDescendants=true) {},verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(name,fontWeight=FontWeight.Medium)
            Text("${stringResource(R.string.progress_first)}: ${percent(stat.accuracy)} · ${stringResource(R.string.progress_tried)}: ${stat.attempted}",
                fontSize=12.sp,color=palette.muted)
            Meter(stat.accuracy.toFloat(),Modifier.fillMaxWidth(0.9f).clearAndSetSemantics {},height=6.dp)
        }
        val label=stringResource(R.string.progress_train)
        if(primary) Button(onClick={ onTrain(stat.tag) },modifier=Modifier.heightIn(min=48.dp).semantics { contentDescription="$label: $name" }) { Text(label) }
        else TextButton(onClick={ onTrain(stat.tag) },modifier=Modifier.heightIn(min=48.dp).semantics { contentDescription="$label: $name" }) { Text(label) }
    }
}

/** Fourteen days as columns, oldest first; the counts are written above the bars and read aloud. */
@Composable private fun DayBars(ui: PuzzleUi) {
    val palette=LocalPalette.current
    val days=lastDays(ui.days,ui.today,14)
    val top=maxOf(ui.goal,days.maxOf { it.second },1)
    val dateFormat=remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val spoken=days.joinToString(", ") { (day,count) -> "${dateFormat.format(day)}: $count" }
    val chart=96.dp
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription=spoken },
        horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.Bottom) {
        days.forEach { (day,count) ->
            val met=ui.goal>0 && count>=ui.goal
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                Text(if(count>0) count.toString() else "",fontSize=10.sp,maxLines=1,color=palette.muted)
                Box(Modifier.height(chart),contentAlignment=Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().height(chart*(count.toFloat()/top)).clip(RoundedCornerShape(topStart=3.dp,topEnd=3.dp))
                        .background(when {
                            palette.eink -> palette.text
                            met -> palette.accent
                            else -> palette.accent.copy(alpha=0.55f)
                        }))
                }
                Text(day.dayOfWeek.getDisplayName(TextStyle.NARROW,Locale.getDefault()),fontSize=11.sp,color=palette.muted,textAlign=TextAlign.Center)
            }
        }
    }
}

/** A repeat set: make one, play its cycles, and see the time and first-try count of each. */
@Composable fun RepeatScreen(ui: PuzzleUi, onBack: () -> Unit, onMake: (Int,RepeatSource) -> Unit, onStart: () -> Unit, onNew: () -> Unit) {
    val palette=LocalPalette.current
    val set=ui.repeatSet
    TrainingPage(stringResource(R.string.repeat_title),onBack) {
        if(set==null) {
            var size by rememberSaveable { mutableIntStateOf(20) }
            var source by rememberSaveable { mutableStateOf(RepeatSource.NEAR) }
            val hasMistakes=ui.progress.values.any { it.attempts>it.firstTrySolved }
            Text(stringResource(R.string.repeat_intro))
            Text(stringResource(R.string.repeat_size),fontWeight=FontWeight.Medium)
            Choice(REPEAT_SIZES.map { it to it.toString() },size,{ size=it })
            Text(stringResource(R.string.repeat_source),fontWeight=FontWeight.Medium)
            Choice(listOf(RepeatSource.NEAR to stringResource(R.string.repeat_near),RepeatSource.MISTAKES to stringResource(R.string.repeat_mistakes)),source,{ source=it })
            val blocked=source==RepeatSource.MISTAKES && !hasMistakes
            if(blocked) Text(stringResource(R.string.repeat_none_mistakes),fontWeight=FontWeight.Medium,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
            Button(onClick={ onMake(size,source) },enabled=!blocked,modifier=Modifier.heightIn(min=48.dp)) { Text(stringResource(R.string.repeat_start)) }
            return@TrainingPage
        }
        val round=set.round
        ui.cycleDone?.let { done ->
            val previous=set.cycles.getOrNull(set.cycles.indexOfFirst { it.index==done.index }-1)
            val faster=previous?.takeIf { done.ms<it.ms }?.let { " "+stringResource(R.string.repeat_faster,cycleTimeText(it.ms-done.ms)) }.orEmpty()
            Text(stringResource(R.string.repeat_cycle_done,done.index,cycleTimeText(done.ms))+faster,fontWeight=FontWeight.Medium,
                modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).card(palette).padding(16.dp).semantics { liveRegion=LiveRegionMode.Polite })
        }
        val made=remember(set.created) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(java.time.Instant.ofEpochMilli(set.created).atZone(ZoneId.systemDefault()))
        }
        Text(stringResource(R.string.repeat_set_of,set.ids.size,made)+" · "+
            stringResource(if(set.source==RepeatSource.MISTAKES) R.string.repeat_mistakes else R.string.repeat_near))
        Button(onClick=onStart,modifier=Modifier.heightIn(min=48.dp)) {
            Text(stringResource(if(round.results.isNotEmpty()) R.string.repeat_continue else R.string.repeat_begin,round.index))
        }
        if(round.results.isNotEmpty()) Text(stringResource(R.string.repeat_progress,round.index,round.results.size,set.ids.size),color=palette.muted)
        Text(stringResource(R.string.repeat_tip),fontSize=13.sp,color=palette.muted)
        if(set.cycles.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).card(palette).padding(horizontal=16.dp,vertical=8.dp)) {
            @Composable fun Cells(a: String, b: String, c: String, bold: Boolean) {
                Row(Modifier.fillMaxWidth().padding(vertical=6.dp).semantics(mergeDescendants=true) {}) {
                    val weight=if(bold) FontWeight.Medium else FontWeight.Normal
                    Text(a,Modifier.weight(1f),fontWeight=weight)
                    Text(b,Modifier.weight(1f),fontWeight=weight,textAlign=TextAlign.End)
                    Text(c,Modifier.weight(1f),fontWeight=weight,textAlign=TextAlign.End)
                }
            }
            Cells(stringResource(R.string.repeat_cycle),stringResource(R.string.repeat_time),stringResource(R.string.repeat_clean),true)
            HorizontalDivider(color=palette.muted.copy(alpha=0.4f))
            set.cycles.forEach { Cells(it.index.toString(),cycleTimeText(it.ms),"${it.clean}/${it.total}",false) }
        }
        var confirm by rememberSaveable { mutableStateOf(false) }
        OutlinedButton(onClick={ confirm=true },modifier=Modifier.heightIn(min=48.dp)) { Text(stringResource(R.string.repeat_new)) }
        if(confirm) AppAlertDialog(onDismissRequest={ confirm=false },title={ Text(stringResource(R.string.repeat_new)) },
            text={ Text(stringResource(R.string.repeat_new_confirm)) },
            confirmButton={ TextButton(onClick={ confirm=false; onNew() }) { Text(stringResource(R.string.repeat_new)) } },
            dismissButton={ TextButton(onClick={ confirm=false }) { Text(stringResource(R.string.cancel)) } })
    }
}
