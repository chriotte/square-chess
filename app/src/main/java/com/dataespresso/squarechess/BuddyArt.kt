package com.dataespresso.squarechess

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.github.bhlangonijr.chesslib.Piece

/**
 * A buddy, drawn as in the Playroom mockup: a 100 × 130 unit figure with its outfit and pet. It
 * moves (breathing, waving, jumping, tears, flying pancakes) and pops in when its pose changes.
 * It holds still with [animate] off, on e-paper ([mono], black lines on white) and when the
 * phone's animations are off. Only the drawing repeats, at 30 frames a second, and only while
 * the screen is in front.
 */
@Composable fun BuddyFigure(buddy: Buddy, outfit: Outfit, pose: BuddyPose, modifier: Modifier, mono: Boolean = false,
                            description: String? = null, animate: Boolean = true, trigger: Any? = null, tapToReplay: Boolean = false) {
    val moving = animate && !mono && animationsOn()
    // Moves in bursts: a few seconds after each change (pose, line, outfit) or tap, then holds still,
    // so a long game does not keep the screen redrawing.
    var replays by remember { mutableIntStateOf(0) }
    val clock = rememberBuddyClock(moving, listOf(pose, trigger, outfit, replays))
    val time = clock.time
    // The time a pose began, for the pop-in. Read without observing, so a pose change alone does not tick.
    var shownPose by remember { mutableStateOf(pose) }
    var poseStart by remember { mutableFloatStateOf(-10f) }
    if (shownPose != pose) { shownPose = pose; poseStart = Snapshot.withoutReadObservation { time.floatValue } }
    val semantics = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    // Arms and legs are bare lines: on a dark card they are drawn light, so they do not disappear.
    // Piece pets switch to the white set there for the same reason.
    val palette = LocalPalette.current
    val darkCard = !mono && palette.card.compositeOver(palette.background).luminance() < 0.3f
    val limb = when {
        mono -> Color.Black
        darkCard -> Color(0xFFDDE3EE)
        else -> Ink.line
    }
    val petPiece = petPiece(outfit[Slot.PET], white = darkCard)?.let { painterResource(pieceDrawable(it)) }
    val knight = if (pose == BuddyPose.THINK) painterResource(pieceDrawable(if (darkCard) Piece.WHITE_KNIGHT else Piece.BLACK_KNIGHT)) else null
    // Its own layer: without it, every frame of a drag on the board beside it re-ran this drawing.
    val tap = if (tapToReplay && moving) Modifier.pointerInput(Unit) { detectTapGestures { replays++ } } else Modifier
    Spacer(modifier.then(semantics).then(tap).graphicsLayer().drawWithCache {
        onDrawBehind {
            val scale = minOf(size.width / 100f, size.height / 130f)
            val dx = (size.width - 100f * scale) / 2f
            val dy = (size.height - 130f * scale) / 2f
            val t = if (moving) time.floatValue else 0f
            // Every motion shrinks to nothing over the end of a burst, so the buddy settles instead of freezing mid-wave.
            val amp = if (moving) ((BUDDY_BURST_SECONDS - (t - clock.start.floatValue)) / BUDDY_FADE_SECONDS).coerceIn(0f, 1f) else 0f
            // Pop in: a quick overshoot from 85 % to full size in the first 0.4 s of a new pose.
            val since = t - poseStart
            val pop = if (moving && since in 0f..0.4f) 0.85f + 0.15f * easeOutBack(since / 0.4f) else 1f
            withTransform({
                translate(dx, dy); scale(scale, scale, Offset.Zero); translate(0f, 14f)
                scale(pop, pop, Offset(50f, 108f))
            }) {
                BuddyPen(this, mono, limb).apply { this.amp = amp }.drawBuddy(buddy, outfit, pose, petPiece, knight, t, moving)
            }
        }
    })
}

private fun easeOutBack(x: Float): Float {
    val c = 1.70158f; val y = x - 1f
    return 1f + (c + 1f) * y * y * y + c * y * y
}

/** Seconds of animation, advancing only while [running] and the screen is resumed. */
/** How long the buddy moves after a change, and how long it takes to settle at the end. */
internal const val BUDDY_BURST_SECONDS = 6f
internal const val BUDDY_FADE_SECONDS = 0.8f

/** Animation time, and when the current burst began. */
private class BuddyClock(val time: androidx.compose.runtime.MutableFloatState, val start: androidx.compose.runtime.MutableFloatState)

/**
 * Seconds of animation, advancing at 30 frames a second only during a burst that starts whenever
 * [key] changes (and when the screen comes back to the front), and only while it is in front.
 */
@Composable private fun rememberBuddyClock(running: Boolean, key: Any): BuddyClock {
    val clock = remember { BuddyClock(mutableFloatStateOf(0f), mutableFloatStateOf(0f)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    val on = running && state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    LaunchedEffect(on, key) {
        if (!on) return@LaunchedEffect
        clock.start.floatValue = clock.time.floatValue
        var last = withFrameNanos { it }
        while (clock.time.floatValue - clock.start.floatValue < BUDDY_BURST_SECONDS) {
            delay(33)
            withFrameNanos { now -> clock.time.floatValue += ((now - last) / 1_000_000_000f).coerceAtMost(0.1f); last = now }
        }
    }
    return clock
}

/** The chess piece a piece pet shows: the black set on light cards, the white set on dark ones. */
fun petPiece(item: Item?, white: Boolean = false): Piece? = when (item) {
    Item.PET_PAWN -> if (white) Piece.WHITE_PAWN else Piece.BLACK_PAWN
    Item.PET_KNIGHT -> if (white) Piece.WHITE_KNIGHT else Piece.BLACK_KNIGHT
    Item.PET_BISHOP -> if (white) Piece.WHITE_BISHOP else Piece.BLACK_BISHOP
    Item.PET_ROOK -> if (white) Piece.WHITE_ROOK else Piece.BLACK_ROOK
    Item.PET_QUEEN -> if (white) Piece.WHITE_QUEEN else Piece.BLACK_QUEEN
    Item.PET_KING -> if (white) Piece.WHITE_KING else Piece.BLACK_KING
    else -> null
}

private object Ink {
    val line = Color(0xFF26302C)
    val pancake = Color(0xFFF3B854)
    val gold = Color(0xFFFFD35A)
    val red = Color(0xFFE34850)
    val syrup = Color(0xFF8A4B1C)
    val wood = Color(0xFF6B4A2B)
}

/** Parsed shapes by path text. The figure uses a few hundred distinct paths at most. */
private val parsedPaths = java.util.concurrent.ConcurrentHashMap<String, Path>()

private class BuddyPen(val scope: DrawScope, val mono: Boolean, val limb: Color) {
    val ink = if (mono) Color.Black else Ink.line
    /** How much of each motion to draw: 1 while moving, down to 0 as a burst ends. */
    var amp = 1f
    fun osc(x: Float) = sin(x) * amp
    val sw = if (mono) 2.4f else 2f
    fun f(c: Color) = if (mono) Color.White else c
    /** A colour that stays dark on e-paper (pupils, visors, details). */
    fun dark(c: Color) = if (mono) Color.Black else c

    fun path(d: String): Path = parsedPaths.getOrPut(d) { PathParser().parsePathString(d).toPath() }
    fun shape(p: Path, fill: Color?, stroke: Float = sw, strokeColor: Color = ink, alpha: Float = 1f) {
        if (fill != null) scope.drawPath(p, fill, alpha)
        if (stroke > 0f) scope.drawPath(p, strokeColor, alpha, Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    fun d(d: String, fill: Color?, stroke: Float = sw, strokeColor: Color = ink, alpha: Float = 1f) = shape(path(d), fill, stroke, strokeColor, alpha)
    fun line(d: String, stroke: Float = sw, color: Color = ink, alpha: Float = 1f) = shape(path(d), null, stroke, color, alpha)
    fun circle(cx: Float, cy: Float, r: Float, fill: Color?, stroke: Float = sw, strokeColor: Color = ink, alpha: Float = 1f) {
        if (fill != null) scope.drawCircle(fill, r, Offset(cx, cy), alpha)
        if (stroke > 0f) scope.drawCircle(strokeColor, r, Offset(cx, cy), alpha, Stroke(stroke))
    }
    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, fill: Color?, rotate: Float = 0f, stroke: Float = sw, strokeColor: Color = ink, alpha: Float = 1f) {
        scope.rotate(rotate, Offset(cx, cy)) {
            if (fill != null) drawOval(fill, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), alpha)
            if (stroke > 0f) drawOval(strokeColor, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), alpha, Stroke(stroke))
        }
    }
    fun rect(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Color?, stroke: Float = sw, alpha: Float = 1f) {
        val p = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(x, y, x + w, y + h), androidx.compose.ui.geometry.CornerRadius(r, r))) }
        shape(p, fill, stroke, ink, alpha)
    }
    fun painter(p: Painter, x: Float, y: Float, size: Float) {
        with(scope) { translate(x, y) { with(p) { draw(Size(size, size)) } } }
    }

    /** An outlined limb from ([x1], [y1]) to ([x2], [y2]). */
    fun tube(x1: Float, y1: Float, x2: Float, y2: Float, elbow: Pair<Float, Float>?, thick: Float, fill: Color) {
        val hx = x2; val hy = y2
        val p = Path().apply {
            moveTo(x1, y1)
            if (elbow != null) lineTo(elbow.first, elbow.second)
            lineTo(hx, hy)
        }
        val outline = if (mono) 1.4f else 1.2f
        scope.drawPath(p, ink, style = Stroke(thick + outline * 2, cap = StrokeCap.Round, join = StrokeJoin.Round))
        scope.drawPath(p, fill, style = Stroke(thick, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    fun drawBuddy(buddy: Buddy, outfit: Outfit, pose: BuddyPose, petPiece: Painter?, knight: Painter?, t: Float, moving: Boolean) {
        val skin = when (buddy) {
            Buddy.ROBOT -> Color(0xFFA9B1B8); Buddy.MONKEY -> Color(0xFFB07A4F); Buddy.SLOTH -> Color(0xFF9A8670)
            Buddy.BOB -> Color(0xFF54D8FF); else -> Color(0xFFF3CDA9)
        }
        val shirt = when (buddy) {
            Buddy.ROBOT -> skin; Buddy.MAGNUS -> Color(0xFF2A5DB0); Buddy.GIRL -> Color(0xFFD9645A)
            Buddy.PRINCESS -> Color(0xFFB57BD1); Buddy.MONKEY -> Color(0xFFE0A23C); Buddy.SLOTH -> skin; Buddy.BOB -> Color(0xFF2A5DB0)
        }
        val hair = when (buddy) {
            Buddy.MAGNUS -> Color(0xFF6B4423); Buddy.GIRL -> Color(0xFFB86E2A); Buddy.PRINCESS -> Color(0xFFE2BC55); else -> null
        }
        val arms = when (pose) {
            BuddyPose.STAND -> listOf(30f to 80f, 70f to 80f)
            BuddyPose.WAVE -> listOf(30f to 80f, 73f to 40f)
            BuddyPose.CHEER -> listOf(27f to 40f, 73f to 40f)
            BuddyPose.THINK -> listOf(30f to 80f, 59f to 49f)
            BuddyPose.EUREKA -> listOf(30f to 80f, 71f to 41f)
            BuddyPose.SHRUG -> listOf(27f to 60f, 73f to 60f)
            BuddyPose.CRY -> listOf(36f to 84f, 64f to 84f)
            BuddyPose.PANCAKES -> listOf(40f to 68f, 60f to 68f)
        }
        val (lx0, ly0) = arms[0]; val (rx0, ry0) = arms[1]
        // Shoulders, hidden behind the body: inside the top corners of a rounded body, and at the very
        // corners of Little Robot's wide box, so the arms leave the body high on its sides.
        val robot = buddy == Buddy.ROBOT
        val shoulderL = if (robot) 38f else 44f
        val shoulderR = if (robot) 62f else 56f
        val shoulderY = if (robot) 54f else 58f
        val w = if (moving) t else 0f
        /** The hand at ([x], [y]) turned [deg] degrees about the shoulder at ([sx], [sy]). */
        fun swing(sx: Float, sy: Float, x: Float, y: Float, deg: Float): Pair<Float, Float> {
            val a = Math.toRadians(deg.toDouble()); val vx = x - sx; val vy = y - sy
            return (sx + vx * cos(a) - vy * sin(a)).toFloat() to (sy + vx * sin(a) + vy * cos(a)).toFloat()
        }
        val armBob = osc(w * 4f) * 1.5f
        val (lx, ly) = when (pose) {
            BuddyPose.CHEER -> swing(shoulderL, shoulderY, lx0, ly0, osc(w * 5f) * 12f)
            BuddyPose.SHRUG -> lx0 to ly0 + osc(w * 3f) * 2f
            BuddyPose.PANCAKES -> lx0 to ly0 + armBob
            else -> lx0 to ly0
        }
        val (rx, ry) = when (pose) {
            BuddyPose.WAVE -> swing(shoulderR, shoulderY, rx0, ry0, osc(w * 7f) * 24f)
            BuddyPose.CHEER -> swing(shoulderR, shoulderY, rx0, ry0, -osc(w * 5f) * 12f)
            BuddyPose.EUREKA -> swing(shoulderR, shoulderY, rx0, ry0, osc(w * 3f) * 6f)
            BuddyPose.SHRUG -> rx0 to ry0 + osc(w * 3f) * 2f
            BuddyPose.PANCAKES -> rx0 to ry0 + armBob
            else -> rx0 to ry0
        }
        val jumping = pose == BuddyPose.CHEER || pose == BuddyPose.PANCAKES
        val lift = (if (jumping) -5f else 0f) + when {
            !moving -> 0f
            jumping -> -abs(osc(t * 4.5f)) * 5f
            pose == BuddyPose.CRY -> osc(t * 14f) * 0.6f
            else -> osc(t * 2.2f) * 0.7f
        }
        // Sloth sways, slowly.
        val sway = if (buddy == Buddy.SLOTH && moving) osc(t * 1.1f) * 2.5f else 0f
        val head = outfit[Slot.HEAD] ?: when (buddy) { Buddy.PRINCESS -> Item.CROWN; else -> null }
        val magnusCrown = buddy == Buddy.MAGNUS && outfit[Slot.HEAD] == null
        val cape = outfit[Slot.BACK] == Item.CAPE || buddy == Buddy.MAGNUS
        val top = when (buddy) { Buddy.ROBOT -> 20f; Buddy.BOB -> 12f; else -> 18f }
        val eyeY = when (buddy) { Buddy.ROBOT -> 31f; Buddy.BOB -> 28f; else -> 33f }
        val eyes = if (buddy == Buddy.ROBOT) listOf(42.5f, 57.5f) else listOf(44f, 56f)
        val light = when (pose) {
            BuddyPose.CRY -> Color(0xFFE34850)
            BuddyPose.PANCAKES, BuddyPose.CHEER -> Color(0xFF4CC46A)
            else -> Color(0xFFE7AD58)
        }

        scope.rotate(sway, Offset(50f, 108f)) { scope.translate(0f, lift) {
            if (cape) d("M41 55 L28 100 Q50 106 72 100 L59 55Z", f(Ink.red))
            if (buddy == Buddy.MONKEY) line("M58 84 Q80 88 78 70 Q76 60 70 66", color = limb)
            // Legs.
            when (buddy) {
                Buddy.BOB -> {
                    ellipse(50f, 106f, 15f, 4.5f, f(Color(0xFFB9EEFF)))
                    line("M40 104 L44 90 M60 104 L56 90", color = limb, alpha = 0.35f)
                }
                Buddy.ROBOT -> {
                    rect(41f, 80f, 7f, 22f, 1.5f, f(skin)); rect(52f, 80f, 7f, 22f, 1.5f, f(skin))
                    rect(39f, 101f, 10f, 5f, 1.5f, f(Color(0xFF6E767D))); rect(51f, 101f, 10f, 5f, 1.5f, f(Color(0xFF6E767D)))
                }
                Buddy.MAGNUS -> {
                    line("M46 86 L41 104 M54 86 L59 104", 5f, dark(Color(0xFF1C2A55)))
                    rect(35f, 102f, 10f, 6f, 2f, f(Ink.red)); rect(55f, 102f, 10f, 6f, 2f, f(Ink.red))
                }
                else -> {
                    // Legs start hidden behind the body; small dark shoes.
                    val leg = f(if (buddy == Buddy.GIRL) Color(0xFF3B4A6B) else skin)
                    tube(46f, 82f, 42f, 104f, null, 5f, leg); tube(54f, 82f, 58f, 104f, null, 5f, leg)
                    ellipse(40.5f, 106f, 4.5f, 2.6f, dark(Color(0xFF4A3B2E)), stroke = 1.2f)
                    ellipse(59.5f, 106f, 4.5f, 2.6f, dark(Color(0xFF4A3B2E)), stroke = 1.2f)
                }
            }
            // Arms: sleeves from shoulders hidden behind the body, so they come out of its sides.
            if (buddy != Buddy.BOB) {
                val sleeve = if (buddy == Buddy.MONKEY || buddy == Buddy.SLOTH) skin else shirt
                val thick = if (buddy == Buddy.ROBOT) 6f else 5f
                tube(shoulderL, shoulderY, lx, ly, null, thick, f(sleeve))
                tube(shoulderR, shoulderY, rx, ry, if (pose == BuddyPose.THINK) 68f to 66f else null, thick, f(sleeve))
            }
            // Body.
            when (buddy) {
                Buddy.ROBOT -> {
                    rect(36f, 50f, 28f, 31f, 2f, f(skin))
                    rect(42f, 56f, 16f, 11f, 1.5f, dark(Color(0xFF22304A)))
                    // Bolts where the arms join.
                    circle(shoulderL, shoulderY, 2.2f, f(Color(0xFF6E767D)), 1.2f); circle(shoulderR, shoulderY, 2.2f, f(Color(0xFF6E767D)), 1.2f)
                    // The chest light glows on and off while Little Robot waits.
                    val glow = if (moving && pose != BuddyPose.CRY && !jumping) 1f - 0.45f * amp * (1f - abs(sin(t * 2f))) else 1f
                    circle(50f, 61.5f, 2.8f, if (mono) Color.White else light, stroke = 0f, alpha = glow)
                }
                Buddy.BOB -> d("M40 54 L60 54 L63 86 L37 86Z", f(shirt), alpha = if (mono) 1f else 0.85f + osc(w * 9f) * 0.05f)
                else -> {
                    rect(40f, 52f, 20f, 35f, 8f, f(shirt))
                    if (buddy == Buddy.MAGNUS) {
                        rect(40f, 76f, 20f, 4f, 0f, f(Ink.gold))
                        circle(50f, 65f, 4.5f, f(Ink.pancake))
                        line("M47.5 65 L52.5 65", 1.2f)
                    }
                    if (buddy == Buddy.SLOTH) line("M44 62 l2 3 M54 66 l2 3 M47 74 l2 3", 1.2f)
                }
            }
            // Hands, in front of the body.
            if (buddy == Buddy.BOB) {
                circle(lx, ly, 3.6f, f(Color(0xFF54D8FF))); circle(rx, ry, 3.6f, f(Color(0xFF54D8FF)))
            } else {
                val hand = if (buddy == Buddy.ROBOT) Color(0xFFE7AD58) else skin
                val hr = if (buddy == Buddy.ROBOT) 3.4f else 3.1f
                circle(lx, ly, hr, f(hand), 1.4f); circle(rx, ry, hr, f(hand), 1.4f)
                if (buddy == Buddy.SLOTH) listOf(lx to ly, rx to ry).forEach { (x, y) ->
                    scope.translate(x, y) { line("M-2 0 l-2 4 M0 0 l0 4.5 M2 0 l2 4", 1.4f, limb) }
                }
            }
            if (buddy == Buddy.MAGNUS) { ellipse(40f, 55f, 5f, 3.5f, f(Ink.red)); ellipse(60f, 55f, 5f, 3.5f, f(Ink.red)) }
            when (outfit[Slot.NECK]) {
                Item.SCARF -> if (robot) {
                    // Wrapped round the gap between his head and his box, the end hanging beside the chest screen.
                    rect(35f, 47.5f, 30f, 7.5f, 3f, f(Color(0xFFE0A23C))); d("M58 54 L62 69 L55 68Z", f(Color(0xFFE0A23C)))
                } else { rect(39f, 49f, 22f, 6f, 3f, f(Color(0xFFE0A23C))); d("M56 54 L58 66 L52 65Z", f(Color(0xFFE0A23C))) }
                Item.AMULET -> if (robot) {
                    // A gold chain past the chest screen, so the amulet hangs below it, not on it.
                    line("M39 51 Q38 74 50 74 Q62 74 61 51", 1.4f, if (mono) ink else Ink.gold)
                    circle(50f, 74f, 4.2f, f(Ink.gold)); circle(50f, 74f, 1.7f, dark(Color(0xFF2EC4D6)), stroke = 0f)
                } else {
                    line("M42 51 Q50 60 58 51", 1.3f)
                    circle(50f, 58f, 4f, f(Ink.gold)); circle(50f, 58f, 1.6f, dark(Color(0xFF2EC4D6)), stroke = 0f)
                }
                else -> Unit
            }
            // Head.
            if (buddy == Buddy.PRINCESS) d("M33 30 Q31 52 38 56 L62 56 Q69 52 67 30Z", f(hair!!))
            if (buddy == Buddy.GIRL) { circle(31f, 36f, 5f, f(hair!!)); circle(69f, 36f, 5f, f(hair)) }
            if (buddy == Buddy.MONKEY) { circle(33f, 33f, 6f, f(skin)); circle(67f, 33f, 6f, f(skin)) }
            when (buddy) {
                Buddy.ROBOT -> {
                    line("M50 20 L50 11"); circle(50f, 9f, 2.6f, f(Color(0xFFE7AD58)))
                    rect(29f, 20f, 42f, 28f, 3f, f(skin))
                    rect(33f, 25f, 34f, 15f, 2f, dark(Color(0xFF22304A)))
                    val eye = if (mono) Color.White else Color(0xFF8FF7EC)
                    scope.drawRect(eye, Offset(40f, 29f), Size(5f, 5f)); scope.drawRect(eye, Offset(55f, 29f), Size(5f, 5f))
                }
                Buddy.BOB -> {
                    rect(33f, 12f, 34f, 32f, 7f, f(skin), alpha = if (mono) 1f else 0.86f + osc(w * 9f) * 0.06f)
                    if (!mono) listOf(18f, 23f, 28f, 33f, 38f).forEach { y -> line("M36 $y L64 $y", 1f, Color.White, 0.4f) }
                    circle(44f, 28f, 2f, dark(Color(0xFF0E3345)), stroke = 0f); circle(56f, 28f, 2f, dark(Color(0xFF0E3345)), stroke = 0f)
                    line("M40 22 L47 21")
                    line(if (pose == BuddyPose.THINK) "M53 17 L60 15" else "M53 21 L60 22")
                    line(if (pose == BuddyPose.CRY) "M45 38 Q50 34 55 38" else "M45 35 Q50 39 55 35")
                }
                Buddy.SLOTH -> {
                    circle(50f, 34f, 16f, f(skin))
                    ellipse(50f, 36f, 12f, 10f, f(Color(0xFFEADCC0)))
                    ellipse(44f, 33f, 5f, 3f, dark(Color(0xFF4A3B2E)), -20f, 0f)
                    ellipse(56f, 33f, 5f, 3f, dark(Color(0xFF4A3B2E)), 20f, 0f)
                    circle(44.5f, 32.5f, 1f, Color.White, 0f); circle(55.5f, 32.5f, 1f, Color.White, 0f)
                    ellipse(50f, 38f, 2.3f, 1.5f, ink, stroke = 0f)
                    line(if (pose == BuddyPose.CRY) "M46 44 Q50 41 54 44" else "M46 41.5 Q50 44.5 54 41.5", 1.6f)
                }
                else -> {
                    circle(50f, 34f, 16f, f(skin))
                    if (buddy == Buddy.MONKEY) ellipse(50f, 39f, 10f, 7.5f, f(Color(0xFFEBCDA6)))
                    circle(44f, 33f, 1.9f, ink, 0f); circle(56f, 33f, 1.9f, ink, 0f)
                    line(if (pose == BuddyPose.CRY) "M45 43 Q50 39 55 43" else "M44.5 40 Q50 45 55.5 40")
                    if (hair != null && head !in setOf(Item.BEANIE, Item.KNIGHT_HELMET, Item.WIZARD_HAT))
                        d("M34 31 Q36 16 50 17 Q64 16 66 31 Q58 23 50 25 Q42 23 34 31Z", f(hair))
                }
            }
            if (outfit[Slot.FACE] == Item.SUNGLASSES) {
                // On Little Robot's dark visor the frame is light, or the glasses would vanish.
                val frame = if (!robot) ink else if (mono) Color.White else Color(0xFFDDE3EE)
                line("M${eyes[0] - 6} ${eyeY - 2} L${eyes[1] + 6} ${eyeY - 2}", color = frame)
                listOf(eyes[0], eyes[1]).forEach { x ->
                    shape(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(x - 5.5f, eyeY - 3f, x + 5.5f, eyeY + 4f), androidx.compose.ui.geometry.CornerRadius(2.5f, 2.5f))) },
                        if (mono) Color.Black else Color(0xFF15181C), strokeColor = frame)
                    if (robot && !mono) line("M${x - 3} ${eyeY - 1} l2.2 0", 1.1f, Color.White, 0.8f)
                }
            }
            drawHat(if (magnusCrown) null else head, magnusCrown, top, robot)
            if (pose == BuddyPose.CRY) {
                val tear = if (mono) Color.White else Color(0xFF6EC6F0)
                eyes.forEachIndexed { i, x ->
                    val y = eyeY + 5f
                    d("M$x $y q-3.6 6 0 8.2 q3.6 -2.2 0 -8.2Z", tear, 1.1f)
                    val x2 = x + if (i == 1) 4f else -4f
                    // The second tear keeps falling and fades as it goes.
                    val fall = if (moving) ((t * 1.3f + i * 0.5f) % 1f) * 14f * amp else 0f
                    scope.translate(0f, fall) {
                        d("M$x2 ${y + 9} q-2.8 4.6 0 6.4 q2.8 -1.8 0 -6.4Z", tear, 1f, alpha = 1f - fall / 16f)
                    }
                }
            }
            scope.translate(lx - lx0, ly - ly0) { drawHandItem(outfit[Slot.HAND], lx0, ly0) }
            if (pose == BuddyPose.PANCAKES) {
                scope.translate(0f, armBob) {
                    ellipse(50f, 69f, 15f, 3f, f(Color(0xFFE8ECEF)))
                    listOf(65f, 61.5f, 58f).forEach { y -> ellipse(50f, y, 10f, 3.2f, f(Ink.pancake)) }
                    line("M46 56.5 Q50 59 54 56.5", 1.3f, dark(Ink.syrup))
                }
                // Pancakes flying round Little Robot's head, spinning as they go.
                for (i in 0 until 4) {
                    val a = w * 1.8f + i * (Math.PI.toFloat() / 2f)
                    val x = 50f + cos(a) * 38f; val y = 22f + sin(a) * 16f
                    ellipse(x, y, 8f, 2.8f, f(Ink.pancake), Math.toDegrees(a.toDouble()).toFloat() % 360f * 0.3f - 20f)
                }
            }
            if (pose == BuddyPose.EUREKA) {
                // The bulb glows and its rays flicker.
                circle(80f, 18f, 7f + osc(w * 6f) * 0.9f, f(Color(0xFFF2C14E)))
                line("M77 26 L83 26 M80 6 L80 2 M90 18 L94 18 M70 18 L66 18", color = limb, alpha = if (moving) 1f - 0.45f * amp * (1f - abs(sin(t * 6f))) else 1f)
            }
            if (pose == BuddyPose.THINK) {
                // Thought bubbles swell in turn; the idea bobs above them.
                circle(74f, 20f, 2f + max(0f, osc(w * 3f)) * 0.8f, null, strokeColor = limb)
                circle(80f, 12f, 3f + max(0f, osc(w * 3f - 1.5f)) * 0.9f, null, strokeColor = limb)
                knight?.let { painter(it, 80f, -10f + osc(w * 2f) * 1.5f, 14f) }
            }
        } }
        drawPet(outfit[Slot.PET], petPiece, w)
    }

    /** Hats are drawn for a round head; Little Robot's box head is wider, so they are widened to fit it. */
    private fun drawHat(head: Item?, magnusCrown: Boolean, top: Float, robot: Boolean) {
        if (robot && head != Item.ADMIRAL_HAT) scope.scale(1.24f, 1f, Offset(50f, top)) { drawHatShape(head, magnusCrown, top, robot) }
        else drawHatShape(head, magnusCrown, top, robot)
    }

    private fun drawHatShape(head: Item?, magnusCrown: Boolean, top: Float, robot: Boolean) {
        val t = top
        if (magnusCrown) {
            d("M38 ${t + 2} L39 ${t - 10} L45 ${t - 4} L50 ${t - 12} L55 ${t - 4} L61 ${t - 10} L62 ${t + 2}Z", f(Ink.gold))
            circle(44f, t - 1, 1.8f, dark(Ink.red), 0f); circle(50f, t - 2, 1.8f, dark(Color(0xFF2EC4D6)), 0f); circle(56f, t - 1, 1.8f, dark(Ink.red), 0f)
            return
        }
        when (head) {
            Item.BEANIE -> { val b = if (robot) t + 5 else t + 12
                d("M33 $b Q33 ${t - 6} 50 ${t - 6} Q67 ${t - 6} 67 ${b}Z", f(Color(0xFF4E9A5B))); circle(50f, t - 8, 3.5f, f(Color(0xFFF3EEDF))) }
            Item.PARTY_HAT -> { d("M42 ${t + 1} L50 ${t - 20} L58 ${t + 1}Z", f(Color(0xFFE0812C))); circle(50f, t - 21, 2.5f, f(Color(0xFFF2C14E))) }
            Item.CROWN -> d("M39 ${t + 2} L41 ${t - 9} L46 ${t - 3} L50 ${t - 11} L54 ${t - 3} L59 ${t - 9} L61 ${t + 2}Z", f(Color(0xFFF2C14E)))
            Item.WIZARD_HAT -> {
                line("M31 ${t + 8} L69 ${t + 8}", 4f)
                d("M36 ${t + 7} L52 ${t - 26} L64 ${t + 7}Z", f(Color(0xFF5B4FA8)))
                circle(50f, t - 6, 1.6f, f(Color(0xFFF2C14E)), 0f); circle(55f, t, 1.3f, f(Color(0xFFF2C14E)), 0f)
            }
            Item.KNIGHT_HELMET -> {
                d("M32 ${t + 20} Q31 ${t - 5} 50 ${t - 5} Q69 ${t - 5} 68 ${t + 20}Z", f(Color(0xFFB8C2C8)))
                line("M38 ${t + 13} L62 ${t + 13}", 3f)
                d("M50 ${t - 5} Q54 ${t - 20} 64 ${t - 16} Q58 ${t - 10} 52 ${t - 4}", f(Color(0xFFC8463D)))
            }
            Item.ADMIRAL_HAT -> {
                // A tall bicorne, worn side to side.
                d("M23 ${t + 5} C25 ${t - 12} 38 ${t - 25} 50 ${t - 25} C62 ${t - 25} 75 ${t - 12} 77 ${t + 5} Q50 ${t - 3} 23 ${t + 5}Z", f(Color(0xFF1E2A4F)))
                line("M25 ${t + 4} Q50 ${t - 4} 75 ${t + 4}", 2.4f, if (mono) ink else Ink.gold)
                line("M28 ${t + 1} C30 ${t - 10} 40 ${t - 21} 50 ${t - 21} C60 ${t - 21} 70 ${t - 10} 72 ${t + 1}", 1.2f, if (mono) ink else Ink.gold)
                circle(50f, t - 10, 4.2f, f(Ink.gold))
                line("M50 ${t - 12.5f} L50 ${t - 7.5f} M48 ${t - 9.5f} L52 ${t - 9.5f}", 1.1f)
            }
            else -> Unit
        }
    }

    private fun drawHandItem(item: Item?, x: Float, y: Float) {
        when (item) {
            Item.PANCAKE_SHIELD -> {
                ellipse(x - 2, y + 3, 11f, 4f, f(Color(0xFFD9A35B))); ellipse(x - 2, y - 1, 11f, 4f, f(Color(0xFFE2B26C)))
                ellipse(x - 2, y - 5, 11f, 4f, f(Color(0xFFEBC27E)))
                line("M${x - 6} ${y - 6} Q${x - 2} ${y - 2} ${x + 3} ${y - 6}", color = dark(Ink.syrup))
            }
            Item.PAINTBRUSH -> {
                line("M${x + 4} ${y + 6} L${x - 8} ${y - 14}", 3f, dark(Color(0xFF8A5A2B)))
                d("M${x - 8} ${y - 14} q-3 -5 1 -8 q3 3 -1 8Z", f(Ink.red))
            }
            Item.GOLDEN_BANANA, Item.BANANA -> {
                val golden = item == Item.GOLDEN_BANANA
                d("M${x - 10} ${y - 10} Q${x - 12} ${y + 6} ${x + 6} ${y + 4} Q${x - 6} $y ${x - 6} ${y - 10}Z", f(if (golden) Ink.gold else Color(0xFFF4DC4A)))
                line("M${x - 10} ${y - 10} l-1 -3", if (golden) sw else 2.4f, if (golden) ink else dark(Ink.wood))
                if (!golden) line("M${x + 6} ${y + 4} l2 0", 2.4f, dark(Ink.wood))
            }
            Item.KNIGHT_SWORD -> {
                d("M${x - 1} ${y - 4} L${x - 6} ${y - 32} L${x - 3.5f} ${y - 36} L${x - 2} ${y - 32} L${x + 2} ${y - 4}Z", f(Color(0xFFD5DCE2)), 1.4f)
                line("M${x - 7} ${y - 4} L${x + 8} ${y - 6}", 3.2f, dark(Color(0xFFC9962E)))
                line("M${x + 0.5f} ${y - 3} L${x + 1.5f} ${y + 5}", 3f, dark(Ink.wood))
                circle(x + 1.7f, y + 6.5f, 2f, f(Color(0xFFC9962E)), 1f)
            }
            Item.MAGIC_WAND -> {
                val tx = x - 7; val ty = y - 20
                line("M${x + 2} ${y + 4} L$tx ${ty + 4}", 2.6f, dark(Color(0xFF3A2B4A)))
                val star = Path().apply {
                    for (i in 0 until 10) {
                        val a = -Math.PI / 2 + i * Math.PI / 5; val r = if (i % 2 == 1) 2.6 else 6.0
                        val px = (tx + kotlin.math.cos(a) * r).toFloat(); val py = (ty + kotlin.math.sin(a) * r).toFloat()
                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                shape(star, f(Ink.gold), 1.2f)
                if (!mono) listOf(tx - 9 to ty - 4, tx + 8 to ty - 8, tx + 6 to ty + 7).forEach { (a, b) ->
                    line("M${a - 2} $b L${a + 2} $b M$a ${b - 2} L$a ${b + 2}", 1.2f, Color(0xFFB57BD1))
                }
            }
            Item.PANCAKE, Item.FROST_PANCAKE -> {
                val frost = item == Item.FROST_PANCAKE
                line("M${x + 2} ${y + 4} L${x - 6} ${y - 12}", 2.6f, dark(Ink.wood))
                rect(x - 15, y - 17, 16f, 4f, 1.5f, f(Color(0xFFB8C2C8)), 1.2f)
                ellipse(x - 7, y - 19, 9f, 3f, f(if (frost) Color(0xFFBFE9FF) else Ink.pancake), stroke = 1.4f)
                if (frost) line("M${x - 7} ${y - 23} L${x - 7} ${y - 15} M${x - 10.5f} ${y - 21} L${x - 3.5f} ${y - 17} M${x - 3.5f} ${y - 21} L${x - 10.5f} ${y - 17}", 1f, dark(Color(0xFF5AA9D6)))
                else line("M${x - 11} ${y - 20} Q${x - 7} ${y - 17} ${x - 3} ${y - 20}", 1.3f, dark(Ink.syrup))
            }
            Item.TROPHY -> {
                d("M${x - 9} ${y - 14} L${x + 5} ${y - 14} L${x + 3} ${y - 5} Q${x - 2} $y ${x - 7} ${y - 5}Z", f(Ink.gold), 1.4f)
                line("M${x - 9} ${y - 12} q-5 1 -2 6 M${x + 5} ${y - 12} q5 1 2 6", 1.3f)
                rect(x - 5, y - 1, 6f, 3f, 0f, f(Color(0xFFC9962E)), 1.2f)
                rect(x - 8, y + 2, 12f, 4f, 1f, f(Ink.wood), 1.2f)
            }
            Item.TELESCOPE -> scope.rotate(-35f, Offset(x, y)) {
                rect(x - 20, y - 3.5f, 14f, 7f, 1.5f, f(Color(0xFF5B4FA8)), 1.4f)
                rect(x - 7, y - 2.6f, 12f, 5.2f, 1.2f, f(Color(0xFFC9962E)), 1.4f)
                rect(x - 23, y - 4.5f, 4f, 9f, 1f, f(Color(0xFF3A2B4A)), 1.2f)
            }
            Item.LANTERN -> {
                line("M$x $y L$x ${y + 4}", 1.4f)
                line("M${x - 3} ${y + 4} Q$x ${y + 1} ${x + 3} ${y + 4}", 1.3f)
                if (!mono) circle(x, y + 10.5f, 8f, Color(0xFFFFC86A), 0f, alpha = 0.25f)
                rect(x - 5, y + 4, 10f, 13f, 2f, f(Color(0xFFFFC86A)), 1.4f)
                rect(x - 6, y + 3, 12f, 2.6f, 1f, f(Color(0xFF3D2B3B)), 1f)
                rect(x - 6, y + 16, 12f, 2.6f, 1f, f(Color(0xFF3D2B3B)), 1f)
            }
            else -> Unit
        }
    }

    private fun drawPet(item: Item?, petPiece: Painter?, w: Float) {
        // Pets hop now and then.
        val hop = -max(0f, osc(w * 3f)) * 2f
        if (petPiece != null) { painter(petPiece, 72f, 91f + hop, 22f); return }
        when (item) {
            Item.LAVA_MONSTER -> {
                // The tail wags.
                scope.rotate(osc(w * 7f) * 18f, Offset(90f, 100f)) { line("M90 100 q8 -6 6 -12", 2.4f) }
                line("M74 112 L75 106 M84 112 L84 106 M90 112 L89 106", 2.6f)
                ellipse(82f, 102f, 11f, 6.5f, f(Color(0xFF3D2B3B)), stroke = 1.6f)
                ellipse(81f, 105f, 7f, 2.6f, f(Color(0xFFFFC34D)), stroke = 0f)
                line("M78 98 l3 3 l-1 3 M86 97 l-2 3", 1.2f, if (mono) ink else Color(0xFFFF7A2E))
                circle(70f, 96f, 6f, f(Color(0xFF3D2B3B)), 1.6f)
                d("M66 91 l-1 -4 l3 2 M72 90 l2 -4 l1 4", f(Color(0xFF3D2B3B)), 1.6f)
                circle(68.5f, 95.5f, 1.3f, dark(Color(0xFFFFC34D)), 0f)
                line("M65 99 Q68 101 71 99", 1.2f, if (mono) ink else Color(0xFFFF7A2E))
            }
            Item.LITTLE_FROSTY -> {
                // The wing flaps.
                scope.rotate(osc(w * 8f) * 20f, Offset(87f, 99f)) { d("M86 98 q6 -10 12 -6 q-4 2 -6 8Z", f(Color(0xFFD8F4FF)), 1.5f) }
                line("M90 106 q8 2 9 -4", 1.5f)
                ellipse(83f, 105f, 8f, 6f, f(Color(0xFF9ADCF2)), stroke = 1.5f)
                line("M79 111 l0 2 M87 111 l0 2", 2f)
                circle(75f, 97f, 5.5f, f(Color(0xFF9ADCF2)), 1.5f)
                line("M73 92 l-1 -3 M77 92 l1 -3", 1.5f)
                circle(74f, 96.5f, 1.1f, ink, 0f)
                line("M68 92 l2 2 M68 96 l2 0 M69 99 l2 -1", 1f, if (mono) ink else Color(0xFF7FC8E8))
            }
            Item.MOON_MOUSE -> {
                d("M70 110 L84 104 L84 112 L70 112Z", f(Color(0xFFF6D46A)), 1.4f)
                circle(76f, 109f, 1.1f, dark(Color(0xFFE2B744)), 0f)
                line("M96 108 q4 -2 2 -6", 1.4f)
                ellipse(90f, 107f, 6f, 4.5f, f(Color(0xFFB8B8C4)), stroke = 1.4f)
                circle(86f, 102f, 3f, f(Color(0xFFC9C9D4)), 1.4f)
                circle(85.5f, 105f, 0.9f, ink, 0f)
            }
            Item.FROG -> scope.translate(0f, hop) {
                ellipse(84f, 107f, 10f, 6f, f(Color(0xFF6CBF4A)), stroke = 1.5f)
                circle(79f, 101f, 3f, f(Color(0xFF6CBF4A)), 1.5f); circle(89f, 101f, 3f, f(Color(0xFF6CBF4A)), 1.5f)
                circle(79f, 101f, 1.1f, ink, 0f); circle(89f, 101f, 1.1f, ink, 0f)
                line("M79 108 Q84 111 89 108", 1.5f)
            }
            else -> Unit
        }
    }
}
