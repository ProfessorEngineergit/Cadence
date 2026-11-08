@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.professorengineergit.cadence

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.EmojiEvents

val LocalHapticsEnabled = compositionLocalOf { true }

@Composable
fun CadenceFeedback(enabled: Boolean, content: @Composable () -> Unit) {
    val system = LocalHapticFeedback.current
    val feedback = remember(enabled, system) { object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { if (enabled) system.performHapticFeedback(hapticFeedbackType) }
    } }
    CompositionLocalProvider(LocalHapticsEnabled provides enabled, LocalHapticFeedback provides feedback, content = content)
}

@Composable
fun tap(action: () -> Unit, type: HapticFeedbackType = HapticFeedbackType.SegmentTick): () -> Unit {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return { if (enabled) feedback.performHapticFeedback(type); action() }
}

@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shapes: ButtonShapes = ButtonDefaults.shapes(), colors: ButtonColors = ButtonDefaults.buttonColors(), feedback: HapticFeedbackType = HapticFeedbackType.SegmentTick, content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.Button(onClick = tap(onClick, feedback), shapes = shapes, modifier = modifier, enabled = enabled, colors = colors, content = content)
}
@Composable
fun FilledTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shapes: ButtonShapes = ButtonDefaults.shapes(), colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(), content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.FilledTonalButton(onClick = tap(onClick), shapes = shapes, modifier = modifier, enabled = enabled, colors = colors, content = content)
}
@Composable
fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(), content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.TextButton(onClick = tap(onClick), modifier = modifier, enabled = enabled, colors = colors, content = content)
}
@Composable
fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50), content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick = tap(onClick), modifier = modifier, enabled = enabled, shape = shape, content = content)
}
@Composable
fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    androidx.compose.material3.IconButton(onClick = tap(onClick), modifier = modifier, enabled = enabled, content = content)
}
@Composable
fun FilledTonalIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(), content: @Composable () -> Unit) {
    androidx.compose.material3.FilledTonalIconButton(onClick = tap(onClick), modifier = modifier, enabled = enabled, colors = colors, content = content)
}
/**
 * Selection control shared across the app (same feel as Downlink): unselected is a softly rounded rectangle,
 * selected morphs into a full pill in the accent colour. A short squish on press makes it feel physical.
 */
@Composable
fun Pill(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false, content: @Composable RowScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val radius by androidx.compose.animation.core.animateDpAsState(if (selected) 24.dp else 12.dp, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f), label = "pill")
    val bg by androidx.compose.animation.animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHighest, label = "pillBg")
    val fg by androidx.compose.animation.animateColorAsState(if (selected) colors.onPrimary else colors.onSurface, label = "pillFg")
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.94f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 800f), label = "pillPress")
    val click = tap(onClick, if (selected) HapticFeedbackType.SegmentTick else HapticFeedbackType.SegmentFrequentTick)
    Row(modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(radius)).background(bg)
        .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), role = Role.RadioButton, onClick = click)
        .heightIn(min = 44.dp).padding(horizontal = if (compact) 4.dp else 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides fg) { ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() } }
    }
}

@Composable
fun FilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier, leadingIcon: (@Composable () -> Unit)? = null) {
    Pill(selected, onClick, modifier) {
        if (leadingIcon != null) { leadingIcon(); Spacer(Modifier.width(8.dp)) }
        label()
    }
}

/** A quick pick that applies something: a small tonal button with expressive shape morphing. */
@Composable
fun SuggestionChip(onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.FilledTonalButton(onClick = tap(onClick), shapes = ButtonDefaults.shapes(), modifier = modifier.heightIn(min = 40.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { ProvideTextStyle(MaterialTheme.typography.labelLarge) { label() } }
}

/** One of a few options, as a row of pills that share the width. */
@Composable
fun SegmentedChoice(options: List<String>, selected: Int, modifier: Modifier = Modifier.fillMaxWidth(), onSelect: (Int) -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label -> Pill(selected == i, { onSelect(i) }, Modifier.weight(1f), compact = true) { Text(label, maxLines = 1) } }
    }
}

/** Monday to Sunday as seven pills. */
@Composable
fun DayToggles(selected: Set<Int>, onToggle: (Int) -> Unit, labels: List<String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        (0..6).forEach { d -> Pill(d in selected, { onToggle(d) }, Modifier.weight(1f), compact = true) { Text(labels[d], maxLines = 1) } }
    }
}

@Composable
fun HapticSlider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, steps: Int = 0, onValueChangeFinished: (() -> Unit)? = null) {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    var last by remember { mutableFloatStateOf(value) }
    Slider(value = value, onValueChange = {
        val count = steps + 1
        val before = ((last - valueRange.start) / (valueRange.endInclusive - valueRange.start) * count).toInt()
        val after = ((it - valueRange.start) / (valueRange.endInclusive - valueRange.start) * count).toInt()
        if (enabled && before != after) feedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        last = it; onValueChange(it)
    }, valueRange = valueRange, steps = steps, onValueChangeFinished = onValueChangeFinished)
}

/** Haptic patterns for moments that matter, kept short so they feel like a reward and not an alarm. */
object Celebrate {
    fun vibrate(context: android.content.Context, enabled: Boolean, big: Boolean) {
        if (!enabled) return
        runCatching {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) context.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
                else @Suppress("DEPRECATION") context.getSystemService(android.os.Vibrator::class.java)
            if (!vibrator.hasVibrator()) return
            val effect = if (android.os.Build.VERSION.SDK_INT >= 30 && vibrator.areAllPrimitivesSupported(android.os.VibrationEffect.Composition.PRIMITIVE_CLICK, android.os.VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, android.os.VibrationEffect.Composition.PRIMITIVE_TICK)) {
                val c = android.os.VibrationEffect.Composition.PRIMITIVE_CLICK
                val rise = android.os.VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
                val tick = android.os.VibrationEffect.Composition.PRIMITIVE_TICK
                android.os.VibrationEffect.startComposition().apply {
                    if (big) { addPrimitive(rise, 0.6f); addPrimitive(c, 1f, 40); addPrimitive(tick, 0.7f, 90); addPrimitive(tick, 0.5f, 80); addPrimitive(c, 0.9f, 120) }
                    else { addPrimitive(c, 0.8f); addPrimitive(tick, 0.6f, 90); addPrimitive(c, 1f, 70) }
                }.compose()
            } else android.os.VibrationEffect.createWaveform(if (big) longArrayOf(0, 40, 70, 30, 60, 30, 90, 70) else longArrayOf(0, 30, 80, 50), -1)
            vibrator.vibrate(effect)
        }
    }
}

@Composable
fun CelebrationOverlay(celebration: Celebration, onDone: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    val pop = remember { androidx.compose.animation.core.Animatable(0.6f) }
    val particles = remember { List(if (celebration.big) 70 else 36) { kotlin.random.Random.nextFloat() to kotlin.random.Random.nextFloat() } }
    val palette = listOf(colors.primary, colors.secondary, colors.tertiary, colors.primaryContainer, colors.tertiaryContainer)
    LaunchedEffect(celebration) {
        kotlinx.coroutines.coroutineScope {
            launch { pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 380f)) }
            progress.animateTo(1f, androidx.compose.animation.core.tween(if (celebration.big) 2800 else 2000, easing = androidx.compose.animation.core.LinearEasing))
        }
        onDone()
    }
    Box(Modifier.fillMaxSize().androidx_clickable(onDone), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val t = progress.value
            particles.forEachIndexed { i, (a, b) ->
                val angle = a * 2 * Math.PI
                val speed = 0.35f + b * 0.65f
                val x = size.width / 2 + kotlin.math.cos(angle).toFloat() * size.width * 0.55f * speed * t
                val y = size.height * 0.42f + kotlin.math.sin(angle).toFloat() * size.height * 0.25f * speed * t + size.height * 0.5f * t * t
                val alpha = (1f - t).coerceIn(0f, 1f)
                rotate(t * 720f * (if (i % 2 == 0) 1 else -1), androidx.compose.ui.geometry.Offset(x, y)) {
                    drawRoundRect(palette[i % palette.size].copy(alpha = alpha), topLeft = androidx.compose.ui.geometry.Offset(x - 7f, y - 4f), size = androidx.compose.ui.geometry.Size(14f + b * 10f, 8f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                }
            }
        }
        val fade = (1f - ((progress.value - 0.8f) / 0.2f).coerceIn(0f, 1f))
        Surface(shape = RoundedCornerShape(32.dp), color = colors.surfaceContainerHigh, shadowElevation = 12.dp,
            modifier = Modifier.padding(32.dp).androidx_scale(pop.value).androidx_alpha(fade)) {
            Column(Modifier.padding(horizontal = 28.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (celebration.streak > 0 && celebration.title.contains(celebration.streak.toString())) androidx.compose.material.icons.Icons.Rounded.LocalFireDepartment else androidx.compose.material.icons.Icons.Rounded.EmojiEvents,
                    null, Modifier.size(52.dp), tint = colors.primary)
                Text(celebration.title, style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(celebration.body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

private fun Modifier.androidx_clickable(onClick: () -> Unit) = this.then(Modifier.pointerInput(Unit) { detectTapGestures { onClick() } })
private fun Modifier.androidx_scale(value: Float) = this.then(Modifier.graphicsLayer { scaleX = value; scaleY = value })
private fun Modifier.androidx_alpha(value: Float) = this.then(Modifier.graphicsLayer { alpha = value })
