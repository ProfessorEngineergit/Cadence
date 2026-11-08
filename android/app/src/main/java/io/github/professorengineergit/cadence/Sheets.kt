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
    var focus by remember { mutableStateOf(state.focus) }
    var minutes by remember { mutableIntStateOf(state.focusMinutes) }
    SheetColumn(onDismiss) {
        SheetHeading("Woran möchtest du üben?", "Ein klarer Fokus macht den Anfang leichter.")
        OutlinedTextField(value = translate(focus, LocalEnglish.current), onValueChange = { focus = it.take(60) }, label = { Text("Dein Fokus") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state.activeGoal.kind) { GoalKind.MUSIC -> listOf("Freies Üben", "Technik", "Repertoire"); GoalKind.STUDY -> listOf("Wiederholen", "Aufgaben", "Prüfungstraining"); GoalKind.OTHER -> listOf("Freies Üben", "Grundlagen", "Projekt") }.forEach { title -> SuggestionChip(onClick = { focus = title }, label = { Text(title) }) }
        }
        Text("Fokuszeit", style = MaterialTheme.typography.titleMedium)
        Text("$minutes Minuten", style = MaterialTheme.typography.headlineLarge)
        HapticSlider(value = minutes.toFloat(), onValueChange = { minutes = (it / 5).roundToInt() * 5 }, valueRange = 5f..120f, steps = 22)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 25, 45).forEach { value -> FilterChip(selected = minutes == value, onClick = { minutes = value }, label = { Text("$value Min.") }) }
        }
        Text("Dein Timer zählt aufwärts und läuft auch nach der Fokuszeit weiter. Beende deine Session, wenn es für dich passt.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { onSave(focus, minutes) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(58.dp)) { Text("Fokus übernehmen") }
    }
}

@Composable
fun SessionSheet(existing: PracticeSession?, initialDate: LocalDate, onDismiss: () -> Unit,
    onSave: (LocalDate, Long, String, String) -> Unit, onDelete: (() -> Unit)?) {
    var date by remember { mutableStateOf(initialDate) }
    var minutes by remember { mutableStateOf((existing?.durationSeconds?.div(60) ?: 15).toString()) }
    var seconds by remember { mutableStateOf((existing?.durationSeconds?.rem(60) ?: 0).toString()) }
    var focus by remember { mutableStateOf(existing?.focus ?: "Freies Üben") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var picker by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val m = minutes.toLongOrNull()
    val s = seconds.toLongOrNull()
    val duration = if (m != null && s != null && m in 0..1440 && s in 0..59) m * 60 + s else 0
    val valid = duration in 1..86400 && date <= LocalDate.now()
    SheetColumn(onDismiss) {
        SheetHeading(if (existing == null) "Ein Moment Fortschritt." else "Deine Session", if (existing == null) "Timer vergessen? Trage deine Übezeit einfach nach." else "Passe Dauer, Fokus oder deine Notiz an.")
        OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.CalendarToday, null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
            Text(date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)), modifier = Modifier.weight(1f)); Icon(Icons.Rounded.Edit, "Datum ändern", Modifier.size(18.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = minutes, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) minutes = it }, label = { Text("Minuten") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), isError = m == null || m !in 0..1440)
            OutlinedTextField(value = seconds, onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) seconds = it }, label = { Text("Sekunden") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), isError = s == null || s !in 0..59)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 15, 30, 45).forEach { value -> SuggestionChip(onClick = { minutes = value.toString(); seconds = "0" }, label = { Text("$value Min.") }) }
        }
        OutlinedTextField(value = translate(focus, LocalEnglish.current), onValueChange = { focus = it.take(60) }, label = { Text("Fokus") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text("Notiz (optional)") }, placeholder = { Text("Was lief gut? Was kommt als Nächstes?") },
            minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        if (!valid) Text("Bitte gib eine Dauer zwischen 1 Sekunde und 24 Stunden ein.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Button(onClick = { onSave(date, duration, focus, note) }, shapes = ButtonDefaults.shapes(), enabled = valid, modifier = Modifier.fillMaxWidth().height(58.dp)) {
            Icon(Icons.Rounded.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Session speichern")
        }
        if (onDelete != null) TextButton(onClick = { deleteConfirm = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Session löschen")
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
        }, enabled = dateState.selectedDateMillis != null) { Text("Übernehmen") } }, dismissButton = { TextButton(onClick = { picker = false }) { Text("Abbrechen") } }) {
            DatePicker(state = dateState, title = { Text("Datum auswählen", modifier = Modifier.padding(24.dp)) })
        }
    }
    if (deleteConfirm) AlertDialog(onDismissRequest = { deleteConfirm = false }, title = { Text("Session löschen?") }, text = { Text("Dieser Eintrag wird aus deinem Übetagebuch entfernt.") },
        confirmButton = { TextButton(onClick = { deleteConfirm = false; onDelete?.invoke() }) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Behalten") } })
}

@Composable
fun DaySheet(day: LocalDate, stats: PracticeStats, onDismiss: () -> Unit, onAdd: () -> Unit, onEdit: (PracticeSession) -> Unit) {
    val sessions = stats.sessions.filter { it.date() == day }.sortedByDescending { it.startedAt }
    SheetColumn(onDismiss) {
        SheetHeading(day.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)),
            if (stats.goalOn(day) > 0) "${stats.minutesOn(day)} von ${stats.goalOn(day)} Minuten geübt" else "Freier Tag · ${stats.minutesOn(day)} Minuten Bonuszeit")
        if (sessions.isEmpty()) Text(if (day > stats.today) "Dein Plan für diesen Tag steht. Alles in deinem Tempo." else "Für diesen Tag sind noch keine Sessions eingetragen.", style = MaterialTheme.typography.bodyMedium)
        sessions.forEach { session ->
            OutlinedCard(onClick = tap({ onEdit(session) }), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(session.focus, style = MaterialTheme.typography.titleMedium); if (session.note.isNotEmpty()) Text(session.note, style = MaterialTheme.typography.bodySmall) }
                    Text(formatDuration(session.durationSeconds), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (day <= stats.today) Button(onClick = onAdd, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(58.dp)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Übezeit nachtragen") }
    }
}
