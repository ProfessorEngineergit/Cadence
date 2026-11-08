package io.github.professorengineergit.cadence

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.*

// Geometry and interaction taken from the user's Downlink GlassTabBar.
@Composable
fun CadenceTabBar(selected: Int, haze: HazeState, onSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.clip(CircleShape).hazeEffect(haze, HazeStyle(backgroundColor = colors.surface, tints = listOf(HazeTint(colors.surfaceContainerHigh.copy(alpha = 0.2f))), blurRadius = 24.dp, noiseFactor = 0f))
            .background(colors.surfaceContainerHigh.copy(alpha = 0.1f)).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Heute" to Icons.Rounded.WbSunny, "Verlauf" to Icons.AutoMirrored.Rounded.ListAlt, "Rhythmus" to Icons.Rounded.Tune, "Optionen" to Icons.Rounded.Settings).forEachIndexed { i, item ->
                val active = selected == i
                val bg by animateColorAsState(if (active) colors.primary else Color.Transparent, label = "Tab-Hintergrund")
                val fg by animateColorAsState(if (active) colors.onPrimary else colors.onSurface.copy(alpha = 0.85f), label = "Tab-Symbol")
                Row(Modifier.height(52.dp).clip(CircleShape).background(bg).semantics { this.selected = active }
                    .clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) { if (!active && enabled) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(i) }
                    .padding(horizontal = if (active) 20.dp else 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.second, translate(item.first, LocalEnglish.current), tint = fg, modifier = Modifier.size(24.dp))
                    if (active) { Spacer(Modifier.width(10.dp)); Text(item.first, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                }
            }
        }
    }
}
