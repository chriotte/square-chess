package com.dataespresso.squarechess

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.alpha
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

private val Ink=Color(0xFF171D1C)
private val Sand=Color(0xFFDCC399)
private val LightSquare=Color(0xFFF2E7CF)
private val DarkSquare=Color(0xFF526D62)
private val CoordinateStyle=TextStyle(
    fontSize=9.sp,lineHeight=10.sp,fontWeight=FontWeight.SemiBold,
    platformStyle=PlatformTextStyle(includeFontPadding=false)
)

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings=settingsStore.load()
        enableEdgeToEdge()
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemBars()
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(
                primary=Sand,onPrimary=Ink,
                // Tonal buttons, dialogs and menus otherwise fall back to Material's purple.
                secondary=Color(0xFFB9CBBF),onSecondary=Ink,
                secondaryContainer=Color(0xFF3B4A43),onSecondaryContainer=Color(0xFFF3EEDF),
                tertiary=Sand,onTertiary=Ink,
                primaryContainer=Color(0xFF4A5A52),onPrimaryContainer=Color(0xFFF3EEDF),
                background=Ink,onBackground=Color(0xFFF3EEDF),
                surface=Color(0xFF222B28),onSurface=Color(0xFFF3EEDF),
                surfaceVariant=Color(0xFF2E3833),onSurfaceVariant=Color(0xFFAFBCB4),
                surfaceTint=Color(0xFF3B4A43),
                surfaceContainerLowest=Color(0xFF151A19),surfaceContainerLow=Color(0xFF1D2422),
                surfaceContainer=Color(0xFF222B28),surfaceContainerHigh=Color(0xFF28322E),
                surfaceContainerHighest=Color(0xFF2E3833),
                outline=Color(0xFF6F7F77),outlineVariant=Color(0xFF3B4A43)
            )) {
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
                                fenImportError="Could not read the selected file: ${e.message ?: "I/O error"}"
                            } catch(e: SecurityException) {
                                fenImportError="Permission to read the selected file was denied."
                            } catch(e: IllegalArgumentException) {
                                fenImportError=e.message ?: "The selected file is not a valid FEN position."
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
                            "Saved $count game${if(count==1) "" else "s"}."
                        } catch(e: Exception) { "Export failed: ${e.message ?: "I/O error"}" }
                    }
                }
                fun openNewGame(mode: GameMode) {
                    selectedMode=mode
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
                Surface(Modifier.fillMaxSize(),color=Ink) {
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
                                    Text("Square Chess",color=Color(0xFFF3EEDF),fontSize=24.sp,fontWeight=FontWeight.Medium)
                                }
                                if(s.game!=null) Button(
                                    onClick={ flip=defaultFlipFor(s.game); reviewPly=null; screen="game"; vm.foreground() },
                                    modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),
                                    shape=RoundedCornerShape(12.dp),
                                    colors=ButtonDefaults.buttonColors(containerColor=Sand,contentColor=Ink)
                                ) { Text(if(s.game?.result=="*") "Continue game" else "View last game",fontSize=17.sp) }
                                LandingOption(stringResource(R.string.play_against_computer)) { openNewGame(GameMode.COMPUTER) }
                                LandingOption(stringResource(R.string.over_the_board)) { openNewGame(GameMode.LOCAL_TWO_PLAYER) }
                                LandingOption("Record physical game") { openNewGame(GameMode.PHYSICAL_BOARD_RECORDING) }
                                LandingOption(
                                    stringResource(R.string.chess_clock),
                                    stringResource(R.string.chess_clock_subtitle)
                                ) {
                                    screen="standaloneClock"
                                }
                              }
                                Row(Modifier.fillMaxWidth().padding(top=4.dp,bottom=16.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                                    TextButton(onClick={screen="history"}) { Text("Game history") }
                                    TextButton(onClick={screen="settings"}) { Text("Settings") }
                                    TextButton(onClick={screen="help";vm.pauseForNavigation()}) { Text(stringResource(R.string.help_title)) }
                                }
                            }
                            "history" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                val history by vm.history.collectAsState(initial=emptyList())
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                    TextButton(onClick={screen="home"}) { Text("‹ Home") }
                                    Spacer(Modifier.weight(1f))
                                    if(history.isNotEmpty()) TextButton(onClick={dialog="export"}) { Text("Export games") }
                                }
                                Text("Your games",fontFamily=FontFamily.Serif,fontSize=30.sp)
                                if(history.isEmpty()) Text("Your first game starts here.")
                                else Text("Long-press a game to delete it.",fontSize=12.sp,color=Color(0xFFAFBCB4),modifier=Modifier.padding(bottom=8.dp))
                                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    history.forEach { g -> HistoryRow(g,
                                        onOpen={ flip=defaultFlipFor(g); reviewPly=null; vm.resume(g); screen="game" },
                                        onDelete={ pendingDelete=g }) }
                                }
                            }
                            "settings" -> SettingsScreen(settings,onChange={ settings=it; settingsStore.save(it) },onExit={screen="home"})
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
                                onBack={screen="home"}
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
                                        lastMove=if(moveStripShown) null else lastMoveText(san,firstMoveNumber,whiteFirst))
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
                                        CapturedColumn(takenBy(topWhite),leadOf(topWhite),if(topWhite) "White" else "Black",gutter,fromBottom=false,
                                            modifier=Modifier.align(Alignment.TopStart).height(side))
                                        CapturedColumn(takenBy(!topWhite),leadOf(!topWhite),if(topWhite) "Black" else "White",gutter,fromBottom=true,
                                            modifier=Modifier.align(Alignment.TopEnd).height(side))
                                    }
                                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                    if(capturesBelow) CapturedRow(takenBy(topWhite),leadOf(topWhite),if(topWhite) "White" else "Black",Modifier.width(side))
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
                                    if(draft.text.isNotEmpty()) Surface(
                                        modifier=Modifier.align(Alignment.TopCenter).padding(8.dp),
                                        shape=RoundedCornerShape(12.dp), tonalElevation=8.dp
                                    ) {
                                        Row(Modifier.padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f).padding(8.dp)) {
                                                Text("Move: ${draft.text}",fontFamily=FontFamily.Monospace,
                                                    modifier=Modifier.semantics { contentDescription="Move entry ${draft.text}" })
                                                draft.error?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp) }
                                            }
                                            TextButton(onClick={draft=NotationDraft()}) { Text("Clear") }
                                            TextButton(enabled=!draft.submitting,onClick={submitDraft()}) { Text("Play") }
                                        }
                                    }
                                    }
                                    if(capturesBelow) CapturedRow(takenBy(!topWhite),leadOf(!topWhite),if(topWhite) "Black" else "White",Modifier.width(side))
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
                if(dialog=="new") AlertDialog(onDismissRequest={dialog=""},title={Text(when(selectedMode){GameMode.COMPUTER->stringResource(R.string.play_against_computer); GameMode.LOCAL_TWO_PLAYER->stringResource(R.string.over_the_board); else->"Record physical game"})},text={
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if(selectedMode==GameMode.COMPUTER) {
                            val shownLevel=level.coerceIn(1,ENGINE_LEVELS.size)
                            Text("${difficultyLabel(shownLevel)} · ${if(BuildConfig.FAIRY_ENGINE) engineLevel(shownLevel).description else "Experimental strength"}")
                            Slider(value=shownLevel.toFloat(),onValueChange={level=it.roundToInt()},valueRange=1f..10f,steps=8,
                                modifier=Modifier.semantics { contentDescription="Difficulty" })
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text("Easier",fontSize=12.sp,color=Color(0xFFAFBCB4))
                                Text("Stronger",fontSize=12.sp,color=Color(0xFFAFBCB4))
                            }
                            Text("Choose your side",fontWeight=FontWeight.SemiBold)
                            SideSelectionButtons(
                                selectedWhite=white,
                                whiteDescription="Play as White",
                                blackDescription="Play as Black",
                                onWhiteSelected={white=true},
                                onBlackSelected={white=false}
                            )
                        } else Text(if(selectedMode==GameMode.PHYSICAL_BOARD_RECORDING) "Enter moves from your board. No hints or engine analysis during recording. A casual companion, not tournament-approved equipment." else "Share this board with a friend. Choose a time control or play untimed.")
                        Spacer(Modifier.height(8.dp))
                        if(selectedMode!=GameMode.PHYSICAL_BOARD_RECORDING) Box {
                            TextButton(onClick={clockPresetMenu=true},modifier=Modifier.semantics { contentDescription="Time control: $clockPreset" }) {
                                Text("Time control · $clockPreset")
                            }
                            DropdownMenu(expanded=clockPresetMenu,onDismissRequest={clockPresetMenu=false}) {
                                CLOCK_PRESETS.forEach { preset ->
                                    DropdownMenuItem(
                                        text={Text(preset.label)},
                                        onClick={clockPreset=preset.label;clockPresetMenu=false}
                                    )
                                }
                            }
                        }
                        TextButton(onClick={moreSetupOptions=!moreSetupOptions}) {
                            Text(if(moreSetupOptions) "Fewer options" else "More options")
                        }
                        if(moreSetupOptions) {
                            TextButton(onClick={fenPicker.launch(arrayOf("*/*"))},enabled=!fenImporting) {
                                Text(stringResource(if(importedFen==null) R.string.import_position else R.string.replace_position))
                            }
                            Text(stringResource(R.string.import_position_help),fontSize=13.sp)
                        }
                        if(fenImporting) Text("Importing position…")
                        if(importedFen!=null) Text("Starting from imported position",fontSize=13.sp,fontWeight=FontWeight.Medium)
                        fenImportError?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp) }
                        if(importedFen!=null || fenImportError!=null) {
                            TextButton(onClick={importedFen=null;fenImportError=null}) { Text("Use standard position") }
                        }
                    }
                },confirmButton={TextButton(enabled=!fenImporting && fenImportError==null,onClick={
                    val selectedClockPreset=CLOCK_PRESETS.firstOrNull { it.label==clockPreset }
                        ?: error("Unknown time control: $clockPreset")
                    val clockConfig=if(selectedMode==GameMode.PHYSICAL_BOARD_RECORDING) null else selectedClockPreset.config
                    flip=selectedMode==GameMode.COMPUTER && !white
                    vm.newGame(selectedMode,level.coerceIn(1,ENGINE_LEVELS.size),white,clockConfig,importedFen ?: START_FEN)
                    importedFen=null;fenImportError=null
                    screen="game";draft=NotationDraft();dialog=""
                }) {Text("Start game")}},dismissButton={TextButton(onClick={dialog=""}){Text("Cancel")}})
                if(dialog=="menu") AlertDialog(onDismissRequest={dialog=""},title={Text("At the board")},text={Column(Modifier.verticalScroll(rememberScrollState())){
                    val computerGame=s.game?.mode==GameMode.COMPUTER.name
                    if(s.game?.result=="*" && reviewPly==null) {
                        MenuSection("Game")
                        if(s.position.moves.isNotEmpty()) TextButton(enabled=!s.busy,onClick={dialog="undo"}){Text("Undo / take back")}
                        if(s.position.canClaimDraw()) TextButton(onClick={vm.end("1/2-1/2","Draw claimed");dialog=""}){Text("Claim draw")}
                        if(computerGame) TextButton(onClick={dialog="resign"}){Text("Resign")}
                        else TextButton(onClick={dialog="end"}){Text("End game")}
                        if(computerGame && s.engineError!=null) TextButton(onClick={vm.maybeEngine();dialog=""}){Text("Retry engine")}
                    }
                    MenuSection("View")
                    if(reviewPly==null) TextButton(enabled=!s.busy,onClick={beginReview();draft=NotationDraft();reviewPly=s.position.moves.size;vm.pauseForNavigation();dialog=""}) { Text("Review game") }
                    else TextButton(onClick={endReview();reviewPly=null;vm.foreground();dialog=""}) { Text("Return to game") }
                    TextButton(onClick={dialog="history"}){Text("Move list")}
                    TextButton(onClick={flip=!flip;vm.setOrientation(flip);dialog=""}){Text("Flip board")}
                    s.game?.let { game ->
                        MenuSection("Share")
                        TextButton(onClick={
                            val shown=reviewPly?.let { ChessPosition(s.position.initialFen,s.position.moves.take(it)) } ?: s.position
                            startActivity(Intent.createChooser(fenShareIntent(shown),"Share position FEN"))
                            dialog=""
                        }) { Text("Share FEN") }
                        TextButton(onClick={
                            startActivity(Intent.createChooser(pgnShareIntent(game),"Share game PGN"))
                            dialog=""
                        }) { Text("Share PGN") }
                    }
                    HorizontalDivider(Modifier.padding(vertical=4.dp))
                    TextButton(onClick={screen="home";dialog="";vm.pauseForNavigation()}){Text("Save & home")}
                }},confirmButton={TextButton(onClick={dialog=""}){Text("Back to board")}})
                if(dialog=="resign") AlertDialog(onDismissRequest={dialog=""},title={Text("Resign this game?")},
                    text={Text("The computer wins. The game stays in your history.")},
                    confirmButton={TextButton(onClick={
                        vm.end(if(s.game?.humanWhite==true) "0-1" else "1-0","You resigned");dialog=""
                    }){Text("Resign")}},
                    dismissButton={TextButton(onClick={dialog=""}){Text("Keep playing")}})
                pendingDelete?.let { game ->
                    AlertDialog(onDismissRequest={pendingDelete=null},title={Text("Delete this game?")},
                        text={Text("${game.white} · ${game.black}, ${historyDate(game.updated)}. This cannot be undone. Export your games first if you want a copy.")},
                        confirmButton={TextButton(onClick={vm.delete(game);pendingDelete=null}){Text("Delete")}},
                        dismissButton={TextButton(onClick={pendingDelete=null}){Text("Cancel")}})
                }
                if(dialog=="export") {
                    var count by remember { mutableStateOf<Int?>(null) }
                    LaunchedEffect(exportMode,exportPeriod) { count=vm.gamesForExport(exportMode,exportPeriod).size }
                    AlertDialog(onDismissRequest={dialog=""},title={Text("Export games")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("One PGN file with every selected game. Other chess apps, such as Lichess and ChessBase, can import it.",fontSize=13.sp)
                        MenuSection("Games")
                        ExportModeFilter.entries.forEach { option ->
                            FilterChip(selected=exportMode==option,onClick={exportMode=option},label={Text(option.label)})
                        }
                        MenuSection("Period")
                        ExportPeriod.entries.forEach { option ->
                            FilterChip(selected=exportPeriod==option,onClick={exportPeriod=option},label={Text(option.label)})
                        }
                        Text(when(count) { null -> "Counting games…"; 0 -> "No games match."; 1 -> "1 game selected."; else -> "$count games selected." },
                            fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=8.dp))
                    }},confirmButton={TextButton(enabled=(count ?: 0)>0,onClick={dialog="";exportSaver.launch(exportFileName())}){Text("Save file")}},
                    dismissButton={Row {
                        TextButton(enabled=(count ?: 0)>0,onClick={
                            dialog=""
                            coroutineScope.launch {
                                try { shareExport(exportMode,exportPeriod) }
                                catch(e: Exception) { exportMessage="Export failed: ${e.message ?: "I/O error"}" }
                            }
                        }){Text("Share")}
                        TextButton(onClick={dialog=""}){Text("Cancel")}
                    }})
                }
                exportMessage?.let { message ->
                    AlertDialog(onDismissRequest={exportMessage=null},title={Text("Export")},text={Text(message)},
                        confirmButton={TextButton(onClick={exportMessage=null}){Text("OK")}})
                }
                if(dialog=="undo") AlertDialog(onDismissRequest={dialog=""},title={Text("Take back the last turn?")},text={Text("The removed move can be played again. Against the computer, both moves are removed when possible.")},confirmButton={TextButton(onClick={vm.undo();dialog=""}){Text("Take back")}},dismissButton={TextButton(onClick={dialog=""}){Text("Keep playing")}})
                if(dialog=="end") AlertDialog(onDismissRequest={dialog=""},title={Text("Finish this game")},text={Column{ Text("Choose the agreed result."); listOf("White wins" to "1-0","Black wins" to "0-1","Draw" to "1/2-1/2").forEach{(name,result)->TextButton(onClick={vm.end(result,"Result recorded by the players");dialog=""}){Text(name)}} }},confirmButton={TextButton(onClick={dialog=""}){Text("Cancel")}})
                if(dialog=="history") AlertDialog(onDismissRequest={dialog=""},title={Text("Moves")},text={Column(Modifier.verticalScroll(rememberScrollState())){if(s.position.moves.isEmpty()) Text("No moves yet.") else s.position.san.chunked(2).forEachIndexed { i,pair-> Text("${i+1}.  ${pair.joinToString("    ")}",fontFamily=FontFamily.Monospace,modifier=Modifier.padding(4.dp)) }}},confirmButton={TextButton(onClick={dialog=""}){Text("Close")}})
                if(entryPromotions.isNotEmpty()) PromotionDialog(entryPromotions,white=s.position.board.sideToMove==Side.WHITE,
                    onPick={ move -> entryPromotions=emptyList();submitDraft(move) },onCancel={entryPromotions=emptyList()})
                if(resultDialog!=null) {
                    val event=resultDialog!!
                    AlertDialog(
                        onDismissRequest={resultDialog=null;vm.acknowledgeResult()},
                        title={Text(resultHeadline(event,s.game))},
                        text={Column {
                            Text(event.reason)
                            Text(resultScore(event.result),modifier=Modifier.padding(top=8.dp),fontWeight=FontWeight.Medium)
                            TextButton(onClick={resultDialog=null;vm.acknowledgeResult()}) { Text("Review board") }
                        }},
                        confirmButton={TextButton(onClick={
                            val g=s.game
                            resultDialog=null;vm.acknowledgeResult()
                            if(g!=null) { selectedMode=runCatching { GameMode.valueOf(g.mode) }.getOrDefault(selectedMode); level=g.level; white=g.humanWhite }
                            dialog="new"
                        }) { Text("Play again") }},
                        dismissButton={TextButton(onClick={resultDialog=null;vm.acknowledgeResult();screen="home";vm.pauseForNavigation()}) { Text("Save & home") }}
                    )
                }
            }
        }
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // A captured key's UP must not reach a focused button and activate it.
        if(event.action==KeyEvent.ACTION_UP && consumedKeys.remove(event.keyCode)) return true
        if(event.action==KeyEvent.ACTION_DOWN && event.repeatCount>0 && event.keyCode in consumedKeys) return true
        if(!gameVisible || modalVisible) blockedNotation=false
        if(gameVisible && !modalVisible && !event.isCtrlPressed && !event.isMetaPressed) {
            if(event.action==KeyEvent.ACTION_DOWN && event.keyCode in listOf(KeyEvent.KEYCODE_TAB,
                    KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_ESCAPE,KeyEvent.KEYCODE_BACK)) blockedNotation=false
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
                KeyEvent.KEYCODE_ESCAPE,KeyEvent.KEYCODE_BACK -> if(draft.text.isNotEmpty()) {draft=NotationDraft();return consumeGameKey(event)}
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
            "Choose a position file ending in .fen. Whole-game files (.pgn) are not supported here."
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
            if(draft.positionKey==pending.positionKey) draft=if(error==null) NotationDraft() else pending.rejected(error)
        }
    }
    /** Writes the selected games as one PGN file to a document the user chose. */
    private suspend fun writeExport(uri: Uri, mode: ExportModeFilter, period: ExportPeriod): Int {
        val games=vm.gamesForExport(mode,period)
        withContext(Dispatchers.IO) {
            val output=contentResolver.openOutputStream(uri,"wt") ?: throw IOException("The file could not be opened.")
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
            putExtra(Intent.EXTRA_SUBJECT,"Square Chess games (${games.size})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send,"Share games"))
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
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF26302C)).clickable(role=Role.Button,onClick=onClick)
            .heightIn(min=64.dp).padding(horizontal=20.dp,vertical=14.dp),
        verticalAlignment=Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title,fontSize=18.sp,fontWeight=FontWeight.Medium)
            subtitle?.let { Text(it,fontSize=13.sp,color=Color(0xFFAFBCB4)) }
        }
        Spacer(Modifier.width(12.dp))
        Text("›",color=Sand,fontSize=24.sp)
    }
}

@Composable private fun MenuSection(title: String) {
    Text(title.uppercase(),fontSize=11.sp,letterSpacing=1.sp,color=Color(0xFFAFBCB4),
        modifier=Modifier.padding(start=12.dp,top=10.dp,bottom=2.dp).semantics { heading() })
}

internal fun historyDate(millis: Long): String =
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(millis))

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun HistoryRow(game: SavedGame, onOpen: ()->Unit, onDelete: ()->Unit) {
    val moveCount=game.moves.split(" ").count { it.isNotBlank() }
    val fullMoves=(moveCount+1)/2
    val result=if(game.result=="*") "In progress" else game.result.replace("1/2","½")
    val mode=when(game.mode) {
        GameMode.COMPUTER.name -> "Against computer"
        GameMode.LOCAL_TWO_PLAYER.name -> "Over the board"
        GameMode.PHYSICAL_BOARD_RECORDING.name -> "Recorded game"
        else -> game.mode.lowercase().replace('_',' ')
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF26302C))
        .combinedClickable(onClick=onOpen,onLongClick=onDelete,onLongClickLabel="Delete game")
        .padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${game.white} · ${game.black}",fontSize=17.sp,fontWeight=FontWeight.Medium)
            Text("$mode · ${historyDate(game.updated)} · $fullMoves move${if(fullMoves==1) "" else "s"}",fontSize=12.sp,color=Color(0xFFAFBCB4))
        }
        Text(result,color=Sand,fontSize=13.sp,modifier=Modifier.padding(horizontal=8.dp))
        Text("›",color=Sand,fontSize=24.sp)
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
    val lightSquare=prefs.boardTheme.light; val darkSquare=prefs.boardTheme.dark
    fun squareAt(offset: Offset, boardPx: Float): Square? {
        val cell=boardPx/8f
        val col=(offset.x/cell).toInt(); val row=(offset.y/cell).toInt()
        if(col !in 0..7 || row !in 0..7) return null
        val file=if(flip) 7-col else col; val rank=if(flip) row else 7-row
        return Square.squareAt(rank*8+file)
    }
    val currentOnMove by rememberUpdatedState(onMove)
    val currentCancelDraft by rememberUpdatedState(cancelDraft)
    BoxWithConstraints(modifier) {
    val boardPx=with(LocalDensity.current) { maxWidth.toPx() }
    val cellDp=maxWidth/8
    Column(Modifier.fillMaxSize().pointerInput(canInteract,s.position.moves,flip) {
        if(!canInteract) return@pointerInput
        detectDragGestures(
            onDragStart={ offset ->
                val square=squareAt(offset,boardPx)
                val piece=square?.let { s.position.board.getPiece(it) }
                if(square!=null && piece!=null && piece!=Piece.NONE && piece.pieceSide==s.position.board.sideToMove && !currentCancelDraft()) {
                    dragFrom=square; selected=square; dragPosition=offset
                }
            },
            onDrag={ change,amount -> if(dragFrom!=null) { change.consume(); dragPosition+=amount } },
            onDragEnd={
                val from=dragFrom; dragFrom=null
                val to=squareAt(dragPosition,boardPx)
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
                val file=if(flip) 7-col else col; val rank=if(flip) row else 7-row
                val square=Square.squareAt(rank*8+file)
                val piece=s.position.board.getPiece(square)
                val targets=if(canInteract) legal.filter{it.from==selected && it.to==square} else emptyList()
                val last=s.highlightMove.orEmpty()
                val recent=last.startsWith(square.name.lowercase()) || last.drop(2).startsWith(square.name.lowercase())
                val check=piece!=Piece.NONE && piece.pieceType.name=="KING" && piece.pieceSide==s.position.board.sideToMove && s.position.board.isKingAttacked
                val color=when { selected==square->Color(0xFFC8B56E);check->Color(0xFFBF7669);recent->Color(0xFFA7AC78);(rank+file)%2==1->lightSquare;else->darkSquare }
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().background(color).semantics { contentDescription="${square.name.lowercase()}, ${if(piece==Piece.NONE) "empty" else piece.name.lowercase().replace('_',' ')}${if(targets.isNotEmpty()) ", legal destination" else ""}${if(check) ", check" else ""}" }.clickable(enabled=canInteract,role=Role.Button) {
                    if(cancelDraft()) selected=null
                    else if(targets.size>1) promotion=targets.map{it.toString()}
                    else if(targets.size==1) {onMove(targets[0].toString());selected=null}
                    else selected=if(piece!=Piece.NONE && piece.pieceSide==s.position.board.sideToMove && selected!=square) square else null
                },contentAlignment=Alignment.Center) {
                    if(piece!=Piece.NONE) {
                        // Chessnut uses simple silhouettes and contrasting internal lines.
                        Image(
                            painter = painterResource(pieceDrawable(piece)),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(3.dp).alpha(if(dragFrom==square) 0.3f else 1f)
                        )
                    }
                    // Drawn over the piece: a dot for a quiet move, a ring for a capture.
                    if(targets.isNotEmpty() && prefs.legalMoves) Canvas(Modifier.fillMaxSize()) {
                        val marker=Color(0x8C171D1C)
                        if(piece==Piece.NONE) drawCircle(marker,radius=size.minDimension*0.17f)
                        else drawCircle(marker,radius=size.minDimension*0.44f,style=Stroke(width=size.minDimension*0.09f))
                    }
                    // Use the displayed colour, including move/check highlights, so
                    // coordinates stay legible. Square semantics already name them.
                    val backgroundLuminance=color.luminance()+0.05f
                    val darkContrast=backgroundLuminance/(Ink.luminance()+0.05f)
                    val lightContrast=(lightSquare.luminance()+0.05f)/backgroundLuminance
                    val coordinateColor=if(darkContrast>=lightContrast) Ink else lightSquare
                    if(prefs.coordinates && row==7) Text(('a'+file).toString(),
                        modifier=(if(col==0) Modifier.align(Alignment.BottomEnd).padding(end=8.dp,bottom=2.dp)
                        else if(col==7) Modifier.align(Alignment.BottomStart).padding(start=8.dp,bottom=2.dp)
                        else Modifier.align(Alignment.BottomStart).padding(start=2.dp,bottom=2.dp)).clearAndSetSemantics {},
                        style=CoordinateStyle,color=coordinateColor)
                    if(prefs.coordinates && col==7) Text((rank+1).toString(),
                        modifier=Modifier.align(Alignment.TopEnd).padding(2.dp).clearAndSetSemantics {},
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

private fun resultHeadline(event: ResultEvent, game: SavedGame?): String = when {
    event.result=="1/2-1/2" -> "Draw"
    game?.mode==GameMode.COMPUTER.name && ((event.result=="1-0")==game.humanWhite) -> "You won"
    game?.mode==GameMode.COMPUTER.name -> "You lost"
    event.result=="1-0" -> "White wins"
    else -> "Black wins"
}

private fun resultScore(result: String): String = when(result) {
    "1-0" -> "White · 1–0"
    "0-1" -> "Black · 0–1"
    else -> "Draw · ½–½"
}

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
