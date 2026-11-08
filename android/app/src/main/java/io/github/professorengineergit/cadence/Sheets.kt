@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.professorengineergit.cadence

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SheetColumn(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val threshold = with(LocalDensity.current) { 80.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // One vertical gesture owner: downward swipes move the sheet from anywhere in
    // its body. Upward gestures remain available to the content's scroll view;
    // horizontal sliders and the availability timeline keep their own gestures.
    val dragModifier = Modifier.offset { IntOffset(0, dragOffset.roundToInt()) }.pointerInput(sheetState) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            settling?.cancel()
            val origin = down.position
            var dragging = false
            var letContentHandle = false
            var finished = false
            while (!finished) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val dy = change.position.y - origin.y
                val dx = change.position.x - origin.x
                if (!dragging && !letContentHandle && (abs(dx) > viewConfiguration.touchSlop || abs(dy) > viewConfiguration.touchSlop)) {
                    if (dy > 0 && dy > abs(dx) * 1.1f) dragging = true else letContentHandle = true
                }
                if (dragging) { dragOffset = (dy - viewConfiguration.touchSlop).coerceAtLeast(0f); change.consume() }
                if (!change.pressed) {
                    finished = true
                    if (dragging) {
                        if (dragOffset >= threshold) scope.launch { sheetState.hide(); dismiss() }
                        else settling = scope.launch { animate(dragOffset, 0f, animationSpec = tween(160)) { value, _ -> dragOffset = value } }
                    }
                }
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = dragModifier, sheetGesturesEnabled = false,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { Box(Modifier.padding(top = 14.dp, bottom = 18.dp).size(width = 44.dp, height = 5.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))) },
        containerColor = MaterialTheme.colorScheme.surface, contentWindowInsets = { WindowInsets.navigationBars }) {
        Column(Modifier.fillMaxWidth().widthIn(max = 640.dp).verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
    }
}

@Composable
fun SheetHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun FocusSheet(state: CadenceState, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var focus by remember { mutableStateOf(translate(state.focus, isEnglish(state.language))) }
    var minutes by remember { mutableIntStateOf(state.focusMinutes) }
    SheetColumn(onDismiss) {
        SheetHeading(words("Woran arbeitest du?", "What are you working on?"), words("Ein konkreter Fokus macht den Anfang leichter. Er steht später auch im Verlauf.", "A concrete focus makes it easier to start. It also shows up in your history."))
        OutlinedTextField(value = focus, onValueChange = { focus = it.take(60) }, label = { Text(words("Fokus", "Focus")) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state.activeGoal.kind) {
                GoalKind.MUSIC -> listOf(words("Freies Üben", "Free practice"), words("Technik", "Technique"), words("Neues Stück", "New piece"), words("Repertoire", "Repertoire"))
                GoalKind.SPEECH -> listOf(words("Durchsprechen", "Run-through"), words("Einstieg", "Opening"), words("Folien", "Slides"), words("Mit Stoppuhr", "Timed run"))
                GoalKind.STUDY -> listOf(words("Wiederholen", "Review"), words("Aufgaben", "Exercises"), words("Karteikarten", "Flashcards"), words("Probeklausur", "Practice exam"))
                GoalKind.OTHER -> listOf(words("Freies Üben", "Free practice"), words("Grundlagen", "Basics"), words("Projekt", "Project"))
            }.forEach { title -> SuggestionChip(onClick = { focus = title }, label = { Text(title) }) }
        }
        Text(words("Timer-Ziel, wenn heute nichts mehr ansteht", "Timer goal when nothing is left for today"), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 25, 45).forEach { value -> FilterChip(selected = minutes == value, onClick = { minutes = value }, label = { Text(words("$value Min.", "$value min")) }) }
        }
        Text(words("Sonst richtet sich der Timer nach deinem Tagesziel. Er läuft weiter, bis du auf Fertig tippst.", "Otherwise the timer follows today's goal. It keeps running until you tap Done."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { onSave(focus, minutes) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(words("Übernehmen", "Apply")) }
    }
}

@Composable
fun SessionSheet(existing: PracticeSession?, initialDate: LocalDate, state: CadenceState, onDismiss: () -> Unit,
    onSave: (LocalDate, Long, String, String, String) -> Unit, onDelete: (() -> Unit)?) {
    val english = isEnglish(state.language)
    var date by remember { mutableStateOf(initialDate) }
    var minutes by remember { mutableStateOf((existing?.durationSeconds?.div(60) ?: 20).toString()) }
    var seconds by remember { mutableStateOf((existing?.durationSeconds?.rem(60) ?: 0).toString()) }
    var focus by remember { mutableStateOf(translate(existing?.focus ?: state.focus, english)) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var goalId by remember { mutableStateOf(existing?.goalId ?: state.activeGoal.id) }
    var picker by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val m = minutes.toLongOrNull()
    val s = seconds.toLongOrNull()
    val duration = if (m != null && s != null && m in 0..1440 && s in 0..59) m * 60 + s else 0
    val valid = duration in 1..86400 && date <= LocalDate.now()
    SheetColumn(onDismiss) {
        SheetHeading(if (existing == null) words("Zeit nachtragen", "Log practice") else words("Session bearbeiten", "Edit session"),
            if (existing == null) words("Ohne Timer geübt? Trag es hier ein, es zählt genauso.", "Practised without the timer? Add it here, it counts just the same.") else words("Dauer, Fokus oder Notiz ändern.", "Change the duration, focus or note."))
        if (state.practiceGoals.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.practiceGoals.forEach { g -> FilterChip(selected = goalId == g.id, onClick = { goalId = g.id }, label = { Text(g.title) }, leadingIcon = { Icon(goalIcon(g.kind), null, Modifier.size(16.dp)) }) }
        }
        OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.CalendarToday, null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
            Text(if (date == LocalDate.now()) words("Heute", "Today") else longDate(date), modifier = Modifier.weight(1f)); Icon(Icons.Rounded.Edit, words("Datum ändern", "Change date"), Modifier.size(18.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = minutes, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) minutes = it }, label = { Text(words("Minuten", "Minutes")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), isError = m == null || m !in 0..1440)
            OutlinedTextField(value = seconds, onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) seconds = it }, label = { Text(words("Sekunden", "Seconds")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), isError = s == null || s !in 0..59)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 15, 20, 30, 45, 60).forEach { value -> SuggestionChip(onClick = { minutes = value.toString(); seconds = "0" }, label = { Text(words("$value Min.", "$value min")) }) }
        }
        OutlinedTextField(value = focus, onValueChange = { focus = it.take(60) }, label = { Text(words("Fokus", "Focus")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text(words("Notiz (optional)", "Note (optional)")) }, placeholder = { Text(words("Was lief gut? Woran willst du als Nächstes arbeiten?", "What went well? What's next?")) },
            minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        if (!valid) Text(words("Bitte gib eine Dauer zwischen 1 Sekunde und 24 Stunden ein.", "Enter a duration between 1 second and 24 hours."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Button(onClick = { onSave(date, duration, focus, note, goalId) }, enabled = valid, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Rounded.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(words("Speichern", "Save"))
        }
        if (onDelete != null) TextButton(onClick = { deleteConfirm = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(words("Session löschen", "Delete session"))
        }
    }
    if (picker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= LocalDate.now()
                override fun isSelectableYear(year: Int) = year <= LocalDate.now().year
            })
        DatePickerDialog(onDismissRequest = { picker = false }, confirmButton = { TextButton(onClick = {
            dateState.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; picker = false
        }, enabled = dateState.selectedDateMillis != null) { Text(words("Übernehmen", "Apply")) } }, dismissButton = { TextButton(onClick = { picker = false }) { Text(words("Abbrechen", "Cancel")) } }) {
            DatePicker(state = dateState, title = { Text(words("Datum", "Date"), modifier = Modifier.padding(24.dp)) })
        }
    }
    if (deleteConfirm) AlertDialog(onDismissRequest = { deleteConfirm = false }, title = { Text(words("Session löschen?", "Delete session?")) }, text = { Text(words("Der Eintrag wird aus deinem Verlauf entfernt.", "The entry will be removed from your history.")) },
        confirmButton = { TextButton(onClick = { deleteConfirm = false; onDelete?.invoke() }) { Text(words("Löschen", "Delete")) } },
        dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text(words("Behalten", "Keep")) } })
}

@Composable
fun DaySheet(day: LocalDate, state: CadenceState, onDismiss: () -> Unit, onAdd: () -> Unit, onEdit: (PracticeSession) -> Unit) {
    val sessions = state.sessions.filter { it.date() == day }.sortedByDescending { it.startedAt }
    val total = (sessions.sumOf { it.durationSeconds } / 60).toInt()
    val plan = Planner.plan(state, state.activeGoal, java.time.ZonedDateTime.now()).days.firstOrNull { it.date == day }
    SheetColumn(onDismiss) {
        SheetHeading(if (day == LocalDate.now()) words("Heute", "Today") else longDate(day), when {
            total > 0 && plan != null && plan.planned > 0 && day <= LocalDate.now() -> words("$total Min. geübt, geplant waren ${plan.planned} Min. für ${state.activeGoal.title}.", "$total min practised, ${plan.planned} min were planned for ${state.activeGoal.title}.")
            total > 0 -> words("$total Min. geübt", "$total min practised")
            day >= LocalDate.now() && plan != null && plan.planned > 0 -> words("Geplant: ${plan.planned} Min. ${state.activeGoal.title}", "Planned: ${plan.planned} min of ${state.activeGoal.title}")
            day > LocalDate.now() -> words("Für diesen Tag ist nichts geplant.", "Nothing planned for this day.")
            else -> words("An diesem Tag hast du nicht geübt.", "No practice on this day.")
        })
        sessions.forEach { session -> SessionRow(session, state, showGoal = state.practiceGoals.size > 1, showDate = false) { onEdit(session) } }
        if (day <= LocalDate.now()) Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(56.dp)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text(words("Zeit nachtragen", "Log practice")) }
    }
}
