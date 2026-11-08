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
@Composable
fun FilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier, leadingIcon: (@Composable () -> Unit)? = null) {
    androidx.compose.material3.FilterChip(selected = selected, onClick = tap(onClick), label = label, modifier = modifier, leadingIcon = leadingIcon)
}
@Composable
fun SuggestionChip(onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.SuggestionChip(onClick = tap(onClick), label = label, modifier = modifier)
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
