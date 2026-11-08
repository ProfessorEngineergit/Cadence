@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.professorengineergit.cadence

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun Onboarding(model: PracticeViewModel) {
    val state = model.state
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var title by rememberSaveable { mutableStateOf(state.activeGoal.title) }
    var kind by rememberSaveable { mutableStateOf(state.activeGoal.kind) }
    var weeklyMinutes by rememberSaveable { mutableIntStateOf(state.goals.sum().coerceAtLeast(20)) }
    var days by remember { mutableStateOf(state.goals.mapIndexedNotNull { i, value -> if (value > 0) i else null }.toSet()) }
    var weekday by remember { mutableStateOf(state.reminders.windows[0]) }
    var weekend by remember { mutableStateOf(state.reminders.windows[5]) }
    var reminders by rememberSaveable { mutableStateOf(true) }
    val availableMinutes = days.sumOf { if (it < 5) minOf(240, weekday.minutes) else minOf(240, weekend.minutes) }
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }
    fun finish(allowed: Boolean) {
        model.finishOnboarding(title, kind, weeklyMinutes, days, weekday, weekend)
        model.setReminders(model.state.reminders.copy(enabled = reminders && allowed))
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { finish(it) }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Cadence", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = !LocalEnglish.current, onClick = { model.setLanguage(AppLanguage.DE) }, label = { Text("DE") })
                    FilterChip(selected = LocalEnglish.current, onClick = { model.setLanguage(AppLanguage.EN) }, label = { Text("EN") })
                }
            }
            LinearProgressIndicator(progress = { (step + 1) / 4f }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 22.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                when (step) {
                    0 -> {
                        Box(Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(androidx.compose.ui.graphics.Color(0xFFE5DAF5))) { Image(painterResource(R.drawable.ic_cadence), "Cadence", Modifier.fillMaxSize()) }
                        }
                        Text(words("Aus Übezeit\nwird Fortschritt.", "Turn practice\ninto progress."), style = MaterialTheme.typography.headlineLarge)
                        Text(words("Cadence zählt deine Übezeit und zeigt, wie viel bis zu deinem Ziel noch fehlt. Du entscheidest, was du übst.", "Cadence tracks your practice time and shows how much is left to your goal. You choose what to practise."), style = MaterialTheme.typography.bodyLarge)
                        IntroFeature(Icons.Rounded.Timer, words("Miss jede Übesession.", "Track each practice session."), words("Timer starten, pausieren und beenden. Ohne Timer geübt? Zeit nachtragen.", "Start, pause and finish the timer. Practised without it? Log the time afterwards."))
                        IntroFeature(Icons.Rounded.TrackChanges, words("Behalte jedes Vorhaben im Blick.", "Make room for every goal."), words("Instrument, Referat oder Test: eigene Ziele und getrennte Fortschritte.", "An instrument, a presentation or a test: separate goals and separate progress."))
                        IntroFeature(Icons.Rounded.BarChart, words("Plane und bleib dran.", "Make a plan. Keep going."), words("Wochenziel und Übetage wählen. Erinnerungen passen in deinen Alltag.", "Choose a weekly target and practice days. Reminders fit your daily routine."))
                    }
                    1 -> {
                        Text(words("Was ist dein\nerstes Übeziel?", "What's your\nfirst practice goal?"), style = MaterialTheme.typography.headlineLarge)
                        Text(words("Ein Übeziel ist ein Vorhaben: zum Beispiel Klavier spielen, ein Referat proben oder für einen Test lernen. Jede Sache bekommt ihre eigene Übezeit und ihren eigenen Fortschritt.", "A practice goal is something you work on: playing piano, rehearsing a presentation or preparing for a test. Each goal has its own practice time and progress."), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(title, { title = it.take(60) }, label = { Text(words("Dein erstes Ziel", "Your first goal")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                        KindChips(kind) { kind = it }
                        Text(words("An welchen Tagen?", "Which days?"), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) { (0..6).forEach { i -> FilterChip(selected = i in days, onClick = { days = if (i in days) days - i else days + i; weeklyMinutes = weeklyMinutes.coerceAtLeast(days.size * 5) }, label = { Text(dayShort(i)) }) } }
                        Text(words("$weeklyMinutes Minuten pro Woche", "$weeklyMinutes minutes per week"), style = MaterialTheme.typography.headlineSmall)
                        HapticSlider(weeklyMinutes.toFloat().coerceIn(20f, 300f), { weeklyMinutes = ((it / 5).roundToInt() * 5).coerceAtLeast(days.size * 5) }, 20f..300f, 55)
                        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Column(Modifier.fillMaxWidth().padding(20.dp)) { Text("$weeklyMinutes min", style = MaterialTheme.typography.headlineLarge); Text(words("Cadence verteilt dein Wochenziel auf ${days.size} Übetage. Jeden Tag kannst du später einzeln anpassen.", "Cadence shares your weekly target across ${days.size} practice days. Fine-tune individual days later."), style = MaterialTheme.typography.bodySmall) }
                        }
                        Text(words("Weitere Ziele oder ein anderes Vorhaben: Tippe später auf der Startseite auf den Zielnamen. Dort kannst du wechseln, Ziele hinzufügen und auch einen Zieltermin festlegen.", "For more goals or another project, tap the goal name on the home screen. Switch goals, add new ones and set deadlines there."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    2 -> {
                        Text(words("Wann passt\nÜben in dein Leben?", "When does practice\nfit your life?"), style = MaterialTheme.typography.headlineLarge)
                        Text(words("Hier trägst du ein, wann du Zeit hast – zum Beispiel nach der Schule. Du musst nicht das ganze Fenster üben: Dein Ziel bleibt $weeklyMinutes Minuten pro Woche. Im Rhythmus kannst du später jeden Tag genau planen.", "Set when you have time, for example after school. You don't have to fill the whole window: your target stays $weeklyMinutes minutes per week. Fine-tune individual days in Rhythm later."), style = MaterialTheme.typography.bodyMedium)
                        Text(words("Schultage / Montag–Freitag", "School days / Monday–Friday"), style = MaterialTheme.typography.titleMedium)
                        AvailabilityEditor(weekday) { weekday = it }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(words("Wochenende / Samstag–Sonntag", "Weekend / Saturday–Sunday"), style = MaterialTheme.typography.titleMedium)
                        AvailabilityEditor(weekend) { weekend = it }
                        Text(words("Deine gewählten Übetage bieten $availableMinutes Minuten Platz für dein Wochenziel von $weeklyMinutes Minuten.", "Your selected practice days offer $availableMinutes minutes for your weekly target of $weeklyMinutes minutes."), style = MaterialTheme.typography.bodySmall, color = if (availableMinutes < weeklyMinutes) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        Text(words("Motivation,\ndie mitdenkt.", "Motivation\nthat pays attention."), style = MaterialTheme.typography.headlineLarge)
                        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.NotificationsActive, null)
                                Text(words("Gestern stark. Heute ganz entspannt.", "Great work yesterday. Take it easy today."), style = MaterialTheme.typography.titleLarge)
                                Text(words("Beispiel: Noch 30 % bis zu deinem Wochenziel. Morgen ist dein Check-in. 15 Minuten passen heute gut.", "Example: 30% left to your weekly goal. Your check-in is tomorrow. 15 minutes fits today."), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        IntroFeature(Icons.Rounded.Schedule, words("Nur, wenn es passt.", "Only when it fits."), words("In deinen Zeitfenstern. Mindestens drei Stunden Abstand. Nach dem Üben erst einmal Ruhe.", "Within your available hours. At least three hours apart. Quiet after practice."))
                        IntroFeature(Icons.AutoMirrored.Rounded.ListAlt, words("Dein Fortschritt bleibt sichtbar.", "See how far you've come."), words("Im Verlauf findest du deine Sessions. Ergänze Notizen oder korrigiere eine Übezeit. Im Rhythmus änderst du deinen Plan.", "Find your sessions in Journal. Add notes or correct practice time. Change your plan in Rhythm."))
                        IntroFeature(Icons.Rounded.Widgets, words("Dein Fortschritt, immer in Reichweite.", "Your progress, always close by."), words("Füge später das Widget hinzu: Wochenstand und Timer direkt auf deinem Homescreen.", "Add the widget later for weekly progress and quick start on your home screen."))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(words("Smarte Erinnerungen", "Smart reminders"), style = MaterialTheme.typography.titleMedium); Text(words("Android fragt dich gleich nach der Erlaubnis.", "Android will ask for permission next."), style = MaterialTheme.typography.bodySmall) }
                            val toggle = tap({ reminders = !reminders }); Switch(reminders, { toggle() })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0) OutlinedButton(onClick = { step-- }, modifier = Modifier.height(58.dp)) { Text(words("Zurück", "Back")) }
                Button(onClick = {
                    if (step < 3) step++
                    else if (reminders && Build.VERSION.SDK_INT >= 33 && !ReminderScheduler.allowed(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else finish(ReminderScheduler.allowed(context))
                }, enabled = (step != 1 || (title.isNotBlank() && days.isNotEmpty())) && (step != 2 || availableMinutes >= weeklyMinutes), modifier = Modifier.weight(1f).height(58.dp)) { Text(if (step == 3) words("Mit Cadence starten", "Start with Cadence") else words("Weiter", "Continue")) }
            }
        }
    }
}

@Composable private fun IntroFeature(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 3.dp).size(24.dp), tint = MaterialTheme.colorScheme.secondary)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
