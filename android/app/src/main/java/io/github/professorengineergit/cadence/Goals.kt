@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.professorengineergit.cadence

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun GoalPickerSheet(state: CadenceState, onDismiss: () -> Unit, onSelect: (String) -> Unit, onNew: () -> Unit, onEdit: (PracticeGoal) -> Unit) {
    SheetColumn(onDismiss) {
        SheetHeading(words("Deine Übeziele", "Your practice goals"), words("Eine Routine oder ein Termin. Alles hat seinen Platz.", "A daily rhythm or a deadline. Make room for both."))
        state.practiceGoals.forEach { goal ->
            val minutes = state.sessions.filter { it.goalId == goal.id }.sumOf { it.durationSeconds } / 60
            Surface(shape = RoundedCornerShape(22.dp), color = if (state.activeGoal.id == goal.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().clickable(onClick = tap({ onSelect(goal.id) })).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(when(goal.kind) { GoalKind.MUSIC -> Icons.Rounded.MusicNote; GoalKind.STUDY -> Icons.Rounded.School; GoalKind.OTHER -> Icons.Rounded.AutoAwesome }, null)
                    Column(Modifier.weight(1f)) {
                        Text(goal.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (goal.dueDate == null) words("${goal.dayTargets.sum()} Min. jede Woche", "${goal.dayTargets.sum()} min every week") else words("$minutes / ${goal.totalTarget} Min. bis ${goal.dueDate}", "$minutes / ${goal.totalTarget} min by ${goal.dueDate}"), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onEdit(goal) }) { Icon(Icons.Rounded.Edit, words("Ziel bearbeiten", "Edit goal"), Modifier.size(20.dp)) }
                }
            }
        }
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth().height(58.dp)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text(words("Übeziel hinzufügen", "Add practice goal")) }
    }
}

@Composable
fun GoalEditorSheet(existing: PracticeGoal?, onDismiss: () -> Unit, onSave: (PracticeGoal) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: GoalKind.MUSIC) }
    var dated by remember { mutableStateOf(existing?.dueDate != null) }
    var date by remember { mutableStateOf(existing?.dueDate ?: LocalDate.now().plusDays(14)) }
    var total by remember { mutableStateOf((existing?.totalTarget ?: 240).toString()) }
    var days by remember { mutableStateOf(existing?.dayTargets?.mapIndexedNotNull { i, m -> if (m > 0) i else null }?.toSet() ?: setOf(0, 2, 4)) }
    var minutes by remember { mutableIntStateOf(existing?.dayTargets?.filter { it > 0 }?.average()?.takeIf { !it.isNaN() }?.roundToInt() ?: 20) }
    var planChanged by remember { mutableStateOf(false) }
    SheetColumn(onDismiss) {
        SheetHeading(if (existing == null) words("Ein neues Können.", "A new possibility.") else words("Dein Übeziel", "Your practice goal"), words("Was möchtest du regelmäßig üben oder bis zu einem Termin schaffen?", "What would you like to practise regularly or work towards by a deadline?"))
        OutlinedTextField(title, { title = it.take(60) }, label = { Text(words("Name deines Ziels", "Goal name")) }, placeholder = { Text(words("Klavier, Mathetest, Zeichnen …", "Piano, maths test, drawing …")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        KindChips(kind) { kind = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(words("Mit Zieltermin", "Set a deadline"), style = MaterialTheme.typography.titleMedium); Text(words("Zum Beispiel ein Test in zwei Wochen", "For example, a test in two weeks"), style = MaterialTheme.typography.bodySmall) }
            val toggle = tap({ dated = !dated }); Switch(dated, { toggle() })
        }
        if (dated) {
            OutlinedButton(onClick = {
                DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth).apply {
                    datePicker.minDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.show()
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.CalendarToday, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(date.toString()) }
            OutlinedTextField(total, { if (it.length <= 5 && it.all(Char::isDigit)) total = it }, label = { Text(words("Gesamte Übezeit bis dahin (Minuten)", "Total practice by then (minutes)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        }
        Text(words("An welchen Tagen?", "Which days?"), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) { (0..6).forEach { i -> FilterChip(selected = i in days, onClick = { days = if (i in days) days - i else days + i; planChanged = true }, label = { Text(dayShort(i)) }) } }
        Text(words("$minutes Minuten je Übetag", "$minutes minutes per practice day"), style = MaterialTheme.typography.headlineSmall)
        HapticSlider(minutes.toFloat(), { minutes = (it / 5).roundToInt() * 5; planChanged = true }, 5f..120f, 22)
        Text(words("Den genauen Plan und deine verfügbaren Zeiten kannst du danach im Rhythmus anpassen.", "Fine-tune each day and your availability in Rhythm afterwards."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = {
            val original = existing ?: PracticeGoal(title = title)
            onSave(original.copy(title = title.trim(), kind = kind, deadline = if (dated) date.toString() else null, totalTarget = total.toIntOrNull()?.coerceIn(1, 99999) ?: 240,
                dayTargets = if (existing != null && !planChanged) existing.dayTargets else List(7) { if (it in days) minutes else 0 }))
        }, enabled = title.isNotBlank() && days.isNotEmpty() && (!dated || (total.toIntOrNull() ?: 0) > 0) && (!dated || date >= LocalDate.now()), modifier = Modifier.fillMaxWidth().height(58.dp)) { Text(words("Übeziel speichern", "Save practice goal")) }
    }
}

@Composable fun KindChips(selected: GoalKind, onSelect: (GoalKind) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { GoalKind.entries.forEach { kind -> FilterChip(selected = selected == kind, onClick = { onSelect(kind) }, label = { Text(when(kind) { GoalKind.MUSIC -> words("Musik", "Music"); GoalKind.STUDY -> words("Lernen", "Study"); GoalKind.OTHER -> words("Anderes", "Other") }) }) } }
}
