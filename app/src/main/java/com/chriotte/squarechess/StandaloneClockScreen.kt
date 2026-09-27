package com.chriotte.squarechess

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import kotlinx.coroutines.delay

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
        Modifier.fillMaxWidth().background(Color(0xFF26302C), RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(
            Triple("White", true, onWhiteSelected),
            Triple("Black", false, onBlackSelected)
        ).forEach { (label, isWhite, onSelect) ->
            val selected = selectedWhite == isWhite
            Box(
                Modifier.weight(1f).heightIn(min=48.dp)
                    .background(if (selected) Color(0xFFDCC399) else Color.Transparent, RoundedCornerShape(11.dp))
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
                    color = if (selected) Color(0xFF171D1C) else Color(0xFFF3EEDF),
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
    onBack: () -> Unit
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
    val currentClock by rememberUpdatedState(clock)
    val changeClock by rememberUpdatedState(onClockChange)

    DisposableEffect(lifecycleOwner) {
        val observer=LifecycleEventObserver { _, event ->
            if(event==Lifecycle.Event.ON_STOP && currentClock.phase==ClockPhase.RUNNING) {
                changeClock(currentClock.pause(SystemClock.elapsedRealtime(),interrupted=true))
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
            onClockChange(clock.settled(SystemClock.elapsedRealtime()))
        }
    }

    fun prepareSetup() {
        val config=clock.config
        baseMinutes=(config.baseMs/60_000).toString()
        baseSeconds=((config.baseMs%60_000)/1_000).toString()
        incrementSeconds=(config.incrementMs/1_000).toString()
        delaySeconds=(config.delayMs/1_000).toString()
        presetLabel=CLOCK_PRESETS.firstOrNull { it.config==config }?.label ?: "Custom"
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
            TextButton(onClick=onBack) { Text("‹ Home") }
            Text("Chess clock",fontSize=19.sp,fontWeight=FontWeight.SemiBold)
            TextButton(onClick={rotated=!rotated}) { Text(if(rotated) "Rotate 0°" else "Rotate 180°") }
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
        HorizontalDivider(color=Color(0xFF54615B),thickness=2.dp)
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
                ) { Text("Start clock") }
                ClockPhase.RUNNING -> Button(
                    onClick={onClockChange(clock.pause(SystemClock.elapsedRealtime()))},
                    modifier=Modifier.weight(1f).heightIn(min=52.dp)
                ) { Text("Pause clock") }
                ClockPhase.PAUSED -> Button(
                    onClick={onClockChange(clock.resume(SystemClock.elapsedRealtime()))},
                    modifier=Modifier.weight(1f).heightIn(min=52.dp)
                ) { Text("Resume clock") }
                ClockPhase.FLAGGED -> Text(
                    "${if(clock.active==ClockSide.WHITE) "White" else "Black"} flagged",
                    modifier=Modifier.weight(1f).padding(12.dp),
                    color=MaterialTheme.colorScheme.error,
                    fontWeight=FontWeight.Bold
                )
                ClockPhase.FINISHED -> Text("Clock finished",modifier=Modifier.weight(1f).padding(12.dp))
            }
            OutlinedButton(
                onClick={showResetConfirmation=true},
                modifier=Modifier.heightIn(min=52.dp)
            ) { Text("Reset") }
            OutlinedButton(
                onClick={openSetup()},
                modifier=Modifier.heightIn(min=52.dp)
            ) { Text("Setup") }
        }
    }

    if (showSetup) AlertDialog(
        onDismissRequest={showSetup=false},
        title={Text("Set up clock")},
        text={
            Column(
                Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    TextButton(onClick={presetMenu=true}) { Text("Preset · $presetLabel") }
                    DropdownMenu(expanded=presetMenu,onDismissRequest={presetMenu=false}) {
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
                Text("Custom time",fontWeight=FontWeight.SemiBold)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    ClockNumberField("Minutes",baseMinutes,{baseMinutes=it},Modifier.weight(1f))
                    ClockNumberField("Seconds",baseSeconds,{baseSeconds=it},Modifier.weight(1f))
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    ClockNumberField("Increment (s)",incrementSeconds,{incrementSeconds=it},Modifier.weight(1f))
                    ClockNumberField("Delay (s)",delaySeconds,{delaySeconds=it},Modifier.weight(1f))
                }
                Text("Starting side",fontWeight=FontWeight.SemiBold)
                SideSelectionButtons(
                    selectedWhite=startSide==ClockSide.WHITE,
                    whiteDescription="Start with White",
                    blackDescription="Start with Black",
                    onWhiteSelected={onStartSideChange(ClockSide.WHITE)},
                    onBlackSelected={onStartSideChange(ClockSide.BLACK)}
                )
                setupError?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=13.sp) }
            }
        },
        confirmButton={TextButton(onClick={
            val minutes=baseMinutes.toLongOrNull()
            val seconds=baseSeconds.toLongOrNull()
            val increment=incrementSeconds.toLongOrNull()
            val delay=delaySeconds.toLongOrNull()
            val message=when {
                minutes==null || minutes !in 0..1_440 -> "Minutes must be between 0 and 1440."
                seconds==null || seconds !in 0..59 -> "Seconds must be between 0 and 59."
                minutes*60+seconds==0L -> "The starting time must be greater than zero."
                minutes*60+seconds>86_400 -> "The starting time cannot exceed 24 hours."
                increment==null || increment !in 0..3_600 -> "Increment must be between 0 and 3600 seconds."
                delay==null || delay !in 0..3_600 -> "Delay must be between 0 and 3600 seconds."
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
        }) { Text("Apply time control") }},
        dismissButton={TextButton(onClick={showSetup=false}) { Text("Cancel") }}
    )

    if(showResetConfirmation) AlertDialog(
        onDismissRequest={showResetConfirmation=false},
        title={Text("Reset clock?")},
        text={Text("Both clocks will return to ${clockText(clock.config.baseMs)}.")},
        confirmButton={TextButton(onClick={
            onClockChange(ClockState(clock.config,active=startSide))
            showResetConfirmation=false
        }) { Text("Reset") }},
        dismissButton={TextButton(onClick={showResetConfirmation=false}) { Text("Keep clock") }}
    )

    if(showSetupConfirmation) AlertDialog(
        onDismissRequest={showSetupConfirmation=false},
        title={Text("Change time control?")},
        text={Text("The running clock will be stopped and replaced.")},
        confirmButton={TextButton(onClick={
            onClockChange(clock.pause(SystemClock.elapsedRealtime()))
            showSetupConfirmation=false
            prepareSetup()
            showSetup=true
        }) { Text("Change") }},
        dismissButton={TextButton(onClick={showSetupConfirmation=false}) { Text("Keep clock") }}
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
    Column(
        modifier.fillMaxWidth()
            .background(if(active) Color(0xFF35463E) else Color(0xFF222B28),RoundedCornerShape(14.dp))
            .clickable(enabled=active,onClick=onPress)
            .semantics {
                contentDescription="${if(isWhite) "White" else "Black"} clock${if(active) ", active" else ""}"
            },
        horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.Center
    ) {
        Column(
            horizontalAlignment=Alignment.CenterHorizontally,
            modifier=Modifier.graphicsLayer { rotationZ=if(rotated) 180f else 0f }
        ) {
            Text(if(isWhite) "White" else "Black",fontSize=18.sp,color=Color(0xFFDCC399))
            Text(clockText(remaining),fontSize=48.sp,fontWeight=FontWeight.Bold,fontFamily=FontFamily.Monospace)
            if(active && clock.delayRemainingMs>0) {
                Text("Delay ${clockText(clock.delayRemainingMs)}",fontSize=14.sp)
            }
            when {
                clock.phase==ClockPhase.FLAGGED && clock.active==side -> Text("Flag fallen",color=MaterialTheme.colorScheme.error)
                active -> Text("Tap after your move",fontSize=13.sp)
                clock.phase==ClockPhase.READY && clock.active==side -> Text("Ready to start",fontSize=13.sp)
                clock.phase==ClockPhase.PAUSED && clock.active==side -> Text("Paused",fontSize=13.sp)
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
