package com.dataespresso.squarechess

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay


/** Preset label for a time control that matches no preset; shown as R.string.custom. */
private const val CUSTOM_PRESET = "Custom"

internal val clockStateSaver: Saver<ClockState, Any> = listSaver(
    save = {
        listOf(
            it.config.baseMs,
            it.config.incrementMs,
            it.config.delayMs,
            it.whiteMs,
            it.blackMs,
            it.active.name,
            it.phase.name,
            it.interrupted,
            it.delayRemainingMs
        )
    },
    restore = { saved ->
        val config = ClockConfig(
            saved[0] as Long,
            saved[1] as Long,
            saved[2] as Long
        )
        val savedPhase = ClockPhase.valueOf(saved[6] as String)
        ClockState(
            config = config,
            whiteMs = saved[3] as Long,
            blackMs = saved[4] as Long,
            active = ClockSide.valueOf(saved[5] as String),
            phase = if (savedPhase == ClockPhase.RUNNING) ClockPhase.PAUSED else savedPhase,
            interrupted = (saved[7] as Boolean) || savedPhase == ClockPhase.RUNNING,
            delayRemainingMs = saved[8] as Long
        )
    }
)

@Composable
internal fun SideSelectionButtons(
    selectedWhite: Boolean,
    whiteDescription: String,
    blackDescription: String,
    onWhiteSelected: () -> Unit,
    onBlackSelected: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(LocalPalette.current.card, RoundedCornerShape(14.dp))
            .then(if (LocalPalette.current.eink) Modifier.border(1.5.dp, LocalPalette.current.text, RoundedCornerShape(14.dp)) else Modifier).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(
            Triple(stringResource(R.string.white), true, onWhiteSelected),
            Triple(stringResource(R.string.black), false, onBlackSelected)
        ).forEach { (label, isWhite, onSelect) ->
            val selected = selectedWhite == isWhite
            Box(
                Modifier.weight(1f).heightIn(min=48.dp)
                    .background(if (selected) LocalPalette.current.accent else Color.Transparent, RoundedCornerShape(11.dp))
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = onSelect
                    )
                    .semantics {
                        contentDescription = if (isWhite) whiteDescription else blackDescription
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) LocalPalette.current.onAccent else LocalPalette.current.text,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
internal fun StandaloneClockScreen(
    clock: ClockState,
    startSide: ClockSide,
    onClockChange: (ClockState) -> Unit,
    onStartSideChange: (ClockSide) -> Unit,
    onConfigure: (ClockConfig, ClockSide) -> Unit,
    onBack: () -> Unit,
    // The state holder's current value: after a tap, [clock] updates only at the next frame.
    latestClock: () -> ClockState = { clock }
) {
    var showSetup by rememberSaveable { mutableStateOf(clock.phase == ClockPhase.READY) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var showSetupConfirmation by remember { mutableStateOf(false) }
    var rotated by rememberSaveable { mutableStateOf(false) }
    var presetMenu by remember { mutableStateOf(false) }
    var presetLabel by rememberSaveable { mutableStateOf("5+0") }
    var baseMinutes by rememberSaveable { mutableStateOf("5") }
    var baseSeconds by rememberSaveable { mutableStateOf("0") }
    var incrementSeconds by rememberSaveable { mutableStateOf("0") }
    var delaySeconds by rememberSaveable { mutableStateOf("0") }
    var setupError by remember { mutableStateOf<String?>(null) }
    val view=LocalView.current
    val lifecycleOwner=LocalLifecycleOwner.current
    val currentClock by rememberUpdatedState(latestClock)
    val changeClock by rememberUpdatedState(onClockChange)

    DisposableEffect(lifecycleOwner) {
        val observer=LifecycleEventObserver { _, event ->
            // Read the clock when the event arrives: leaving right after a tap on Resume must still pause it.
            val latest=currentClock()
            if(event==Lifecycle.Event.ON_STOP && latest.phase==ClockPhase.RUNNING) {
                changeClock(latest.pause(SystemClock.elapsedRealtime(),interrupted=true))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(view,clock.phase) {
        val window=view.context.findActivity()?.window
        val wasKeepingScreenOn=window != null &&
            window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        if(clock.phase==ClockPhase.RUNNING) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if(wasKeepingScreenOn==false) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BackHandler(onBack = onBack)
    LaunchedEffect(clock.phase, clock.anchorMs) {
        if (clock.phase == ClockPhase.RUNNING) {
            val remaining = if (clock.active == ClockSide.WHITE) clock.whiteMs else clock.blackMs
            val untilDisplayChange = if (clock.delayRemainingMs > 0) {
                clockTickDelayMs(clock.delayRemainingMs)
            } else {
                clockTickDelayMs(remaining)
            }
            delay(untilDisplayChange.coerceAtLeast(1))
            // No frames are drawn while the app is stopped, so this effect may outlive a pause on
            // ON_STOP. Tick only the clock this effect started with, or it would undo the pause.
            val latest=latestClock()
            if(latest.phase==ClockPhase.RUNNING && latest.anchorMs==clock.anchorMs) {
                onClockChange(latest.settled(SystemClock.elapsedRealtime()))
            }
        }
    }

    fun prepareSetup() {
        val config=clock.config
        baseMinutes=(config.baseMs/60_000).toString()
        baseSeconds=((config.baseMs%60_000)/1_000).toString()
        incrementSeconds=(config.incrementMs/1_000).toString()
        delaySeconds=(config.delayMs/1_000).toString()
        presetLabel=CLOCK_PRESETS.firstOrNull { it.config==config }?.label ?: CUSTOM_PRESET
        setupError=null
    }

    fun openSetup() {
        prepareSetup()
        if (clock.phase == ClockPhase.RUNNING) {
            showSetupConfirmation = true
        } else {
            showSetup = true
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal=12.dp, vertical=8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min=48.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.SpaceBetween
        ) {
            TextButton(onClick=onBack) { Text(stringResource(R.string.home_back)) }
            Text(stringResource(R.string.chess_clock),fontSize=19.sp,fontWeight=FontWeight.SemiBold)
            TextButton(onClick={rotated=!rotated}) { Text(stringResource(if(rotated) R.string.rotate_0 else R.string.rotate_180)) }
        }
        ClockSidePanel(
            side=ClockSide.WHITE,
            clock=clock,
            rotated=rotated,
            modifier=Modifier.weight(1f).fillMaxWidth(),
            onPress={
                when (clock.phase) {
                    ClockPhase.RUNNING -> if (clock.active == ClockSide.WHITE) {
                        onClockChange(clock.switch(ClockSide.WHITE,SystemClock.elapsedRealtime()))
                    }
                    else -> Unit
                }
            }
        )
        HorizontalDivider(color=if(LocalPalette.current.eink) Color.Black else Color(0xFF54615B),thickness=2.dp)
        ClockSidePanel(
            side=ClockSide.BLACK,
            clock=clock,
            rotated=rotated,
            modifier=Modifier.weight(1f).fillMaxWidth(),
            onPress={
                when (clock.phase) {
                    ClockPhase.RUNNING -> if (clock.active == ClockSide.BLACK) {
                        onClockChange(clock.switch(ClockSide.BLACK,SystemClock.elapsedRealtime()))
                    }
                    else -> Unit
                }
            }
        )
        Row(
            Modifier.fillMaxWidth().padding(top=8.dp),
            horizontalArrangement=Arrangement.spacedBy(8.dp)
        ) {
            when (clock.phase) {
                ClockPhase.READY -> Button(
                    onClick={onClockChange(clock.start(SystemClock.elapsedRealtime()))},
                    modifier=Modifier.weight(1f).heightIn(min=52.dp)
                ) { Text(stringResource(R.string.start_clock)) }
                ClockPhase.RUNNING -> Button(
                    onClick={onClockChange(clock.pause(SystemClock.elapsedRealtime()))},
                    modifier=Modifier.weight(1f).heightIn(min=52.dp)
                ) { Text(stringResource(R.string.pause_clock)) }
                ClockPhase.PAUSED -> Button(
                    onClick={onClockChange(clock.resume(SystemClock.elapsedRealtime()))},
                    modifier=Modifier.weight(1f).heightIn(min=52.dp)
                ) { Text(stringResource(R.string.resume_clock)) }
                ClockPhase.FLAGGED -> Text(
                    stringResource(if(clock.active==ClockSide.WHITE) R.string.white_flagged else R.string.black_flagged),
                    modifier=Modifier.weight(1f).padding(12.dp),
                    color=MaterialTheme.colorScheme.error,
                    fontWeight=FontWeight.Bold
                )
                ClockPhase.FINISHED -> Text(stringResource(R.string.clock_finished),modifier=Modifier.weight(1f).padding(12.dp))
            }
            OutlinedButton(
                onClick={showResetConfirmation=true},
                modifier=Modifier.heightIn(min=52.dp)
            ) { Text(stringResource(R.string.reset),maxLines=1) }
            OutlinedButton(
                onClick={openSetup()},
                modifier=Modifier.heightIn(min=52.dp)
            ) { Text(stringResource(R.string.setup),maxLines=1) }
        }
    }

    if (showSetup) AppAlertDialog(
        onDismissRequest={showSetup=false},
        title={Text(stringResource(R.string.setup_title))},
        text={
            Column(
                Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    TextButton(onClick={presetMenu=true}) { Text(stringResource(R.string.preset,if(presetLabel==CUSTOM_PRESET) stringResource(R.string.custom) else presetLabel)) }
                    DropdownMenu(expanded=presetMenu,onDismissRequest={presetMenu=false},border=menuBorder()) {
                        CLOCK_PRESETS.filter { it.config != null }.forEach { preset ->
                            DropdownMenuItem(
                                text={Text(preset.label)},
                                onClick={
                                    val config=requireNotNull(preset.config)
                                    presetLabel=preset.label
                                    baseMinutes=(config.baseMs/60_000).toString()
                                    baseSeconds=((config.baseMs%60_000)/1_000).toString()
                                    incrementSeconds=(config.incrementMs/1_000).toString()
                                    delaySeconds=(config.delayMs/1_000).toString()
                                    setupError=null
                                    presetMenu=false
                                }
                            )
                        }
                    }
                }
                Text(stringResource(R.string.custom_time),fontWeight=FontWeight.SemiBold)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    ClockNumberField(stringResource(R.string.minutes),baseMinutes,{baseMinutes=it},Modifier.weight(1f))
                    ClockNumberField(stringResource(R.string.seconds),baseSeconds,{baseSeconds=it},Modifier.weight(1f))
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    ClockNumberField(stringResource(R.string.increment_seconds),incrementSeconds,{incrementSeconds=it},Modifier.weight(1f))
                    ClockNumberField(stringResource(R.string.delay_seconds),delaySeconds,{delaySeconds=it},Modifier.weight(1f))
                }
                Text(stringResource(R.string.starting_side),fontWeight=FontWeight.SemiBold)
                SideSelectionButtons(
                    selectedWhite=startSide==ClockSide.WHITE,
                    whiteDescription=stringResource(R.string.start_with_white),
                    blackDescription=stringResource(R.string.start_with_black),
                    onWhiteSelected={onStartSideChange(ClockSide.WHITE)},
                    onBlackSelected={onStartSideChange(ClockSide.BLACK)}
                )
                setupError?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=13.sp) }
            }
        },
        confirmButton={val res=appResources(); TextButton(onClick={
            val minutes=baseMinutes.toLongOrNull()
            val seconds=baseSeconds.toLongOrNull()
            val increment=incrementSeconds.toLongOrNull()
            val delay=delaySeconds.toLongOrNull()
            val message=when {
                minutes==null || minutes !in 0..1_440 -> res.getString(R.string.error_minutes)
                seconds==null || seconds !in 0..59 -> res.getString(R.string.error_seconds)
                minutes*60+seconds==0L -> res.getString(R.string.error_zero_time)
                minutes*60+seconds>86_400 -> res.getString(R.string.error_max_time)
                increment==null || increment !in 0..3_600 -> res.getString(R.string.error_increment)
                delay==null || delay !in 0..3_600 -> res.getString(R.string.error_delay)
                else -> null
            }
            if(message!=null) {
                setupError=message
            } else {
                val config=ClockConfig(
                    (minutes!!*60+seconds!!)*1_000,
                    increment!!*1_000,
                    delay!!*1_000
                )
                onConfigure(config,startSide)
                showSetup=false
                setupError=null
            }
        }) { Text(stringResource(R.string.apply_time_control)) }},
        dismissButton={TextButton(onClick={showSetup=false}) { Text(stringResource(R.string.cancel)) }}
    )

    if(showResetConfirmation) AppAlertDialog(
        onDismissRequest={showResetConfirmation=false},
        title={Text(stringResource(R.string.reset_title))},
        text={Text(stringResource(R.string.reset_text,clockText(clock.config.baseMs)))},
        confirmButton={TextButton(onClick={
            onClockChange(ClockState(clock.config,active=startSide))
            showResetConfirmation=false
        }) { Text(stringResource(R.string.reset)) }},
        dismissButton={TextButton(onClick={showResetConfirmation=false}) { Text(stringResource(R.string.keep_clock)) }}
    )

    if(showSetupConfirmation) AppAlertDialog(
        onDismissRequest={showSetupConfirmation=false},
        title={Text(stringResource(R.string.change_title))},
        text={Text(stringResource(R.string.change_text))},
        confirmButton={TextButton(onClick={
            onClockChange(clock.pause(SystemClock.elapsedRealtime()))
            showSetupConfirmation=false
            prepareSetup()
            showSetup=true
        }) { Text(stringResource(R.string.change)) }},
        dismissButton={TextButton(onClick={showSetupConfirmation=false}) { Text(stringResource(R.string.keep_clock)) }}
    )
}

private tailrec fun Context.findActivity(): Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ClockSidePanel(
    side: ClockSide,
    clock: ClockState,
    rotated: Boolean,
    modifier: Modifier,
    onPress: () -> Unit
) {
    val isWhite=side==ClockSide.WHITE
    val active=clock.active==side && clock.phase==ClockPhase.RUNNING
    val remaining=if(isWhite) clock.whiteMs else clock.blackMs
    val palette=LocalPalette.current
    val description=stringResource(if(isWhite) R.string.clock_panel_white else R.string.clock_panel_black)+
        if(active) stringResource(R.string.active_suffix) else ""
    // On e-ink the running clock is shown inverted: white text on black.
    CompositionLocalProvider(LocalContentColor provides if(palette.eink && active) Color.White else LocalContentColor.current) {
    Column(
        modifier.fillMaxWidth()
            .background(if(active) palette.clockPanelActive else palette.clockPanel,RoundedCornerShape(14.dp))
            .then(if(palette.eink) Modifier.border(2.dp,Color.Black,RoundedCornerShape(14.dp)) else Modifier)
            .clickable(enabled=active,onClick=onPress)
            .semantics {
                contentDescription=description
            },
        horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.Center
    ) {
        Column(
            horizontalAlignment=Alignment.CenterHorizontally,
            modifier=Modifier.graphicsLayer { rotationZ=if(rotated) 180f else 0f }
        ) {
            Text(stringResource(if(isWhite) R.string.white else R.string.black),fontSize=18.sp,color=if(palette.eink) LocalContentColor.current else palette.accent)
            Text(clockText(remaining),fontSize=48.sp,fontWeight=FontWeight.Bold,fontFamily=FontFamily.Monospace)
            if(active && clock.delayRemainingMs>0) {
                Text(stringResource(R.string.delay_value,clockText(clock.delayRemainingMs)),fontSize=14.sp)
            }
            when {
                clock.phase==ClockPhase.FLAGGED && clock.active==side -> Text(stringResource(R.string.flag_fallen),color=MaterialTheme.colorScheme.error)
                active -> Text(stringResource(R.string.tap_after_move),fontSize=13.sp)
                clock.phase==ClockPhase.READY && clock.active==side -> Text(stringResource(R.string.ready_to_start),fontSize=13.sp)
                clock.phase==ClockPhase.PAUSED && clock.active==side -> Text(stringResource(R.string.paused),fontSize=13.sp)
            }
        }
    }
    }
}

@Composable
private fun ClockNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value=value,
        onValueChange={ if(it.all(Char::isDigit)) onValueChange(it) },
        label={Text(label)},
        singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
        modifier=modifier
    )
}
