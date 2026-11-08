@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.professorengineergit.cadence

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Sorts slots and joins overlapping ones. */
fun normalize(slots: List<TimeSlot>): List<TimeSlot> {
    val sorted = slots.filter { it.minutes >= 5 }.sortedBy { it.start }
    val result = mutableListOf<TimeSlot>()
    for (slot in sorted) {
        val last = result.lastOrNull()
        if (last != null && slot.start <= last.end) result[result.lastIndex] = TimeSlot(last.start, maxOf(last.end, slot.end)) else result += slot
    }
    return result
}

@Composable
fun availabilitySummary(availability: List<List<TimeSlot>>): String {
    val days = availability.count { it.isNotEmpty() }
    val total = availability.sumOf { d -> d.sumOf { it.minutes } }
    return if (days == 0) words("Noch keine freie Zeit eingetragen", "No free time added yet")
    else words("$days Tage, ${minutesText(total)} pro Woche", "$days days, ${minutesText(total)} per week")
}

@Composable
fun WeekAvailabilityEditor(availability: List<List<TimeSlot>>, onChange: (List<List<TimeSlot>>) -> Unit, highlightToday: Boolean = false) {
    var editing by remember { mutableStateOf<Pair<Int, Int?>?>(null) }
    val todayIndex = java.time.LocalDate.now().dayOfWeek.value - 1
    val colors = MaterialTheme.colorScheme
    fun set(day: Int, slots: List<TimeSlot>) = onChange(availability.mapIndexed { i, d -> if (i == day) normalize(slots) else d })
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        (0..6).forEach { day ->
            var menu by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dayShort(day), style = MaterialTheme.typography.titleMedium, color = if (highlightToday && day == todayIndex) colors.primary else colors.onSurface, modifier = Modifier.width(48.dp))
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (availability[day].isEmpty()) Text(words("keine Zeit", "no time"), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 14.dp))
                    availability[day].forEachIndexed { i, slot ->
                        SuggestionChip(onClick = { editing = day to i }, label = { Text(slot.label()) })
                    }
                }
                IconButton(onClick = { editing = day to null }) { Icon(Icons.Rounded.AddCircleOutline, words("Zeit hinzufügen", "Add time")) }
                Box {
                    IconButton(onClick = { menu = true }, enabled = availability[day].isNotEmpty()) { Icon(Icons.Rounded.ContentCopy, words("Auf andere Tage übertragen", "Copy to other days"), Modifier.size(20.dp)) }
                    DropdownMenu(menu, { menu = false }) {
                        listOf(words("Auf Mo–Fr übertragen", "Copy to Mon–Fri") to (0..4), words("Auf Sa–So übertragen", "Copy to Sat–Sun") to (5..6), words("Auf alle Tage übertragen", "Copy to every day") to (0..6)).forEach { (label, range) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; onChange(availability.mapIndexed { i, d -> if (i in range) availability[day] else d }) })
                        }
                        DropdownMenuItem(text = { Text(words("Zeiten an diesem Tag löschen", "Clear this day")) }, onClick = { menu = false; set(day, emptyList()) })
                    }
                }
            }
            if (day < 6) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
        }
    }
    editing?.let { (day, index) ->
        val existing = index?.let { availability[day].getOrNull(it) }
        val last = availability[day].lastOrNull()
        val initial = existing ?: if (last != null && last.end <= 22 * 60) TimeSlot(last.end + 60, (last.end + 120).coerceAtMost(1440)) else TimeSlot(17 * 60, 18 * 60)
        SlotDialog(dayName(day), initial, existing != null, onDismiss = { editing = null },
            onSave = { slot -> set(day, availability[day].filterIndexed { i, _ -> i != index } + slot); editing = null },
            onDelete = { set(day, availability[day].filterIndexed { i, _ -> i != index }); editing = null })
    }
}

@Composable
fun SlotDialog(title: String, initial: TimeSlot, existing: Boolean, onDismiss: () -> Unit, onSave: (TimeSlot) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var start by remember { mutableIntStateOf(initial.start) }
    var end by remember { mutableIntStateOf(initial.end) }
    val valid = end - start >= 10
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(words("Wann hast du an diesem Tag Zeit zum Üben?", "When do you have time to practise on this day?"), style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickTime(context, start) { start = it } }, modifier = Modifier.weight(1f)) { Text(clock(start), style = MaterialTheme.typography.titleLarge) }
                    Text(words("bis", "to"))
                    OutlinedButton(onClick = { pickTime(context, end) { end = if (it == 0) 1440 else it } }, modifier = Modifier.weight(1f)) { Text(clock(end), style = MaterialTheme.typography.titleLarge) }
                }
                if (!valid) Text(words("Das Ende muss mindestens 10 Minuten nach dem Beginn liegen.", "The end must be at least 10 minutes after the start."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                else Text(minutesText(end - start), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(TimeSlot(start, end)) }, enabled = valid) { Text(words("Übernehmen", "Apply")) } },
        dismissButton = {
            Row {
                if (existing) TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(words("Löschen", "Delete")) }
                TextButton(onClick = onDismiss) { Text(words("Abbrechen", "Cancel")) }
            }
        })
}

/** Quick starting points for the week. */
@Composable
fun AvailabilityPresets(onPick: (List<List<TimeSlot>>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SuggestionChip(onClick = { onPick(List(7) { if (it < 5) listOf(TimeSlot(17 * 60, 18 * 60)) else listOf(TimeSlot(15 * 60, 16 * 60)) }) },
            label = { Text(words("Abends, Wochenende nachmittags", "Evenings, weekend afternoons")) })
        SuggestionChip(onClick = { onPick(List(7) { listOf(TimeSlot(7 * 60, 7 * 60 + 30)) }) }, label = { Text(words("Jeden Morgen 30 Min.", "Every morning 30 min")) })
        SuggestionChip(onClick = { onPick(List(7) { emptyList() }) }, label = { Text(words("Leer anfangen", "Start empty")) })
    }
}
