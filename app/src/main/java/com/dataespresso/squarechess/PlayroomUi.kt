package com.dataespresso.squarechess

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square

/** The buddy on Home: its hello, and the sticker count or a new sticker. Opens the buddy screen. */
@Composable fun HomeBuddyCard(settings: AppSettings, earned: Set<Sticker>, onOpen: () -> Unit) {
    val palette = LocalPalette.current
    val unseen = (earned - settings.seenStickers).minByOrNull { it.ordinal }
    val line = stringResource(buddyLine(settings.buddy, BuddyMoment.HOME))
    val detail = unseen?.let { stringResource(R.string.sticker_new, stringResource(it.label)) }
        ?: stringResource(R.string.sticker_count, earned.size, Sticker.entries.size)
    val name = stringResource(settings.buddy.label)
    val openLabel = stringResource(R.string.buddy_open)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).card(palette)
            .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen)
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        BuddyFigure(settings.buddy, settings.outfit.allowed(settings.wearable(earned)), BuddyPose.WAVE, Modifier.size(64.dp, 84.dp), mono = palette.eink,
            description = name, trigger = detail)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(line, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(detail, fontSize = 12.sp, color = palette.muted, fontWeight = if (unseen != null) FontWeight.Bold else FontWeight.Normal)
        }
        Text("›", color = palette.accent, fontSize = 24.sp)
    }
}

/** Where a speech bubble's tail points: at a buddy to its left, or at one below it. */
enum class BubbleTail { LEFT, BOTTOM }

/**
 * The buddy's reaction to a puzzle move or a game. The buddy stands on the screen itself and
 * speaks in a bubble, so it belongs to the board screen instead of sitting in a box. [large]
 * puts a bigger buddy under its bubble, for the space beside a board on tablets. Read on request,
 * not as a live region: the header and the move announcements already speak.
 */
@Composable fun BuddyReaction(settings: AppSettings, earned: Set<Sticker>, moment: BuddyMoment, modifier: Modifier = Modifier,
                              extra: String? = null, large: Boolean = false, text: String? = null,
                              bubbleColor: Color = LocalPalette.current.card, maxHeight: Dp? = null) {
    val palette = LocalPalette.current
    val line = text ?: stringResource(buddyLine(settings.buddy, moment))
    val name = stringResource(settings.buddy.label)
    val outfit = settings.outfit.allowed(settings.wearable(earned))
    val pose = buddyPose(settings.buddy, moment)
    val semantics = Modifier.clearAndSetSemantics { contentDescription = listOfNotNull("$name: $line", extra).joinToString(". ") }
    if (large) Column(modifier.then(semantics), horizontalAlignment = Alignment.CenterHorizontally) {
        SpeechBubble(line, extra, BubbleTail.BOTTOM, bubbleColor, Modifier.fillMaxWidth(), large = true)
        // Width first, then the height left under the bubble, so the figure never runs off the screen.
        val figureHeight = maxHeight?.let { (it - 90.dp).coerceIn(80.dp, 221.dp) } ?: 221.dp
        WorldStage(Modifier.widthIn(max = 170.dp).fillMaxWidth().aspectRatio(100f / 130f).heightIn(max = figureHeight)) {
            BuddyFigure(settings.buddy, outfit, pose, it, mono = palette.eink, trigger = line, tapToReplay = true)
        }
    } else Row(modifier.then(semantics), verticalAlignment = Alignment.CenterVertically) {
        WorldStage(Modifier.size(84.dp, 108.dp)) { BuddyFigure(settings.buddy, outfit, pose, it, mono = palette.eink, trigger = line, tapToReplay = true) }
        SpeechBubble(line, extra, BubbleTail.LEFT, bubbleColor, Modifier.weight(1f, fill = false), large = false)
    }
}

/** The frame round a world's stage: wood, bamboo, a brass porthole, a steel porthole. */
private fun stageFrame(world: World): Color = when (world) {
    World.FOREST -> Color(0xFF7A5531)
    World.JUNGLE -> Color(0xFFA88B3E)
    World.OCEAN -> Color(0xFFB8893A)
    World.SPACE -> Color(0xFF9AA3C7)
    World.CLASSIC -> Color.Transparent
}

/**
 * In a world, the buddy stands in a small framed scene of that world; in Classic and on e-ink it
 * stands on the screen as before. [content] gets the modifier for the figure inside the frame.
 */
@Composable fun WorldStage(modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    val world = LocalWorld.current
    if (world == World.CLASSIC || LocalPalette.current.eink) { content(modifier); return }
    val round = if (world == World.OCEAN || world == World.SPACE) 28.dp else 16.dp
    val shape = RoundedCornerShape(round)
    BoxWithConstraints(modifier.clip(shape).border(3.dp, stageFrame(world), shape)) {
        val unit = with(LocalDensity.current) { maxWidth.toPx() } / 120f
        WorldBackground(world, Modifier.fillMaxSize(), unit = unit)
        content(Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp))
    }
}

/**
 * Words in a bubble with a tail. It pops in a little when the words change, except on e-paper,
 * where every animation frame would be a screen refresh.
 */
@Composable fun SpeechBubble(text: String, extra: String?, tail: BubbleTail, color: Color, modifier: Modifier = Modifier, large: Boolean = false) {
    val palette = LocalPalette.current
    val eink = palette.eink
    val scale = remember { Animatable(1f) }
    val animate = !eink && animationsOn()
    LaunchedEffect(text) {
        if (animate) { scale.snapTo(0.82f); scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
    }
    val fill = if (eink) Color.White else color
    // Outlined like the buddy's frame, so bubble and buddy read as one: the world's frame colour,
    // the accent in Classic, black on e-ink.
    val world = LocalWorld.current
    val outline = when {
        eink -> Color.Black
        world == World.CLASSIC -> palette.accent
        else -> stageFrame(world)
    }
    val tailSize = 10.dp
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale.value; scaleY = scale.value
                transformOrigin = if (tail == BubbleTail.LEFT) TransformOrigin(0f, 0.5f) else TransformOrigin(0.5f, 1f)
            }
            .drawBehind {
                val t = tailSize.toPx(); val r = 14.dp.toPx()
                val body = if (tail == BubbleTail.LEFT) Rect(t, 0f, size.width, size.height) else Rect(0f, 0f, size.width, size.height - t)
                val bubble = Path().apply { addRoundRect(RoundRect(body, CornerRadius(r, r))) }
                val point = Path().apply {
                    if (tail == BubbleTail.LEFT) { moveTo(t + 1f, size.height / 2 - t * 0.8f); lineTo(0f, size.height / 2); lineTo(t + 1f, size.height / 2 + t * 0.8f) }
                    else { moveTo(size.width / 2 - t, size.height - t - 1f); lineTo(size.width / 2, size.height); lineTo(size.width / 2 + t, size.height - t - 1f) }
                    close()
                }
                val shape = Path.combine(PathOperation.Union, bubble, point)
                drawPath(shape, fill)
                drawPath(shape, outline, style = Stroke(if (eink) 1.5.dp.toPx() else 2.5.dp.toPx(), join = StrokeJoin.Round))
            }
            .padding(
                start = if (tail == BubbleTail.LEFT) tailSize + 12.dp else 14.dp, end = 14.dp,
                top = 10.dp, bottom = if (tail == BubbleTail.BOTTOM) tailSize + 10.dp else 10.dp
            ),
        horizontalAlignment = if (large) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(text, fontSize = if (large) 17.sp else 15.sp, fontWeight = FontWeight.Medium, color = palette.text,
            textAlign = if (large) TextAlign.Center else TextAlign.Start)
        extra?.let { Text(it, fontSize = if (large) 13.sp else 12.sp, color = palette.muted, fontWeight = FontWeight.Bold,
            textAlign = if (large) TextAlign.Center else TextAlign.Start) }
    }
}

/** False when the phone's animations are turned off (Developer options or accessibility). */
@Composable fun animationsOn(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context) {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/**
 * Recently played games under the Home menu, each with its position, result and date. Tapping
 * one opens it, as in Game history; the last row opens the whole history.
 */
@Composable fun RecentGamesList(games: List<SavedGame>, prefs: AppSettings, onOpen: (SavedGame) -> Unit, onAll: () -> Unit) {
    if (games.isEmpty()) return
    val palette = LocalPalette.current
    val drawn = LocalWorld.current != World.CLASSIC
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.recent_games).uppercase(), fontSize = 11.sp, letterSpacing = 1.sp,
            color = if (drawn) palette.text else palette.muted, fontWeight = FontWeight.Medium,
            modifier = Modifier.semantics { heading() }.then(
                if (drawn) Modifier.clip(RoundedCornerShape(6.dp)).background(palette.card).padding(horizontal = 8.dp, vertical = 3.dp)
                else Modifier.padding(start = 4.dp)))
        games.forEach { game -> RecentGameRow(game, prefs) { onOpen(game) } }
        TextButton(onClick = onAll, modifier = if (drawn) Modifier.clip(RoundedCornerShape(10.dp)).background(palette.card) else Modifier) {
            Text(stringResource(R.string.recent_all_games))
        }
    }
}

@Composable private fun RecentGameRow(game: SavedGame, prefs: AppSettings, onOpen: () -> Unit) {
    val palette = LocalPalette.current
    val computer = game.mode == GameMode.COMPUTER.name
    val result = stringResource(when {
        game.result == "*" -> R.string.game_in_progress
        game.result == "1/2-1/2" -> R.string.draw
        computer && ((game.result == "1-0") == game.humanWhite) -> R.string.you_won
        computer -> R.string.you_lost
        game.result == "1-0" -> R.string.white_wins
        else -> R.string.black_wins
    })
    val who = when (game.mode) {
        GameMode.COMPUTER.name -> levelLabel(game.level)
        GameMode.PHYSICAL_BOARD_RECORDING.name -> stringResource(R.string.mode_recorded)
        else -> stringResource(R.string.mode_over_board)
    }
    val plies = game.moves.split(' ').count { it.isNotBlank() }
    val moves = pluralStringResource(R.plurals.history_moves, (plies + 1) / 2, (plies + 1) / 2)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).card(palette).clickable(role = Role.Button, onClick = onOpen)
            .padding(8.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniBoard(game, prefs, Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("$result · $who", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("$moves · ${historyDate(game.updated)}", fontSize = 12.sp, color = palette.muted)
        }
        Text("›", color = palette.accent, fontSize = 24.sp, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

/** The final position of [game], drawn without coordinates or input. */
@Composable fun MiniBoard(game: SavedGame, prefs: AppSettings, modifier: Modifier) {
    val pieces = remember(game.id, game.moves, game.initialFen) {
        runCatching {
            val board = ChessPosition(game.initialFen, game.moves.split(' ').filter(String::isNotBlank)).board
            Square.entries.filter { it != Square.NONE }.mapNotNull { sq -> board.getPiece(sq).takeIf { it != Piece.NONE }?.let { sq to it } }
        }.getOrDefault(emptyList())
    }
    val painters = Piece.entries.filter { it != Piece.NONE }.associateWith { painterResource(pieceDrawable(it)) }
    val palette = LocalPalette.current
    val eink = palette.eink
    val light = if (eink) Color.White else prefs.lightSquare
    val dark = if (eink) (if (prefs.einkGreySquares) EinkGreySquare else Color.White) else prefs.darkSquare
    val hatched = eink && !prefs.einkGreySquares
    val edge = if (eink) Modifier.border(1.dp, Color.Black) else Modifier
    // The final position belongs to the card's text; the board is decoration for screen readers.
    Canvas(modifier.then(edge).clearAndSetSemantics {}) {
        val cell = size.width / 8f
        val flip = game.mode == GameMode.COMPUTER.name && !game.humanWhite
        for (rank in 0..7) for (file in 0..7) {
            val col = if (flip) 7 - file else file
            val row = if (flip) rank else 7 - rank
            val isLight = (rank + file) % 2 == 1
            val topLeft = Offset(col * cell, row * cell)
            drawRect(if (isLight) light else dark, topLeft, Size(cell, cell))
            if (!isLight && hatched) clipRect(topLeft.x, topLeft.y, topLeft.x + cell, topLeft.y + cell) {
                var x = -cell
                while (x < cell) {
                    drawLine(Color.Black, Offset(topLeft.x + x, topLeft.y + cell), Offset(topLeft.x + x + cell, topLeft.y), cell * 0.06f)
                    x += cell / 3f
                }
            }
        }
        for ((sq, piece) in pieces) {
            val file = sq.file.ordinal; val rank = sq.rank.ordinal
            val col = if (flip) 7 - file else file
            val row = if (flip) rank else 7 - rank
            val painter = painters[piece] ?: continue
            translate(col * cell, row * cell) { with(painter) { draw(Size(cell, cell)) } }
        }
    }
}

/** A small preview of a world for Settings. */
@Composable fun WorldSwatch(world: World, selected: Boolean, onSelect: () -> Unit) {
    val name = stringResource(world.label)
    val description = stringResource(R.string.world_description, name) + if (selected) stringResource(R.string.selected_suffix) else ""
    Column(
        Modifier.clickable(role = Role.RadioButton, onClick = onSelect).semantics { contentDescription = description }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(64.dp).background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(10.dp)).padding(3.dp)) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(worldPalette(world).background).clipToBounds()) {
                if (world == World.CLASSIC) Row(Modifier.fillMaxSize().padding(10.dp)) {
                    Column(Modifier.weight(1f)) { Box(Modifier.weight(1f).fillMaxWidth().background(world.light)); Box(Modifier.weight(1f).fillMaxWidth().background(world.dark)) }
                    Column(Modifier.weight(1f)) { Box(Modifier.weight(1f).fillMaxWidth().background(world.dark)); Box(Modifier.weight(1f).fillMaxWidth().background(world.light)) }
                } else WorldBackground(world, Modifier.fillMaxSize(), unit = with(LocalDensity.current) { 58.dp.toPx() } / 130f)
            }
        }
        Text(name, fontSize = 13.sp, maxLines = 2)
    }
}

/**
 * Choose the buddy, dress it, and see the sticker book. Locked items say which sticker opens
 * them; nothing here can be lost or bought.
 */
@Composable fun BuddyScreen(settings: AppSettings, earned: Set<Sticker>, onChange: (AppSettings) -> Unit, onExit: () -> Unit) {
    val palette = LocalPalette.current
    val wearable = settings.wearable(earned)
    val outfit = settings.outfit.allowed(wearable)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.fillMaxHeight().widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onExit, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.home_back)) }
        Text(stringResource(R.string.buddy_title), fontFamily = FontFamily.Serif, fontSize = 30.sp, modifier = Modifier.semantics { heading() })
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).card(palette).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BuddyFigure(settings.buddy, outfit, BuddyPose.STAND, Modifier.size(120.dp, 156.dp), mono = palette.eink,
                description = stringResource(settings.buddy.label), trigger = settings.buddy, tapToReplay = true)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(settings.buddy.label), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(settings.buddy.intro), fontSize = 14.sp)
                Text(stringResource(R.string.sticker_count, earned.size, Sticker.entries.size), fontSize = 12.sp, color = palette.muted)
            }
        }
        MenuSection(stringResource(R.string.buddy_choose))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Buddy.entries.forEach { buddy ->
                val selected = buddy == settings.buddy
                val name = stringResource(buddy.label)
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = if (palette.eink) 1f else 0.25f) else Color.Transparent)
                        .then(if (palette.eink && selected) Modifier.border(2.dp, Color.Black, RoundedCornerShape(10.dp)) else Modifier)
                        .selectable(selected = selected, role = Role.RadioButton) { onChange(settings.copy(buddy = buddy)) }
                        .padding(6.dp).semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BuddyFigure(buddy, outfit, BuddyPose.STAND, Modifier.size(56.dp, 72.dp), mono = palette.eink, animate = false)
                    Text(name, fontSize = 12.sp, maxLines = 1, color = if (selected && palette.eink) Color.Black else Color.Unspecified)
                }
            }
        }
        SettingSwitch(stringResource(R.string.unlock_all_title), stringResource(R.string.unlock_all_help), settings.unlockAll) {
            onChange(settings.copy(unlockAll = it))
        }
        Slot.entries.forEach { slot ->
            MenuSection(stringResource(slot.label))
            ItemChips(slot, outfit, wearable) { item -> onChange(settings.copy(outfit = outfit.with(slot, item))) }
        }
        MenuSection(stringResource(R.string.sticker_book))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).card(palette)) {
            Sticker.entries.forEachIndexed { i, sticker ->
                if (i > 0) HorizontalDivider(color = palette.muted.copy(alpha = 0.25f))
                StickerRow(sticker, sticker in earned)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    }
}

/**
 * One slot's items. Tapping a locked item explains, right under the chips, which sticker opens it
 * and how to earn it, so nobody has to look it up in the sticker book.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ItemChips(slot: Slot, outfit: Outfit, wearable: Set<Sticker>, onPick: (Item?) -> Unit) {
    val res = appResources()
    val palette = LocalPalette.current
    var explained by remember(slot) { mutableStateOf<Item?>(null) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = outfit[slot] == null, onClick = { explained = null; onPick(null) }, label = { Text(stringResource(R.string.item_none)) })
        Item.entries.filter { it.slot == slot }.forEach { item ->
            val unlocked = item.unlocked(wearable)
            val name = stringResource(item.label)
            val sticker = item.sticker?.let { res.getString(it.label) }
            val label = if (unlocked) name else stringResource(R.string.item_locked, name)
            val description = if (unlocked || sticker == null) name else stringResource(R.string.item_locked_description, name, sticker)
            FilterChip(selected = outfit[slot] == item || explained == item,
                onClick = { if (unlocked) { explained = null; onPick(item) } else explained = if (explained == item) null else item },
                label = { Text(label, color = if (unlocked || explained == item) Color.Unspecified else palette.muted) },
                modifier = Modifier.semantics { contentDescription = description })
        }
    }
    val locked = explained?.takeIf { !it.unlocked(wearable) }
    val sticker = locked?.sticker
    if (locked != null && sticker != null) Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).card(palette).padding(12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(stringResource(R.string.item_locked_title, stringResource(locked.label)), fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.item_locked_need, stringResource(sticker.label), stringResource(sticker.how)), fontSize = 14.sp)
    }
}

@Composable private fun StickerRow(sticker: Sticker, earned: Boolean) {
    val palette = LocalPalette.current
    val unlocks = Item.entries.filter { it.sticker == sticker }
    val res = appResources()
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically) {
        StickerBadge(earned)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(sticker.label), fontWeight = if (earned) FontWeight.Bold else FontWeight.Medium)
            Text(stringResource(sticker.how), fontSize = 13.sp, color = palette.muted)
            if (unlocks.isNotEmpty()) Text(stringResource(R.string.sticker_unlocks, unlocks.joinToString(", ") { res.getString(it.label) }),
                fontSize = 12.sp, color = palette.muted)
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (earned) R.string.sticker_earned else R.string.sticker_not_yet), fontSize = 12.sp,
            color = if (earned) palette.accent else palette.muted, fontWeight = if (earned) FontWeight.Bold else FontWeight.Normal)
    }
}

/** A round sticker: filled when earned, an empty outline when not. */
@Composable private fun StickerBadge(earned: Boolean) {
    val palette = LocalPalette.current
    Canvas(Modifier.size(28.dp).clearAndSetSemantics {}) {
        val r = size.minDimension / 2f
        if (earned) {
            drawCircle(palette.accent, r)
            // A five-pointed star in the sticker.
            val path = androidx.compose.ui.graphics.Path()
            for (i in 0 until 10) {
                val a = -Math.PI / 2 + i * Math.PI / 5; val rr = if (i % 2 == 1) r * 0.32f else r * 0.72f
                val x = (center.x + kotlin.math.cos(a) * rr).toFloat(); val y = (center.y + kotlin.math.sin(a) * rr).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, palette.onAccent)
        } else drawCircle(palette.muted, r - 2f, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    }
}
