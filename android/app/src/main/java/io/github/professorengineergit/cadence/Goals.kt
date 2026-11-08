@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.professorengineergit.cadence

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun goalIcon(kind: GoalKind): ImageVector = when (kind) {
    GoalKind.MUSIC -> Icons.Rounded.MusicNote
    GoalKind.SPEECH -> Icons.Rounded.RecordVoiceOver
    GoalKind.STUDY -> Icons.Rounded.School
    GoalKind.OTHER -> Icons.Rounded.AutoAwesome
}

@Composable fun appLocale(): Locale = if (LocalEnglish.current) Locale.ENGLISH else Locale.GERMAN

/** "Thu, 12 Nov" / "Do., 12. Nov." */
@Composable fun shortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern(if (LocalEnglish.current) "EEE, d MMM" else "EEE, d. MMM", appLocale()))

@Composable fun longDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern(if (LocalEnglish.current) "EEEE, d MMMM" else "EEEE, d. MMMM", appLocale()))

/** 45 Min. · 1 Std. 30 Min. · 3 Std. */
@Composable fun minutesText(minutes: Int): String {
    val en = LocalEnglish.current
    val h = minutes / 60
    val m = minutes % 60
    return when {
        minutes <= 300 -> if (en) "$minutes min" else "$minutes Min."
        m == 0 -> if (en) "$h h" else "$h Std."
        else -> if (en) "$h h $m min" else "$h Std. $m Min."
    }
}

@Composable fun dayShort(i: Int) = if (LocalEnglish.current) listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[i] else listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")[i]
@Composable fun dayName(i: Int) = if (LocalEnglish.current) listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[i] else listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")[i]

/** "100 Min. pro Woche" / "200 Min. bis Do., 12. Nov." */
@Composable fun goalSummary(goal: PracticeGoal, withReset: Boolean = true): String {
    val amount = minutesText(goal.minutes)
    return when (goal.type) {
        GoalType.WEEKLY -> {
            val base = if (goal.perDay) words("$amount pro Übetag", "$amount per practice day") else words("$amount pro Woche", "$amount per week")
            if (!withReset || (goal.resetDay == 0 && goal.resetMinute == 0)) base
            else base + words(", Woche ab ${dayShort(goal.resetDay)} ${clock(goal.resetMinute)}", ", week starts ${dayShort(goal.resetDay)} ${clock(goal.resetMinute)}")
        }
        GoalType.DEADLINE -> {
            val due = goal.dueDate?.let { shortDate(it) + (goal.deadlineMinute?.let { m -> " ${clock(m)}" } ?: "") } ?: "?"
            if (goal.perDay) words("$amount täglich bis $due", "$amount daily until $due") else words("$amount bis $due", "$amount by $due")
        }
    }
}

@Composable fun kindLabel(kind: GoalKind) = when (kind) {
    GoalKind.MUSIC -> words("Instrument", "Instrument"); GoalKind.SPEECH -> words("Vortrag", "Talk")
    GoalKind.STUDY -> words("Lernen", "Studying"); GoalKind.OTHER -> words("Anderes", "Other")
}

@Composable fun KindChips(selected: GoalKind, onSelect: (GoalKind) -> Unit) {
    PillGrid(GoalKind.entries, { it == selected }, columns = 2, onPick = onSelect) { kind ->
        Icon(goalIcon(kind), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(kindLabel(kind), maxLines = 1)
    }
}

@Composable
fun ChoiceCard(selected: Boolean, icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = tap(onClick), shape = RoundedCornerShape(22.dp), color = if (selected) colors.secondaryContainer else colors.surfaceContainerLow,
        border = if (selected) BorderStroke(2.dp, colors.secondary) else null, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(26.dp), tint = colors.secondary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

/** Minus / value / plus with quick picks. */
@Composable
fun MinutesStepper(value: Int, onChange: (Int) -> Unit, range: IntRange, step: Int, quick: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalIconButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value > range.first) { Icon(Icons.Rounded.Remove, words("Weniger", "Less")) }
            Text(minutesText(value), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            FilledTonalIconButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.last) { Icon(Icons.Rounded.Add, words("Mehr", "More")) }
        }
        PillGrid(quick, { it == value }, onPick = onChange) { Text(minutesText(it), maxLines = 1) }
    }
}

/** Type, amount and timing of a goal. Used by the setup flow and the goal editor. */
@Composable
fun GoalTargetEditor(goal: PracticeGoal, onChange: (PracticeGoal) -> Unit) {
    val pickers = LocalPickers.current
    val lessonTitle = words("Wann ist der Termin?", "When is the lesson?")
    val eventTitle = words("Um wie viel Uhr?", "At what time?")
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ChoiceCard(goal.type == GoalType.WEEKLY, Icons.Rounded.Repeat, words("Regelmäßig üben", "Practise regularly"),
            words("Jede Woche eine feste Menge, zum Beispiel für ein Instrument.", "A set amount every week, for example for an instrument.")) {
            onChange(goal.copy(type = GoalType.WEEKLY, minutes = if (goal.type == GoalType.WEEKLY) goal.minutes else 100, perDay = false))
        }
        ChoiceCard(goal.type == GoalType.DEADLINE, Icons.Rounded.Event, words("Bis zu einem Termin", "Towards a date"),
            words("Zum Beispiel ein Referat, ein Vorspiel oder eine Prüfung.", "For example a talk, a recital or an exam.")) {
            onChange(goal.copy(type = GoalType.DEADLINE, deadline = goal.deadline ?: LocalDate.now().plusDays(7).toString(), minutes = if (goal.type == GoalType.DEADLINE) goal.minutes else 120, perDay = false))
        }
        HorizontalDivider(color = colors.outlineVariant)
        when (goal.type) {
            GoalType.WEEKLY -> {
                Text(words("Wie viel möchtest du üben?", "How much do you want to practise?"), style = MaterialTheme.typography.titleMedium)
                SegmentedChoice(listOf(words("pro Woche", "per week"), words("pro Übetag", "per practice day")), if (goal.perDay) 1 else 0) {
                    val perDay = it == 1
                    if (perDay != goal.perDay) onChange(goal.copy(perDay = perDay, minutes = if (perDay) 20 else 100))
                }
                if (goal.perDay) MinutesStepper(goal.minutes, { onChange(goal.copy(minutes = it)) }, 5..240, 5, listOf(10, 15, 20, 30, 45, 60))
                else MinutesStepper(goal.minutes, { onChange(goal.copy(minutes = it)) }, 10..1200, 10, listOf(60, 90, 120, 150, 200, 300))
                Text(words("Wann beginnt eine neue Woche?", "When does a new week start?"), style = MaterialTheme.typography.titleMedium)
                val custom = goal.resetDay != 0 || goal.resetMinute != 0
                SegmentedChoice(listOf(words("Montags", "Mondays"), words("Nach einem Termin", "After a lesson")), if (custom) 1 else 0) {
                    onChange(if (it == 0) goal.copy(resetDay = 0, resetMinute = 0) else goal.copy(resetDay = if (custom) goal.resetDay else 0, resetMinute = if (custom) goal.resetMinute else 16 * 60))
                }
                if (custom) {
                    Text(words("Zum Beispiel Klavierunterricht. Was du am Unterrichtstag vorher übst, zählt noch für die Woche davor.",
                        "For example a piano lesson. Practice on lesson day before the lesson still counts for the week before."), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    DayToggles(setOf(goal.resetDay), { d -> onChange(goal.copy(resetDay = d)) }, (0..6).map { dayShortPlain(it, LocalEnglish.current).take(2) })
                    OutlinedButton(onClick = { pickers.time(goal.resetMinute, lessonTitle) { onChange(goal.copy(resetMinute = it)) } }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(words("Termin um ${clock(goal.resetMinute)}", "Lesson at ${clock(goal.resetMinute)}"))
                    }
                }
            }
            GoalType.DEADLINE -> {
                val due = goal.dueDate ?: LocalDate.now().plusDays(7)
                Text(words("Wann ist der Termin?", "When is it?"), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickers.date(due) { onChange(goal.copy(deadline = it.toString())) } }, modifier = Modifier.weight(1.4f)) {
                        Icon(Icons.Rounded.CalendarToday, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(shortDate(due), maxLines = 1)
                    }
                    OutlinedButton(onClick = { pickers.time(goal.deadlineMinute ?: (10 * 60), eventTitle) { onChange(goal.copy(deadlineMinute = it)) } }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(goal.deadlineMinute?.let { clock(it) } ?: words("Uhrzeit", "Time"), maxLines = 1)
                    }
                }
                if (goal.deadlineMinute != null) TextButton(onClick = { onChange(goal.copy(deadlineMinute = null)) }) { Text(words("Ohne Uhrzeit (ganzer Tag zählt)", "No time (whole day counts)")) }
                Text(words("Wie viel möchtest du bis dahin üben?", "How much do you want to practise by then?"), style = MaterialTheme.typography.titleMedium)
                SegmentedChoice(listOf(words("insgesamt", "in total"), words("pro Tag", "per day")), if (goal.perDay) 1 else 0) {
                    val perDay = it == 1
                    if (perDay != goal.perDay) onChange(goal.copy(perDay = perDay, minutes = if (perDay) 20 else 120))
                }
                if (goal.perDay) MinutesStepper(goal.minutes, { onChange(goal.copy(minutes = it)) }, 5..240, 5, listOf(10, 15, 20, 30, 45, 60))
                else MinutesStepper(goal.minutes, { onChange(goal.copy(minutes = it)) }, 10..6000, 10, listOf(60, 90, 120, 180, 300, 600))
            }
        }
    }
}

@Composable
fun GoalDaysEditor(goal: PracticeGoal, availability: List<List<TimeSlot>>, onChange: (PracticeGoal) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(words("An welchen Tagen?", "Which days?"), style = MaterialTheme.typography.titleMedium)
        DayToggles(goal.days, { d -> val next = if (d in goal.days) goal.days - d else goal.days + d; if (next.isNotEmpty()) onChange(goal.copy(days = next)) }, (0..6).map { dayShortPlain(it, LocalEnglish.current).take(2) })
        val noTime = goal.days.filter { availability[it].isEmpty() }
        Text(if (noTime.isEmpty()) words("Cadence plant nur in deiner freien Zeit an diesen Tagen.", "Cadence only plans within your free time on these days.")
            else words("An ${noTime.map { dayShortPlain(it, false) }.joinToString()} hast du noch keine freie Zeit eingetragen.", "You haven't added free time on ${noTime.map { dayShortPlain(it, true) }.joinToString()} yet."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun dayShortPlain(i: Int, english: Boolean) = if (english) listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[i] else listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")[i]

@Composable
fun GoalEditorSheet(existing: PracticeGoal?, state: CadenceState, onDismiss: () -> Unit, onSave: (PracticeGoal) -> Unit, onDelete: ((PracticeGoal) -> Unit)?) {
    var goal by remember { mutableStateOf(existing ?: PracticeGoal(title = "", kind = GoalKind.MUSIC)) }
    var confirmDelete by remember { mutableStateOf(false) }
    val validDate = goal.type == GoalType.WEEKLY || (goal.dueDate != null && goal.dueDate!! >= LocalDate.now())
    SheetColumn(onDismiss) {
        SheetHeading(if (existing == null) words("Neues Ziel", "New goal") else words("Ziel bearbeiten", "Edit goal"),
            words("Was übst du, und wie viel?", "What are you practising, and how much?"))
        OutlinedTextField(goal.title, { goal = goal.copy(title = it.take(40)) }, label = { Text(words("Name", "Name")) },
            placeholder = { Text(words("z. B. Klavier, Referat Bio, Mathe", "e.g. Piano, Biology talk, Maths")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        KindChips(goal.kind) { goal = goal.copy(kind = it) }
        GoalTargetEditor(goal) { goal = it }
        GoalDaysEditor(goal, state.availability) { goal = it }
        val previewGoal = if (existing != null && existing.type == goal.type) goal else goal.copy(createdOn = Planner.freshStart(state.availability).toString())
        val preview = Planner.plan(state.copy(practiceGoals = state.practiceGoals.filterNot { it.id == goal.id } + previewGoal), previewGoal, java.time.ZonedDateTime.now())
        PlanHint(preview)
        Button(onClick = { onSave(goal.copy(title = goal.title.trim(), createdOn = if (existing != null && existing.type == goal.type) existing.createdOn else Planner.freshStart(state.availability).toString())) },
            enabled = goal.title.isNotBlank() && validDate, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(words("Speichern", "Save")) }
        if (existing != null && onDelete != null) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(words("Ziel löschen", "Delete goal"))
        }
    }
    if (confirmDelete && existing != null) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(words("„${existing.title}“ löschen?", "Delete “${existing.title}”?")) },
        text = { Text(words("Das Ziel und alle seine Sessions werden entfernt. Das lässt sich nicht rückgängig machen.", "The goal and all its sessions will be removed. This can't be undone.")) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete?.invoke(existing) }) { Text(words("Löschen", "Delete")) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(words("Behalten", "Keep")) } })
}

/** One line on whether the plan works out. */
@Composable
fun PlanHint(plan: GoalPlan) {
    val colors = MaterialTheme.colorScheme
    val (text, warn) = when {
        plan.status == DayStatus.OVER -> words("Der Termin ist vorbei.", "The date has passed.") to true
        plan.shortfall > 0 -> words("Deine freie Zeit reicht dafür nicht: es fehlen ${plan.shortfall} Min. Trag mehr Zeit ein oder senke das Ziel.",
            "Your free time isn't enough: ${plan.shortfall} min short. Add more free time or lower the goal.") to true
        plan.practiceDaysLeft == 0 && plan.remaining > 0 -> words("In diesem Zeitraum ist keine freie Zeit mehr eingetragen.", "No free time left in this period.") to true
        else -> words("Passt in deine freie Zeit.", "Fits into your free time.") to false
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(if (warn) Icons.Rounded.WarningAmber else Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = if (warn) colors.error else colors.tertiary)
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (warn) colors.error else colors.onSurfaceVariant)
    }
}
