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

/*
 * World palettes. Cards are slightly see-through so the drawn world shows at their edges on Home;
 * text keeps at least 4.5:1 contrast on them (PlayroomTest checks this).
 */
val ForestPalette = Palette(
    eink = false, background = Color(0xFFE6EEDA), card = Color(0xEBFFFCF1), text = Color(0xFF22351E), muted = Color(0xFF4A6443),
    accent = Color(0xFFA9530F), onAccent = Color.White, selectedSquare = Color(0xFFE8B04A), checkSquare = Color(0xFFD9645A),
    recentSquare = Color(0xFFC9CF7A), inactiveTrack = Color(0xFFC9D6C0),
    clockPanel = Color(0xFFF6F3E6), clockPanelActive = Color(0xFFD4E3C4)
)
val JunglePalette = Palette(
    eink = false, background = Color(0xFFE9F2D6), card = Color(0xEBFFFBE8), text = Color(0xFF1F3318), muted = Color(0xFF465F35),
    accent = Color(0xFF7E5B00), onAccent = Color.White, selectedSquare = Color(0xFFF29E4C), checkSquare = Color(0xFFD9645A),
    recentSquare = Color(0xFFB9D27A), inactiveTrack = Color(0xFFCADDB0),
    clockPanel = Color(0xFFF7F4E2), clockPanelActive = Color(0xFFDDE8BE)
)
val OceanPalette = Palette(
    eink = false, background = Color(0xFFE3F1F5), card = Color(0xEBF4FBFD), text = Color(0xFF0E3345), muted = Color(0xFF365E70),
    accent = Color(0xFF1B6990), onAccent = Color.White, selectedSquare = Color(0xFFF2C14E), checkSquare = Color(0xFFE07A6A),
    recentSquare = Color(0xFF9FD3C7), inactiveTrack = Color(0xFFBFDCE6),
    clockPanel = Color(0xFFF1F8FA), clockPanelActive = Color(0xFFC9E4EE)
)
val SpacePalette = Palette(
    eink = false, background = Color(0xFF141634), card = Color(0xE61F2350), text = Color(0xFFEEF0FF), muted = Color(0xFFB5BAE0),
    accent = Color(0xFFF3B854), onAccent = Color(0xFF1A1630), selectedSquare = Color(0xFFF3B854), checkSquare = Color(0xFFE06A7A),
    recentSquare = Color(0xFF9C9BD8), inactiveTrack = Color(0xFF3A3F78),
    clockPanel = Color(0xFF1C2048), clockPanelActive = Color(0xFF34397A)
)

fun worldPalette(world: World): Palette = when (world) {
    World.CLASSIC -> StandardPalette
    World.FOREST -> ForestPalette
    World.JUNGLE -> JunglePalette
    World.OCEAN -> OceanPalette
    World.SPACE -> SpacePalette
}

val LocalPalette = staticCompositionLocalOf { StandardPalette }
/** The world drawn behind Home and the menus; Classic draws nothing. E-ink always gives Classic. */
val LocalWorld = staticCompositionLocalOf { World.CLASSIC }

private fun lightWorldScheme(p: Palette, surface: Color, container: Color, outline: Color) = lightColorScheme(
    primary = p.accent, onPrimary = p.onAccent,
    secondary = p.muted, onSecondary = Color.White,
    secondaryContainer = container, onSecondaryContainer = p.text,
    tertiary = p.accent, onTertiary = p.onAccent,
    primaryContainer = container, onPrimaryContainer = p.text,
    background = p.background, onBackground = p.text,
    surface = surface, onSurface = p.text,
    surfaceVariant = container, onSurfaceVariant = p.muted,
    surfaceTint = container,
    surfaceContainerLowest = surface, surfaceContainerLow = surface,
    surfaceContainer = surface, surfaceContainerHigh = surface,
    surfaceContainerHighest = container,
    outline = outline, outlineVariant = container
)

private val ForestScheme = lightWorldScheme(ForestPalette, Color(0xFFFBF8EC), Color(0xFFDCE8D0), Color(0xFF6E8466))
private val JungleScheme = lightWorldScheme(JunglePalette, Color(0xFFFCF8E6), Color(0xFFE1ECC4), Color(0xFF6A7F55))
private val OceanScheme = lightWorldScheme(OceanPalette, Color(0xFFF5FBFD), Color(0xFFD3E8EF), Color(0xFF5E7F8C))
private val SpaceScheme = darkColorScheme(
    primary = SpacePalette.accent, onPrimary = SpacePalette.onAccent,
    secondary = Color(0xFFC2C6F0), onSecondary = Color(0xFF1A1630),
    secondaryContainer = Color(0xFF34397A), onSecondaryContainer = SpacePalette.text,
    tertiary = SpacePalette.accent, onTertiary = SpacePalette.onAccent,
    primaryContainer = Color(0xFF34397A), onPrimaryContainer = SpacePalette.text,
    background = SpacePalette.background, onBackground = SpacePalette.text,
    surface = Color(0xFF1C2048), onSurface = SpacePalette.text,
    surfaceVariant = Color(0xFF2A2E62), onSurfaceVariant = SpacePalette.muted,
    surfaceTint = Color(0xFF34397A),
    surfaceContainerLowest = Color(0xFF101230), surfaceContainerLow = Color(0xFF171A3E),
    surfaceContainer = Color(0xFF1C2048), surfaceContainerHigh = Color(0xFF232756),
    surfaceContainerHighest = Color(0xFF2A2E62),
    outline = Color(0xFF7A7FB8), outlineVariant = Color(0xFF34397A)
)

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
private val VisibleRipple = RippleConfiguration()
@OptIn(ExperimentalMaterial3Api::class)
private val InvisibleRipple = RippleConfiguration(Color.Transparent, androidx.compose.material.ripple.RippleAlpha(0f, 0f, 0f, 0f))

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SquareChessTheme(eink: Boolean, world: World = World.CLASSIC, content: @Composable () -> Unit) {
    val shown = if (eink) World.CLASSIC else world
    val scheme = when {
        eink -> EinkScheme
        shown == World.FOREST -> ForestScheme
        shown == World.JUNGLE -> JungleScheme
        shown == World.OCEAN -> OceanScheme
        shown == World.SPACE -> SpaceScheme
        else -> StandardScheme
    }
    MaterialTheme(colorScheme = scheme) {
        // Ripples fade in and out, which leaves ghost images on e-paper. One provider call for both
        // modes keeps the content at the same place in the tree, so switching keeps the screen state.
        // E-ink gets an invisible ripple, not null: Material 3 1.3 crashes when a ripple that null
        // already removed is removed again (switching e-ink on, off and on).
        CompositionLocalProvider(
            LocalPalette provides if (eink) EinkPalette else worldPalette(shown),
            LocalWorld provides shown,
            LocalRippleConfiguration provides if (eink) InvisibleRipple else VisibleRipple,
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
