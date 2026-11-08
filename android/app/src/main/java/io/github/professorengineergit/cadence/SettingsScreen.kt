@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.professorengineergit.cadence

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZonedDateTime

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 12.dp))
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) { Column(Modifier.fillMaxWidth(), content = content) }
}

@Composable
private fun ToggleOption(icon: ImageVector, title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val toggle = tap({ onChange(!checked) }, if (checked) androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOff else androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn)
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = toggle).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(23.dp), tint = if (enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = { toggle() }, enabled = enabled)
    }
}

@Composable private fun Divider() = HorizontalDivider(Modifier.padding(start = 55.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

@Composable
fun nextReminderText(next: PlannedReminder?): String {
    if (next == null) return words("In den nächsten Tagen steht keine Erinnerung an.", "No reminder coming up in the next days.")
    val date = next.at.toLocalDate()
    val day = when (date) { LocalDate.now() -> words("heute", "today"); LocalDate.now().plusDays(1) -> words("morgen", "tomorrow"); else -> dayName(date.dayOfWeek.value - 1) }
    val time = clock(next.at.hour * 60 + next.at.minute)
    return when (next.kind) {
        ReminderKind.PRAISE -> words("Nächste Nachricht: $day um $time (ein freier Tag)", "Next message: $day at $time (a day off)")
        ReminderKind.SNOOZED -> words("Verschoben auf $time", "Snoozed until $time")
        else -> words("Nächste Erinnerung: $day um $time", "Next reminder: $day at $time")
    }
}

@Composable
fun SettingsScreen(state: CadenceState, model: PracticeViewModel) {
    val context = LocalContext.current
    val reminders = state.reminders
    val sessions by rememberUpdatedState(state.sessions)
    var enableAfterGrant by remember { mutableStateOf(false) }
    var liveAfterGrant by remember { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(ReminderScheduler.allowed(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = ReminderScheduler.allowed(context)
        if (granted && liveAfterGrant) model.setLiveTimer(true)
        else if (granted && enableAfterGrant) model.setReminders(model.state.reminders.copy(enabled = true))
        else if (!granted) model.notify(wordsNow("Benachrichtigungen sind noch nicht erlaubt. Du kannst sie in den Android-Einstellungen freigeben.", "Notifications aren't allowed yet. You can turn them on in Android settings.", model.state.language))
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching { (context.contentResolver.openOutputStream(uri) ?: error("File unavailable")).bufferedWriter(Charsets.UTF_8).use { it.write(exportCsv(sessions, model.state.practiceGoals, isEnglish(model.state.language))) } }
            .onSuccess { model.notify(wordsNow("Export gespeichert", "Export saved", model.state.language)) }.onFailure { model.notify(wordsNow("Export hat nicht geklappt.", "Export didn't work.", model.state.language)) }
    }
    fun openNotificationSettings() = context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    fun enable(value: Boolean) {
        liveAfterGrant = false
        if (!value || ReminderScheduler.allowed(context)) model.setReminders(reminders.copy(enabled = value))
        else if (Build.VERSION.SDK_INT >= 33) { enableAfterGrant = true; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        else openNotificationSettings()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 124.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageHeader(words("Einstellungen", "Settings"))
        Group(words("Erinnerungen", "Reminders")) {
            ToggleOption(Icons.Rounded.NotificationsActive, words("Erinnerungen", "Reminders"), words("Wenn deine freie Zeit beginnt und heute noch etwas ansteht", "When your free time starts and something is left for today"), reminders.enabled, onChange = ::enable)
            if (reminders.enabled) {
                val next = remember(state) { ReminderScheduler.nextPreview(context, state) }
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Schedule, null, Modifier.size(20.dp)); Text(nextReminderText(next), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(words("Höchstens pro Tag", "At most per day"), style = MaterialTheme.typography.titleMedium)
                SegmentedChoice(listOf("1", "2", "3"), reminders.dailyLimit - 1) { model.setReminders(reminders.copy(dailyLimit = it + 1)) }
                Text(words("Nach dem Üben ist eine Stunde Ruhe. Reagierst du drei Tage lang nicht, kommt nur noch eine Erinnerung pro Tag, bis du wieder übst.",
                    "After practice there's an hour of quiet. If you don't react for three days, you only get one reminder a day until you practise again."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Divider()
            ToggleOption(Icons.Rounded.AutoAwesome, words("Meine Übezeit lernen", "Learn when I practise"), words("Erinnert dann, wenn du meistens anfängst (ab drei Timer-Sessions)", "Reminds you when you usually start (after three timed sessions)"), reminders.learnTiming) { model.setReminders(reminders.copy(learnTiming = it)) }
            Divider()
            ToggleOption(Icons.Rounded.WbSunny, words("Freie Tage melden", "Tell me about days off"), words("Kurze Nachricht, wenn du vorgearbeitet hast und heute nichts machen musst", "A short note when you're ahead and can skip today"), reminders.praise) { model.setReminders(reminders.copy(praise = it)) }
            Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    if (ReminderScheduler.allowed(context)) {
                        val plans = Planner.plans(state, ZonedDateTime.now())
                        val copy = if (plans.any { it.todayRemaining > 0 }) ReminderEngine.copy(state, plans, ZonedDateTime.now(), ReminderKind.START)
                            else ReminderCopy(wordsNow("${state.activeGoal.title}: heute 20 Minuten", "${state.activeGoal.title}: 20 minutes today", state.language), wordsNow("So sieht eine Erinnerung aus. Echte Erinnerungen richten sich nach deinem Plan.", "This is what a reminder looks like. Real ones follow your plan.", state.language), state.activeGoal.id)
                        ReminderScheduler.show(context, copy, state.language, preview = true); model.notify(wordsNow("Probe-Benachrichtigung gesendet", "Test notification sent", state.language))
                    } else if (Build.VERSION.SDK_INT >= 33) { enableAfterGrant = false; liveAfterGrant = false; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }, modifier = Modifier.fillMaxWidth()) { Text(words("Probe-Benachrichtigung", "Send a test notification")) }
                if (!allowed) TextButton(onClick = ::openNotificationSettings) { Text(words("Android-Benachrichtigungen öffnen", "Open Android notification settings")) }
                Text(words("Manche Geräte verzögern Erinnerungen im Energiesparmodus um einige Minuten.", "Some devices delay reminders by a few minutes in battery saver."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Group(words("Timer & Klang", "Timer & sound")) {
            ToggleOption(Icons.Rounded.Timer, words("Timer in der Benachrichtigung", "Timer in notifications"), words("Mit Fortschritt, Pause und Fertig, auch auf dem Sperrbildschirm", "With progress, pause and done, also on the lock screen"), state.liveTimer) { enabled ->
                if (enabled && !ReminderScheduler.allowed(context) && Build.VERSION.SDK_INT >= 33) { liveAfterGrant = true; enableAfterGrant = false; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                else model.setLiveTimer(enabled)
            }
            Divider()
            ToggleOption(Icons.Rounded.MusicNote, words("Abschlussklang", "Completion chime"), words("Ein kurzer Klang, wenn du eine Session beendest", "A short chime when you finish a session"), state.completionSound, onChange = model::setCompletionSound)
            TextButton(onClick = {
                if (CompletionSound.canPlay(context)) CompletionSound.play(context, true)
                else model.notify(wordsNow("Dein Gerät ist lautlos oder „Nicht stören“ ist an.", "Your device is on silent or Do Not Disturb is on.", state.language))
            }, modifier = Modifier.padding(start = 46.dp, bottom = 8.dp)) { Text(words("Klang anhören", "Play chime")) }
        }
        Group(words("Darstellung", "Appearance")) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(words("Sprache", "Language"), style = MaterialTheme.typography.titleMedium)
                SegmentedChoice(listOf("System", "Deutsch", "English"), AppLanguage.entries.indexOf(state.language)) { model.setLanguage(AppLanguage.entries[it]) }
                Text(words("Design", "Theme"), style = MaterialTheme.typography.titleMedium)
                SegmentedChoice(listOf("System", words("Hell", "Light"), words("Dunkel", "Dark")), AppTheme.entries.indexOf(state.theme)) { model.setTheme(AppTheme.entries[it]) }
            }
            if (Build.VERSION.SDK_INT >= 31) ToggleOption(Icons.Rounded.Palette, words("Farben vom Hintergrundbild", "Wallpaper colours"), words("Passt die App-Farben an dein Gerät an", "Matches the app colours to your device"), state.dynamicColors, onChange = model::setDynamicColors)
            Divider()
            ToggleOption(Icons.Rounded.Vibration, words("Vibration", "Haptics"), words("Feines Feedback bei Tasten, Reglern und Erfolgen", "Subtle feedback for buttons, sliders and milestones"), state.haptics, onChange = model::setHaptics)
        }
        Group(words("Widget & Daten", "Widget & data")) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(words("Zeigt auf dem Startbildschirm, was heute dran ist, und startet den Timer mit einem Tipp.", "Shows today's practice on your home screen and starts the timer with one tap."), style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = {
                    val manager = AppWidgetManager.getInstance(context)
                    if (manager.isRequestPinAppWidgetSupported) manager.requestPinAppWidget(ComponentName(context, CadenceWidget::class.java), null, null)
                    else model.notify(wordsNow("Wähle Cadence im Widget-Menü deines Startbildschirms.", "Pick Cadence in your home screen's widget menu.", state.language))
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Widgets, null); Spacer(Modifier.width(8.dp)); Text(words("Widget hinzufügen", "Add widget")) }
                OutlinedButton(onClick = { export.launch("Cadence-${LocalDate.now()}.csv") }, enabled = state.sessions.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.FileDownload, null); Spacer(Modifier.width(8.dp)); Text(words("Sessions als CSV exportieren", "Export sessions as CSV")) }
            }
        }
        Group(words("Über Cadence", "About Cadence")) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(words("Deine Daten bleiben auf deinem Gerät. Kein Konto, keine Werbung, keine Tracker. Wenn die Android-Sicherung aktiv ist, kann sie die App-Daten mitsichern.",
                    "Your data stays on your device. No account, no ads, no trackers. If Android backup is on, it may include the app data."), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = model::replayOnboarding) { Icon(Icons.Rounded.Replay, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(words("Einrichtung noch einmal durchgehen", "Run setup again")) }
                Text("Cadence ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
