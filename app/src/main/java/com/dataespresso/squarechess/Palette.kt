package com.dataespresso.squarechess

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.clipRect

/**
 * App colours outside the Material scheme. The e-ink palette uses only black and white, so every
 * screen looks the same in an e-paper display's 1-bit mode; board and highlight information that
 * other palettes give by colour comes from shapes there (see ChessBoard).
 */
data class Palette(
    val eink: Boolean,
    val background: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val selectedSquare: Color,
    val checkSquare: Color,
    val recentSquare: Color,
    val inactiveTrack: Color,
    val clockPanel: Color,
    val clockPanelActive: Color
)

private val Ink = Color(0xFF171D1C)
private val Sand = Color(0xFFDCC399)
private val Paper = Color(0xFFF3EEDF)

val StandardPalette = Palette(
    eink = false, background = Ink, card = Color(0xFF26302C), text = Paper, muted = Color(0xFFAFBCB4),
    accent = Sand, onAccent = Ink, selectedSquare = Color(0xFFC8B56E), checkSquare = Color(0xFFBF7669),
    recentSquare = Color(0xFFA7AC78), inactiveTrack = Color(0xFF3B4A43),
    clockPanel = Color(0xFF222B28), clockPanelActive = Color(0xFF35463E)
)

val EinkPalette = Palette(
    eink = true, background = Color.White, card = Color.White, text = Color.Black, muted = Color.Black,
    accent = Color.Black, onAccent = Color.White, selectedSquare = Color.White, checkSquare = Color.White,
    recentSquare = Color.White, inactiveTrack = Color(0xFFBDBDBD),
    clockPanel = Color.White, clockPanelActive = Color.Black
)

val LocalPalette = staticCompositionLocalOf { StandardPalette }

private val StandardScheme = darkColorScheme(
    primary = Sand, onPrimary = Ink,
    // Tonal buttons, dialogs and menus otherwise fall back to Material's purple.
    secondary = Color(0xFFB9CBBF), onSecondary = Ink,
    secondaryContainer = Color(0xFF3B4A43), onSecondaryContainer = Paper,
    tertiary = Sand, onTertiary = Ink,
    primaryContainer = Color(0xFF4A5A52), onPrimaryContainer = Paper,
    background = Ink, onBackground = Paper,
    surface = Color(0xFF222B28), onSurface = Paper,
    surfaceVariant = Color(0xFF2E3833), onSurfaceVariant = Color(0xFFAFBCB4),
    surfaceTint = Color(0xFF3B4A43),
    surfaceContainerLowest = Color(0xFF151A19), surfaceContainerLow = Color(0xFF1D2422),
    surfaceContainer = Color(0xFF222B28), surfaceContainerHigh = Color(0xFF28322E),
    surfaceContainerHighest = Color(0xFF2E3833),
    outline = Color(0xFF6F7F77), outlineVariant = Color(0xFF3B4A43)
)

// Black and white only: filled and tonal buttons are black with white text, surfaces white.
private val EinkScheme = lightColorScheme(
    primary = Color.Black, onPrimary = Color.White,
    secondary = Color.Black, onSecondary = Color.White,
    secondaryContainer = Color.Black, onSecondaryContainer = Color.White,
    tertiary = Color.Black, onTertiary = Color.White,
    primaryContainer = Color.Black, onPrimaryContainer = Color.White,
    background = Color.White, onBackground = Color.Black,
    surface = Color.White, onSurface = Color.Black,
    surfaceVariant = Color.White, onSurfaceVariant = Color.Black,
    surfaceTint = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    outline = Color.Black, outlineVariant = Color.Black,
    error = Color.Black, onError = Color.White
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SquareChessTheme(eink: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (eink) EinkScheme else StandardScheme) {
        // Ripples fade in and out, which leaves ghost images on e-paper. One provider call for both
        // modes keeps the content at the same place in the tree, so switching keeps the screen state.
        CompositionLocalProvider(
            LocalPalette provides if (eink) EinkPalette else StandardPalette,
            LocalRippleConfiguration provides if (eink) null else RippleConfiguration(),
            content = content
        )
    }
}

/** Dark squares on e-ink: 45° black lines, as in printed chess diagrams. Seven lines per square
 * keep the pattern continuous from one square to the next. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.hatch() {
    val step = size.width / 7f
    val width = size.minDimension * 0.035f
    clipRect {
        var x = -size.height
        while (x < size.width) {
            drawLine(Color.Black, androidx.compose.ui.geometry.Offset(x, size.height),
                androidx.compose.ui.geometry.Offset(x + size.height, 0f), width)
            x += step
        }
    }
}

/** Last-move squares on e-ink: an L-shaped mark in each corner. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.cornerMarks() {
    val arm = size.minDimension * 0.28f
    val stroke = size.minDimension * 0.09f
    val inset = stroke / 2
    val w = size.width; val h = size.height
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(Color.Black, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x + dx * arm, y), stroke)
        drawLine(Color.Black, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x, y + dy * arm), stroke)
    }
    corner(inset, inset, 1f, 1f); corner(w - inset, inset, -1f, 1f)
    corner(inset, h - inset, 1f, -1f); corner(w - inset, h - inset, -1f, -1f)
}

/**
 * An AlertDialog with a black outline in e-ink mode. On e-paper a white dialog over the dimmed,
 * dithered screen has no clear edge; the other modes look unchanged.
 */
@Composable fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null
) {
    val outline = if (LocalPalette.current.eink)
        androidx.compose.ui.Modifier.border(2.dp, Color.Black, AlertDialogDefaults.shape) else androidx.compose.ui.Modifier
    AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier.then(outline),
        dismissButton = dismissButton, title = title, text = text)
}

/** The outline for drop-down menus in e-ink mode, for the same reason. */
@Composable fun menuBorder(): androidx.compose.foundation.BorderStroke? =
    if (LocalPalette.current.eink) androidx.compose.foundation.BorderStroke(2.dp, Color.Black) else null
