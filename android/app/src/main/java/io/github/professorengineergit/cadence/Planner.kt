@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.professorengineergit.cadence

import android.app.TimePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun GoalPill(state: CadenceState, onClick: () -> Unit) {
    Surface(onClick = tap(onClick), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = CircleShape) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(when (state.activeGoal.kind) { GoalKind.MUSIC -> Icons.Rounded.MusicNote; GoalKind.STUDY -> Icons.Rounded.School; GoalKind.OTHER -> Icons.Rounded.AutoAwesome }, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(state.activeGoal.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                val total = state.selectedSessions.sumOf { it.durationSeconds } / 60
                Text(if (state.activeGoal.dueDate == null) words("Deine laufende Routine", "Your ongoing routine") else words("$total / ${state.activeGoal.totalTarget} Min. bis ${state.activeGoal.dueDate}", "$total / ${state.activeGoal.totalTarget} min by ${state.activeGoal.dueDate}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.activeGoal.dueDate != null) LinearProgressIndicator(progress = { (total.toFloat() / state.activeGoal.totalTarget).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 1.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant)
            }
            Icon(Icons.Rounded.ExpandMore, words("Übeziel wechseln oder hinzufügen", "Switch or add practice goal"), Modifier.size(22.dp))
        }
    }
}

@Composable
fun PlanScreen(state: CadenceState, stats: PracticeStats, onSettings: () -> Unit, onGoal: () -> Unit, onDayPlan: (Int, Int, Availability) -> Unit, onWeeklyPlan: (Int, Set<Int>) -> Unit) {
    var selected by remember { mutableStateOf<Int?>(null) }
    var weeklyEditor by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val windows = state.reminders.windows
    val totalAvailable = windows.sumOf { it.minutes }
    val allocated = state.practiceGoals.sumOf { it.dayTargets.sum() }
    val maxAvailable = windows.maxOf { it.minutes }.coerceAtLeast(60).toFloat()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 124.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PageHeader("", onSettings)
        Text(words("Deine Woche.\nDein Rhythmus.", "Your week.\nYour rhythm."), style = MaterialTheme.typography.headlineLarge)
        GoalPill(state, onGoal)
        Surface(onClick = tap({ weeklyEditor = true }), color = colors.primaryContainer, shape = RoundedCornerShape(28.dp)) {
            Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(words("WOCHENZIEL · ANTIPPEN", "WEEKLY GOAL · TAP TO EDIT"), style = MaterialTheme.typography.labelMedium)
                    Text("${state.goals.sum()} min", style = MaterialTheme.typography.headlineLarge)
                    Text(words("für ${state.activeGoal.title}", "for ${state.activeGoal.title}"), style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.Rounded.Edit, null, Modifier.size(28.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(words("An diesen Tagen übe ich", "My practice days"), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val selectedDays = (0..6).filter { state.goals[it] > 0 }.toSet()
                (0..6).forEach { i -> FilterChip(selected = i in selectedDays, onClick = {
                    val next = if (i in selectedDays) selectedDays - i else selectedDays + i
                    val capacity = next.sumOf { (windows[it].end - windows[it].start).coerceAtMost(240) }
                    val total = if (next.isEmpty()) 0 else (state.goals.sum().takeIf { it > 0 } ?: 100).coerceIn(next.size * 5, capacity)
                    onWeeklyPlan(total, next)
                }, label = { Text(dayShort(i)) }) }
            }
            Text(words("Wochenziel wird auf deine Übetage verteilt. Für genaue Uhrzeiten oder eigene Tagesziele: unten auf den Tag tippen.", "Your weekly goal is shared across these days. Tap a day below for exact times or an individual daily target."), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        state.activeGoal.dueDate?.let { due ->
            val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(stats.today, due).coerceIn(0, 3650)
            val planned = (0..daysLeft).sumOf { state.goals[stats.today.plusDays(it).dayOfWeek.value - 1] } - minOf(stats.todayMinutes.toInt(), stats.todayGoal)
            val remaining = (state.activeGoal.totalTarget - state.selectedSessions.sumOf { it.durationSeconds } / 60).coerceAtLeast(0)
            Surface(shape = RoundedCornerShape(24.dp), color = if (remaining > planned) colors.primaryContainer else colors.tertiaryContainer) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(words("Dein Zieltermin im Blick", "Keep your deadline in sight"), style = MaterialTheme.typography.titleMedium)
                    Text(words("Noch $remaining Min. bis $due · $planned Min. in deinem aktuellen Plan.", "$remaining min left by $due · $planned min in your current plan."), style = MaterialTheme.typography.bodyMedium)
                    if (remaining > planned) Text(words("Für dein Gesamtziel fehlen im Plan noch ${remaining - planned} Minuten. Erhöhe dein Wochenziel oder passe einzelne Tage an.", "Your schedule is ${remaining - planned} minutes short of the total target. Increase your weekly goal or adjust individual days."), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerLow) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(words("Zeit haben. Zeit nehmen.", "Make room. Make progress."), style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Legend(colors.secondaryContainer, words("Verfügbar", "Available")); Legend(colors.primary, words("Dein Übeziel", "Practice goal"))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    (0..6).forEach { i ->
                        val window = windows[i]
                        val dayLabel = dayName(i)
                        val goal = state.goals[i]
                        val total = state.practiceGoals.sumOf { it.dayTargets[i] }
                        val spoken = words("$dayLabel, $goal Minuten Ziel, ${window.minutes} Minuten verfügbar", "$dayLabel, $goal minutes target, ${window.minutes} minutes available")
                        val availableHeight = (window.minutes / maxAvailable * 128).coerceAtLeast(6f)
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = tap({ selected = i }))
                            .padding(vertical = 8.dp).semantics { contentDescription = spoken }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (goal == 0) "–" else "$goal", style = MaterialTheme.typography.labelMedium, color = if (total > window.minutes) colors.error else colors.primary)
                            Box(Modifier.height(128.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                Box(Modifier.width(27.dp).height(availableHeight.dp).clip(CircleShape).background(colors.secondaryContainer))
                                if (goal > 0) Box(Modifier.width(27.dp).height((goal / maxAvailable * 128).coerceIn(6f, 128f).dp).clip(CircleShape).background(colors.primary))
                            }
                            Surface(color = if (stats.today.dayOfWeek.value == i + 1) colors.primary else androidx.compose.ui.graphics.Color.Transparent, contentColor = if (stats.today.dayOfWeek.value == i + 1) colors.onPrimary else colors.onSurfaceVariant, shape = CircleShape) {
                                Text(dayShort(i), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 5.dp, vertical = 7.dp))
                            }
                        }
                    }
                }
                Text(words("Tippe auf einen Wochentag. Verschiebe dein Zeitfenster und wähle, wie viel davon du üben möchtest.", "Tap a weekday. Move your availability window and choose how much of it you want to practise."), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = colors.tertiaryContainer) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(words("Ein Plan für alle deine Ziele.", "One schedule for all your goals."), style = MaterialTheme.typography.titleMedium)
                Text(words("${allocated} Min. eingeplant · ${totalAvailable} Min. verfügbar pro Woche. Deine Zeitfenster gelten für alle Ziele gemeinsam.", "$allocated min planned · $totalAvailable min available per week. Availability is shared across all your goals."), style = MaterialTheme.typography.bodyMedium)
                val names = (0..6).map { dayShort(it) }
                val overbooked = (0..6).filter { i -> state.practiceGoals.sumOf { it.dayTargets[i] } > windows[i].minutes }
                if (overbooked.isNotEmpty()) Text(words("An ${overbooked.joinToString { names[it] }} ist mehr geplant, als Zeit frei ist. Passe dort Ziele oder Zeitfenster an.", "${overbooked.joinToString { names[it] }}: planned practice exceeds your available time. Adjust the targets or availability."), style = MaterialTheme.typography.bodySmall, color = colors.error)
                TextButton(onClick = onGoal) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text(words("Übeziele verwalten", "Manage practice goals")) }
            }
        }
        Surface(onClick = tap(onSettings), shape = RoundedCornerShape(24.dp), color = colors.secondaryContainer) {
            Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NotificationsActive, null)
                Column(Modifier.weight(1f)) {
                    Text(words("Dein smarter Übepartner", "Your smart practice companion"), style = MaterialTheme.typography.titleMedium)
                    Text(if (state.reminders.enabled) words("Erinnerungen passen sich diesem Plan an.", "Reminders adapt to this plan.") else words("Aktiviere Erinnerungen in den Optionen.", "Enable reminders in Settings."), style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Rounded.ChevronRight, null)
            }
        }
    }
    if (weeklyEditor) WeeklyGoalSheet(state, onDismiss = { weeklyEditor = false }, onSave = { total, days -> onWeeklyPlan(total, days); weeklyEditor = false })
    selected?.let { i -> DayPlanSheet(i, state.goals[i], windows[i], onDismiss = { selected = null }, onSave = { goal, window -> onDayPlan(i, goal, window); selected = null }) }
}

@Composable private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.size(10.dp).clip(CircleShape).background(color)); Text(label, style = MaterialTheme.typography.bodySmall) }
}
@Composable fun dayShort(i: Int) = if (LocalEnglish.current) listOf("M", "T", "W", "T", "F", "S", "S")[i] else listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")[i]
@Composable fun dayName(i: Int) = if (LocalEnglish.current) listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[i] else listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")[i]

@Composable
fun DayPlanSheet(index: Int, initialGoal: Int, initialWindow: Availability, onDismiss: () -> Unit, onSave: (Int, Availability) -> Unit) {
    var window by remember { mutableStateOf(initialWindow) }
    var goal by remember { mutableIntStateOf(initialGoal) }
    SheetColumn(onDismiss) {
        SheetHeading(dayName(index), words("Erst die freie Zeit. Dann dein Übeziel.", "Your available time first. Your practice goal next."))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(words("Zeit zum Üben", "Time available"), style = MaterialTheme.typography.titleMedium); Text(words("Für alle deine Übeziele", "Shared by all your practice goals"), style = MaterialTheme.typography.bodySmall) }
            val toggle = tap({ window = window.copy(enabled = !window.enabled); if (!window.enabled) goal = 0 })
            Switch(checked = window.enabled, onCheckedChange = { toggle() })
        }
        if (window.enabled) {
            AvailabilityEditor(window, goal) { next -> window = next; goal = min(goal, min(240, next.minutes)) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SuggestionChip(onClick = { window = Availability(16 * 60, 19 * 60); goal = min(goal, 180) }, label = { Text(words("Nach der Schule", "After school")) })
                SuggestionChip(onClick = { window = Availability(10 * 60, 13 * 60); goal = min(goal, 180) }, label = { Text(words("Vormittags", "Morning")) })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(words("Davon möchte ich üben", "Of that, I want to practise"), style = MaterialTheme.typography.titleMedium)
            Text(if (goal == 0) words("Freier Tag", "Rest day") else "$goal min", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            val maxTarget = min(240, window.minutes).coerceAtLeast(5)
            HapticSlider(value = goal.toFloat(), onValueChange = { goal = ((it / 5).roundToInt() * 5).coerceAtMost(maxTarget) }, valueRange = 0f..maxTarget.toFloat(), steps = maxTarget / 5 - 1)
            SuggestionChip(onClick = { goal = min(240, window.minutes) }, label = { Text(words("Ganzes Zeitfenster als Übezeit", "Use the whole window for practice")) })
            Text(words("${window.minutes - goal} Minuten bleiben für andere Ziele, Pausen und alles andere.", "${window.minutes - goal} minutes remain for other goals, breaks and everything else."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Text(words("Heute keine Zeit eingeplant. Dein Tagesziel ist 0 Minuten.", "No time planned today. Your daily target is 0 minutes."), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { onSave(goal, window) }, modifier = Modifier.fillMaxWidth().height(58.dp)) { Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(8.dp)); Text(words("Tagesplan speichern", "Save daily plan")) }
    }
}

@Composable
fun WeeklyGoalSheet(state: CadenceState, onDismiss: () -> Unit, onSave: (Int, Set<Int>) -> Unit) {
    var days by remember { mutableStateOf((0..6).filter { state.goals[it] > 0 }.toSet()) }
    var total by remember { mutableStateOf(state.goals.sum().toString()) }
    val capacity = days.sumOf { (state.reminders.windows[it].end - state.reminders.windows[it].start).coerceAtMost(240) }
    val minimum = days.size * 5
    val value = total.toIntOrNull() ?: 0
    SheetColumn(onDismiss) {
        SheetHeading(words("Deine Woche. Einfach geplant.", "Your week. Simply planned."), words("Wähle ein Gesamtziel und deine Übetage. Cadence verteilt die Minuten für dich.", "Choose one weekly target and your practice days. Cadence shares the minutes for you."))
        androidx.compose.material3.OutlinedTextField(total, { if (it.length <= 4 && it.all(Char::isDigit)) total = it }, label = { Text(words("Minuten pro Woche", "Minutes per week")) }, singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        if (days.isNotEmpty()) HapticSlider(value.toFloat().coerceIn(minimum.toFloat(), capacity.toFloat()), { total = (it / 5).roundToInt().times(5).coerceIn(minimum, capacity).toString() }, minimum.toFloat()..capacity.toFloat(), ((capacity - minimum) / 5 - 1).coerceAtLeast(0))
        Text(words("An welchen Tagen möchtest du üben?", "Which days would you like to practise?"), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) { (0..6).forEach { i -> FilterChip(selected = i in days, onClick = {
            days = if (i in days) days - i else days + i
            val cap = days.sumOf { d -> (state.reminders.windows[d].end - state.reminders.windows[d].start).coerceAtMost(240) }
            if (days.isNotEmpty()) total = value.coerceIn(days.size * 5, cap).toString()
        }, label = { Text(dayShort(i)) }) } }
        if (days.isNotEmpty() && value in minimum..capacity) {
            val preview = distributeWeeklyGoal(value, days, state.reminders.windows)
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    days.sorted().forEach { i -> Row(Modifier.fillMaxWidth()) { Text(dayName(i), Modifier.weight(1f)); Text("${preview[i]} min", style = MaterialTheme.typography.titleMedium) } }
                }
            }
        } else Text(words("Wähle mindestens einen Tag und ein Ziel zwischen $minimum und $capacity Minuten.", "Choose at least one day and a target between $minimum and $capacity minutes."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Button(onClick = { onSave(value, days) }, enabled = days.isNotEmpty() && value in minimum..capacity, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text(words("Wochenplan übernehmen", "Apply weekly plan")) }
    }
}

@Composable
fun AvailabilityEditor(window: Availability, goal: Int = 0, onChange: (Availability) -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val feedback = LocalHapticFeedback.current
    val haptics = LocalHapticsEnabled.current
    val latest by rememberUpdatedState(window)
    val callback by rememberUpdatedState(onChange)
    var base by remember { mutableStateOf(window) }
    var delta by remember { mutableFloatStateOf(0f) }
    var mode by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> val start = (h * 60 + m).coerceIn(360, latest.end - 30); callback(latest.copy(start = start)) }, window.start / 60, window.start % 60, true).show() }) { Text(clock(window.start)) }
            Text(words("bis", "to"), modifier = Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> val end = (h * 60 + m).coerceIn(latest.start + 30, 1380); callback(latest.copy(end = end)) }, window.end / 60, window.end % 60, true).show() }) { Text(clock(window.end)) }
        }
        Canvas(Modifier.fillMaxWidth().height(70.dp).testTag("availability-timeline").pointerInput(haptics) {
            detectHorizontalDragGestures(onDragStart = { p ->
                base = latest; delta = 0f
                val startX = (base.start - 360f) / 1020 * size.width
                val endX = (base.end - 360f) / 1020 * size.width
                mode = when { abs(p.x - startX) < 24.dp.toPx() -> 0; abs(p.x - endX) < 24.dp.toPx() -> 1; p.x in startX..endX -> 2; abs(p.x - startX) < abs(p.x - endX) -> 0; else -> 1 }
            }, onHorizontalDrag = { change, amount ->
                change.consume(); delta += amount
                val shift = (delta / size.width * 1020 / 15).roundToInt() * 15
                val next = when (mode) {
                    0 -> base.copy(start = (base.start + shift).coerceIn(360, base.end - 30))
                    1 -> base.copy(end = (base.end + shift).coerceIn(base.start + 30, 1380))
                    else -> { val start = (base.start + shift).coerceIn(360, 1380 - base.minutes); base.copy(start = start, end = start + base.minutes) }
                }
                if (next != latest) { if (haptics) feedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick); callback(next) }
            })
        }) {
            val y = size.height / 2
            val trackHeight = 32.dp.toPx()
            drawRoundRect(colors.surfaceContainerHighest, topLeft = Offset(0f, y - trackHeight / 2), size = Size(size.width, trackHeight), cornerRadius = CornerRadius(trackHeight / 2))
            (0..17).forEach { tick -> val x = tick / 17f * size.width; drawLine(colors.outlineVariant, Offset(x, y + trackHeight / 2 + 5.dp.toPx()), Offset(x, y + trackHeight / 2 + 10.dp.toPx()), strokeWidth = 1.dp.toPx()) }
            val x1 = (window.start - 360f) / 1020 * size.width
            val x2 = (window.end - 360f) / 1020 * size.width
            drawRoundRect(colors.secondaryContainer, Offset(x1, y - trackHeight / 2), Size(x2 - x1, trackHeight), CornerRadius(trackHeight / 2))
            if (goal > 0) drawRoundRect(colors.primary, Offset(x1, y - trackHeight / 2), Size(((x2 - x1) * goal / window.minutes).coerceAtLeast(2f), trackHeight), CornerRadius(trackHeight / 2))
            listOf(x1, x2).forEach { x -> drawCircle(colors.secondary, 12.dp.toPx(), Offset(x, y)); drawLine(colors.onSecondary, Offset(x, y - 5.dp.toPx()), Offset(x, y + 5.dp.toPx()), 2.dp.toPx()) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("06:00", "12:00", "18:00", "23:00").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) } }
        Text(words("Ziehe die Griffe oder verschiebe den ganzen Bereich. Tippe auf die Uhrzeiten für eine genaue Eingabe.", "Drag either handle or slide the whole window. Tap the times for precise input."), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}
