package io.github.professorengineergit.cadence

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

private enum class Kind { CLOCK, BALL, CALENDAR, NOTE, CHECK }

/** Lets UI tests render the scene without motion, as with system animations turned off. */
object IntroMotion { @Volatile var forceStatic = false }

/** One round object on the floor of the screen. */
private class Body(val kind: Kind, val r: Float, var x: Float, var y: Float, val tint: Int) {
    /** The clock is drawn larger than it collides, so small objects can rest on its rim. */
    val drawn get() = if (kind == Kind.CLOCK) r * 1.45f else r
    var vx = 0f
    var vy = 0f
    var angle = 0f
    /** Set once the object has dropped below the ceiling; from then on the ceiling is solid for it. */
    var entered = false
    // Softened mass ratio keeps small objects from jittering on top of the heavy clock.
    val mass get() = r * sqrt(r)
}

/**
 * A tiny 2D physics world: circles under gravity, walls at the screen edges, and collisions with
 * restitution and friction. Gravity comes from the phone's gravity sensor, so tilting the phone
 * lets the objects fall, roll and pile up against each other.
 */
private class World(width: Float, height: Float, var top: Float) {
    var w = width
    var h = height
    val bodies = mutableListOf<Body>()
    /** Strongest impact speed in the last step, for haptics. */
    var impact = 0f

    init {
        // Large enough to rise above the glass, few enough to settle into a stable pile.
        // The clock goes in last so it rests on top of the pile at first.
        val specs = listOf(Kind.BALL to 0.16f, Kind.CHECK to 0.14f, Kind.BALL to 0.11f, Kind.NOTE to 0.16f, Kind.CALENDAR to 0.2f, Kind.CLOCK to 0.45f)
        // Lay them out just above the floor, left to right, so they drop a little and settle.
        var cx = 0f
        var rowBottom = height
        var rowHeight = 0f
        specs.forEachIndexed { i, (kind, size) ->
            val r = width * size
            if (cx + 2 * r > width) { cx = 0f; rowBottom -= rowHeight; rowHeight = 0f }
            val y = (rowBottom - r - height * 0.08f).coerceAtLeast(top + r)
            bodies += Body(kind, r, cx + r, y, i).also { it.entered = true }
            cx += 2 * r
            rowHeight = maxOf(rowHeight, 2 * r)
        }
    }

    /** The object held by a finger, pulled towards [target] by a soft spring so it keeps its weight. */
    var grabbed: Body? = null
    var target = Offset.Zero

    fun pick(p: Offset): Body? = bodies.filter { it.kind != Kind.CLOCK }.lastOrNull { hypot(it.x - p.x, it.y - p.y) <= it.r * 1.05f }
        ?: bodies.firstOrNull { it.kind == Kind.CLOCK && hypot(it.x - p.x, it.y - p.y) <= it.drawn }

    fun grab(b: Body, p: Offset) { grabbed = b; target = p; bodies.remove(b); bodies.add(b) }

    fun release() {
        grabbed?.let { b -> val speed = hypot(b.vx, b.vy); val max = h * 2.5f; if (speed > max) { b.vx *= max / speed; b.vy *= max / speed } }
        grabbed = null
    }

    /** Drops a new object in from above the ceiling. */
    fun spawn(kind: Kind, size: Float, delay: Int) {
        val r = w * size
        val x = (w * (0.15f + ((bodies.size * 0.31f) % 0.7f))).coerceIn(r, w - r)
        bodies += Body(kind, r, x, top - r - delay * w * 0.35f, bodies.size).also { it.vy = h * 0.4f }
    }

    fun step(dt: Float, gx: Float, gy: Float) {
        impact = 0f
        val substeps = 4
        val h2 = dt / substeps
        repeat(substeps) {
            grabbed?.let { b ->
                // A spring with damping: the object follows the finger but lags and sags under gravity.
                val ty = target.y.coerceAtLeast(top + b.r)
                b.vx += ((target.x - b.x) * 120f - b.vx * 16f) * h2
                b.vy += ((ty - b.y) * 120f - b.vy * 16f) * h2
            }
            for (b in bodies) {
                b.vx += gx * h2; b.vy += gy * h2
                val damping = 1f - 0.12f * h2
                b.vx *= damping; b.vy *= damping
                b.x += b.vx * h2; b.y += b.vy * h2
            }
            repeat(3) { solve() }
        }
        // Rolling: movement across the direction of gravity turns the object.
        val gl = hypot(gx, gy).coerceAtLeast(1f)
        for (b in bodies) {
            val across = (b.vx * gy - b.vy * gx) / gl
            b.angle += (across / b.r) * dt * (180f / PI.toFloat())
        }
    }

    private fun solve() {
        val e = 0.28f
        val friction = 0.04f
        for (b in bodies) {
            if (b.x - b.r < 0) { b.x = b.r; if (b.vx < 0) { impact = maxOf(impact, -b.vx); b.vx = -b.vx * e; b.vy *= 1 - friction } }
            if (b.x + b.r > w) { b.x = w - b.r; if (b.vx > 0) { impact = maxOf(impact, b.vx); b.vx = -b.vx * e; b.vy *= 1 - friction } }
            if (b.y + b.r > h) { b.y = h - b.r; if (b.vy > 0) { impact = maxOf(impact, b.vy); b.vy = -b.vy * e; b.vx *= 1 - friction } }
            // The ceiling sits below the header and becomes solid once an object has dropped in.
            if (!b.entered && b.y - b.r >= top) b.entered = true
            if (b.entered && b.y - b.r < top) { b.y = top + b.r; if (b.vy < 0) b.vy = -b.vy * e }
        }
        for (i in bodies.indices) for (j in i + 1 until bodies.size) {
            val a = bodies[i]; val b = bodies[j]
            val dx = b.x - a.x; val dy = b.y - a.y
            val dist = sqrt(dx * dx + dy * dy)
            val overlap = a.r + b.r - dist
            if (overlap <= 0f || dist < 0.0001f) continue
            val nx = dx / dist; val ny = dy / dist
            val total = a.mass + b.mass
            a.x -= nx * overlap * (b.mass / total); a.y -= ny * overlap * (b.mass / total)
            b.x += nx * overlap * (a.mass / total); b.y += ny * overlap * (a.mass / total)
            val rvx = b.vx - a.vx; val rvy = b.vy - a.vy
            val vn = rvx * nx + rvy * ny
            if (vn < 0) {
                impact = maxOf(impact, -vn)
                val jn = -(1 + e) * vn / (1 / a.mass + 1 / b.mass)
                a.vx -= jn * nx / a.mass; a.vy -= jn * ny / a.mass
                b.vx += jn * nx / b.mass; b.vy += jn * ny / b.mass
                val tx = -ny; val ty = nx
                val vt = rvx * tx + rvy * ty
                val jt = -vt * friction / (1 / a.mass + 1 / b.mass)
                a.vx -= jt * tx / a.mass; a.vy -= jt * ty / a.mass
                b.vx += jt * tx / b.mass; b.vy += jt * ty / b.mass
            }
        }
    }

    /** True when nothing moves noticeably any more. */
    fun resting(): Boolean = grabbed == null && bodies.all { it.entered && hypot(it.vx, it.vy) < w * 0.06f }

    /** A small hop for every object, against gravity and slightly sideways. */
    fun nudge(gx: Float, gy: Float, strength: Float) {
        val gl = hypot(gx, gy).coerceAtLeast(1f)
        bodies.forEachIndexed { i, b ->
            val side = if (i % 2 == 0) 1f else -1f
            b.vx += (-gy / gl * side) * strength * 0.25f - gx / gl * strength
            b.vy += (gx / gl * side) * strength * 0.25f - gy / gl * strength
        }
    }
}

/** Gravity in screen coordinates (x right, y down), in m/s². Points down without a sensor. */
@Composable
private fun rememberGravity(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val gravity = remember { mutableStateOf(Offset(0f, SensorManager.GRAVITY_EARTH)) }
    if (enabled) DisposableEffect(Unit) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            var smooth = Offset(0f, SensorManager.GRAVITY_EARTH)
            override fun onSensorChanged(event: SensorEvent) {
                val inPlane = Offset(-event.values[0], event.values[1])
                // Only the direction comes from the tilt; objects always fall with full weight.
                // Lying flat leaves little to go on, so "down" takes over smoothly.
                val keep = (4f - inPlane.getDistance()).coerceAtLeast(0f)
                val dir = Offset(inPlane.x, inPlane.y + keep)
                val target = dir / dir.getDistance().coerceAtLeast(0.001f) * SensorManager.GRAVITY_EARTH
                // Low-pass so sensor noise doesn't shake the pile.
                smooth += (target - smooth) * 0.15f
                gravity.value = smooth
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager?.unregisterListener(listener) }
    }
    return gravity
}

/**
 * Full-screen background for the setup: large matte objects lying on the floor of the screen.
 * They follow real gravity when the phone is tilted. The content sits on frosted glass above them.
 */
@Composable
fun IntroScene(step: Int, fill: Float, modifier: Modifier = Modifier, ceiling: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(0f)) {
    val ceilingPx = with(androidx.compose.ui.platform.LocalDensity.current) { ceiling.toPx() }
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val haptics = LocalHapticsEnabled.current
    val dark = colors.background.red * 0.3f + colors.background.green * 0.6f + colors.background.blue * 0.1f < 0.5f
    val motion = remember { !IntroMotion.forceStatic && android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val gravity by rememberGravity(motion)
    var world by remember { mutableStateOf<World?>(null) }
    var frame by remember { mutableLongStateOf(0L) }
    var seconds by remember { mutableFloatStateOf(0f) }
    val arc by animateFloatAsState(fill, spring(dampingRatio = 1f, stiffness = 30f), label = "arc")

    // The simulation sleeps once everything rests and wakes when the phone is tilted or the step changes.
    var wake by remember { mutableIntStateOf(0) }
    var wakeGravity by remember { mutableStateOf(gravity) }
    LaunchedEffect(gravity) {
        if ((gravity - wakeGravity).getDistance() > 0.8f) { wakeGravity = gravity; wake++ }
    }
    LaunchedEffect(world, motion, wake) {
        val w = world ?: return@LaunchedEffect
        if (!motion) {
            repeat(900) { w.step(1f / 60f, 0f, w.h * 2.0f) }
            frame++
            return@LaunchedEffect
        }
        var last = 0L
        var lastBuzz = 0L
        var calm = 0f
        while (calm < 0.8f) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(0f, 1f / 30f)
                last = now
                seconds += dt
                // At 1 g things fall about 1.6 screen heights per second squared: heavy, but not sluggish.
                val scale = w.h * 2.0f / SensorManager.GRAVITY_EARTH
                w.step(dt, gravity.x * scale, gravity.y * scale)
                if (haptics && w.impact > w.h * 0.9f && now - lastBuzz > 140_000_000L) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick); lastBuzz = now
                }
                frame++
                calm = if (w.resting()) calm + dt else 0f
            }
        }
    }
    // "Let's go" drops more objects onto the pile; later steps give everything a little hop.
    var lastStep by remember { mutableIntStateOf(step) }
    LaunchedEffect(step) {
        val w = world
        if (w != null && motion && step != lastStep) {
            if (lastStep == 0 && step == 1 && w.bodies.size < 10) {
                listOf(Kind.BALL to 0.10f, Kind.NOTE to 0.12f, Kind.CHECK to 0.10f, Kind.BALL to 0.08f).forEachIndexed { i, (kind, size) -> w.spawn(kind, size, i) }
            } else w.nudge(gravity.x, gravity.y, w.h * 0.7f)
            wake++
        }
        lastStep = step
    }

    val coral = colors.primary
    val lavender = if (dark) Color(0xFFB9A3D0) else Color(0xFFCDB9E6)
    val mint = if (dark) Color(0xFF8DBB9B) else Color(0xFFA9D4B4)
    val sand = if (dark) Color(0xFFE2C48F) else Color(0xFFF0D7A8)
    val cream = if (dark) Color(0xFFE9E1D6) else Color(0xFFFFFBF4)
    val balls = listOf(mint, sand, lavender)
    val shadowAlpha = if (dark) 0.45f else 0.16f

    Box(modifier.pointerInput(world, motion) {
        val w = world ?: return@pointerInput
        if (!motion) return@pointerInput
        // Pick up any object with a finger; it hangs from the finger with its weight and drops when let go.
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val body = w.pick(down.position) ?: return@awaitEachGesture
            down.consume()
            w.grab(body, down.position); wake++
            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                w.target = change.position
                change.consume()
            }
            w.release()
        }
    }.onSizeChanged { size ->
        if (size.width > 0 && size.height > 0) {
            val current = world
            if (current == null) world = World(size.width.toFloat(), size.height.toFloat(), ceilingPx)
            else { current.w = size.width.toFloat(); current.h = size.height.toFloat(); current.top = ceilingPx }
        }
    }) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") frame // redraw on every physics frame
            val w = world ?: return@Canvas
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, lavender.copy(alpha = if (dark) 0.08f else 0.14f)), startY = size.height * 0.4f))
            // The big clock lies underneath unless it's being held; held objects are drawn on top.
            val order = w.bodies.sortedBy { if (it === w.grabbed) 2 else if (it.kind == Kind.CLOCK) 0 else 1 }
            for (b in order) floorShadow(Offset(b.x, b.y), b.drawn, shadowAlpha)
            for (b in order) {
                val c = Offset(b.x, b.y)
                when (b.kind) {
                    Kind.CLOCK -> stopwatch(c, b.drawn, cream, lavender, coral, arc, b.angle + seconds * 6f, b.angle)
                    Kind.CALENDAR -> calendarToken(c, b.r, cream, coral, lavender, b.angle)
                    Kind.NOTE -> symbolBall(c, b.r, lavender, b.angle) { cc, rr -> note(cc, rr, cream) }
                    Kind.CHECK -> symbolBall(c, b.r, mint, b.angle) { cc, rr -> check(cc, rr, cream) }
                    Kind.BALL -> matteBall(c, b.r, balls[b.tint % balls.size])
                }
            }
        }
    }
}

/** Soft shadow on the floor, offset away from the light (top left). */
private fun DrawScope.floorShadow(c: Offset, r: Float, alpha: Float) {
    val sc = Offset(c.x + r * 0.10f, c.y + r * 0.16f)
    drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = alpha), Color.Black.copy(alpha = alpha * 0.45f), Color.Transparent), sc, r * 1.18f), r * 1.18f, sc)
}

/** Matte volume: gentle light from the top left, darker towards the edges, no gloss. */
private fun matteBrush(c: Offset, r: Float, base: Color) = Brush.linearGradient(
    listOf(lerp(base, Color.White, 0.10f), base, lerp(base, Color.Black, 0.14f)), Offset(c.x - r * 0.6f, c.y - r), Offset(c.x + r * 0.5f, c.y + r))

private fun DrawScope.edgeFalloff(c: Offset, r: Float, amount: Float) {
    drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = amount)), Offset(c.x - r * 0.12f, c.y - r * 0.18f), r * 1.1f), r, c)
}

private fun DrawScope.matteBall(c: Offset, r: Float, base: Color) {
    drawCircle(matteBrush(c, r, base), r, c)
    edgeFalloff(c, r, 0.16f)
}

private fun DrawScope.symbolBall(c: Offset, r: Float, base: Color, angle: Float, symbol: DrawScope.(Offset, Float) -> Unit) {
    matteBall(c, r, base)
    rotate(angle, c) { symbol(c, r) }
}

private fun DrawScope.note(c: Offset, r: Float, ink: Color) {
    val stem = Offset(c.x + r * 0.18f, c.y - r * 0.5f)
    drawLine(ink, stem, Offset(stem.x, c.y + r * 0.3f), strokeWidth = r * 0.14f, cap = StrokeCap.Round)
    drawOval(ink, Offset(c.x - r * 0.35f, c.y + r * 0.12f), Size(r * 0.55f, r * 0.42f))
    drawLine(ink, stem, Offset(stem.x + r * 0.35f, stem.y + r * 0.2f), strokeWidth = r * 0.14f, cap = StrokeCap.Round)
}

private fun DrawScope.check(c: Offset, r: Float, ink: Color) {
    drawLine(ink, Offset(c.x - r * 0.38f, c.y + r * 0.02f), Offset(c.x - r * 0.08f, c.y + r * 0.32f), strokeWidth = r * 0.18f, cap = StrokeCap.Round)
    drawLine(ink, Offset(c.x - r * 0.08f, c.y + r * 0.32f), Offset(c.x + r * 0.42f, c.y - r * 0.28f), strokeWidth = r * 0.18f, cap = StrokeCap.Round)
}

/** A round calendar badge: coral header band and two free-time slots, turning as it rolls. */
private fun DrawScope.calendarToken(c: Offset, r: Float, paper: Color, accent: Color, line: Color, angle: Float) {
    drawCircle(matteBrush(c, r, paper), r, c)
    rotate(angle, c) {
        drawArc(accent, 180f + 38f, 104f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
        drawRect(accent, Offset(c.x - r * 0.79f, c.y - r * 0.62f), Size(r * 1.58f, r * 0.27f))
        drawRoundRect(line, Offset(c.x - r * 0.55f, c.y - r * 0.12f), Size(r * 1.1f, r * 0.22f), CornerRadius(r * 0.11f))
        drawRoundRect(line.copy(alpha = 0.55f), Offset(c.x - r * 0.55f, c.y + r * 0.24f), Size(r * 0.7f, r * 0.22f), CornerRadius(r * 0.11f))
    }
    edgeFalloff(c, r, 0.12f)
}

/** Matte stopwatch seen from above. Shading stays fixed to the light; the dial turns with the body. */
private fun DrawScope.stopwatch(c: Offset, r: Float, face: Color, shell: Color, accent: Color, fill: Float, hand: Float, angle: Float) {
    val metal = lerp(shell, Color.Black, 0.10f)
    rotate(angle, c) {
        drawRoundRect(matteBrush(Offset(c.x, c.y - r * 1.18f), r * 0.2f, metal), Offset(c.x - r * 0.15f, c.y - r * 1.24f), Size(r * 0.3f, r * 0.26f), CornerRadius(r * 0.09f))
        drawRect(lerp(metal, Color.Black, 0.12f), Offset(c.x - r * 0.06f, c.y - r * 1.04f), Size(r * 0.12f, r * 0.1f))
        rotate(42f, c) { drawRoundRect(matteBrush(Offset(c.x, c.y - r * 1.1f), r * 0.15f, metal), Offset(c.x - r * 0.09f, c.y - r * 1.13f), Size(r * 0.18f, r * 0.18f), CornerRadius(r * 0.07f)) }
    }
    drawCircle(matteBrush(c, r, shell), r, c)
    edgeFalloff(c, r, 0.16f)
    val f = r * 0.8f
    drawCircle(lerp(shell, Color.Black, 0.18f), f + r * 0.035f, c)
    drawCircle(Brush.linearGradient(listOf(lerp(face, Color.Black, 0.05f), face), Offset(c.x, c.y - f), Offset(c.x, c.y + f)), f, c)
    drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.07f)), c, f), f, c)
    rotate(angle, c) {
        for (i in 0 until 12) {
            val a = i / 12f * 2 * PI
            val inner = f * if (i % 3 == 0) 0.74f else 0.82f
            drawLine(lerp(shell, Color.Black, 0.35f).copy(alpha = 0.55f), Offset(c.x + sin(a).toFloat() * inner, c.y - cos(a).toFloat() * inner), Offset(c.x + sin(a).toFloat() * f * 0.9f, c.y - cos(a).toFloat() * f * 0.9f), strokeWidth = r * 0.03f, cap = StrokeCap.Round)
        }
        val arcR = f * 0.6f
        val stroke = Stroke(r * 0.1f, cap = StrokeCap.Round)
        drawArc(accent.copy(alpha = 0.16f), -90f, 360f, false, Offset(c.x - arcR, c.y - arcR), Size(arcR * 2, arcR * 2), style = stroke)
        if (fill > 0.001f) drawArc(accent, -90f, 360f * fill.coerceIn(0f, 1f), false, Offset(c.x - arcR, c.y - arcR), Size(arcR * 2, arcR * 2), style = stroke)
    }
    rotate(hand, c) { drawLine(lerp(shell, Color.Black, 0.5f), c, Offset(c.x, c.y - f * 0.7f), strokeWidth = r * 0.04f, cap = StrokeCap.Round) }
    drawCircle(accent, r * 0.07f, c)
}
