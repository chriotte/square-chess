package com.dataespresso.squarechess

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin

/**
 * The worlds behind Home and the menus, drawn from shapes so the APK carries no pictures. Nothing
 * moves: the drawing is built once per size and then only repainted. [veil] lays the world's
 * background colour over the picture, for screens with text straight on the page.
 */
@Composable fun WorldBackground(world: World, modifier: Modifier, veil: Float = 0f, unit: Float? = null) {
    if (world == World.CLASSIC) return
    val veilColor = worldPalette(world).background.copy(alpha = 1f)
    Spacer(modifier.drawWithCache {
        val art = buildArt(world, size.width, size.height, unit ?: min(size.width / 282f, size.height / 620f))
        onDrawBehind {
            art.draw(this)
            if (veil > 0f) drawRect(veilColor.copy(alpha = veil))
        }
    })
}

private class Art(val w: Float, val h: Float, val u: Float) {
    private class Op(val path: Path, val color: Color, val alpha: Float, val stroke: Float)
    private val ops = mutableListOf<Op>()
    var sky: Brush? = null

    fun fill(path: Path, color: Color, alpha: Float = 1f) { ops += Op(path, color, alpha, 0f) }
    fun stroke(path: Path, color: Color, width: Float, alpha: Float = 1f) { ops += Op(path, color, alpha, width) }
    fun circle(cx: Float, cy: Float, r: Float, color: Color, alpha: Float = 1f, stroke: Float = 0f) {
        val p = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
        ops += Op(p, color, alpha, stroke)
    }
    fun oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, rotate: Float = 0f, alpha: Float = 1f, stroke: Float = 0f) {
        val p = Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }
        if (rotate != 0f) p.transform(Matrix().apply { translate(cx, cy); rotateZ(rotate); translate(-cx, -cy) })
        ops += Op(p, color, alpha, stroke)
    }
    fun poly(color: Color, vararg pts: Float) {
        val p = Path().apply { moveTo(pts[0], pts[1]); var i = 2; while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }; close() }
        fill(p, color)
    }
    fun rect(x: Float, y: Float, rw: Float, rh: Float, color: Color) {
        fill(Path().apply { addRect(Rect(x, y, x + rw, y + rh)) }, color)
    }
    /** A filled band under a sine wave, from [base] down to the bottom edge. */
    fun wave(base: Float, amp: Float, len: Float, phase: Float, color: Color, alpha: Float = 1f) {
        val p = Path().apply {
            moveTo(0f, h); lineTo(0f, base)
            var x = 0f
            while (x <= w + 6 * u) { lineTo(x, base + sin(x / len + phase) * amp); x += 6 * u }
            lineTo(w, h); close()
        }
        fill(p, color, alpha)
    }
    fun waveY(x: Float, base: Float, amp: Float, len: Float, phase: Float) = base + sin(x / len + phase) * amp
    /** How many times wider than the 282-unit design the screen is: wider screens get more trees. */
    val repeat: Float get() = (w / (282f * u)).coerceIn(0.3f, 8f)

    fun draw(scope: DrawScope) = with(scope) {
        sky?.let { drawRect(it) }
        for (op in ops) {
            if (op.stroke > 0f) drawPath(op.path, op.color, op.alpha, Stroke(op.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            else drawPath(op.path, op.color, op.alpha)
        }
    }
}

private fun frac(x: Float) = x - kotlin.math.floor(x)

private fun buildArt(world: World, w: Float, h: Float, u: Float): Art = Art(w, h, u).apply {
    when (world) {
        World.FOREST -> forest()
        World.JUNGLE -> jungle()
        World.OCEAN -> ocean()
        World.SPACE -> space()
        World.CLASSIC -> Unit
    }
}

private fun Art.forest() {
    sky = Brush.verticalGradient(0f to Color(0xFFBFE2CB), 0.6f to Color(0xFFEAF0D2), 1f to Color(0xFFE9E3C0))
    circle(w * 0.8f, h * 0.1f, 22f * u, Color(0xFFF7E3A0))
    val layers = listOf(
        Triple(Color(0xFF9CC59A), 0.42f, Color(0xFF4E7F48)),
        Triple(Color(0xFF7FAF73), 0.6f, Color(0xFF3E6E3B)),
        Triple(Color(0xFF5E8E52), 0.78f, Color(0xFF2F5A31))
    )
    val amps = listOf(18f, 14f, 12f); val lens = listOf(40f, 30f, 24f); val phases = listOf(0f, 2f, 4f)
    layers.forEachIndexed { li, (hill, base, tree) ->
        val b = h * base; val a = amps[li] * u; val l = lens[li] * u; val p = phases[li]
        wave(b, a, l, p, hill)
        val count = ceil(9 * repeat).toInt()
        for (i in 0 until count) {
            val x = frac(i * 0.618f + li * 0.29f) * w
            val y = waveY(x, b, a, l, p) + 4 * u
            val t = (22 + li * 10 + (i * 37) % 9) * u
            poly(tree, x, y - t, x - t * 0.38f, y, x + t * 0.38f, y)
            poly(tree, x, y - t * 1.45f, x - t * 0.28f, y - t * 0.55f, x + t * 0.28f, y - t * 0.55f)
        }
    }
    // A red toadstool near the bottom.
    rect(w * 0.15f - 1.5f * u, h * 0.93f, 3 * u, 7 * u, Color(0xFFF3EEDF))
    circle(w * 0.15f, h * 0.93f, 5 * u, Color(0xFFC8463D))
}

private fun Art.jungle() {
    sky = Brush.verticalGradient(0f to Color(0xFFD9F0B0), 0.45f to Color(0xFF9FCB7E), 1f to Color(0xFF3F7A45))
    // The Old Temple.
    val tx = w * 0.62f; val ty = h * 0.62f
    listOf(60f to 0f, 46f to -12f, 32f to -24f, 18f to -36f).forEach { (tw, dy) ->
        rect(tx - tw * u, ty + dy * u, tw * 2 * u, 12 * u, Color(0xFF8E9A6C))
    }
    rect(tx - 6 * u, ty - 6 * u, 12 * u, 18 * u, Color(0xFF5C6648))
    // The tiny robots' banana-pancake restaurant: a hut with a pancake sign.
    val rx = w * 0.08f; val ry = h * 0.6f
    rect(rx, ry, 70 * u, 34 * u, Color(0xFFF6E7C4))
    poly(Color(0xFFC8463D), rx - 4 * u, ry, rx + 35 * u, ry - 16 * u, rx + 74 * u, ry)
    listOf(24f, 19f, 14f).forEach { dy -> oval(rx + 35 * u, ry + dy * u, 12 * u, 3.4f * u, Color(0xFFF3B854)) }
    oval(rx + 35 * u, ry + 11 * u, 5 * u, 2 * u, Color(0xFF8A4B1C))
    rect(rx + 8 * u, ry + 14 * u, 10 * u, 20 * u, Color(0xFF8A5A2B))
    wave(h * 0.74f, 8 * u, 26 * u, 1f, Color(0xFF4F8A3F))
    wave(h * 0.86f, 6 * u, 20 * u, 3f, Color(0xFF356B37))
    // Vines with leaves, and banana bunches on every other vine.
    val vines = ceil(6 * repeat).toInt()
    for (i in 0 until vines) {
        val x = (i + 0.35f) * w / vines
        val len = h * (0.18f + frac(i * 0.37f) * 0.22f)
        val vine = Path().apply {
            moveTo(x, 0f); var y = 0f
            while (y <= len) { lineTo(x + sin(y / (14 * u) + i) * 5 * u, y); y += 8 * u }
        }
        stroke(vine, Color(0xFF3F7A3A), 2.5f * u)
        var y = 14 * u; var left = false
        while (y < len) {
            val lx = x + sin(y / (14 * u) + i) * 5 * u
            oval(lx + (if (left) -6 else 6) * u, y, 6 * u, 2.6f * u, Color(0xFF5DA34A), if (left) -25f else 25f)
            y += 18 * u; left = !left
        }
        if (i % 2 == 0) {
            val bx = x + sin(len / (14 * u) + i) * 5 * u
            for (b in 0 until 3) {
                val sx = bx - 4 * u + b * 4 * u
                val banana = Path().apply {
                    moveTo(sx, len)
                    quadraticTo(sx - 3 * u, len + 8 * u, sx + 3 * u, len + 12 * u)
                    quadraticTo(sx + 2 * u, len + 6 * u, sx + 1 * u, len)
                    close()
                }
                fill(banana, Color(0xFFF2C230)); stroke(banana, Color(0xFFB68A12), 0.6f * u)
            }
        }
    }
    // Sloth, hanging from a branch by one arm.
    val sx = w * 0.78f; val sy = h * 0.12f
    stroke(Path().apply { moveTo(w * 0.55f, h * 0.07f); quadraticTo(w * 0.8f, h * 0.05f, w, h * 0.09f) }, Color(0xFF6B4A2B), 5 * u)
    stroke(Path().apply { moveTo(sx, h * 0.065f); lineTo(sx, sy + 4 * u) }, Color(0xFF7D6B57), 3 * u)
    oval(sx + u, sy + 16 * u, 9 * u, 12 * u, Color(0xFF9A8670))
    circle(sx - 6 * u, sy + 8 * u, 7 * u, Color(0xFF9A8670))
    oval(sx - 6 * u, sy + 9 * u, 5 * u, 4.2f * u, Color(0xFFEADCC0))
    oval(sx - 8.5f * u, sy + 8 * u, 2.2f * u, 1.2f * u, Color(0xFF4A3B2E))
    oval(sx - 3.5f * u, sy + 8 * u, 2.2f * u, 1.2f * u, Color(0xFF4A3B2E))
    // Big leaves at the bottom.
    oval(0f, h, 46 * u, 18 * u, Color(0xFF2E6B34), -30f)
    oval(w, h, 46 * u, 18 * u, Color(0xFF2E6B34), 210f)
    oval(w * 0.5f, h + 10 * u, 46 * u, 18 * u, Color(0xFF2E6B34), -80f)
}

private fun Art.ocean() {
    sky = Brush.verticalGradient(0f to Color(0xFFCDEEF5), 0.45f to Color(0xFF8FCFE0), 1f to Color(0xFF2F7392))
    wave(h * 0.16f, 5 * u, 22 * u, 0f, Color(0xFFA9DDEA), 0.85f)
    wave(h * 0.34f, 7 * u, 28 * u, 1.5f, Color(0xFF86C9DD), 0.85f)
    wave(h * 0.55f, 8 * u, 34 * u, 3f, Color(0xFF5EAECB), 0.85f)
    wave(h * 0.76f, 9 * u, 30 * u, 4.5f, Color(0xFF3F8FB0), 0.85f)
    val weeds = ceil(7 * repeat).toInt()
    for (i in 0 until weeds) {
        val x = w * (i + 0.5f) / weeds
        val weed = Path().apply {
            moveTo(x, h)
            quadraticTo(x - 9 * u, h - 30 * u, x + 2 * u, h - 55 * u)
            quadraticTo(x + 10 * u, h - 75 * u, x - 2 * u, h - 95 * u)
        }
        stroke(weed, Color(0xFF2E7A55), 5 * u)
    }
    listOf(Triple(0.2f, 0.62f, Color(0xFFF2C14E)), Triple(0.72f, 0.44f, Color(0xFFE0812C)), Triple(0.5f, 0.86f, Color(0xFFF08A7A)))
        .forEachIndexed { i, (fx, fy, c) ->
            val x = w * fx; val y = h * fy; val d = if (i % 2 == 1) -1f else 1f
            oval(x, y, 11 * u, 6 * u, c)
            poly(c, x - 10 * u * d, y, x - 18 * u * d, y - 6 * u, x - 18 * u * d, y + 6 * u)
            circle(x + 5 * u * d, y - 1.5f * u, 1.4f * u, Color(0xFF0E3345))
        }
    val bubbles = ceil(14 * repeat).toInt()
    for (i in 0 until bubbles) {
        circle(frac(i * 0.618f) * w, frac(i * 0.381f + 0.1f) * h, (2 + i % 3) * u, Color.White, 0.6f, 1.2f * u)
    }
}

private fun Art.space() {
    sky = Brush.verticalGradient(0f to Color(0xFF0B0D26), 0.6f to Color(0xFF1B1E4A), 1f to Color(0xFF2C2463))
    val stars = ceil(70 * repeat).toInt().coerceAtMost(400)
    for (i in 0 until stars) {
        circle(frac(i * 0.618f) * w, frac(i * 0.7548f) * h, (0.5f + frac(i * 0.37f) * 1.3f) * u, Color.White, 0.35f + frac(i * 0.53f) * 0.6f)
    }
    // The Cheese Moon, with two of its mice on top.
    val mx = w * 0.76f; val my = h * 0.14f; val mr = 36 * u
    circle(mx, my, mr, Color(0xFFF6D46A))
    listOf(Triple(-0.4f, -0.3f, 0.22f), Triple(0.3f, -0.1f, 0.16f), Triple(-0.1f, 0.35f, 0.2f), Triple(0.45f, 0.4f, 0.12f), Triple(-0.55f, 0.2f, 0.1f))
        .forEach { (dx, dy, r) -> circle(mx + dx * mr, my + dy * mr, r * mr, Color(0xFFE2B744)) }
    oval(mx - mr * 0.2f, my - mr - 2 * u, 4 * u, 2.6f * u, Color(0xFFB8B8C4))
    circle(mx - mr * 0.2f - 3 * u, my - mr - 4 * u, 1.5f * u, Color(0xFFB8B8C4))
    oval(mx + mr * 0.35f, my - mr + u, 3.4f * u, 2.2f * u, Color(0xFFB8B8C4))
    // A ringed planet.
    val px = w * 0.18f; val py = h * 0.5f; val pr = 26 * u
    circle(px, py, pr, Color(0xFFC46ADB))
    fill(Path().apply { moveTo(px - pr, py); arcTo(Rect(px - pr, py - pr, px + pr, py + pr), 180f, -180f, false); close() }, Color(0xFFA54FBF))
    oval(px, py, pr * 1.8f, pr * 0.45f, Color(0xFFF3B854), -15f, stroke = 3 * u)
    // Bob's ship: pearl-white, an orange stripe, a glass dome and warm portholes.
    val ship = Matrix().apply { translate(w * 0.66f, h * 0.8f); rotateZ(-12f); scale(1.1f * u, 1.1f * u) }
    fun local(p: Path) = p.apply { transform(ship) }
    stroke(local(Path().apply { moveTo(-20f, 4f); lineTo(-34f, 16f); moveTo(20f, 4f); lineTo(34f, 16f) }), Color(0xFFC9C1B2), 3.3f * u)
    fill(local(Path().apply { addOval(Rect(-46f, -15f, 46f, 15f)) }), Color(0xFFEFE6D6))
    fill(local(Path().apply { addRect(Rect(-46f, -2f, 46f, 2f)) }), Color(0xFFE7883A))
    fill(local(Path().apply { addOval(Rect(-8f, -21f, 20f, -3f)) }), Color(0xFF8FE3F5), 0.85f)
    listOf(-24f, -12f, 0f).forEach { x -> fill(local(Path().apply { addOval(Rect(x - 2.6f, 4.4f, x + 2.6f, 9.6f)) }), Color(0xFFFFC86A)) }
    fill(local(Path().apply { addRect(Rect(24f, -18f, 29f, -9f)) }), Color(0xFFC9C1B2))
    fill(local(Path().apply { moveTo(-46f, 0f); lineTo(-62f, -6f); lineTo(-62f, 6f); close() }), Color(0xFFFF9F43))
    // A pancake drifting past.
    oval(w * 0.35f, h * 0.24f, 13 * u, 4.4f * u, Color(0xFFF3B854), -18f)
}
