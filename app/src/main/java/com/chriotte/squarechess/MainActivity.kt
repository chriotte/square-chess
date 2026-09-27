package com.chriotte.squarechess

import android.os.Bundle
import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
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
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.bhlangonijr.chesslib.Square

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemBars()
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(primary=Sand,background=Ink,surface=Color(0xFF222B28),onBackground=Color(0xFFF3EEDF))) {
                val s by vm.state.collectAsState()
                val history by vm.history.collectAsState(initial=emptyList())
                var screen by rememberSaveable { mutableStateOf("home") }
                var dialog by remember { mutableStateOf("") }
                var resultDialog by remember { mutableStateOf<ResultEvent?>(null) }
                var selectedMode by remember { mutableStateOf(GameMode.COMPUTER) }
                var level by rememberSaveable { mutableIntStateOf(4) }
                var white by rememberSaveable { mutableStateOf(true) }
                var reviewPly by rememberSaveable(s.game?.id) { mutableStateOf<Int?>(null) }
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
                                LandingOption("Play computer") { selectedMode=GameMode.COMPUTER; dialog="new" }
                                LandingOption("Two players") { selectedMode=GameMode.LOCAL_TWO_PLAYER; dialog="new" }
                                LandingOption("Record physical game") { selectedMode=GameMode.PHYSICAL_BOARD_RECORDING; dialog="new" }
                              }
                                Row(Modifier.fillMaxWidth().padding(top=4.dp,bottom=16.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                                    TextButton(onClick={screen="history"}) { Text("Game history") }
                                    TextButton(onClick={dialog="help"}) { Text("Help & about") }
                                }
                            }
                            "history" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                TextButton(onClick={screen="home"}) { Text("‹ Home") }
                                Text("Your games",fontFamily=FontFamily.Serif,fontSize=30.sp)
                                if(history.isEmpty()) Text("Your first game starts here.")
                                history.forEach { g -> HomeAction(g.result,"${g.white} · ${g.black}",g.mode.lowercase().replace('_',' ')) { flip=defaultFlipFor(g); reviewPly=null; vm.resume(g); screen="game" } }
                            }
                            else -> Column(Modifier.fillMaxSize()) {
                                GameHeader {
                                    GameToolbar(s,reviewPly,
                                        onReview={ draft=NotationDraft();reviewPly=s.position.moves.size;vm.background() },
                                        onReturn={reviewPly=null;vm.foreground()},
                                        onUndo={dialog="undo"},onMenu={dialog="menu"})
                                }
                                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                                    val side=minOf(maxWidth-4.dp,maxHeight)
                                    val display=remember(s,reviewPly) {
                                        reviewPly?.let { s.copy(position=ChessPosition(s.position.initialFen,s.position.moves.take(it)),busy=true,highlightMove=null) } ?: s
                                    }
                                    ChessBoard(display,flip,Modifier.size(side), cancelDraft={
                                        if(draft.text.isNotEmpty()) { draft=NotationDraft(); true } else false
                                    }) { move -> vm.enter(move) }
                                    if(reviewPly!=null) Surface(Modifier.align(Alignment.BottomCenter).padding(bottom=8.dp),shape=RoundedCornerShape(12.dp)) {
                                        Row(verticalAlignment=Alignment.CenterVertically) {
                                            TextButton(enabled=reviewPly!!>0,onClick={reviewPly=0},modifier=Modifier.semantics { contentDescription="First position" }) { Text("|‹") }
                                            TextButton(enabled=reviewPly!!>0,onClick={reviewPly=reviewPly!!-1},modifier=Modifier.semantics { contentDescription="Previous move" }) { Text("‹") }
                                            TextButton(onClick={dialog="history"}) { Text("Moves") }
                                            TextButton(enabled=reviewPly!!<s.position.moves.size,onClick={reviewPly=reviewPly!!+1},modifier=Modifier.semantics { contentDescription="Next move" }) { Text("›") }
                                            TextButton(enabled=reviewPly!!<s.position.moves.size,onClick={reviewPly=s.position.moves.size},modifier=Modifier.semantics { contentDescription="Last position" }) { Text("›|") }
                                        }
                                    }
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
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
                if(dialog=="new") AlertDialog(onDismissRequest={dialog=""},title={Text(when(selectedMode){GameMode.COMPUTER->"Play computer"; GameMode.LOCAL_TWO_PLAYER->"Two players"; else->"Record physical game"})},text={
                    Column {
                        if(selectedMode==GameMode.COMPUTER) {
                            Text("Level $level · Experimental strength")
                            Slider(value=level.toFloat(),onValueChange={level=it.toInt()},valueRange=1f..10f,steps=8)
                            Row(verticalAlignment=Alignment.CenterVertically) { Text("Play as White",modifier=Modifier.weight(1f)); Switch(checked=white,onCheckedChange={white=it}) }
                        } else Text(if(selectedMode==GameMode.PHYSICAL_BOARD_RECORDING) "Enter moves from your board. No hints or engine analysis during recording. A casual companion, not tournament-approved equipment." else "Share this board with a friend. Untimed play.")
                    }
                },confirmButton={TextButton(onClick={flip=selectedMode==GameMode.COMPUTER && !white;vm.newGame(selectedMode,level,white);screen="game";draft=NotationDraft();dialog=""}) {Text("Start game")}},dismissButton={TextButton(onClick={dialog=""}){Text("Cancel")}})
                if(dialog=="menu") AlertDialog(onDismissRequest={dialog=""},title={Text("At the board")},text={Column(Modifier.verticalScroll(rememberScrollState())){
                    if(reviewPly==null) TextButton(enabled=!s.busy,onClick={draft=NotationDraft();reviewPly=s.position.moves.size;vm.background();dialog=""}) { Text("Review game") }
                    else TextButton(onClick={reviewPly=null;vm.foreground();dialog=""}) { Text("Return to game") }
                    TextButton(onClick={dialog="history"}){Text("Move list")}
                    TextButton(onClick={flip=!flip;vm.setOrientation(flip);dialog=""}){Text("Flip board")}
                    if(s.game?.result=="*" && reviewPly==null) {
                        if(s.position.moves.isNotEmpty()) TextButton(enabled=!s.busy,onClick={dialog="undo"}){Text("Undo / take back")}
                        if(s.position.canClaimDraw()) TextButton(onClick={vm.end("1/2-1/2","Draw claimed");dialog=""}){Text("Claim draw")}
                        TextButton(onClick={dialog="end"}){Text("End game")}
                        if(s.game?.mode==GameMode.COMPUTER.name && s.engineError!=null) TextButton(onClick={vm.maybeEngine();dialog=""}){Text("Retry engine")}
                    }
                    TextButton(onClick={screen="home";dialog="";vm.background()}){Text("Save & home")}
                }},confirmButton={TextButton(onClick={dialog=""}){Text("Back to board")}})
                if(dialog=="undo") AlertDialog(onDismissRequest={dialog=""},title={Text("Take back the last turn?")},text={Text("The removed move can be played again. Against the computer, both moves are removed when possible.")},confirmButton={TextButton(onClick={vm.undo();dialog=""}){Text("Take back")}},dismissButton={TextButton(onClick={dialog=""}){Text("Keep playing")}})
                if(dialog=="end") AlertDialog(onDismissRequest={dialog=""},title={Text("Finish this game")},text={Column{ Text("Choose the agreed result."); listOf("White wins" to "1-0","Black wins" to "0-1","Draw" to "1/2-1/2").forEach{(name,result)->TextButton(onClick={vm.end(result,"Result recorded by the players");dialog=""}){Text(name)}} }},confirmButton={TextButton(onClick={dialog=""}){Text("Cancel")}})
                if(dialog=="history") AlertDialog(onDismissRequest={dialog=""},title={Text("Moves")},text={Column(Modifier.verticalScroll(rememberScrollState())){if(s.position.moves.isEmpty()) Text("No moves yet.") else s.position.san.chunked(2).forEachIndexed { i,pair-> Text("${i+1}.  ${pair.joinToString("    ")}",fontFamily=FontFamily.Monospace,modifier=Modifier.padding(4.dp)) }}},confirmButton={TextButton(onClick={dialog=""}){Text("Close")}})
                if(dialog=="help") AlertDialog(onDismissRequest={dialog=""},title={Text("Made for a smaller board")},text={Text("Tap a piece, then its destination. Or type e2e4 and press Enter. SAN such as Nf3 works too. Backspace edits; Back cancels your entry. Use Menu to flip the board.\n\nGames save after each confirmed move.\n\nDevelopment build 0.1 · Stockfish 19 (GPLv3), Chesslib (Apache 2.0), Chessnut pieces by Alexis Luengas (Apache 2.0). Offline. No accounts or analytics.\n\nClocks, import/export and release hardening are still in development.")},confirmButton={TextButton(onClick={dialog=""}){Text("Close")}})
                if(entryPromotions.isNotEmpty()) AlertDialog(onDismissRequest={entryPromotions=emptyList()},title={Text("Promote pawn")},text={Column {
                    entryPromotions.forEach { move -> TextButton(onClick={entryPromotions=emptyList();submitDraft(move)}) {
                        Text(when(move.last()) { 'q'->"Queen"; 'r'->"Rook"; 'b'->"Bishop"; else->"Knight" })
                    } }
                }},confirmButton={TextButton(onClick={entryPromotions=emptyList()}) { Text("Cancel") }})
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
                        dismissButton={TextButton(onClick={resultDialog=null;vm.acknowledgeResult();screen="home";vm.background()}) { Text("Save & home") }}
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

@Composable private fun LandingOption(title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF26302C)).clickable(role=Role.Button,onClick=onClick)
            .heightIn(min=64.dp).padding(horizontal=20.dp,vertical=14.dp),
        verticalAlignment=Alignment.CenterVertically
    ) {
        Text(title,fontSize=18.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text("›",color=Sand,fontSize=24.sp)
    }
}

@Composable private fun HomeAction(number:String,title:String,subtitle:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF26302C),RoundedCornerShape(12.dp)).clickable(onClick=onClick).padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(number,color=Sand,fontSize=12.sp,modifier=Modifier.width(36.dp))
        Column(Modifier.weight(1f)) { Text(title,fontSize=18.sp,fontWeight=FontWeight.Medium); Text(subtitle,fontSize=12.sp,color=Color(0xFFAFBCB4)) }
        Text("›",color=Sand,fontSize=24.sp)
    }
}

@Composable private fun ChessBoard(s:GameUi,flip:Boolean,modifier:Modifier,cancelDraft:()->Boolean={false},onMove:(String)->Unit) {
    var selected by remember(s.position.moves) { mutableStateOf<Square?>(null) }
    var promotion by remember { mutableStateOf<List<String>>(emptyList()) }
    val legal=s.position.legal
    val game=s.game
    val humanTurn=game==null || game.mode!=GameMode.COMPUTER.name || ((s.position.board.sideToMove==Side.WHITE)==game.humanWhite)
    val canInteract=!s.busy && game?.result=="*" && humanTurn
    Column(modifier) {
        for(row in 0..7) Row(Modifier.weight(1f)) {
            for(col in 0..7) {
                val file=if(flip) 7-col else col; val rank=if(flip) row else 7-row
                val square=Square.squareAt(rank*8+file)
                val piece=s.position.board.getPiece(square)
                val targets=if(canInteract) legal.filter{it.from==selected && it.to==square} else emptyList()
                val last=s.highlightMove.orEmpty()
                val recent=last.startsWith(square.name.lowercase()) || last.drop(2).startsWith(square.name.lowercase())
                val check=piece!=Piece.NONE && piece.pieceType.name=="KING" && piece.pieceSide==s.position.board.sideToMove && s.position.board.isKingAttacked
                val color=when { selected==square->Color(0xFFC8B56E);check->Color(0xFFBF7669);recent->Color(0xFFA7AC78);(rank+file)%2==1->LightSquare;else->DarkSquare }
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().background(color).semantics { contentDescription="${square.name.lowercase()}, ${if(piece==Piece.NONE) "empty" else piece.name.lowercase().replace('_',' ')}${if(targets.isNotEmpty()) ", legal destination" else ""}${if(check) ", check" else ""}" }.clickable(enabled=canInteract,role=Role.Button) {
                    if(cancelDraft()) selected=null
                    else if(targets.size>1) promotion=targets.map{it.toString()}
                    else if(targets.size==1) {onMove(targets[0].toString());selected=null}
                    else selected=if(piece!=Piece.NONE && piece.pieceSide==s.position.board.sideToMove) square else null
                },contentAlignment=Alignment.Center) {
                    if(piece!=Piece.NONE) {
                        // Chessnut uses simple silhouettes and contrasting internal lines.
                        Image(
                            painter = painterResource(pieceDrawable(piece)),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(3.dp)
                        )
                    }
                    if(targets.isNotEmpty()) Box(Modifier.size(9.dp).background(Color(0xFF374A3A),RoundedCornerShape(10.dp)))
                    // Use the displayed colour, including move/check highlights, so
                    // coordinates stay legible. Square semantics already name them.
                    val backgroundLuminance=color.luminance()+0.05f
                    val darkContrast=backgroundLuminance/(Ink.luminance()+0.05f)
                    val lightContrast=(LightSquare.luminance()+0.05f)/backgroundLuminance
                    val coordinateColor=if(darkContrast>=lightContrast) Ink else LightSquare
                    if(row==7) Text(('a'+file).toString(),
                        modifier=(if(col==0) Modifier.align(Alignment.BottomEnd).padding(end=8.dp,bottom=2.dp)
                        else if(col==7) Modifier.align(Alignment.BottomStart).padding(start=8.dp,bottom=2.dp)
                        else Modifier.align(Alignment.BottomStart).padding(start=2.dp,bottom=2.dp)).clearAndSetSemantics {},
                        style=CoordinateStyle,color=coordinateColor)
                    if(col==7) Text((rank+1).toString(),
                        modifier=Modifier.align(Alignment.TopEnd).padding(2.dp).clearAndSetSemantics {},
                        style=CoordinateStyle,color=coordinateColor)
                }
            }
        }
    }
    if(promotion.isNotEmpty()) AlertDialog(onDismissRequest={promotion=emptyList()},title={Text("Promote pawn")},text={Column{promotion.forEach { move->TextButton(onClick={onMove(move);promotion=emptyList();selected=null}){Text(when(move.last()){'q'->"Queen";'r'->"Rook";'b'->"Bishop";else->"Knight"})}}}},confirmButton={TextButton(onClick={promotion=emptyList()}){Text("Cancel")}})
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

private fun pieceDrawable(piece: Piece): Int = when (piece) {
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
