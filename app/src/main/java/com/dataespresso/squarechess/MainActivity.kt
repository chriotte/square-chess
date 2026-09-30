package com.dataespresso.squarechess

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.FileProvider
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import android.content.Context
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.bhlangonijr.chesslib.Square
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

private val CoordinateInk=Color(0xFF171D1C)
private val CoordinateStyle=TextStyle(
    fontSize=9.sp,lineHeight=10.sp,fontWeight=FontWeight.SemiBold,
    platformStyle=PlatformTextStyle(includeFontPadding=false)
)

@OptIn(ExperimentalLayoutApi::class)
class MainActivity: ComponentActivity() {
    private val vm: GameViewModel by viewModels()
    private var gameVisible=false
    private var modalVisible=false
    private var reviewing=false
    private var blockedNotation=false
    private val consumedKeys=mutableSetOf<Int>()
    private var draft by mutableStateOf(NotationDraft())
    private var entryPromotions by mutableStateOf<List<String>>(emptyList())
    private var flip by mutableStateOf(false)
    private val settingsStore by lazy { SettingsStore(this) }
    private var settings by mutableStateOf(AppSettings())
    private val sounds by lazy { MoveSounds() }
    // Android 10–12: apply the language chosen in Settings (Android 13+ does this itself).
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(LanguageSetting.wrap(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings=settingsStore.load()
        // The dark window background would flash on e-paper before the first frame.
        if(settings.eink) window.decorView.setBackgroundColor(android.graphics.Color.WHITE)
        enableEdgeToEdge()
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemBars()
        setContent {
            SquareChessTheme(eink=settings.eink) {
                val palette=LocalPalette.current
                val s by vm.state.collectAsState()
                val prefs=settings
                val haptics=LocalHapticFeedback.current
                // Sound and vibration only for a move just added to the same game, never on load or undo.
                var soundKey by remember { mutableStateOf<Pair<String?,Int>?>(null) }
                LaunchedEffect(s.game?.id,s.position.moves.size) {
                    val key=s.game?.id to s.position.moves.size
                    val previous=soundKey; soundKey=key
                    if(previous!=null && previous.first==key.first && key.second==previous.second+1) {
                        val kind=lastMoveSound(s.position)
                        if(kind!=null && prefs.sound) sounds.play(kind)
                        if(prefs.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
                var screen by rememberSaveable { mutableStateOf("home") }
                var dialog by rememberSaveable { mutableStateOf("") }
                var resultDialog by remember { mutableStateOf<ResultEvent?>(null) }
                var selectedMode by rememberSaveable { mutableStateOf(GameMode.COMPUTER) }
                var level by rememberSaveable { mutableIntStateOf(DEFAULT_LEVEL) }
                var white by rememberSaveable { mutableStateOf(true) }
                var allowHints by rememberSaveable { mutableStateOf(false) }
                var standaloneClock by rememberSaveable(stateSaver=clockStateSaver) {
                    mutableStateOf(ClockState(ClockConfig(300_000,0)))
                }
                var standaloneStartSide by rememberSaveable { mutableStateOf(ClockSide.WHITE) }
                var clockPreset by rememberSaveable { mutableStateOf("Untimed") }
                var clockPresetMenu by remember { mutableStateOf(false) }
                var importedFen by rememberSaveable { mutableStateOf<String?>(null) }
                var moreSetupOptions by rememberSaveable { mutableStateOf(false) }
                var fenImportError by remember { mutableStateOf<String?>(null) }
                var fenImporting by remember { mutableStateOf(false) }
                val coroutineScope=rememberCoroutineScope()
                val fenPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if(uri!=null) {
                        importedFen=null
                        fenImportError=null
                        fenImporting=true
                        coroutineScope.launch {
                            try {
                                importedFen=loadFenDocument(uri)
                            } catch(e: IOException) {
                                fenImportError=getString(R.string.file_read_failed,e.message ?: getString(R.string.io_error))
                            } catch(e: SecurityException) {
                                fenImportError=getString(R.string.file_permission_denied)
                            } catch(e: IllegalArgumentException) {
                                fenImportError=e.message?.let { "${getString(R.string.fen_invalid)}\n$it" } ?: getString(R.string.fen_invalid)
                            } finally {
                                fenImporting=false
                            }
                        }
                    }
                }
                var reviewPly by rememberSaveable(s.game?.id) { mutableStateOf<Int?>(null) }
                var pendingDelete by remember { mutableStateOf<SavedGame?>(null) }
                var reviewBarBelow by remember { mutableStateOf(false) }
                var exportMode by rememberSaveable { mutableStateOf(ExportModeFilter.ALL) }
                var exportPeriod by rememberSaveable { mutableStateOf(ExportPeriod.ALL_TIME) }
                var exportMessage by remember { mutableStateOf<String?>(null) }
                val exportSaver=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-chess-pgn")) { uri ->
                    if(uri!=null) coroutineScope.launch {
                        exportMessage=try {
                            val count=writeExport(uri,exportMode,exportPeriod)
                            resources.getQuantityString(R.plurals.export_saved,count,count)
                        } catch(e: Exception) { getString(R.string.export_failed,e.message ?: getString(R.string.io_error)) }
                    }
                }
                var importMessage by remember { mutableStateOf<String?>(null) }
                var importing by remember { mutableStateOf(false) }
                val importPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if(uri!=null) coroutineScope.launch {
                        importing=true
                        importMessage=try {
                            vm.importGames(readTextDocument(uri,MAX_PGN_IMPORT_BYTES)).let { importSummaryText(resources,it.imported,it.duplicates,it.failures) }
                        } catch(e: Exception) { getString(R.string.import_failed,e.message ?: getString(R.string.io_error)) }
                        finally { importing=false }
                    }
                }
                fun openNewGame(mode: GameMode) {
                    selectedMode=mode
                    allowHints=false
                    moreSetupOptions=false
                    importedFen=null
                    fenImportError=null
                    fenImporting=false
                    dialog="new"
                }
                LaunchedEffect(screen,s.game?.id) { if(screen=="game") flip=defaultFlipFor(s.game) }
                LaunchedEffect(s.resultEvent) { s.resultEvent?.let { resultDialog=it } }
                LaunchedEffect(screen, s.positionKey(), s.busy, dialog, resultDialog) {
                    if(screen!="game" || !s.canEnterMove() || dialog.isNotEmpty() || resultDialog!=null ||
                        (draft.positionKey!=null && draft.positionKey!=s.positionKey())) {
                        draft=NotationDraft(); entryPromotions=emptyList()
                    }
                }
                gameVisible=screen=="game"; reviewing=reviewPly!=null
                modalVisible=dialog.isNotEmpty() || resultDialog!=null || entryPromotions.isNotEmpty()
                BackHandler(draft.text.isNotEmpty()) { draft=NotationDraft() }
                Surface(Modifier.fillMaxSize(),color=palette.background) {
                    // Home/history keep a conventional cutout inset. The game header
                    // handles the actual camera rectangle, using the space beside it.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.displayCutout.only(
                                if (screen == "game") WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                                else WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical
                            ))
                            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                            .imePadding()
                    ) {
                        when(screen) {
                            "home" -> Column(Modifier.fillMaxSize().padding(horizontal=24.dp)) {
                              Column(
                                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                                    .padding(top=24.dp,bottom=12.dp),
                                verticalArrangement=Arrangement.spacedBy(12.dp)
                              ) {
                                Row(Modifier.padding(bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Image(painterResource(R.drawable.ic_square_chess),contentDescription=null,
                                        modifier=Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(R.string.app_name_title),color=palette.text,fontSize=24.sp,fontWeight=FontWeight.Medium)
                                }
                                if(s.game!=null) Button(
                                    onClick={ flip=defaultFlipFor(s.game); reviewPly=null; screen="game"; vm.foreground() },
                                    modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),
                                    shape=RoundedCornerShape(12.dp),
                                    colors=ButtonDefaults.buttonColors(containerColor=palette.accent,contentColor=palette.onAccent)
                                ) { Text(stringResource(if(s.game?.result=="*") R.string.continue_game else R.string.view_last_game),fontSize=17.sp) }
                                LandingOption(stringResource(R.string.play_against_computer)) { openNewGame(GameMode.COMPUTER) }
                                LandingOption(stringResource(R.string.over_the_board)) { openNewGame(GameMode.LOCAL_TWO_PLAYER) }
                                LandingOption(stringResource(R.string.record_physical_game)) { openNewGame(GameMode.PHYSICAL_BOARD_RECORDING) }
                                LandingOption(
                                    stringResource(R.string.chess_clock),
                                    stringResource(R.string.chess_clock_subtitle)
                                ) {
                                    screen="standaloneClock"
                                }
                              }
                                Row(Modifier.fillMaxWidth().padding(top=4.dp,bottom=16.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                                    TextButton(onClick={screen="history"}) { Text(stringResource(R.string.game_history)) }
                                    TextButton(onClick={screen="settings"}) { Text(stringResource(R.string.settings_title)) }
                                    TextButton(onClick={screen="help";vm.pauseForNavigation()}) { Text(stringResource(R.string.help_title)) }
                                }
                            }
                            "history" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                val history by vm.history.collectAsState(initial=emptyList())
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                    TextButton(onClick={screen="home"}) { Text(stringResource(R.string.home_back)) }
                                    Spacer(Modifier.weight(1f))
                                    TextButton(enabled=!importing,onClick={importPicker.launch(arrayOf("*/*"))}) { Text(stringResource(if(importing) R.string.importing else R.string.import_action)) }
                                    if(history.isNotEmpty()) TextButton(onClick={dialog="export"}) { Text(stringResource(R.string.export_action)) }
                                }
                                Text(stringResource(R.string.your_games),fontFamily=FontFamily.Serif,fontSize=30.sp)
                                if(history.isEmpty()) Text(stringResource(R.string.first_game_hint))
                                else Text(stringResource(R.string.long_press_delete),fontSize=12.sp,color=LocalPalette.current.muted,modifier=Modifier.padding(bottom=8.dp))
                                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    history.forEach { g -> HistoryRow(g,
                                        onOpen={ flip=defaultFlipFor(g); reviewPly=null; vm.resume(g); screen="game" },
                                        onDelete={ pendingDelete=g }) }
                                }
                            }
                            "settings" -> SettingsScreen(settings,onChange={ settings=it; settingsStore.save(it) },onExit={screen="home"},
                                language=remember { LanguageSetting.chosen(this@MainActivity) },
                                onLanguage={ LanguageSetting.set(this@MainActivity,it) })
                            "help" -> HelpAboutScreen(s.game?.mode,onExit={screen="home"})
                            "standaloneClock" -> StandaloneClockScreen(
                                clock=standaloneClock,
                                startSide=standaloneStartSide,
                                onClockChange={standaloneClock=it},
                                onStartSideChange={standaloneStartSide=it},
                                onConfigure={config,side->
                                    standaloneStartSide=side
                                    standaloneClock=ClockState(config,active=side)
                                },
                                onBack={screen="home"},
                                latestClock={standaloneClock}
                            )
                            else -> Column(Modifier.fillMaxSize()) {
                                val san=remember(s.position) { s.position.san }
                                val fenFields=s.position.initialFen.split(" ")
                                val whiteFirst=fenFields.getOrNull(1)!="b"
                                val firstMoveNumber=fenFields.getOrNull(5)?.toIntOrNull() ?: 1
                                var moveStripShown by remember { mutableStateOf(false) }
                                val stepReview={ ply: Int -> reviewPly=ply.coerceIn(0,s.position.moves.size) }
                                GameHeader {
                                    GameToolbar(s,reviewPly,
                                        clock=s.clock,
                                        onReview={ beginReview();draft=NotationDraft();reviewPly=s.position.moves.size;vm.pauseForNavigation() },
                                        onReturn={ endReview();reviewPly=null;vm.foreground() },
                                        onUndo={dialog="undo"},onMenu={dialog="menu"},
                                        onPauseClock={vm.pauseClock()},onResumeClock={vm.resumeClock()},
                                        onClockExpired={vm.clockExpired()},
                                        reviewNav=if(reviewBarBelow) null else HeaderReviewNav(
                                            onFirst={stepReview(0)},onPrevious={stepReview((reviewPly ?: 0)-1)},
                                            onNext={stepReview((reviewPly ?: 0)+1)},onLast={stepReview(s.position.moves.size)}),
                                        lastMove=if(moveStripShown) null else lastMoveText(san,firstMoveNumber,whiteFirst),
                                        onHint={vm.requestHint()},onHideHint={vm.clearHint()})
                                }
                                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.TopCenter) {
                                    // The board size is decided first and never depends on the optional
                                    // rows: they only use height or width the square board cannot use.
                                    val side=minOf(maxWidth-4.dp,maxHeight)
                                    val spareBelow=maxHeight-side
                                    val gutter=(maxWidth-side)/2
                                    val barBelow=spareBelow>=REVIEW_BAR_HEIGHT
                                    val stripBelow=spareBelow-(if(reviewPly!=null && barBelow) REVIEW_BAR_HEIGHT else 0.dp)>=MOVE_STRIP_HEIGHT
                                    val capturesInGutters=gutter>=CAPTURED_GUTTER_MIN
                                    val capturesBelow=!capturesInGutters && spareBelow-(if(reviewPly!=null && barBelow) REVIEW_BAR_HEIGHT else 0.dp)-
                                        (if(stripBelow) MOVE_STRIP_HEIGHT else 0.dp)>=CAPTURED_ROW_HEIGHT*2
                                    SideEffect { reviewBarBelow=barBelow; moveStripShown=stripBelow }
                                    val display=remember(s,reviewPly) {
                                        reviewPly?.let { s.copy(position=ChessPosition(s.position.initialFen,s.position.moves.take(it)),busy=true,highlightMove=null) } ?: s
                                    }
                                    val taken=remember(display.position) { captures(display.position) }
                                    val topWhite=flip
                                    fun takenBy(white: Boolean)=if(white) taken.byWhite else taken.byBlack
                                    fun leadOf(white: Boolean)=if(white) taken.whiteLead else -taken.whiteLead
                                    if(capturesInGutters) {
                                        CapturedColumn(takenBy(topWhite),leadOf(topWhite),stringResource(if(topWhite) R.string.white else R.string.black),gutter,fromBottom=false,
                                            modifier=Modifier.align(Alignment.TopStart).height(side))
                                        CapturedColumn(takenBy(!topWhite),leadOf(!topWhite),stringResource(if(topWhite) R.string.black else R.string.white),gutter,fromBottom=true,
                                            modifier=Modifier.align(Alignment.TopEnd).height(side))
                                    }
                                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                    if(capturesBelow) CapturedRow(takenBy(topWhite),leadOf(topWhite),stringResource(if(topWhite) R.string.white else R.string.black),Modifier.width(side))
                                    Box(Modifier.size(side).then(if(reviewPly!=null) Modifier.pointerInput(s.position.moves.size) {
                                        // Swipe the board to step through the game while reviewing.
                                        var travelled=0f
                                        detectHorizontalDragGestures(onDragStart={travelled=0f},onDragEnd={
                                            val threshold=size.width/8f
                                            if(travelled<=-threshold) stepReview((reviewPly ?: 0)+1)
                                            else if(travelled>=threshold) stepReview((reviewPly ?: 0)-1)
                                        }) { change,amount -> change.consume(); travelled+=amount }
                                    } else Modifier)) {
                                    ChessBoard(display,flip,Modifier.fillMaxSize(),prefs, cancelDraft={
                                        if(draft.text.isNotEmpty()) { draft=NotationDraft(); true } else false
                                    }) { move -> vm.enter(move) }
                                    // Drawn over the board but without pointer input, so moves still reach it.
                                    if(reviewPly==null) s.visibleHint()?.let { hint ->
                                        HintOverlay(hint.move,flip,Modifier.fillMaxSize())
                                    }
                                    if(draft.text.isNotEmpty()) Surface(
                                        modifier=Modifier.align(Alignment.TopCenter).padding(8.dp),
                                        shape=RoundedCornerShape(12.dp), tonalElevation=8.dp
                                    ) {
                                        Row(Modifier.padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f).padding(8.dp)) {
                                                val entryDescription=stringResource(R.string.move_entry_description,draft.text)
                                                Text(stringResource(R.string.move_entry,draft.text),fontFamily=FontFamily.Monospace,
                                                    modifier=Modifier.semantics { contentDescription=entryDescription })
                                                draft.error?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp) }
                                            }
                                            TextButton(onClick={draft=NotationDraft()}) { Text(stringResource(R.string.clear)) }
                                            TextButton(enabled=!draft.submitting,onClick={submitDraft()}) { Text(stringResource(R.string.play)) }
                                        }
                                    }
                                    }
                                    if(capturesBelow) CapturedRow(takenBy(!topWhite),leadOf(!topWhite),stringResource(if(topWhite) R.string.black else R.string.white),Modifier.width(side))
                                    if(reviewPly!=null && barBelow) ReviewBar(reviewPly!!,s.position.moves.size,
                                        onFirst={stepReview(0)},onPrevious={stepReview(reviewPly!!-1)},onMoves={dialog="history"},
                                        onNext={stepReview(reviewPly!!+1)},onLast={stepReview(s.position.moves.size)},modifier=Modifier.width(side))
                                    if(stripBelow) MoveStrip(san,firstMoveNumber,whiteFirst,reviewPly,onOpen={dialog="history"},modifier=Modifier.width(side))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
                if(dialog=="new") AlertDialog(onDismissRequest={dialog=""},title={Text(when(selectedMode){GameMode.COMPUTER->stringResource(R.string.play_against_computer); GameMode.LOCAL_TWO_PLAYER->stringResource(R.string.over_the_board); else->stringResource(R.string.record_physical_game)})},text={
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if(selectedMode==GameMode.COMPUTER) {
                            val shownLevel=level.coerceIn(1,ENGINE_LEVELS.size)
                            val difficultyDescription=stringResource(R.string.difficulty)
                            Text("${levelLabel(shownLevel)} · ${stringResource(engineLevel(shownLevel).description)}")
                            Slider(value=shownLevel.toFloat(),onValueChange={level=it.roundToInt()},valueRange=1f..10f,steps=8,
                                colors=SliderDefaults.colors(inactiveTrackColor=palette.inactiveTrack),
                                modifier=Modifier.semantics { contentDescription=difficultyDescription })
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.easier),fontSize=12.sp,color=LocalPalette.current.muted)
                                Text(stringResource(R.string.stronger),fontSize=12.sp,color=LocalPalette.current.muted)
                            }
                            Text(stringResource(R.string.choose_side),fontWeight=FontWeight.SemiBold)
                            SideSelectionButtons(
                                selectedWhite=white,
                                whiteDescription=stringResource(R.string.play_as_white),
                                blackDescription=stringResource(R.string.play_as_black),
                                onWhiteSelected={white=true},
                                onBlackSelected={white=false}
                            )
                            Row(Modifier.fillMaxWidth().padding(top=8.dp).clip(RoundedCornerShape(8.dp))
                                .toggleable(value=allowHints,role=Role.Checkbox,onValueChange={allowHints=it})
                                .padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(checked=allowHints,onCheckedChange=null)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(stringResource(R.string.allow_hints))
                                    Text(stringResource(R.string.allow_hints_help),fontSize=12.sp,color=LocalPalette.current.muted)
                                }
                            }
                        } else Text(stringResource(if(selectedMode==GameMode.PHYSICAL_BOARD_RECORDING) R.string.recording_help else R.string.over_board_help))
                        Spacer(Modifier.height(8.dp))
                        if(selectedMode!=GameMode.PHYSICAL_BOARD_RECORDING) Box {
                            val presetName=presetText(clockPreset)
                            val presetDescription=stringResource(R.string.time_control_description,presetName)
                            TextButton(onClick={clockPresetMenu=true},modifier=Modifier.semantics { contentDescription=presetDescription }) {
                                Text(stringResource(R.string.time_control,presetName))
                            }
                            DropdownMenu(expanded=clockPresetMenu,onDismissRequest={clockPresetMenu=false}) {
                                CLOCK_PRESETS.forEach { preset ->
                                    DropdownMenuItem(
                                        text={Text(presetText(preset.label))},
                                        onClick={clockPreset=preset.label;clockPresetMenu=false}
                                    )
                                }
                            }
                        }
                        TextButton(onClick={moreSetupOptions=!moreSetupOptions}) {
                            Text(stringResource(if(moreSetupOptions) R.string.fewer_options else R.string.more_options))
                        }
                        if(moreSetupOptions) {
                            TextButton(onClick={fenPicker.launch(arrayOf("*/*"))},enabled=!fenImporting) {
                                Text(stringResource(if(importedFen==null) R.string.import_position else R.string.replace_position))
                            }
                            Text(stringResource(R.string.import_position_help),fontSize=13.sp)
                        }
                        if(fenImporting) Text(stringResource(R.string.importing_position))
                        if(importedFen!=null) Text(stringResource(R.string.starting_from_imported),fontSize=13.sp,fontWeight=FontWeight.Medium)
                        fenImportError?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp) }
                        if(importedFen!=null || fenImportError!=null) {
                            TextButton(onClick={importedFen=null;fenImportError=null}) { Text(stringResource(R.string.use_standard_position)) }
                        }
                    }
                },confirmButton={TextButton(enabled=!fenImporting && fenImportError==null,onClick={
                    val selectedClockPreset=CLOCK_PRESETS.firstOrNull { it.label==clockPreset }
                        ?: error("Unknown time control: $clockPreset")
                    val clockConfig=if(selectedMode==GameMode.PHYSICAL_BOARD_RECORDING) null else selectedClockPreset.config
                    flip=selectedMode==GameMode.COMPUTER && !white
                    vm.newGame(selectedMode,level.coerceIn(1,ENGINE_LEVELS.size),white,clockConfig,importedFen ?: START_FEN,
                        hintsEnabled=selectedMode==GameMode.COMPUTER && allowHints)
                    importedFen=null;fenImportError=null
                    screen="game";draft=NotationDraft();dialog=""
                }) {Text(stringResource(R.string.start_game))}},dismissButton={TextButton(onClick={dialog=""}){Text(stringResource(R.string.cancel))}})
                if(dialog=="menu") AlertDialog(onDismissRequest={dialog=""},title={Text(stringResource(R.string.at_the_board))},text={Column(Modifier.verticalScroll(rememberScrollState())){
                    val computerGame=s.game?.mode==GameMode.COMPUTER.name
                    if(s.game?.result=="*" && reviewPly==null) {
                        MenuSection(stringResource(R.string.section_game))
                        if(s.hintsAvailable()) {
                            if(s.visibleHint()!=null) TextButton(onClick={vm.clearHint();dialog=""}){Text(stringResource(R.string.hide_hint))}
                            else TextButton(enabled=s.canEnterMove() && !s.hintBusy,onClick={vm.requestHint();dialog=""}){Text(stringResource(R.string.show_hint))}
                        }
                        if(s.position.moves.isNotEmpty()) TextButton(enabled=!s.busy,onClick={dialog="undo"}){Text(stringResource(R.string.undo_take_back))}
                        if(s.position.canClaimDraw()) TextButton(onClick={vm.end("1/2-1/2","Draw claimed");dialog=""}){Text(stringResource(R.string.claim_draw))}
                        if(computerGame) TextButton(onClick={dialog="resign"}){Text(stringResource(R.string.resign))}
                        else TextButton(onClick={dialog="end"}){Text(stringResource(R.string.end_game))}
                        if(computerGame && s.engineError!=null) TextButton(onClick={vm.maybeEngine();dialog=""}){Text(stringResource(R.string.retry_engine))}
                    }
                    MenuSection(stringResource(R.string.section_view))
                    if(reviewPly==null) TextButton(enabled=!s.busy,onClick={beginReview();draft=NotationDraft();reviewPly=s.position.moves.size;vm.pauseForNavigation();dialog=""}) { Text(stringResource(R.string.review_game)) }
                    else TextButton(onClick={endReview();reviewPly=null;vm.foreground();dialog=""}) { Text(stringResource(R.string.return_to_game)) }
                    TextButton(onClick={dialog="history"}){Text(stringResource(R.string.move_list))}
                    TextButton(onClick={flip=!flip;vm.setOrientation(flip);dialog=""}){Text(stringResource(R.string.flip_board))}
                    s.game?.let { game ->
                        MenuSection(stringResource(R.string.section_share))
                        TextButton(onClick={
                            val shown=reviewPly?.let { ChessPosition(s.position.initialFen,s.position.moves.take(it)) } ?: s.position
                            startActivity(Intent.createChooser(fenShareIntent(shown),getString(R.string.share_fen_chooser)))
                            dialog=""
                        }) { Text(stringResource(R.string.share_fen)) }
                        TextButton(onClick={
                            startActivity(Intent.createChooser(pgnShareIntent(game),getString(R.string.share_pgn_chooser)))
                            dialog=""
                        }) { Text(stringResource(R.string.share_pgn)) }
                    }
                    HorizontalDivider(Modifier.padding(vertical=4.dp))
                    TextButton(onClick={screen="home";dialog="";vm.pauseForNavigation()}){Text(stringResource(R.string.save_home))}
                }},confirmButton={TextButton(onClick={dialog=""}){Text(stringResource(R.string.back_to_board))}})
                if(dialog=="resign") AlertDialog(onDismissRequest={dialog=""},title={Text(stringResource(R.string.resign_title))},
                    text={Text(stringResource(R.string.resign_text))},
                    confirmButton={TextButton(onClick={
                        vm.end(if(s.game?.humanWhite==true) "0-1" else "1-0","You resigned");dialog=""
                    }){Text(stringResource(R.string.resign))}},
                    dismissButton={TextButton(onClick={dialog=""}){Text(stringResource(R.string.keep_playing))}})
                pendingDelete?.let { game ->
                    AlertDialog(onDismissRequest={pendingDelete=null},title={Text(stringResource(R.string.delete_title))},
                        text={Text(stringResource(R.string.delete_text,displayName(resources,game.white),displayName(resources,game.black),historyDate(game.updated)))},
                        confirmButton={TextButton(onClick={vm.delete(game);pendingDelete=null}){Text(stringResource(R.string.delete))}},
                        dismissButton={TextButton(onClick={pendingDelete=null}){Text(stringResource(R.string.cancel))}})
                }
                if(dialog=="export") {
                    var count by remember { mutableStateOf<Int?>(null) }
                    LaunchedEffect(exportMode,exportPeriod) { count=vm.gamesForExport(exportMode,exportPeriod).size }
                    AlertDialog(onDismissRequest={dialog=""},title={Text(stringResource(R.string.export_title))},text={Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(stringResource(R.string.export_text),fontSize=13.sp)
                        MenuSection(stringResource(R.string.section_games))
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            ExportModeFilter.entries.forEach { option ->
                                FilterChip(selected=exportMode==option,onClick={exportMode=option},label={Text(stringResource(option.label))})
                            }
                        }
                        MenuSection(stringResource(R.string.section_period))
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            ExportPeriod.entries.forEach { option ->
                                FilterChip(selected=exportPeriod==option,onClick={exportPeriod=option},label={Text(stringResource(option.label))})
                            }
                        }
                        Text(when(val n=count) { null -> stringResource(R.string.counting_games); 0 -> stringResource(R.string.no_games_match); else -> pluralStringResource(R.plurals.games_selected,n,n) },
                            fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=8.dp))
                    }},confirmButton={TextButton(enabled=(count ?: 0)>0,onClick={dialog="";exportSaver.launch(exportFileName())}){Text(stringResource(R.string.save_file))}},
                    dismissButton={Row {
                        TextButton(enabled=(count ?: 0)>0,onClick={
                            dialog=""
                            coroutineScope.launch {
                                try { shareExport(exportMode,exportPeriod) }
                                catch(e: Exception) { exportMessage=getString(R.string.export_failed,e.message ?: getString(R.string.io_error)) }
                            }
                        }){Text(stringResource(R.string.share))}
                        TextButton(onClick={dialog=""}){Text(stringResource(R.string.cancel))}
                    }})
                }
                importMessage?.let { message ->
                    AlertDialog(onDismissRequest={importMessage=null},title={Text(stringResource(R.string.import_title))},
                        text={Text(message,modifier=Modifier.verticalScroll(rememberScrollState()))},
                        confirmButton={TextButton(onClick={importMessage=null}){Text(stringResource(R.string.ok))}})
                }
                exportMessage?.let { message ->
                    AlertDialog(onDismissRequest={exportMessage=null},title={Text(stringResource(R.string.export_action))},text={Text(message)},
                        confirmButton={TextButton(onClick={exportMessage=null}){Text(stringResource(R.string.ok))}})
                }
                if(dialog=="undo") AlertDialog(onDismissRequest={dialog=""},title={Text(stringResource(R.string.undo_title))},text={Text(stringResource(R.string.undo_text))},confirmButton={TextButton(onClick={vm.undo();dialog=""}){Text(stringResource(R.string.take_back))}},dismissButton={TextButton(onClick={dialog=""}){Text(stringResource(R.string.keep_playing))}})
                if(dialog=="end") AlertDialog(onDismissRequest={dialog=""},title={Text(stringResource(R.string.end_title))},text={Column{ Text(stringResource(R.string.end_text)); listOf(R.string.white_wins to "1-0",R.string.black_wins to "0-1",R.string.draw to "1/2-1/2").forEach{(name,result)->TextButton(onClick={vm.end(result,"Result recorded by the players");dialog=""}){Text(stringResource(name))}} }},confirmButton={TextButton(onClick={dialog=""}){Text(stringResource(R.string.cancel))}})
                if(dialog=="history") AlertDialog(onDismissRequest={dialog=""},title={Text(stringResource(R.string.moves_title))},text={Column(Modifier.verticalScroll(rememberScrollState())){if(s.position.moves.isEmpty()) Text(stringResource(R.string.no_moves)) else s.position.san.chunked(2).forEachIndexed { i,pair-> Text("${i+1}.  ${pair.joinToString("    ")}",fontFamily=FontFamily.Monospace,modifier=Modifier.padding(4.dp)) }}},confirmButton={TextButton(onClick={dialog=""}){Text(stringResource(R.string.close))}})
                if(entryPromotions.isNotEmpty()) PromotionDialog(entryPromotions,white=s.position.board.sideToMove==Side.WHITE,
                    onPick={ move -> entryPromotions=emptyList();submitDraft(move) },onCancel={entryPromotions=emptyList()})
                if(resultDialog!=null) {
                    val event=resultDialog!!
                    AlertDialog(
                        onDismissRequest={resultDialog=null;vm.acknowledgeResult()},
                        title={Text(stringResource(resultHeadline(event,s.game)))},
                        text={Column {
                            Text(reasonText(resources,event.reason))
                            Text(stringResource(resultScore(event.result)),modifier=Modifier.padding(top=8.dp),fontWeight=FontWeight.Medium)
                            TextButton(onClick={resultDialog=null;vm.acknowledgeResult()}) { Text(stringResource(R.string.review_board)) }
                        }},
                        confirmButton={TextButton(onClick={
                            val g=s.game
                            resultDialog=null;vm.acknowledgeResult()
                            if(g!=null) { selectedMode=runCatching { GameMode.valueOf(g.mode) }.getOrDefault(selectedMode); level=g.level; white=g.humanWhite; allowHints=g.hintsEnabled }
                            dialog="new"
                        }) { Text(stringResource(R.string.play_again)) }},
                        dismissButton={TextButton(onClick={resultDialog=null;vm.acknowledgeResult();screen="home";vm.pauseForNavigation()}) { Text(stringResource(R.string.save_home)) }}
                    )
                }
            }
        }
    }
    // Lint reports super.dispatchKeyEvent as restricted because androidx.core's
    // ComponentActivity marks its override @RestrictTo; calling the platform
    // Activity method through it is the documented way to intercept keys.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // A captured key's UP must not reach a focused button and activate it.
        if(event.action==KeyEvent.ACTION_UP && consumedKeys.remove(event.keyCode)) return true
        if(event.action==KeyEvent.ACTION_DOWN && event.repeatCount>0 && event.keyCode in consumedKeys) return true
        if(!gameVisible || modalVisible) blockedNotation=false
        if(gameVisible && !modalVisible && !event.isCtrlPressed && !event.isMetaPressed) {
            if(event.action==KeyEvent.ACTION_DOWN && event.keyCode in listOf(KeyEvent.KEYCODE_TAB,
                    KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_ESCAPE)) blockedNotation=false
            if(reviewing || !vm.state.value.canEnterMove()) {
                val characters=if(event.action==KeyEvent.ACTION_MULTIPLE) event.characters.orEmpty()
                    else if(event.action==KeyEvent.ACTION_DOWN) event.unicodeChar.toChar().toString() else ""
                if(characters.any { it.isLetterOrDigit() || it in "-+#=" }) {
                    blockedNotation=true
                    return consumeGameKey(event)
                }
            }
            // Ignore an attempted notation submission while entry is unavailable;
            // ordinary Tab/arrow navigation followed by Enter still activates controls.
            if(blockedNotation && event.action==KeyEvent.ACTION_DOWN &&
                event.keyCode in listOf(KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                blockedNotation=false
                return consumeGameKey(event)
            }
        }
        if(gameVisible && !reviewing && !modalVisible && vm.state.value.canEnterMove() && !event.isCtrlPressed && !event.isMetaPressed) {
            blockedNotation=false
            if(event.action==KeyEvent.ACTION_MULTIPLE && !event.characters.isNullOrEmpty()) {
                draft=draft.type(event.characters,vm.state.value.positionKey()); return consumeGameKey(event)
            }
            if(event.action!=KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
            if(event.repeatCount>0) return super.dispatchKeyEvent(event)
            when(event.keyCode) {
                KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER -> { if(draft.text.isNotBlank()) {submitDraft();return consumeGameKey(event)} }
                KeyEvent.KEYCODE_DEL -> if(draft.text.isNotEmpty()) { draft=draft.backspace(); return consumeGameKey(event) }
                // Back is handled by BackHandler: with predictive back (target SDK 36) the
                // system no longer delivers KEYCODE_BACK here.
                KeyEvent.KEYCODE_ESCAPE -> if(draft.text.isNotEmpty()) {draft=NotationDraft();return consumeGameKey(event)}
                else -> { val c=event.unicodeChar.toChar(); if(c.isLetterOrDigit() || c in "-+#=") {
                    draft=draft.type(c.toString(),vm.state.value.positionKey())
                    return consumeGameKey(event)
                } }
            }
        }
        return super.dispatchKeyEvent(event)
    }
    private fun consumeGameKey(event: KeyEvent): Boolean {
        if(event.action==KeyEvent.ACTION_DOWN) consumedKeys.add(event.keyCode)
        return true
    }
    private fun beginReview() {
        blockedNotation=false
        reviewing=true
    }
    private fun endReview() {
        blockedNotation=false
        reviewing=false
    }
    private suspend fun loadFenDocument(uri: Uri): String = withContext(Dispatchers.IO) {
        val displayName=contentResolver.query(
            uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null
        )?.use { cursor ->
            if(cursor.moveToFirst()) cursor.getString(0) else null
        } ?: throw IOException("The selected document has no file name.")
        require(displayName.endsWith(".fen",ignoreCase=true)) {
            getString(R.string.fen_wrong_file)
        }
        val input=contentResolver.openInputStream(uri)
            ?: throw IOException("The selected document could not be opened.")
        val contents=ByteArrayOutputStream()
        input.use { stream ->
            val buffer=ByteArray(512)
            while(true) {
                val count=stream.read(buffer)
                if(count<0) break
                if(count==0) continue
                require(contents.size()+count<=MAX_FEN_FILE_SIZE_BYTES) {
                    "The FEN file is larger than 4 KB."
                }
                contents.write(buffer,0,count)
            }
        }
        normalizeFenContent(String(contents.toByteArray(),Charsets.UTF_8))
    }
    private fun submitDraft(overrideMove: String? = null) {
        val s=vm.state.value
        if(!gameVisible || reviewing || !s.canEnterMove() || draft.submitting || draft.text.isBlank()) return
        if(draft.positionKey!=s.positionKey()) { draft=NotationDraft(); return }
        if(overrideMove==null && draft.text.matches(Regex("[a-hA-H][1-8][a-hA-H][1-8]"))) {
            val choices=s.position.legal.map { it.toString() }.filter { it.length==5 && it.startsWith(draft.text.lowercase()) }
            if(choices.isNotEmpty()) {entryPromotions=choices;return}
        }
        val pending=draft.submit(s.positionKey()); draft=pending
        vm.enter(overrideMove ?: pending.text,pending.positionKey!!) { error ->
            if(draft.positionKey==pending.positionKey) draft=if(error==null) NotationDraft() else pending.rejected(getString(entryErrorText(error)))
        }
    }
    /** Reads a user-chosen text document; UTF-8 first, then Latin-1 (the PGN standard's encoding). */
    private suspend fun readTextDocument(uri: Uri, maxBytes: Int): String = withContext(Dispatchers.IO) {
        val bytes=ByteArrayOutputStream()
        (contentResolver.openInputStream(uri) ?: throw IOException("The file could not be opened.")).use { input ->
            val buffer=ByteArray(64*1024)
            while(true) {
                val count=input.read(buffer)
                if(count<0) break
                if(bytes.size()+count>maxBytes) throw IOException("The file is larger than ${maxBytes/(1024*1024)} MB.")
                bytes.write(buffer,0,count)
            }
        }
        val raw=bytes.toByteArray()
        runCatching {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(raw)).toString()
        }.getOrElse { String(raw,Charsets.ISO_8859_1) }
    }
    /** Writes the selected games as one PGN file to a document the user chose. */
    private suspend fun writeExport(uri: Uri, mode: ExportModeFilter, period: ExportPeriod): Int {
        val games=vm.gamesForExport(mode,period)
        withContext(Dispatchers.IO) {
            // Some document providers reject "wt"; the picker's new file is empty either way.
            val output=runCatching { contentResolver.openOutputStream(uri,"wt") }.getOrNull()
                ?: contentResolver.openOutputStream(uri,"w") ?: throw IOException("The file could not be opened.")
            output.use { it.write(libraryPgn(games).toByteArray(Charsets.UTF_8)) }
        }
        return games.size
    }
    /** Shares the same PGN file through the Android share menu (email, Drive, other apps). */
    private suspend fun shareExport(mode: ExportModeFilter, period: ExportPeriod) {
        val games=vm.gamesForExport(mode,period)
        val file=withContext(Dispatchers.IO) {
            val folder=java.io.File(cacheDir,"exports").apply { mkdirs() }
            folder.listFiles()?.forEach { it.delete() }
            java.io.File(folder,exportFileName()).apply { writeText(libraryPgn(games),Charsets.UTF_8) }
        }
        val uri=FileProvider.getUriForFile(this,"$packageName.files",file)
        val send=Intent(Intent.ACTION_SEND).apply {
            type="application/x-chess-pgn"
            putExtra(Intent.EXTRA_STREAM,uri)
            putExtra(Intent.EXTRA_SUBJECT,"Square Chess (${games.size})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send,getString(R.string.share_games_chooser)))
    }
    override fun onDestroy() { sounds.release(); super.onDestroy() }
    override fun onStart() { super.onStart(); if(gameVisible && !reviewing) vm.foreground() }
    override fun onStop() { vm.background(); super.onStop() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable private fun GameHeader(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val view = LocalView.current
    // Reading Compose's inset also triggers recomposition after inset/rotation changes.
    val topInset = WindowInsets.displayCutout.getTop(density)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val topCutouts = view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
            .filter { it.top <= 0 && it.bottom > 0 }
        val centralCutout = topCutouts.any { it.left < widthPx / 2 && it.right > widthPx / 2 }
        val unavailableBounds = topInset > 0 && topCutouts.isEmpty()
        val topPadding = if (centralCutout || unavailableBounds) with(density) { topInset.toDp() } else 0.dp
        val leftEdge = topCutouts.filter { it.right <= widthPx / 2 }.maxOfOrNull { it.right } ?: 0
        val rightEdge = topCutouts.filter { it.left >= widthPx / 2 }.minOfOrNull { it.left }
        val leftPadding = if (topPadding > 0.dp) 12.dp else with(density) { leftEdge.toDp() } + 12.dp
        val rightPadding = if (topPadding > 0.dp || rightEdge == null) 12.dp
            else with(density) { (widthPx - rightEdge).toDp() } + 12.dp
        val rowHeight = if (topPadding > 0.dp) 56.dp
            else maxOf(56.dp, with(density) { topInset.toDp() } + 4.dp)
        Row(
            Modifier.fillMaxWidth().padding(top = topPadding).heightIn(min=rowHeight)
                .padding(start = leftPadding, end = rightPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

@Composable private fun LandingOption(title: String, subtitle: String? = null, onClick: () -> Unit) {
    val palette=LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).card(palette)
            .clickable(role=Role.Button,onClick=onClick)
            .heightIn(min=64.dp).padding(horizontal=20.dp,vertical=14.dp),
        verticalAlignment=Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title,fontSize=18.sp,fontWeight=FontWeight.Medium)
            subtitle?.let { Text(it,fontSize=13.sp,color=LocalPalette.current.muted) }
        }
        Spacer(Modifier.width(12.dp))
        Text("›",color=palette.accent,fontSize=24.sp)
    }
}

/** A card: filled in the standard colours, outlined on e-ink where fills disappear. */
private fun Modifier.card(palette: Palette): Modifier =
    background(palette.card).then(if(palette.eink) Modifier.border(1.5.dp,palette.text,RoundedCornerShape(12.dp)) else Modifier)

@Composable private fun MenuSection(title: String) {
    Text(title.uppercase(),fontSize=11.sp,letterSpacing=1.sp,color=LocalPalette.current.muted,
        modifier=Modifier.padding(start=12.dp,top=10.dp,bottom=2.dp).semantics { heading() })
}

internal fun historyDate(millis: Long): String =
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(millis))

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun HistoryRow(game: SavedGame, onOpen: ()->Unit, onDelete: ()->Unit) {
    val moveCount=game.moves.split(" ").count { it.isNotBlank() }
    val fullMoves=(moveCount+1)/2
    val res=appResources()
    val result=if(game.result=="*") stringResource(R.string.game_in_progress) else game.result.replace("1/2","½")
    val mode=when(game.mode) {
        GameMode.COMPUTER.name -> stringResource(R.string.mode_computer)
        GameMode.LOCAL_TWO_PLAYER.name -> stringResource(R.string.mode_over_board)
        GameMode.PHYSICAL_BOARD_RECORDING.name -> stringResource(R.string.mode_recorded)
        else -> game.mode.lowercase().replace('_',' ')
    }
    val deleteLabel=stringResource(R.string.delete_game_action)
    val palette=LocalPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).card(palette)
        .combinedClickable(onClick=onOpen,onLongClick=onDelete,onLongClickLabel=deleteLabel)
        .padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${displayName(res,game.white)} · ${displayName(res,game.black)}",fontSize=17.sp,fontWeight=FontWeight.Medium)
            Text("$mode · ${historyDate(game.updated)} · ${pluralStringResource(R.plurals.history_moves,fullMoves,fullMoves)}",fontSize=12.sp,color=LocalPalette.current.muted)
        }
        Text(result,color=palette.accent,fontSize=13.sp,modifier=Modifier.padding(horizontal=8.dp))
        Text("›",color=palette.accent,fontSize=24.sp)
    }
}


@Composable private fun ChessBoard(s:GameUi,flip:Boolean,modifier:Modifier,prefs:AppSettings=AppSettings(),cancelDraft:()->Boolean={false},onMove:(String)->Unit) {
    var selected by remember(s.position.moves) { mutableStateOf<Square?>(null) }
    var promotion by remember { mutableStateOf<List<String>>(emptyList()) }
    // Drag-and-drop: the dragged piece follows the finger; tap-tap still works.
    var dragFrom by remember(s.position.moves) { mutableStateOf<Square?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val legal=s.position.legal
    val game=s.game
    val humanTurn=game==null || game.mode!=GameMode.COMPUTER.name || ((s.position.board.sideToMove==Side.WHITE)==game.humanWhite)
    val canInteract=!s.busy && game?.result=="*" && humanTurn &&
        (s.clock==null || s.clock.phase==ClockPhase.RUNNING)
    val palette=LocalPalette.current
    val eink=palette.eink
    val res=appResources()
    val lightSquare=prefs.boardTheme.light; val darkSquare=prefs.boardTheme.dark
    fun squareAt(offset: Offset, boardPx: Float): Square? {
        val cell=boardPx/8f
        val col=(offset.x/cell).toInt(); val row=(offset.y/cell).toInt()
        if(col !in 0..7 || row !in 0..7) return null
        return squareAtCell(col,row,flip)
    }
    val currentOnMove by rememberUpdatedState(onMove)
    val currentCancelDraft by rememberUpdatedState(cancelDraft)
    BoxWithConstraints(modifier) {
    val cellDp=maxWidth/8
    Column(Modifier.fillMaxSize().pointerInput(canInteract,s.position.moves,flip) {
        if(!canInteract) return@pointerInput
        // Read the live size: this block is not restarted when only the board size changes.
        detectDragGestures(
            onDragStart={ offset ->
                val square=squareAt(offset,size.width.toFloat())
                val piece=square?.let { s.position.board.getPiece(it) }
                if(square!=null && piece!=null && piece!=Piece.NONE && piece.pieceSide==s.position.board.sideToMove && !currentCancelDraft()) {
                    dragFrom=square; selected=square; dragPosition=offset
                }
            },
            onDrag={ change,amount -> if(dragFrom!=null) { change.consume(); dragPosition+=amount } },
            onDragEnd={
                val from=dragFrom; dragFrom=null
                val to=squareAt(dragPosition,size.width.toFloat())
                if(from!=null && to!=null && to!=from) {
                    val targets=legal.filter { it.from==from && it.to==to }
                    if(targets.size>1) promotion=targets.map { it.toString() }
                    else if(targets.size==1) { currentOnMove(targets[0].toString()); selected=null }
                }
            },
            onDragCancel={ dragFrom=null })
    }) {
        for(row in 0..7) Row(Modifier.weight(1f)) {
            for(col in 0..7) {
                val square=squareAtCell(col,row,flip)
                val file=square.file.ordinal; val rank=square.rank.ordinal
                val piece=s.position.board.getPiece(square)
                val targets=if(canInteract) legal.filter{it.from==selected && it.to==square} else emptyList()
                // E-ink keeps the last-move marks until the next move: a timed fade would cost a screen refresh.
                val last=(if(eink) s.position.moves.lastOrNull() else s.highlightMove).orEmpty()
                val recent=last.startsWith(square.name.lowercase()) || last.drop(2).startsWith(square.name.lowercase())
                val check=piece!=Piece.NONE && piece.pieceType.name=="KING" && piece.pieceSide==s.position.board.sideToMove && s.position.board.isKingAttacked
                val lightCell=(rank+file)%2==1
                val color=when { eink->Color.White;selected==square->palette.selectedSquare;check->palette.checkSquare;recent->palette.recentSquare;lightCell->lightSquare;else->darkSquare }
                Box(Modifier.weight(1f).fillMaxHeight().background(color).then(if(eink) Modifier.drawBehind {
                    if(!lightCell) hatch()
                    if(recent) cornerMarks()
                    if(check) drawCircle(Color.Black,size.minDimension*0.44f,style=Stroke(size.minDimension*0.08f))
                    if(selected==square) {
                        // Inset by half the stroke: drawBehind is not clipped to the square.
                        val stroke=size.minDimension*0.12f
                        drawRect(Color.Black,topLeft=Offset(stroke/2,stroke/2),
                            size=androidx.compose.ui.geometry.Size(size.width-stroke,size.height-stroke),style=Stroke(stroke))
                    }
                } else Modifier).semantics { contentDescription=res.getString(R.string.square_description,square.name.lowercase(),res.getString(squarePieceName(piece)))+
                    (if(targets.isNotEmpty()) res.getString(R.string.square_legal_suffix) else "")+(if(check) res.getString(R.string.square_check_suffix) else "") }.clickable(enabled=canInteract,role=Role.Button) {
                    if(cancelDraft()) selected=null
                    else if(targets.size>1) promotion=targets.map{it.toString()}
                    else if(targets.size==1) {onMove(targets[0].toString());selected=null}
                    else selected=if(piece!=Piece.NONE && piece.pieceSide==s.position.board.sideToMove && selected!=square) square else null
                },contentAlignment=Alignment.Center) {
                    if(piece!=Piece.NONE) {
                        // On hatched squares a white halo keeps the piece outline apart from the lines.
                        if(eink && !lightCell) Image(
                            painter = painterResource(pieceDrawable(piece)),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.fillMaxSize().padding(3.dp).graphicsLayer(scaleX=1.14f,scaleY=1.14f)
                                .alpha(if(dragFrom==square) 0.3f else 1f)
                        )
                        // Chessnut uses simple silhouettes and contrasting internal lines.
                        Image(
                            painter = painterResource(pieceDrawable(piece)),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(3.dp).alpha(if(dragFrom==square) 0.3f else 1f)
                        )
                    }
                    // Drawn over the piece: a dot for a quiet move, a ring for a capture.
                    if(targets.isNotEmpty() && prefs.legalMoves) Canvas(Modifier.fillMaxSize()) {
                        if(eink) {
                            // Black on a white rim, so the marks read on hatching and on pieces.
                            if(piece==Piece.NONE) { drawCircle(Color.White,radius=size.minDimension*0.21f); drawCircle(Color.Black,radius=size.minDimension*0.15f) }
                            else { drawCircle(Color.White,radius=size.minDimension*0.44f,style=Stroke(width=size.minDimension*0.14f))
                                drawCircle(Color.Black,radius=size.minDimension*0.44f,style=Stroke(width=size.minDimension*0.08f)) }
                        } else {
                            val marker=Color(0x8C171D1C)
                            if(piece==Piece.NONE) drawCircle(marker,radius=size.minDimension*0.17f)
                            else drawCircle(marker,radius=size.minDimension*0.44f,style=Stroke(width=size.minDimension*0.09f))
                        }
                    }
                    // Use the displayed colour, including move/check highlights, so
                    // coordinates stay legible. Square semantics already name them.
                    val backgroundLuminance=color.luminance()+0.05f
                    val darkContrast=backgroundLuminance/(CoordinateInk.luminance()+0.05f)
                    val lightContrast=(lightSquare.luminance()+0.05f)/backgroundLuminance
                    val coordinateColor=if(eink) Color.Black else if(darkContrast>=lightContrast) CoordinateInk else lightSquare
                    // On e-ink a white patch keeps the label off the hatching.
                    val label=if(eink) Modifier.background(Color.White).padding(horizontal=1.dp) else Modifier
                    if(prefs.coordinates && row==7) Text(('a'+file).toString(),
                        modifier=(if(col==0) Modifier.align(Alignment.BottomEnd).padding(end=8.dp,bottom=2.dp)
                        else if(col==7) Modifier.align(Alignment.BottomStart).padding(start=8.dp,bottom=2.dp)
                        else Modifier.align(Alignment.BottomStart).padding(start=2.dp,bottom=2.dp)).then(label).clearAndSetSemantics {},
                        style=CoordinateStyle,color=coordinateColor)
                    if(prefs.coordinates && col==7) Text((rank+1).toString(),
                        modifier=Modifier.align(Alignment.TopEnd).padding(2.dp).then(label).clearAndSetSemantics {},
                        style=CoordinateStyle,color=coordinateColor)
                }
            }
        }
    }
    // The dragged piece, slightly enlarged and centred under the finger.
    dragFrom?.let { from ->
        val piece=s.position.board.getPiece(from)
        if(piece!=Piece.NONE) {
            val size=cellDp*1.2f
            val density=LocalDensity.current
            Image(painterResource(pieceDrawable(piece)),null,Modifier.size(size).offset {
                val half=with(density) { size.toPx() }/2f
                IntOffset((dragPosition.x-half).roundToInt(),(dragPosition.y-half).roundToInt())
            })
        }
    }
    }
    if(promotion.isNotEmpty()) PromotionDialog(promotion,white=s.position.board.sideToMove==Side.WHITE,
        onPick={ move -> onMove(move);promotion=emptyList();selected=null },onCancel={promotion=emptyList()})
}

@androidx.annotation.StringRes private fun resultHeadline(event: ResultEvent, game: SavedGame?): Int = when {
    event.result=="1/2-1/2" -> R.string.draw
    game?.mode==GameMode.COMPUTER.name && ((event.result=="1-0")==game.humanWhite) -> R.string.you_won
    game?.mode==GameMode.COMPUTER.name -> R.string.you_lost
    event.result=="1-0" -> R.string.white_wins
    else -> R.string.black_wins
}

@androidx.annotation.StringRes private fun entryErrorText(error: EntryError): Int = when(error) {
    EntryError.POSITION_CHANGED -> R.string.entry_position_changed
    EntryError.ILLEGAL -> R.string.entry_illegal
    EntryError.CLOCK_PAUSED -> R.string.entry_clock_paused
    EntryError.TIME_EXPIRED -> R.string.entry_time_expired
}

@androidx.annotation.StringRes private fun resultScore(result: String): Int = when(result) {
    "1-0" -> R.string.score_white
    "0-1" -> R.string.score_black
    else -> R.string.score_draw
}

/** Clock preset labels are data ("3+2"); only "Untimed" is a word. */
@Composable private fun presetText(label: String): String =
    if(label=="Untimed") stringResource(R.string.untimed) else label

internal fun pieceDrawable(piece: Piece): Int = when (piece) {
    Piece.WHITE_KING -> R.drawable.piece_wk
    Piece.WHITE_QUEEN -> R.drawable.piece_wq
    Piece.WHITE_ROOK -> R.drawable.piece_wr
    Piece.WHITE_BISHOP -> R.drawable.piece_wb
    Piece.WHITE_KNIGHT -> R.drawable.piece_wn
    Piece.WHITE_PAWN -> R.drawable.piece_wp
    Piece.BLACK_KING -> R.drawable.piece_bk
    Piece.BLACK_QUEEN -> R.drawable.piece_bq
    Piece.BLACK_ROOK -> R.drawable.piece_br
    Piece.BLACK_BISHOP -> R.drawable.piece_bb
    Piece.BLACK_KNIGHT -> R.drawable.piece_bn
    Piece.BLACK_PAWN -> R.drawable.piece_bp
    else -> R.drawable.piece_wp
}
