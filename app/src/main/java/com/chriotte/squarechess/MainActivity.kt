package com.chriotte.squarechess

import android.os.Bundle
import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.github.bhlangonijr.chesslib.Piece
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.bhlangonijr.chesslib.Square

private val Ink=Color(0xFF171D1C)
private val Sand=Color(0xFFDCC399)
private val LightSquare=Color(0xFFF2E7CF)
private val DarkSquare=Color(0xFF526D62)

class MainActivity: ComponentActivity() {
    private val vm: GameViewModel by viewModels()
    private var gameVisible=false
    private var modalVisible=false
    private var entry by mutableStateOf("")
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
                var selectedMode by remember { mutableStateOf(GameMode.COMPUTER) }
                var level by rememberSaveable { mutableIntStateOf(4) }
                var white by rememberSaveable { mutableStateOf(true) }
                gameVisible=screen=="game"; modalVisible=dialog.isNotEmpty()
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
                            "home" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                Text("SQUARE / CHESS",color=Sand,fontSize=12.sp,letterSpacing=3.sp)
                                Text("A little room.\nA whole world.",fontFamily=FontFamily.Serif,fontSize=34.sp,lineHeight=36.sp)
                                Text("Offline chess, made for your keyboard.",color=Color(0xFFAFBCB4),fontSize=14.sp)
                                Spacer(Modifier.height(8.dp))
                                HomeAction("01", "Play computer", "Find your pace against Stockfish") { selectedMode=GameMode.COMPUTER; dialog="new" }
                                HomeAction("02", "Two players", "One board. Two sides.") { selectedMode=GameMode.LOCAL_TWO_PLAYER; dialog="new" }
                                HomeAction("03", "Record physical game", "Keep the moves from your real board") { selectedMode=GameMode.PHYSICAL_BOARD_RECORDING; dialog="new" }
                                if(s.game!=null) Button(onClick={ screen="game"; vm.foreground() },modifier=Modifier.fillMaxWidth()) { Text("Continue game · ${s.position.moves.size} plies") }
                                Row { TextButton(onClick={screen="history"}) { Text("Game history") }; TextButton(onClick={dialog="help"}) { Text("Help & about") } }
                            }
                            "history" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                TextButton(onClick={screen="home"}) { Text("‹ Home") }
                                Text("Your games",fontFamily=FontFamily.Serif,fontSize=30.sp)
                                if(history.isEmpty()) Text("Your first game starts here.")
                                history.forEach { g -> HomeAction(g.result,"${g.white} · ${g.black}",g.mode.lowercase().replace('_',' ')) { vm.resume(g); screen="game" } }
                            }
                            else -> Column(Modifier.fillMaxSize()) {
                                GameHeader(s.message) { dialog="menu" }
                                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                                    val side=minOf(maxWidth-4.dp,maxHeight)
                                    ChessBoard(s,flip,Modifier.size(side)) { move -> vm.enter(move) }
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
                },confirmButton={TextButton(onClick={vm.newGame(selectedMode,level,white);screen="game";entry="";dialog=""}) {Text("Start game")}},dismissButton={TextButton(onClick={dialog=""}){Text("Cancel")}})
                if(dialog=="menu") AlertDialog(onDismissRequest={dialog=""},title={Text("At the board")},text={Column(Modifier.verticalScroll(rememberScrollState())){
                    TextButton(onClick={dialog="history"}){Text("Move list")}
                    TextButton(onClick={flip=!flip;dialog=""}){Text("Flip board")}
                    TextButton(onClick={dialog="undo"}){Text("Undo / take back")}
                    if(s.position.canClaimDraw()) TextButton(onClick={vm.end("1/2-1/2");dialog=""}){Text("Claim draw")}
                    TextButton(onClick={dialog="end"}){Text("End game")}
                    if(s.game?.mode==GameMode.COMPUTER.name) TextButton(onClick={vm.maybeEngine();dialog=""}){Text("Retry engine")}
                    TextButton(onClick={screen="home";dialog="";vm.background()}){Text("Save & home")}
                }},confirmButton={TextButton(onClick={dialog=""}){Text("Back to board")}})
                if(dialog=="undo") AlertDialog(onDismissRequest={dialog=""},title={Text("Take back the last turn?")},text={Text("The removed move can be played again. Against the computer, both moves are removed when possible.")},confirmButton={TextButton(onClick={vm.undo();dialog=""}){Text("Take back")}},dismissButton={TextButton(onClick={dialog=""}){Text("Keep playing")}})
                if(dialog=="end") AlertDialog(onDismissRequest={dialog=""},title={Text("Finish this game")},text={Column{ Text("Choose the agreed result."); listOf("White wins" to "1-0","Black wins" to "0-1","Draw" to "1/2-1/2").forEach{(name,result)->TextButton(onClick={vm.end(result);dialog=""}){Text(name)}} }},confirmButton={TextButton(onClick={dialog=""}){Text("Cancel")}})
                if(dialog=="history") AlertDialog(onDismissRequest={dialog=""},title={Text("Moves")},text={Column(Modifier.verticalScroll(rememberScrollState())){if(s.position.moves.isEmpty()) Text("No moves yet.") else s.position.san.chunked(2).forEachIndexed { i,pair-> Text("${i+1}.  ${pair.joinToString("    ")}",fontFamily=FontFamily.Monospace,modifier=Modifier.padding(4.dp)) }}},confirmButton={TextButton(onClick={dialog=""}){Text("Close")}})
                if(dialog=="help") AlertDialog(onDismissRequest={dialog=""},title={Text("Made for a smaller board")},text={Text("Tap a piece, then its destination. Or type e2e4 and press Enter. SAN such as Nf3 works too. Backspace edits; Back cancels your entry. F flips the board.\n\nGames save after each confirmed move.\n\nDevelopment build 0.1 · Stockfish 19 (GPLv3), Chesslib (Apache 2.0), Chessnut pieces by Alexis Luengas (Apache 2.0). Offline. No accounts or analytics.\n\nClocks, import/export and release hardening are still in development.")},confirmButton={TextButton(onClick={dialog=""}){Text("Close")}})
            }
        }
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if(gameVisible && !modalVisible && event.action==KeyEvent.ACTION_DOWN) {
            when(event.keyCode) {
                KeyEvent.KEYCODE_ENTER -> { if(entry.isNotBlank()) vm.enter(entry); entry=""; return true }
                KeyEvent.KEYCODE_DEL -> { entry=entry.dropLast(1); return true }
                KeyEvent.KEYCODE_ESCAPE,KeyEvent.KEYCODE_BACK -> if(entry.isNotEmpty()) {entry="";return true}
                else -> { val c=event.unicodeChar.toChar(); if(c.isLetterOrDigit() || c in "-+#=") {
                    if(entry.isEmpty() && c=='f') flip=!flip else if(entry.length<12) entry+=c
                    return true
                } }
            }
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onStart() { super.onStart(); vm.foreground() }
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

@Composable private fun GameHeader(message: String, onMenu: () -> Unit) {
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
            Modifier.fillMaxWidth().padding(top = topPadding).height(rowHeight)
                .padding(start = leftPadding, end = rightPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, maxLines=1, overflow=TextOverflow.Ellipsis, fontSize=15.sp,
                color=Color(0xFFF3EEDF), modifier=Modifier.weight(1f))
            Button(onClick=onMenu, modifier=Modifier.height(40.dp).semantics { contentDescription="Game menu" },
                contentPadding=PaddingValues(horizontal=18.dp,vertical=0.dp)) { Text("Menu") }
        }
    }
}

@Composable private fun HomeAction(number:String,title:String,subtitle:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF26302C),RoundedCornerShape(12.dp)).clickable(onClick=onClick).padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(number,color=Sand,fontSize=12.sp,modifier=Modifier.width(36.dp))
        Column(Modifier.weight(1f)) { Text(title,fontSize=18.sp,fontWeight=FontWeight.Medium); Text(subtitle,fontSize=12.sp,color=Color(0xFFAFBCB4)) }
        Text("›",color=Sand,fontSize=24.sp)
    }
}

@Composable private fun ChessBoard(s:GameUi,flip:Boolean,modifier:Modifier,onMove:(String)->Unit) {
    var selected by remember(s.position.moves) { mutableStateOf<Square?>(null) }
    var promotion by remember { mutableStateOf<List<String>>(emptyList()) }
    val legal=s.position.legal
    Column(modifier) {
        for(row in 0..7) Row(Modifier.weight(1f)) {
            for(col in 0..7) {
                val file=if(flip) 7-col else col; val rank=if(flip) row else 7-row
                val square=Square.squareAt(rank*8+file)
                val piece=s.position.board.getPiece(square)
                val targets=legal.filter{it.from==selected && it.to==square}
                val last=s.position.moves.lastOrNull().orEmpty()
                val recent=last.startsWith(square.name.lowercase()) || last.drop(2).startsWith(square.name.lowercase())
                val check=piece!=Piece.NONE && piece.pieceType.name=="KING" && piece.pieceSide==s.position.board.sideToMove && s.position.board.isKingAttacked
                val color=when { selected==square->Color(0xFFC8B56E);check->Color(0xFFBF7669);recent->Color(0xFFA7AC78);(rank+file)%2==1->LightSquare;else->DarkSquare }
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().background(color).semantics { contentDescription="${square.name.lowercase()}, ${if(piece==Piece.NONE) "empty" else piece.name.lowercase().replace('_',' ')}${if(targets.isNotEmpty()) ", legal destination" else ""}${if(check) ", check" else ""}" }.clickable {
                    if(targets.size>1) promotion=targets.map{it.toString()}
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
                    val coordinateColor=if((rank+file)%2==1) Color(0xFFE5F0E6) else Color(0xFF33453D)
                    if(row==7) Text(('a'+file).toString(),modifier=Modifier.align(Alignment.BottomEnd).padding(end=2.dp),fontSize=8.sp,color=coordinateColor)
                    if(col==0) Text((rank+1).toString(),modifier=Modifier.align(Alignment.TopStart).padding(start=2.dp),fontSize=8.sp,color=coordinateColor)
                }
            }
        }
    }
    if(promotion.isNotEmpty()) AlertDialog(onDismissRequest={promotion=emptyList()},title={Text("Promote pawn")},text={Column{promotion.forEach { move->TextButton(onClick={onMove(move);promotion=emptyList();selected=null}){Text(when(move.last()){'q'->"Queen";'r'->"Rook";'b'->"Bishop";else->"Knight"})}}}},confirmButton={TextButton(onClick={promotion=emptyList()}){Text("Cancel")}})
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
