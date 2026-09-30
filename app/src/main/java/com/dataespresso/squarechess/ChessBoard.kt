package com.dataespresso.squarechess

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import kotlin.math.roundToInt

private val CoordinateInk=Color(0xFF171D1C)
private val CoordinateStyle=TextStyle(
    fontSize=9.sp,lineHeight=10.sp,fontWeight=FontWeight.SemiBold,
    platformStyle=PlatformTextStyle(includeFontPadding=false)
)

/**
 * The interactive board shared by games and puzzles. [interactive] says whether the side to move
 * may move now; the caller decides that (a game's turn and clock, a puzzle's state). [lastMove]
 * (UCI) is marked on its two squares. Moves are reported as UCI through [onMove]; the caller
 * decides what a move means.
 */
@Composable fun ChessBoard(position: ChessPosition, flip: Boolean, interactive: Boolean, lastMove: String?, modifier: Modifier,
                           prefs: AppSettings = AppSettings(), cancelDraft: () -> Boolean = { false }, onMove: (String) -> Unit) {
    var selected by remember(position.initialFen,position.moves) { mutableStateOf<Square?>(null) }
    var promotion by remember { mutableStateOf<List<String>>(emptyList()) }
    // Drag-and-drop: the dragged piece follows the finger; tap-tap still works.
    var dragFrom by remember(position.initialFen,position.moves) { mutableStateOf<Square?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val legal=position.legal
    val canInteract=interactive
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
    Column(Modifier.fillMaxSize().pointerInput(canInteract,position.moves,flip) {
        if(!canInteract) return@pointerInput
        // Read the live size: this block is not restarted when only the board size changes.
        detectDragGestures(
            onDragStart={ offset ->
                val square=squareAt(offset,size.width.toFloat())
                val piece=square?.let { position.board.getPiece(it) }
                if(square!=null && piece!=null && piece!=Piece.NONE && piece.pieceSide==position.board.sideToMove && !currentCancelDraft()) {
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
                    else position.castlingOntoRook(from,to)?.let { currentOnMove(it.toString()); selected=null }
                }
            },
            onDragCancel={ dragFrom=null })
    }) {
        for(row in 0..7) Row(Modifier.weight(1f)) {
            for(col in 0..7) {
                val square=squareAtCell(col,row,flip)
                val file=square.file.ordinal; val rank=square.rank.ordinal
                val piece=position.board.getPiece(square)
                val targets=if(canInteract) legal.filter{it.from==selected && it.to==square} else emptyList()
                val last=lastMove.orEmpty()
                val recent=last.startsWith(square.name.lowercase()) || last.drop(2).startsWith(square.name.lowercase())
                val check=piece!=Piece.NONE && piece.pieceType.name=="KING" && piece.pieceSide==position.board.sideToMove && position.board.isKingAttacked
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
                    else {
                        // A tap on the king's own rook castles, as in many chess apps.
                        val castle=selected?.let { position.castlingOntoRook(it,square) }
                        if(castle!=null) { onMove(castle.toString());selected=null }
                        else selected=if(piece!=Piece.NONE && piece.pieceSide==position.board.sideToMove && selected!=square) square else null
                    }
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
        val piece=position.board.getPiece(from)
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
    if(promotion.isNotEmpty()) PromotionDialog(promotion,white=position.board.sideToMove==Side.WHITE,
        onPick={ move -> onMove(move);promotion=emptyList();selected=null },onCancel={promotion=emptyList()})
}
/** The typed move over the board: "Move: Nf3", an error when it was not accepted, Clear and Play. */
@Composable fun MoveEntryCard(draft: NotationDraft, onClear: () -> Unit, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier=modifier.padding(8.dp),shape=RoundedCornerShape(12.dp),tonalElevation=8.dp) {
        Row(Modifier.padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(8.dp)) {
                val entryDescription=stringResource(R.string.move_entry_description,draft.text)
                Text(stringResource(R.string.move_entry,draft.text),fontFamily=FontFamily.Monospace,
                    modifier=Modifier.semantics { contentDescription=entryDescription })
                draft.error?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp) }
            }
            TextButton(onClick=onClear) { Text(stringResource(R.string.clear)) }
            TextButton(enabled=!draft.submitting,onClick=onPlay) { Text(stringResource(R.string.play)) }
        }
    }
}
