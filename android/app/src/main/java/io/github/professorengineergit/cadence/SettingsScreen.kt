@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.professorengineergit.cadence

import android.Manifest
import android.app.TimePickerDialog
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 10.dp))
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) { Column(Modifier.fillMaxWidth(), content = content) }
}
@Composable
private fun ToggleOption(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val toggle = tap({ onChange(!checked) })
    Row(Modifier.fillMaxWidth().clickable(onClick = toggle).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.secondary)
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = checked, onCheckedChange = { toggle() })
    }
}

@Composable
fun SettingsScreen(state: CadenceState, model: PracticeViewModel, onPlan: () -> Unit = {}) {
    val context = LocalContext.current
    val reminders = state.reminders
    val sessions by rememberUpdatedState(state.sessions)
    var enableAfterGrant by remember { mutableStateOf(false) }
    var liveAfterGrant by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && liveAfterGrant) model.setLiveTimer(true)
        else if (granted && enableAfterGrant) model.setReminders(model.state.reminders.copy(enabled = true))
        else if (!granted) model.notify(wordsNow("Benachrichtigungen sind noch nicht erlaubt. Du kannst sie in den Android-Einstellungen freigeben.", "Notifications are not allowed yet. You can enable them in Android settings.", model.state.language))
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching { (context.contentResolver.openOutputStream(uri) ?: error("File unavailable")).bufferedWriter(Charsets.UTF_8).use { it.write(exportCsv(sessions, model.state.practiceGoals, isEnglish(model.state.language))) } }
            .onSuccess { model.notify("Übetagebuch exportiert") }.onFailure { model.notify("Export fehlgeschlagen. Bitte erneut versuchen.") }
    }
    fun enable(value: Boolean) {
        liveAfterGrant = false
        if (!value || ReminderScheduler.allowed(context)) model.setReminders(reminders.copy(enabled = value))
        else if (Build.VERSION.SDK_INT >= 33) { enableAfterGrant = true; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    val currentState by rememberUpdatedState(state)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 124.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader("", null)
        Text(words("Ganz dein\nCadence.", "Make it\nyour Cadence."), style = MaterialTheme.typography.headlineLarge)
        Group(words("Timer & Klang", "Timer & sound")) {
            ToggleOption(Icons.Rounded.Timer, words("Live-Timer", "Live timer"), words("Laufzeit, Pause und Beenden in der Benachrichtigung", "Elapsed time, pause and finish in your notification"), state.liveTimer) { enabled ->
                if (enabled && !ReminderScheduler.allowed(context) && Build.VERSION.SDK_INT >= 33) { liveAfterGrant = true; enableAfterGrant = false; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                else model.setLiveTimer(enabled)
            }
            ToggleOption(Icons.Rounded.MusicNote, words("Abschlussklang", "Completion chime"), words("Ein warmer Dreiklang, wenn du eine Session beendest", "A warm chime when you finish a session"), state.completionSound, model::setCompletionSound)
            Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = {
                    if (CompletionSound.canPlay(context)) CompletionSound.play(context, true)
                    else model.notify(wordsNow("Dein Gerät ist lautlos oder „Nicht stören“ ist aktiv.", "Your device is silent or Do Not Disturb is active.", state.language))
                }) { Text(words("Klang anhören", "Preview chime")) }
                Text(words("Der Timer zählt auch bei geschlossener App weiter. Android 16 kann ihn als Live-Update hervorheben; die Darstellung hängt von deinem Gerät ab. Lautlos und „Nicht stören“ werden respektiert.", "Your timer keeps counting when the app is closed. Android 16 can promote it as a Live Update; presentation depends on your device. Silent mode and Do Not Disturb are respected."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Group(words("Erinnerungen", "Reminders")) {
            ToggleOption(Icons.Rounded.NotificationsActive, words("Dein smarter Übepartner", "Your smart practice companion"), words("Fortschritt, Zeitfenster und letzte Sessions berücksichtigen", "Adapts to progress, availability and recent sessions"), reminders.enabled, ::enable)
            HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ToggleOption(Icons.Rounded.AutoAwesome, words("Lerne meinen Rhythmus", "Learn my rhythm"), words("Nach 3 Timer-Sessions passendere Zeiten wählen", "Finds better times after 3 timed sessions"), reminders.learnTiming) { model.setReminders(reminders.copy(learnTiming = it)) }
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(words("Maximale Erinnerungen pro Tag", "Maximum reminders per day"), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..3).forEach { n -> FilterChip(selected = reminders.dailyLimit == n, onClick = { model.setReminders(reminders.copy(dailyLimit = n)) }, label = { Text("$n") }) } }
                Text(words("Mindestens 3 Stunden Abstand. Ruhe nach dem Üben und bei erreichtem Ziel. Direkt in der Nachricht: später erinnern oder heute pausieren.", "At least 3 hours apart. Quiet after practice and when your goal is reached. Snooze or take today off directly from the notification."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(words("Wochen-Check-in", "Weekly check-in"), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEachIndexed { i, day -> FilterChip(selected = reminders.deadlineDay == i + 1, onClick = { model.setReminders(reminders.copy(deadlineDay = i + 1)) }, label = { Text(day) }) }
                }
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, h, m -> model.setReminders(currentState.reminders.copy(deadlineMinute = h * 60 + m)) }, reminders.deadlineMinute / 60, reminders.deadlineMinute % 60, true).show()
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(clock(reminders.deadlineMinute)) }
                TextButton(onClick = onPlan) { Text(words("Zeitfenster im Wochenplan einstellen", "Set availability in your weekly plan")); Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp)) }
                val evaluation = ReminderEngine.evaluateAll(state, ZonedDateTime.now(), DeliveryStore(context).read())
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(words("So denkt Cadence gerade", "What Cadence is thinking"), style = MaterialTheme.typography.labelLarge)
                        Text(evaluation.notification?.body ?: evaluation.explanation, style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = {
                    if (ReminderScheduler.allowed(context)) {
                        val copy = evaluation.notification ?: ReminderCopy(wordsNow("Ein kleiner Schritt für dein Ziel.", "A small step towards your goal.", state.language), wordsNow("Beispiel: Noch 30 % bis zum Wochenziel. 15 Minuten passen heute gut. Deine echten Erinnerungen verwenden deinen aktuellen Fortschritt.", "Example: 30% left to your weekly goal. 15 minutes fits today. Real reminders use your actual progress.", state.language), 15)
                        ReminderScheduler.show(context, copy, preview = true); model.notify("Test-Benachrichtigung gesendet")
                    } else if (Build.VERSION.SDK_INT >= 33) { enableAfterGrant = false; liveAfterGrant = false; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }, modifier = Modifier.fillMaxWidth()) { Text(words("Test-Benachrichtigung", "Send test notification")) }
                if (!ReminderScheduler.allowed(context)) TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text(words("Android-Benachrichtigungen öffnen", "Open Android notification settings")) }
                Text(words("Android und Oppos Energiesparmodus können Hintergrundarbeit verzögern. Cadence erinnert innerhalb deiner Zeitfenster, nicht minutengenau. Nach „Stopp erzwingen“ die App erneut öffnen.", "Android and Oppo's battery saver can delay background work. Reminders run within your time windows, rather than at an exact minute. Reopen Cadence after force-stopping it."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Group(words("Sprache & Darstellung", "Language & appearance")) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(words("Sprache", "Language"), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { AppLanguage.entries.forEach { lang -> FilterChip(selected = state.language == lang, onClick = { model.setLanguage(lang) }, label = { Text(when (lang) { AppLanguage.SYSTEM -> "System"; AppLanguage.DE -> "Deutsch"; AppLanguage.EN -> "English" }) }) } }
                Text(words("Erscheinungsbild", "Appearance"), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AppTheme.entries.forEach { theme -> FilterChip(selected = state.theme == theme, onClick = { model.setTheme(theme) }, label = { Text(when(theme) { AppTheme.SYSTEM -> "System"; AppTheme.LIGHT -> words("Hell", "Light"); AppTheme.DARK -> words("Dunkel", "Dark") }) }) } }
            }
            if (Build.VERSION.SDK_INT >= 31) ToggleOption(Icons.Rounded.Palette, words("Farben deines Geräts", "Use device colours"), words("An dein Hintergrundbild anpassen", "Match your wallpaper"), state.dynamicColors, model::setDynamicColors)
            ToggleOption(Icons.Rounded.Vibration, words("Haptisches Feedback", "Haptic feedback"), words("Feine Klicks bei Tasten, Tabs und Slider-Stufen", "Subtle clicks for buttons, tabs and slider steps"), state.haptics, model::setHaptics)
        }
        Group(words("Widget & Daten", "Widget & data")) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(words("Dein Rhythmus auf dem Homescreen", "Your rhythm on your home screen"), style = MaterialTheme.typography.titleMedium)
                Text(words("Wochenfortschritt, heutiger Plan und Schnellstart – direkt im Cadence-Widget.", "Weekly progress, today's plan and quick start in your Cadence widget."), style = MaterialTheme.typography.bodySmall)
                FilledTonalButton(onClick = {
                    val manager = AppWidgetManager.getInstance(context)
                    if (manager.isRequestPinAppWidgetSupported) manager.requestPinAppWidget(ComponentName(context, CadenceWidget::class.java), null, null)
                    else model.notify(wordsNow("Cadence im Widget-Menü deines Launchers auswählen.", "Choose Cadence in your launcher's widget menu.", state.language))
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Widgets, null); Spacer(Modifier.width(8.dp)); Text(words("Widget hinzufügen", "Add widget")) }
                OutlinedButton(onClick = { export.launch("Cadence-${LocalDate.now()}.csv") }, enabled = state.sessions.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.FileDownload, null); Spacer(Modifier.width(8.dp)); Text(words("Alle Sessions exportieren", "Export all sessions")) }
            }
        }
        Group(words("Über Cadence", "About Cadence")) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Made for your rhythm.", style = MaterialTheme.typography.titleLarge)
                Text(words("Musik, Lernen oder ein neues Können: Cadence macht aus deiner verfügbaren Zeit einen realistischen Übeplan. Fortschritt statt Perfektion.", "Music, studying or a new skill: Cadence turns your available time into a realistic practice plan. Progress over perfection."), style = MaterialTheme.typography.bodyMedium)
                Text(words("Deine Daten bleiben auf deinem Gerät. Android kann sie über die aktivierte Systemsicherung sichern. Kein Konto, keine Werbung.", "Your data stays on your device. Android may include it in enabled system backups. No account, no ads."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = model::replayOnboarding) { Text(words("Einführung und Ziele erneut ansehen", "Revisit introduction and goals")) }
                Text("Cadence 1.0", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
