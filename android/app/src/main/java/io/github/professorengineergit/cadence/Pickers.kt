@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.professorengineergit.cadence

import android.text.format.DateFormat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Material 3 time and date pickers, opened from anywhere through [LocalPickers]. */
class Pickers {
    internal class TimeRequest(val initial: Int, val title: String?, val onPick: (Int) -> Unit)
    internal class DateRequest(val initial: LocalDate, val onPick: (LocalDate) -> Unit)
    internal var time by mutableStateOf<TimeRequest?>(null)
    internal var date by mutableStateOf<DateRequest?>(null)
    fun time(initial: Int, title: String? = null, onPick: (Int) -> Unit) { time = TimeRequest(initial, title, onPick) }
    fun date(initial: LocalDate, onPick: (LocalDate) -> Unit) { date = DateRequest(initial, onPick) }
}

val LocalPickers = staticCompositionLocalOf { Pickers() }

@Composable
fun PickerHost(content: @Composable () -> Unit) {
    val pickers = remember { Pickers() }
    CompositionLocalProvider(LocalPickers provides pickers, content = content)
    pickers.time?.let { request -> TimeDialog(request) { pickers.time = null } }
    pickers.date?.let { request -> DateDialog(request) { pickers.date = null } }
}

@Composable
private fun TimeDialog(request: Pickers.TimeRequest, onClose: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState((request.initial / 60) % 24, request.initial % 60, DateFormat.is24HourFormat(context))
    var keyboard by remember { mutableStateOf(false) }
    val confirm = tap({ request.onPick(state.hour * 60 + state.minute); onClose() })
    TimePickerDialog(onDismissRequest = onClose,
        title = { Text(request.title ?: words("Uhrzeit wählen", "Choose a time"), style = MaterialTheme.typography.labelLarge) },
        modeToggleButton = { TimePickerDialogDefaults.DisplayModeToggle(onDisplayModeChange = { keyboard = !keyboard }, displayMode = if (keyboard) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker) },
        confirmButton = { TextButton(onClick = confirm) { Text(words("OK", "OK")) } },
        dismissButton = { TextButton(onClick = onClose) { Text(words("Abbrechen", "Cancel")) } }) {
        if (keyboard) TimeInput(state) else TimePicker(state)
    }
}

@Composable
private fun DateDialog(request: Pickers.DateRequest, onClose: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = request.initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(LocalDate.now())
            override fun isSelectableYear(year: Int) = year >= LocalDate.now().year
        })
    DatePickerDialog(onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { request.onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }; onClose() }, enabled = state.selectedDateMillis != null) { Text(words("OK", "OK")) } },
        dismissButton = { TextButton(onClick = onClose) { Text(words("Abbrechen", "Cancel")) } }) {
        DatePicker(state, title = { Text(words("Datum wählen", "Choose a date"), modifier = androidx.compose.ui.Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) })
    }
}
