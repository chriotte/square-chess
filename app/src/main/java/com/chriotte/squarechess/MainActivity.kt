package com.chriotte.squarechess

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square

private val Ink=Color(0xFF171D1C)
private val Sand=Color(0xFFDCC399)
private val LightSquare=Color(0xFFE8DFC9)
private val DarkSquare=Color(0xFF71887C)

class MainActivity: ComponentActivity() {
    private val vm: GameViewModel by viewModels()
    private var gameVisible=false
    private var modalVisible=false
    private var entry by mutableStateOf("")
    private var flip by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
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
                    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
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
                                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Text("SQUARE CHESS",color=Sand,fontSize=12.sp,letterSpacing=2.sp,modifier=Modifier.weight(1f))
                                    TextButton(onClick={dialog="menu"}) { Text("Menu") }
                                }
                                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                                    val side=minOf(maxWidth-16.dp,maxHeight)
                                    ChessBoard(s,flip,Modifier.size(side)) { move -> vm.enter(move) }
                                }
                                Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp)) {
                                    Text(if(entry.isEmpty()) s.message else "› $entry",fontSize=14.sp,color=if(entry.isEmpty()) Color(0xFFF3EEDF) else Sand)
                                    Text(if(s.game?.mode==GameMode.PHYSICAL_BOARD_RECORDING.name) "RECORDING · No engine assistance" else "Type e2e4 ↵  ·  F flip  ·  Tap to move",fontSize=11.sp,color=Color(0xFFAFBCB4))
                                }
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
                if(dialog=="help") AlertDialog(onDismissRequest={dialog=""},title={Text("Made for a smaller board")},text={Text("Tap a piece, then its destination. Or type e2e4 and press Enter. SAN such as Nf3 works too. Backspace edits; Back cancels your entry. F flips the board.\n\nGames save after each confirmed move.\n\nDevelopment build 0.1 · Stockfish 19 (GPLv3), Chesslib (Apache 2.0). Offline. No accounts or analytics.\n\nClocks, import/export and release hardening are still in development.")},confirmButton={TextButton(onClick={dialog=""}){Text("Close")}})
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
    val glyphs=mapOf('K' to "♚",'Q' to "♛",'R' to "♜",'B' to "♝",'N' to "♞",'P' to "♟")
    Column(modifier) {
        for(row in 0..7) Row(Modifier.weight(1f)) {
            for(col in 0..7) {
                val file=if(flip) 7-col else col; val rank=if(flip) row else 7-row
                val square=Square.squareAt(rank*8+file)
                val piece=s.position.board.getPiece(square)
                val symbol=piece.fenSymbol
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
                    if(piece!=Piece.NONE) Text(glyphs[symbol.uppercase().first()] ?: "",fontSize=(maxWidth.value*.82).sp,color=if(piece.pieceSide==Side.WHITE) Color.White else Color(0xFF17221E),fontWeight=FontWeight.Bold)
                    if(targets.isNotEmpty()) Box(Modifier.size(9.dp).background(Color(0xFF374A3A),RoundedCornerShape(10.dp)))
                    if(row==7) Text(('a'+file).toString(),modifier=Modifier.align(Alignment.BottomEnd).padding(end=2.dp),fontSize=8.sp,color=Ink)
                    if(col==0) Text((rank+1).toString(),modifier=Modifier.align(Alignment.TopStart).padding(start=2.dp),fontSize=8.sp,color=Ink)
                }
            }
        }
    }
    if(promotion.isNotEmpty()) AlertDialog(onDismissRequest={promotion=emptyList()},title={Text("Promote pawn")},text={Column{promotion.forEach { move->TextButton(onClick={onMove(move);promotion=emptyList();selected=null}){Text(when(move.last()){'q'->"Queen";'r'->"Rook";'b'->"Bishop";else->"Knight"})}}}},confirmButton={TextButton(onClick={promotion=emptyList()}){Text("Cancel")}})
}
