@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.professorengineergit.cadence

import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.app.LocaleManager
import android.os.LocaleList
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import dev.chrisbanes.haze.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private val German = Locale.GERMAN
private val DayNames = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
private val DayShort = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

class MainActivity : ComponentActivity() {
    private var startRequested by mutableStateOf(false)
    private var requestedGoalId by mutableStateOf<String?>(null)
    private var launchConsumed = false
    private var homeRequest by mutableIntStateOf(0)
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); launchConsumed = false; requestedGoalId = intent.getStringExtra("goal_id"); startRequested = intent.action == ReminderScheduler.START_ACTION; if (startRequested || requestedGoalId != null) homeRequest++ }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("launch_consumed", launchConsumed); super.onSaveInstanceState(outState) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchConsumed = savedInstanceState?.getBoolean("launch_consumed", false) ?: false
        startRequested = !launchConsumed && intent.action == ReminderScheduler.START_ACTION
        requestedGoalId = if (launchConsumed) null else intent.getStringExtra("goal_id")
        enableEdgeToEdge()
        setContent {
            val model: PracticeViewModel = viewModel()
            SideEffect {
                if (Build.VERSION.SDK_INT >= 33) {
                    val manager = getSystemService(LocaleManager::class.java)
                    val desired = when (model.state.language) { AppLanguage.SYSTEM -> LocaleList.getEmptyLocaleList(); AppLanguage.DE -> LocaleList.forLanguageTags("de"); AppLanguage.EN -> LocaleList.forLanguageTags("en") }
                    if (manager.applicationLocales != desired) manager.applicationLocales = desired
                }
            }
            LaunchedEffect(startRequested, requestedGoalId) {
                val handled = startRequested || requestedGoalId != null
                requestedGoalId?.let { model.selectGoal(it) }
                if (startRequested) model.start()
                startRequested = false; requestedGoalId = null
                if (handled) launchConsumed = true
            }
            LaunchedEffect(Unit) { ReminderScheduler.ensure(this@MainActivity, model.state.reminders.enabled); CadenceWidget.updateAll(this@MainActivity); TimerNotifications.sync(this@MainActivity, model.state) }
            CadenceTheme(model.state) {
                val dark = MaterialTheme.colorScheme.background.luminanceValue() < 0.5f
                val view = LocalView.current
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                CadenceFeedback(model.state.haptics) { CompositionLocalProvider(LocalEnglish provides isEnglish(model.state.language)) { if (model.state.onboarded) CadenceApp(model, homeRequest) else Onboarding(model) } }
            }
        }
    }
}
private fun Color.luminanceValue() = (red * 0.2126f + green * 0.7152f + blue * 0.0722f)

@Composable
private fun CadenceApp(model: PracticeViewModel, homeRequest: Int) {
    val state = model.state
    val today = Instant.ofEpochMilli(model.now).atZone(ZoneId.systemDefault()).toLocalDate()
    val stats = remember(state.selectedSessions, state.goals, today, state.activeGoalId) { PracticeStats(state.selectedSessions, state.goals, today) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(homeRequest) { if (homeRequest > 0) tab = 0 }
    val haze = remember { HazeState() }
    var goalPicker by remember { mutableStateOf(false) }
    var goalEditor by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<PracticeGoal?>(null) }
    var focusPicker by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf(false) }
    var editingSession by remember { mutableStateOf<PracticeSession?>(null) }
    var entryDate by remember { mutableStateOf(today) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var discard by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { while (true) { model.tick(); delay(1000) } }
    LaunchedEffect(model.message) {
        model.message?.let { snackbar.showSnackbar(it); model.clearMessage() }
    }
    fun openEntry(session: PracticeSession? = null, date: LocalDate = today) {
        editingSession = session; entryDate = session?.date() ?: date; editor = true
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = 100.dp)) },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(tab, label = "Navigation", modifier = Modifier.widthIn(max = 640.dp).fillMaxSize().hazeSource(haze)) { page ->
                when (page) {
                    0 -> HomeScreen(state, stats, model.now, onSettings = { tab = 3 },
                        onStart = model::start, onPause = model::togglePause, onFinish = model::finish,
                        onFocus = { focusPicker = true }, onGoal = { goalPicker = true }, onAdd = { openEntry() }, onDay = { selectedDay = it },
                        onHistory = { tab = 1 }, onEdit = { openEntry(it) }, onDiscard = { discard = true })
                    1 -> HistoryScreen(state, stats, onSettings = { tab = 3 }, onAdd = { openEntry() }, onEdit = { openEntry(it) })
                    2 -> PlanScreen(state, stats, onSettings = { tab = 3 }, onGoal = { goalPicker = true }, onDayPlan = model::setDayPlan, onWeeklyPlan = model::setWeeklyPlan)
                    3 -> SettingsScreen(state, model, onPlan = { tab = 2 })
                }
            }
            Box(Modifier.align(Alignment.BottomCenter)) { CadenceTabBar(tab, haze) { tab = it } }
        }
    }
    if (goalPicker) GoalPickerSheet(state, onDismiss = { goalPicker = false }, onSelect = { model.selectGoal(it); goalPicker = false }, onNew = { goalPicker = false; editingGoal = null; goalEditor = true }, onEdit = { goalPicker = false; editingGoal = it; goalEditor = true })
    if (goalEditor) GoalEditorSheet(editingGoal, onDismiss = { goalEditor = false }, onSave = { model.saveGoal(it); goalEditor = false })
    if (focusPicker) FocusSheet(state, onDismiss = { focusPicker = false }, onSave = { focus, minutes -> model.setFocus(focus, minutes); focusPicker = false })
    if (editor) SessionSheet(editingSession, entryDate, onDismiss = { editor = false }, onSave = { date, seconds, focus, note ->
        model.saveSession(editingSession, date, seconds, focus, note); editor = false
    }, onDelete = editingSession?.let { { model.deleteSession(it); editor = false } })
    selectedDay?.let { day -> DaySheet(day, stats, onDismiss = { selectedDay = null }, onAdd = { selectedDay = null; openEntry(date = day) },
        onEdit = { selectedDay = null; openEntry(it) }) }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, icon = { Icon(Icons.Rounded.RestartAlt, null) },
        title = { Text("Session verwerfen?") }, text = { Text("Die laufende Übezeit wird nicht gespeichert.") },
        confirmButton = { TextButton(onClick = { model.discardTimer(); discard = false }) { Text("Verwerfen") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Weiterüben") } })
}

@Composable
fun PageHeader(subtitle: String, onSettings: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE5DAF5)), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_cadence), "Cadence", modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Cadence", style = MaterialTheme.typography.headlineSmall)
            Text("Übezeit-Tracker", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeScreen(state: CadenceState, stats: PracticeStats, now: Long, onSettings: () -> Unit,
    onStart: () -> Unit, onPause: () -> Unit, onFinish: () -> Unit, onFocus: () -> Unit, onGoal: () -> Unit, onAdd: () -> Unit,
    onDay: (LocalDate) -> Unit, onHistory: () -> Unit, onEdit: (PracticeSession) -> Unit, onDiscard: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 124.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("", onSettings) }
        item { GoalPill(state, onGoal) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.timer != null) words("Bleib in deinem Flow.", "Stay in your flow.") else words("Zeit für Fortschritt.", "Time for progress."), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                if (stats.streak > 0) Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("${stats.streak}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        item { TimerCard(state, now, onStart, onPause, onFinish, onFocus, onDiscard) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onAdd) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Übezeit nachtragen") }
            }
        }
        item { WeekCard(stats, onDay) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Heute", "${stats.todayMinutes}", if (stats.todayGoal > 0) "von ${stats.todayGoal} Minuten" else "Minuten · freier Tag", Icons.Rounded.Today, Modifier.weight(1f), true)
                StatTile("Deine Serie", "${stats.streak}", if (stats.streak == 1) "Tag in Folge" else "Tage in Folge", Icons.Rounded.LocalFireDepartment, Modifier.weight(1f), false)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Zuletzt geübt", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onHistory) { Text("Alle"); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp)) }
            }
        }
        if (state.selectedSessions.isEmpty()) item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(14.dp))
                    Column { Text("Hier beginnt dein Fortschritt.", style = MaterialTheme.typography.titleMedium); Text("Starte deine erste Session. Jeder Moment zählt.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        } else items(state.selectedSessions.sortedByDescending { it.startedAt }.take(3), key = { it.id }) { SessionRow(it, onClick = { onEdit(it) }) }
    }
}

@Composable
private fun TimerCard(state: CadenceState, now: Long, onStart: () -> Unit, onPause: () -> Unit,
    onFinish: () -> Unit, onFocus: () -> Unit, onDiscard: () -> Unit) {
    val timer = state.timer
    val elapsed = timer?.elapsedMillis(now)?.div(1000) ?: 0L
    val target = timer?.targetMinutes ?: state.focusMinutes
    val progress by animateFloatAsState((elapsed.toFloat() / (target * 60)).coerceIn(0f, 1f), label = "Timerfortschritt")
    val haptic = LocalHapticFeedback.current
    val haptics = LocalHapticsEnabled.current
    val cardColor by animateColorAsState(if (timer?.isRunning == true) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer, label = "Timerfarbe")
    val ink = if (timer?.isRunning == true) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(color = cardColor, contentColor = ink, shape = RoundedCornerShape(36.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(when { timer == null -> "ZEIT FÜR DICH"; timer.isRunning -> "IM FLOW"; else -> "KURZ DURCHATMEN" }, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                if (timer == null) {
                    Surface(onClick = tap(onFocus), color = ink.copy(alpha = 0.08f), contentColor = ink, shape = CircleShape) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(state.focus, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp))
                            Icon(Icons.Rounded.ExpandMore, "Fokus und Dauer ändern", Modifier.size(18.dp))
                        }
                    }
                } else IconButton(onClick = onDiscard) { Icon(Icons.Rounded.Close, "Laufende Session verwerfen", Modifier.size(20.dp)) }
            }
            Box(Modifier.size(228.dp), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(progress = { if (timer == null) 1f else progress }, modifier = Modifier.fillMaxSize(),
                    color = ink.copy(alpha = if (timer == null) 0.20f else 0.85f), trackColor = ink.copy(alpha = 0.12f),
                    amplitude = { 1f }, wavelength = 28.dp, waveSpeed = if (timer?.isRunning == true) 16.dp else 0.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatDuration(elapsed), style = if (elapsed < 3600) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium,
                        color = ink, maxLines = 1)
                    Text(if (timer == null) "$target Min. Fokuszeit" else if (timer.isRunning) timer.focus else "Session pausiert", style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                    if (timer != null && elapsed >= target * 60) Text("Fokusziel erreicht ✓", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (timer == null) {
                Button(onClick = onStart, feedback = HapticFeedbackType.Confirm, shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = cardColor)) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(26.dp)); Spacer(Modifier.width(10.dp)); Text("Üben starten", style = MaterialTheme.typography.titleMedium)
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onPause, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).height(60.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = ink.copy(alpha = 0.1f), contentColor = ink)) {
                    Icon(if (timer.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(22.dp)); Spacer(Modifier.width(6.dp)); Text(if (timer.isRunning) "Pause" else "Weiter")
                }
                Button(onClick = onFinish, feedback = HapticFeedbackType.Confirm, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = cardColor)) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Beenden")
                }
            }
        }
    }
}

@Composable
private fun WeekCard(stats: PracticeStats, onDay: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val maxMinutes = max(30L, (0..6).maxOf { max(stats.minutesOn(stats.weekStart.plusDays(it.toLong())), stats.goals[it].toLong()) }).toFloat()
    Surface(color = colors.surfaceContainerLow, shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Diese Woche", style = MaterialTheme.typography.titleLarge)
                    Text(if (stats.weeklyGoal == 0) "Dein Tempo zählt." else if (stats.weekMinutes >= stats.weeklyGoal) "Wochenziel erreicht. Stark!" else "Kleine Schritte. Große Wirkung.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${stats.weekMinutes}", style = MaterialTheme.typography.headlineMedium, color = colors.primary)
                    Text(if (stats.weeklyGoal > 0) "/ ${stats.weeklyGoal} Min." else "Minuten", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom) {
                (0..6).forEach { i ->
                    val date = stats.weekStart.plusDays(i.toLong())
                    val minutes = stats.minutesOn(date)
                    val goal = stats.goals[i]
                    val isToday = date == stats.today
                    val spoken = words("${DayNames[i]}, $minutes von $goal Minuten${if (isToday) ", heute" else ""}", "${translate(DayNames[i], true)}, $minutes of $goal minutes${if (isToday) ", today" else ""}")
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = tap({ onDay(date) })).padding(vertical = 4.dp)
                        .clearAndSetSemantics { contentDescription = spoken },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.height(65.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.width(24.dp).height(max(6f, goal / maxMinutes * 65).dp).clip(CircleShape).background(colors.outlineVariant.copy(alpha = 0.6f)))
                            if (minutes > 0) Box(Modifier.width(24.dp).height(max(6f, minutes / maxMinutes * 65).dp).clip(CircleShape).background(if (isToday) colors.primary else colors.secondary))
                        }
                        Surface(color = if (isToday) colors.primary else Color.Transparent, contentColor = if (isToday) colors.onPrimary else colors.onSurfaceVariant, shape = CircleShape) {
                            Text(DayShort[i], style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp))
                        }
                    }
                }
            }
            LinearWavyProgressIndicator(progress = { if (stats.weeklyGoal > 0) (stats.weekMinutes.toFloat() / stats.weeklyGoal).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(10.dp), color = colors.primary, trackColor = colors.outlineVariant, waveSpeed = 0.dp)
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, subtitle: String, icon: ImageVector, modifier: Modifier, coral: Boolean) {
    Surface(modifier, color = if (coral) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(24.dp),
        contentColor = if (coral) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(label, style = MaterialTheme.typography.labelMedium) }
            Text(value, style = MaterialTheme.typography.headlineLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SessionRow(session: PracticeSession, onClick: () -> Unit) {
    Surface(onClick = tap(onClick), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(46.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(session.focus, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(session.note.ifEmpty { session.date().format(DateTimeFormatter.ofPattern("d. MMMM", German)) }, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (session.durationSeconds < 60) "${session.durationSeconds} Sek." else "${session.durationSeconds / 60} Min.", style = MaterialTheme.typography.titleMedium)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Session bearbeiten", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryScreen(state: CadenceState, stats: PracticeStats, onSettings: () -> Unit, onAdd: () -> Unit, onEdit: (PracticeSession) -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val filtered = state.selectedSessions.filter { when (filter) {
        0 -> it.date() >= stats.weekStart && it.date() < stats.weekStart.plusDays(7)
        1 -> it.date().month == stats.today.month && it.date().year == stats.today.year
        else -> true
    } }.sortedByDescending { it.startedAt }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 124.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("Dein Übetagebuch", onSettings) }
        item { Text("Jeder Moment\nzählt.", style = MaterialTheme.typography.headlineLarge) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Woche", "Monat", "Alle").forEachIndexed { i, title -> FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(title) },
                    leadingIcon = if (filter == i) { { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) } } else null) }
            }
        }
        item {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(28.dp)) {
                Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("${filtered.sumOf { it.durationSeconds } / 60} Min.", style = MaterialTheme.typography.headlineLarge); Text("${filtered.size} Sessions in diesem Zeitraum", style = MaterialTheme.typography.bodyMedium) }
                    FilledTonalIconButton(onClick = onAdd, modifier = Modifier.size(56.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.onTertiaryContainer, contentColor = MaterialTheme.colorScheme.tertiaryContainer)) { Icon(Icons.Rounded.Add, "Übezeit nachtragen") }
                }
            }
        }
        if (filtered.isEmpty()) item { EmptyHistory(onAdd) }
        filtered.groupBy { it.date() }.forEach { (date, sessions) ->
            item(key = "date-$date") {
                Row(Modifier.padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (date == stats.today) "Heute" else date.format(DateTimeFormatter.ofPattern("EEEE, d. MMM", German)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${sessions.sumOf { it.durationSeconds } / 60} Min.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(sessions, key = { it.id }) { session -> SessionRow(session) { onEdit(session) } }
        }
        if (filtered.isNotEmpty()) item { TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Übezeit nachtragen") } }
    }
}

@Composable
private fun EmptyHistory(onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(88.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Spa, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(38.dp))
        }
        Text("Platz für deinen Fortschritt.", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text("Deine Sessions erscheinen hier.\nAuch nachgetragene Übezeit zählt.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        OutlinedButton(onClick = onAdd) { Text("Erste Session eintragen") }
    }
}
