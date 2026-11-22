package com.dataespresso.squarechess

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bhlangonijr.chesslib.Piece

/** What Home can do; the activity fills these in. */
class HomeActions(
    val onContinue: () -> Unit, val onComputer: () -> Unit, val onPuzzles: () -> Unit, val onTwoPlayers: () -> Unit,
    val onRecord: () -> Unit, val onSetUp: () -> Unit, val onClock: () -> Unit,
    val onHistory: () -> Unit, val onSettings: () -> Unit, val onHelp: () -> Unit, val onClose: () -> Unit,
    val onBuddy: () -> Unit, val onOpenGame: (SavedGame) -> Unit
)

/**
 * Home on colour screens: the current game first, the four main ways to play as tiles, the two
 * occasional ones as small buttons, and recent games as a row of small boards. Wide screens
 * (tablets) get two columns. E-ink keeps the plain list in MainActivity, which suits e-paper.
 * Every label is the same as in the list, so finding an option by its name works in both.
 */
@Composable fun TileHome(game: SavedGame?, settings: AppSettings, earned: Set<Sticker>, recent: List<SavedGame>, actions: HomeActions) {
    val palette = LocalPalette.current
    val drawn = LocalWorld.current != World.CLASSIC
    val onCard = if (drawn) Modifier.clip(RoundedCornerShape(12.dp)).background(palette.card) else Modifier
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= 900.dp
        Column(Modifier.fillMaxHeight().widthIn(max = if (wide) 1100.dp else 640.dp).fillMaxWidth().padding(horizontal = 20.dp)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.then(onCard).then(if (drawn) Modifier.padding(6.dp) else Modifier), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_square_chess), contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.app_name_title), color = palette.text, fontSize = 23.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    TextButton(onClick = actions.onClose) { Text(stringResource(R.string.close_app)) }
                }
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(14.dp)) { PlayColumn(game, settings, actions) }
                    Column(Modifier.weight(0.85f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (settings.playful) HomeBuddyCard(settings, earned, actions.onBuddy)
                        RecentGamesList(recent, settings, onOpen = actions.onOpenGame, onAll = actions.onHistory)
                    }
                } else {
                    if (settings.playful) HomeBuddyCard(settings, earned, actions.onBuddy)
                    PlayColumn(game, settings, actions)
                    RecentGamesRow(recent, settings, actions.onOpenGame, actions.onHistory)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp).then(onCard), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = actions.onHistory) { Text(stringResource(R.string.game_history)) }
                TextButton(onClick = actions.onSettings) { Text(stringResource(R.string.settings_title)) }
                TextButton(onClick = actions.onHelp) { Text(stringResource(R.string.help_title)) }
            }
        }
    }
}

/** The current game, the main tiles and the small buttons. */
@Composable private fun PlayColumn(game: SavedGame?, settings: AppSettings, actions: HomeActions) {
    if (game != null) ContinueCard(game, settings, actions.onContinue)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // One accent block at a time: the game to continue, or else the computer tile.
        HomeTile(stringResource(R.string.play_against_computer), stringResource(R.string.tile_computer_sub), primary = game == null,
            modifier = Modifier.weight(1f), onClick = actions.onComputer) { PieceIcon(Piece.BLACK_KNIGHT) }
        HomeTile(stringResource(R.string.puzzles), stringResource(R.string.puzzles_subtitle),
            modifier = Modifier.weight(1f), onClick = actions.onPuzzles) { PuzzleIcon() }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeTile(stringResource(R.string.over_the_board), stringResource(R.string.tile_two_players_sub),
            modifier = Modifier.weight(1f), onClick = actions.onTwoPlayers) {
            Row { PieceIcon(Piece.WHITE_PAWN, 30.dp); PieceIcon(Piece.BLACK_PAWN, 30.dp) }
        }
        HomeTile(stringResource(R.string.editor_title), stringResource(R.string.tile_setup_sub),
            modifier = Modifier.weight(1f), onClick = actions.onSetUp) { PieceIcon(Piece.WHITE_ROOK) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SmallTile(stringResource(R.string.record_physical_game), Modifier.weight(1f), actions.onRecord) { PieceIcon(Piece.BLACK_BISHOP, 28.dp) }
        SmallTile(stringResource(R.string.chess_clock), Modifier.weight(1f), actions.onClock) { ClockIcon() }
    }
}

/** The game in progress (or the last one) with its position, so it is one tap away. */
@Composable private fun ContinueCard(game: SavedGame, settings: AppSettings, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val title = stringResource(if (game.result == "*") R.string.continue_game else R.string.view_last_game)
    val who = when (game.mode) {
        GameMode.COMPUTER.name -> levelLabel(game.level)
        GameMode.PHYSICAL_BOARD_RECORDING.name -> stringResource(R.string.mode_recorded)
        else -> stringResource(R.string.mode_over_board)
    }
    val plies = game.moves.split(' ').count { it.isNotBlank() }
    val moves = pluralStringResource(R.plurals.history_moves, (plies + 1) / 2, (plies + 1) / 2)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.accent)
            .clickable(role = Role.Button, onClick = onClick).padding(10.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniBoard(game, settings, Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.onAccent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text("$who · $moves", color = palette.onAccent.copy(alpha = 0.8f), fontSize = 13.sp)
        }
        Text("›", color = palette.onAccent, fontSize = 26.sp, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

/** A main way to play: an icon, its name and one short line. */
@Composable private fun HomeTile(title: String, subtitle: String, modifier: Modifier, primary: Boolean = false,
                                 onClick: () -> Unit, icon: @Composable () -> Unit) {
    val palette = LocalPalette.current
    val text = if (primary) palette.onAccent else palette.text
    Column(
        modifier.heightIn(min = 124.dp).clip(RoundedCornerShape(16.dp))
            .then(if (primary) Modifier.background(palette.accent.copy(alpha = 0.92f)) else Modifier.card(palette))
            .clickable(role = Role.Button, onClick = onClick).padding(14.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.height(40.dp), contentAlignment = Alignment.CenterStart) { icon() }
        Text(title, color = text, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = if (primary) text.copy(alpha = 0.8f) else palette.muted, fontSize = 12.sp, lineHeight = 15.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** An occasional option: an icon and its name on one line. */
@Composable private fun SmallTile(title: String, modifier: Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).card(palette).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A piece as an icon; black pieces turn white on dark cards so they stay visible. */
@Composable private fun PieceIcon(piece: Piece, size: androidx.compose.ui.unit.Dp = 38.dp) {
    val palette = LocalPalette.current
    val dark = palette.card.compositeOver(palette.background).luminance() < 0.3f
    val shown = if (dark && piece.pieceSide == com.github.bhlangonijr.chesslib.Side.BLACK && piece != Piece.BLACK_PAWN)
        Piece.make(com.github.bhlangonijr.chesslib.Side.WHITE, piece.pieceType) else piece
    Image(painterResource(pieceDrawable(shown)), contentDescription = null, modifier = Modifier.size(size))
}

/** A puzzle piece outline round a small knight: the tactics trainer. */
@Composable private fun PuzzleIcon() {
    val palette = LocalPalette.current
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(40.dp)) {
            val s = size.minDimension
            drawRoundRect(palette.accent.copy(alpha = 0.25f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.2f))
            drawCircle(palette.accent.copy(alpha = 0.25f), s * 0.14f, Offset(s * 0.5f, 0f))
            drawCircle(palette.accent.copy(alpha = 0.25f), s * 0.14f, Offset(s, s * 0.5f))
        }
        PieceIcon(Piece.BLACK_QUEEN, 28.dp)
    }
}

/** A round clock face with two hands. */
@Composable private fun ClockIcon() {
    val color = LocalPalette.current.text
    Canvas(Modifier.size(28.dp)) {
        val r = size.minDimension / 2f
        drawCircle(color, r - 2.dp.toPx(), style = Stroke(2.5.dp.toPx()))
        drawLine(color, center, Offset(center.x, center.y - r * 0.55f), 2.5.dp.toPx(), StrokeCap.Round)
        drawLine(color, center, Offset(center.x + r * 0.4f, center.y + r * 0.1f), 2.5.dp.toPx(), StrokeCap.Round)
    }
}

/** Recent games as a sideways row of small boards; the last card opens the whole history. */
@Composable private fun RecentGamesRow(games: List<SavedGame>, prefs: AppSettings, onOpen: (SavedGame) -> Unit, onAll: () -> Unit) {
    if (games.isEmpty()) return
    val palette = LocalPalette.current
    val drawn = LocalWorld.current != World.CLASSIC
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.recent_games).uppercase(), fontSize = 11.sp, letterSpacing = 1.sp,
            color = if (drawn) palette.text else palette.muted, fontWeight = FontWeight.Medium,
            modifier = Modifier.semantics { heading() }.then(
                if (drawn) Modifier.clip(RoundedCornerShape(6.dp)).background(palette.card).padding(horizontal = 8.dp, vertical = 3.dp)
                else Modifier.padding(start = 4.dp)))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            games.forEach { game -> RecentGameTile(game, prefs) { onOpen(game) } }
            Box(
                Modifier.size(width = 104.dp, height = 158.dp).clip(RoundedCornerShape(12.dp)).card(palette)
                    .clickable(role = Role.Button, onClick = onAll),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(R.string.recent_all_games), color = palette.accent, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable private fun RecentGameTile(game: SavedGame, prefs: AppSettings, onOpen: () -> Unit) {
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
    Column(
        Modifier.width(118.dp).height(158.dp).clip(RoundedCornerShape(12.dp)).card(palette)
            .clickable(role = Role.Button, onClick = onOpen).padding(7.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        MiniBoard(game, prefs, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(4.dp)))
        Text("$result · $who", fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(historyDate(game.updated), fontSize = 10.sp, color = palette.muted, maxLines = 1)
    }
}

