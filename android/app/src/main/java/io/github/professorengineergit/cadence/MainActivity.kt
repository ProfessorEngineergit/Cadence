@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.professorengineergit.cadence

import android.app.LocaleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

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
                if (startRequested) model.start(requestedGoalId)
                startRequested = false; requestedGoalId = null
                if (handled) launchConsumed = true
            }
            LaunchedEffect(Unit) { ReminderScheduler.refresh(this@MainActivity, model.state); CadenceWidget.updateAll(this@MainActivity); TimerNotifications.sync(this@MainActivity, model.state) }
            CadenceTheme(model.state) {
                val dark = MaterialTheme.colorScheme.background.luminanceValue() < 0.5f
                val view = LocalView.current
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                CompositionLocalProvider(LocalEnglish provides isEnglish(model.state.language)) { CadenceFeedback(model.state.haptics) { if (model.state.onboarded) CadenceApp(model, homeRequest) else Onboarding(model) } }
            }
        }
    }
    override fun onResume() { super.onResume(); runCatching { ReminderScheduler.dismiss(this) } }
}
private fun Color.luminanceValue() = (red * 0.2126f + green * 0.7152f + blue * 0.0722f)

@Composable
private fun CadenceApp(model: PracticeViewModel, homeRequest: Int) {
    val state = model.state
    val zoned = Instant.ofEpochMilli(model.now).atZone(ZoneId.systemDefault())
    val minuteKey = model.now / 60_000
    val plan = remember(state, minuteKey) { Planner.plan(state, state.activeGoal, ZonedDateTime.now()) }
    val streak = remember(state.sessions, state.availability, zoned.toLocalDate()) { Planner.streak(state, zoned.toLocalDate()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(homeRequest) { if (homeRequest > 0) tab = 0 }
    val haze = remember { HazeState() }
    var goalEditor by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<PracticeGoal?>(null) }
    var focusPicker by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf(false) }
    var editingSession by remember { mutableStateOf<PracticeSession?>(null) }
    var entryDate by remember { mutableStateOf(zoned.toLocalDate()) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var discard by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { while (true) { model.tick(); delay(1000) } }
    LaunchedEffect(model.message) { model.message?.let { snackbar.showSnackbar(it); model.clearMessage() } }
    fun openEntry(session: PracticeSession? = null, date: LocalDate = LocalDate.now()) { editingSession = session; entryDate = session?.date() ?: date; editor = true }
    fun editGoal(goal: PracticeGoal?) { editingGoal = goal; goalEditor = true }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = 100.dp)) }, contentWindowInsets = WindowInsets.statusBars) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(tab, label = "Navigation", modifier = Modifier.widthIn(max = 640.dp).fillMaxSize().hazeSource(haze)) { page ->
                when (page) {
                    0 -> HomeScreen(state, plan, streak, model.now, onStart = { model.start() }, onPause = model::togglePause, onFinish = model::finish,
                        onFocus = { focusPicker = true }, onSelectGoal = model::selectGoal, onAddGoal = { editGoal(null) }, onEditGoal = { editGoal(it) },
                        onAdd = { openEntry() }, onDay = { selectedDay = it }, onHistory = { tab = 1 }, onEdit = { openEntry(it) }, onDiscard = { discard = true })
                    1 -> HistoryScreen(state, streak, onAdd = { openEntry() }, onEdit = { openEntry(it) }, onDay = { selectedDay = it })
                    2 -> PlanScreen(state, onGoal = { editGoal(it) }, onSelect = model::selectGoal, onAvailability = model::setAvailability)
                    3 -> SettingsScreen(state, model)
                }
            }
            Box(Modifier.align(Alignment.BottomCenter)) { CadenceTabBar(tab, haze) { tab = it } }
            model.celebration?.let { CelebrationOverlay(it) { model.clearCelebration() } }
        }
    }
    if (goalEditor) GoalEditorSheet(editingGoal, state, onDismiss = { goalEditor = false }, onSave = { model.saveGoal(it); goalEditor = false },
        onDelete = if (state.practiceGoals.size > 1) { g -> model.deleteGoal(g); goalEditor = false } else null)
    if (focusPicker) FocusSheet(state, onDismiss = { focusPicker = false }, onSave = { focus, minutes -> model.setFocus(focus, minutes); focusPicker = false })
    if (editor) SessionSheet(editingSession, entryDate, state, onDismiss = { editor = false }, onSave = { date, seconds, focus, note, goalId ->
        model.saveSession(editingSession, date, seconds, focus, note, goalId); editor = false
    }, onDelete = editingSession?.let { { model.deleteSession(it); editor = false } })
    selectedDay?.let { day -> DaySheet(day, state, onDismiss = { selectedDay = null }, onAdd = { selectedDay = null; openEntry(date = day) }, onEdit = { selectedDay = null; openEntry(it) }) }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, icon = { Icon(Icons.Rounded.RestartAlt, null) },
        title = { Text(words("Session verwerfen?", "Discard session?")) }, text = { Text(words("Die Zeit dieser Session wird nicht gespeichert.", "The time from this session won't be saved.")) },
        confirmButton = { TextButton(onClick = { model.discardTimer(); discard = false }) { Text(words("Verwerfen", "Discard")) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(words("Weiterüben", "Keep going")) } })
}

@Composable
fun PageHeader(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE5DAF5))) { Image(painterResource(R.drawable.ic_cadence), "Cadence", modifier = Modifier.fillMaxSize()) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
fun StreakChip(streak: Int) {
    if (streak <= 0) return
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.clearAndSetSemantics { contentDescription = "$streak" }) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("$streak", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun HomeScreen(state: CadenceState, plan: GoalPlan, streak: Int, now: Long, onStart: () -> Unit, onPause: () -> Unit, onFinish: () -> Unit, onFocus: () -> Unit,
    onSelectGoal: (String) -> Unit, onAddGoal: () -> Unit, onEditGoal: (PracticeGoal) -> Unit, onAdd: () -> Unit,
    onDay: (LocalDate) -> Unit, onHistory: () -> Unit, onEdit: (PracticeSession) -> Unit, onDiscard: () -> Unit) {
    val today = LocalDate.now()
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 124.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeader(words("Heute", "Today"), longDate(today)) { StreakChip(streak) } }
        item { GoalTabs(state, onSelectGoal, onAddGoal, onEditGoal) }
        item { TodayCard(state, plan, now, onStart, onPause, onFinish, onFocus, onDiscard) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onAdd) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(words("Ohne Timer geübt? Zeit nachtragen", "Practised without the timer? Log it")) }
            }
        }
        item { PeriodCard(state, plan, onDay) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(words("Zuletzt geübt", "Recent practice"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onHistory) { Text(words("Verlauf", "History")); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp)) }
            }
        }
        val recent = state.selectedSessions.sortedByDescending { it.startedAt }.take(3)
        if (recent.isEmpty()) item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.secondary); Spacer(Modifier.width(14.dp))
                    Text(words("Hier erscheinen deine Sessions, sobald du geübt hast.", "Your sessions show up here once you've practised."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else items(recent, key = { it.id }) { SessionRow(it, state, showGoal = false, onClick = { onEdit(it) }) }
    }
}

/** What today means for the selected goal, as a sentence. */
@Composable
fun todayLine(plan: GoalPlan): Pair<String, String> {
    val slots = plan.todaySlots.joinToString(", ") { it.label() }
    val next = plan.nextPracticeDay?.let { d -> dayName(d.dayOfWeek.value - 1) + (LocalDate.now().let { if (d == it.plusDays(1)) words(" (morgen)", " (tomorrow)") else "" }) }
    return when (plan.status) {
        DayStatus.OVER -> words("Termin vorbei", "Date has passed") to words("Bearbeite das Ziel, um einen neuen Termin festzulegen.", "Edit the goal to set a new date.")
        DayStatus.GOAL_REACHED -> (if (plan.goal.type == GoalType.WEEKLY) words("Wochenziel erreicht", "Weekly goal reached") else words("Ziel erreicht", "Goal reached")) to
            (if (plan.goal.type == GoalType.WEEKLY) words("${plan.done} von ${plan.target} Min. Alles, was jetzt noch dazukommt, ist ein Bonus.", "${plan.done} of ${plan.target} min. Anything extra is a bonus.")
            else words("${plan.done} von ${plan.target} Min. Viel Erfolg beim Termin!", "${plan.done} of ${plan.target} min. Good luck!"))
        DayStatus.REST -> if (plan.goal.createdDate.isAfter(LocalDate.now())) {
            val d = plan.goal.createdDate
            val whenText = if (d == LocalDate.now().plusDays(1)) words("morgen", "tomorrow") else dayName(d.dayOfWeek.value - 1)
            words("Startet $whenText", "Starts $whenText") to words("Ab dann plant Cadence jeden Tag für dich.", "From then on Cadence plans each day for you.")
        } else words("Heute kein Übetag", "No practice planned today") to (next?.let { words("Weiter geht's am $it.", "Next up: $it.") } ?: words("Du hast heute keine freie Zeit eingetragen.", "You have no free time today."))
        DayStatus.AHEAD -> words("Heute ist frei", "You've got today off") to
            (if (plan.yesterdayMinutes > 0) words("Du hast gestern ${plan.yesterdayMinutes} Min. geübt und bist vorne. Gut gemacht! Wenn du trotzdem übst, zählt es natürlich.", "You practised ${plan.yesterdayMinutes} min yesterday and you're ahead. Well done! Practice still counts if you feel like it.")
            else words("Du bist deinem Plan voraus. Wenn du trotzdem übst, zählt es natürlich.", "You're ahead of plan. Practice still counts if you feel like it."))
        DayStatus.DONE_TODAY -> words("Für heute geschafft", "Done for today") to (plan.days.firstOrNull { it.date > LocalDate.now() && it.planned > 0 }?.let { d ->
            words("Als Nächstes: ${dayName(d.date.dayOfWeek.value - 1)}, ${d.planned} Min.", "Next: ${dayName(d.date.dayOfWeek.value - 1)}, ${d.planned} min.") } ?: words("Damit bist du für diese Woche gut dabei.", "You're well on track."))
        DayStatus.TODO -> {
            val head = if (plan.doneToday > 0) words("Noch ${plan.todayRemaining} Min. für heute", "${plan.todayRemaining} min left today") else words("Heute ${plan.todayTarget} Min.", "${plan.todayTarget} min today")
            val nowMinute = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
            val slotsOver = plan.todaySlots.isNotEmpty() && plan.todaySlots.all { it.end <= nowMinute }
            val why = when {
                slotsOver && plan.practiceDaysLeft > 1 -> words("Deine freie Zeit heute ist vorbei. Was offen bleibt, verteilt Cadence auf die nächsten Tage.", "Your free time today is over. Whatever is left gets spread over the next days.")
                slotsOver -> if (plan.goal.type == GoalType.WEEKLY) {
                    val end = plan.period.end
                    val until = if (end.hour == 0 && end.minute == 0) words("bis ${dayName(end.minusDays(1).dayOfWeek.value - 1)}abend", "until ${dayName(end.minusDays(1).dayOfWeek.value - 1)} night")
                        else words("bis ${dayName(end.dayOfWeek.value - 1)}, ${clock(end.hour * 60 + end.minute)}", "until ${dayName(end.dayOfWeek.value - 1)} ${clock(end.hour * 60 + end.minute)}")
                    words("Deine freie Zeit heute ist vorbei, und diese Woche kommt keine mehr. Üben zählt trotzdem $until.", "Your free time today is over and none is left this week. Practice still counts $until.")
                }
                    else words("Deine freie Zeit vor dem Termin ist vorbei. Üben zählt trotzdem noch.", "Your free time before the date is over. Practice still counts.")
                plan.daysUntilDeadline == 0 -> words("Heute ist der Termin.", "Today's the day.")
                plan.daysUntilDeadline == 1 -> words("Morgen ist der Termin.", "It's tomorrow.")
                plan.shortfall > 0 -> words("Es wird knapp: es fehlen ${plan.shortfall} Min., die nicht mehr in deine freie Zeit passen.", "It's tight: ${plan.shortfall} min no longer fit into your free time.")
                plan.yesterdayMinutes > 0 && plan.todayTarget < plan.usualToday -> words("Weniger als sonst, weil du gestern ${plan.yesterdayMinutes} Min. geübt hast.", "Less than usual because you practised ${plan.yesterdayMinutes} min yesterday.")
                plan.todayTarget > plan.usualToday && plan.usualToday > 0 -> words("Etwas mehr als sonst, damit du dein Ziel noch schaffst.", "A bit more than usual so you still reach your goal.")
                plan.isLastPracticeDay && plan.goal.type == GoalType.WEEKLY -> words("Letzter Übetag dieser Woche.", "Last practice day this week.")
                else -> ""
            }
            head to listOf(if (slots.isNotEmpty()) words("Zeit: $slots.", "Time: $slots.") else "", why).filter { it.isNotEmpty() }.joinToString(" ")
        }
    }
}

@Composable
private fun TodayCard(state: CadenceState, plan: GoalPlan, now: Long, onStart: () -> Unit, onPause: () -> Unit, onFinish: () -> Unit, onFocus: () -> Unit, onDiscard: () -> Unit) {
    val timer = state.timer
    val ownTimer = timer?.goalId == plan.goal.id
    val elapsed = timer?.elapsedMillis(now)?.div(1000) ?: 0L
    val liveDone = plan.doneToday * 60L + if (ownTimer) elapsed else 0
    val target = plan.todayTarget
    val ringValue = when {
        target > 0 -> liveDone / (target * 60f)
        plan.status == DayStatus.GOAL_REACHED || plan.status == DayStatus.DONE_TODAY || plan.status == DayStatus.AHEAD -> 1f
        ownTimer -> elapsed / (timer!!.targetMinutes * 60f)
        else -> 0f
    }.coerceIn(0f, 1f)
    val progress by animateFloatAsState(ringValue, label = "Tagesfortschritt")
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    // Reaching today's share while the timer runs gets its own little moment.
    var crossed by remember(plan.goal.id, target) { mutableStateOf(target > 0 && plan.doneToday >= target) }
    LaunchedEffect(ringValue >= 1f && ownTimer && target > 0) {
        if (ringValue >= 1f && ownTimer && target > 0 && !crossed) { crossed = true; haptic.performHapticFeedback(HapticFeedbackType.Confirm); Celebrate.vibrate(context, state.haptics, big = false) }
    }
    val running = timer?.isRunning == true
    val colors = MaterialTheme.colorScheme
    val cardColor by animateColorAsState(if (running && ownTimer) colors.tertiaryContainer else colors.secondaryContainer, label = "Kartenfarbe")
    val ink = if (running && ownTimer) colors.onTertiaryContainer else colors.onSecondaryContainer
    val (headline, detail) = todayLine(plan)
    Surface(color = cardColor, contentColor = ink, shape = RoundedCornerShape(36.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val todo = plan.status == DayStatus.TODO || ownTimer
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(words("Dein Ziel", "Your goal"), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.7f))
                    Text(goalSummary(plan.goal, withReset = false), style = MaterialTheme.typography.titleMedium)
                    if (todo && detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
                }
                if (ownTimer) IconButton(onClick = onDiscard) { Icon(Icons.Rounded.Close, words("Session verwerfen", "Discard session"), Modifier.size(20.dp)) }
            }
            Box(Modifier.size(224.dp), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(),
                    color = ink.copy(alpha = 0.85f), trackColor = ink.copy(alpha = 0.12f), amplitude = { if (running && ownTimer) 1f else 0.5f }, wavelength = 28.dp, waveSpeed = if (running && ownTimer) 16.dp else 0.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        ownTimer -> {
                            Text(formatDuration(elapsed), style = if (elapsed < 3600) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium, color = ink, maxLines = 1)
                            val left = target * 60L - liveDone
                            Text(when { !running -> words("Pausiert", "Paused"); target > 0 && left > 0 -> words("noch ${(left + 59) / 60} Min. für heute", "${(left + 59) / 60} min left today"); target > 0 -> words("Heute geschafft ✓", "Done for today ✓"); else -> translate(timer!!.focus, LocalEnglish.current) },
                                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                        }
                        target > 0 && plan.status == DayStatus.TODO -> {
                            Text("${(target - plan.doneToday).coerceAtLeast(0)}", style = MaterialTheme.typography.displayLarge, color = ink)
                            Text(if (plan.doneToday > 0) words("Minuten übrig", "minutes left") else words("Minuten heute", "minutes today"), style = MaterialTheme.typography.bodyMedium)
                        }
                        else -> {
                            Icon(when (plan.status) { DayStatus.REST -> Icons.Rounded.Bedtime; DayStatus.OVER -> Icons.Rounded.EventBusy; DayStatus.AHEAD -> Icons.Rounded.WbSunny; else -> Icons.Rounded.EmojiEvents }, null, Modifier.size(48.dp))
                            Text(headline, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 170.dp))
                            if (plan.doneToday > 0) Text(words("${plan.doneToday} Min. heute", "${plan.doneToday} min today"), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (!todo && detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = ink.copy(alpha = 0.85f))
            if (timer == null) {
                Surface(onClick = tap(onFocus), color = ink.copy(alpha = 0.08f), contentColor = ink, shape = CircleShape) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(words("Fokus: ", "Focus: ") + translate(state.focus, LocalEnglish.current), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
                        Icon(Icons.Rounded.ExpandMore, words("Fokus ändern", "Change focus"), Modifier.size(18.dp))
                    }
                }
                Button(onClick = onStart, feedback = HapticFeedbackType.Confirm, modifier = Modifier.fillMaxWidth().height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = cardColor)) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(26.dp)); Spacer(Modifier.width(10.dp))
                    Text(if (target > 0 && plan.doneToday < target) words("Üben starten", "Start practising") else words("Trotzdem üben", "Practise anyway"), style = MaterialTheme.typography.titleMedium)
                }
            } else if (!ownTimer) {
                val other = state.goal(timer.goalId)
                Text(words("Gerade läuft eine Session für ${other?.title ?: "ein anderes Ziel"}.", "A session for ${other?.title ?: "another goal"} is running."), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onPause, modifier = Modifier.weight(1f).height(60.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = ink.copy(alpha = 0.1f), contentColor = ink)) {
                    Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(22.dp)); Spacer(Modifier.width(6.dp)); Text(if (running) words("Pause", "Pause") else words("Weiter", "Resume"))
                }
                Button(onClick = onFinish, feedback = HapticFeedbackType.Confirm, modifier = Modifier.weight(1f).height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = cardColor)) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(words("Fertig", "Done"))
                }
            }
        }
    }
}

@Composable
private fun PeriodCard(state: CadenceState, plan: GoalPlan, onDay: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val today = LocalDate.now()
    val maxMinutes = max(30, plan.days.maxOf { max(it.done, it.planned) }).toFloat()
    Surface(color = colors.surfaceContainerLow, shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(periodTitle(plan), style = MaterialTheme.typography.titleLarge)
                    Text(when {
                        plan.goal.type == GoalType.DEADLINE && plan.daysUntilDeadline != null && plan.daysUntilDeadline >= 0 ->
                            if (plan.daysUntilDeadline == 0) words("Heute ist der Termin", "Today's the day") else words("Noch ${plan.daysUntilDeadline} Tage bis ${shortDate(plan.goal.dueDate!!)}", "${plan.daysUntilDeadline} days until ${shortDate(plan.goal.dueDate!!)}")
                        plan.remaining == 0 -> words("Ziel erreicht", "Goal reached")
                        else -> words("Noch ${plan.remaining} Min.", "${plan.remaining} min to go")
                    }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${plan.done}", style = MaterialTheme.typography.headlineMedium, color = colors.primary)
                    Text(words("von ${plan.target} Min.", "of ${plan.target} min"), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                plan.days.forEach { day ->
                    val isToday = day.date == today
                    val label = dayShortPlain(day.date.dayOfWeek.value - 1, LocalEnglish.current).take(2)
                    val spoken = words("$label: ${day.done} von ${day.planned} Minuten", "$label: ${day.done} of ${day.planned} minutes")
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = tap({ onDay(day.date) })).padding(vertical = 4.dp).clearAndSetSemantics { contentDescription = spoken },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.height(70.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            if (day.planned > 0) Box(Modifier.width(22.dp).height(max(6f, day.planned / maxMinutes * 70).dp).clip(CircleShape).border(1.5.dp, colors.outline.copy(alpha = 0.6f), CircleShape))
                            else if (day.capacity == 0 || !day.inPeriod) Box(Modifier.width(22.dp).height(6.dp).clip(CircleShape).background(colors.outlineVariant.copy(alpha = 0.4f)))
                            if (day.done > 0) Box(Modifier.width(22.dp).height(max(6f, day.done / maxMinutes * 70).dp).clip(CircleShape).background(if (day.planned in 1..day.done) colors.tertiary else if (isToday) colors.primary else colors.secondary))
                        }
                        Surface(color = if (isToday) colors.primary else Color.Transparent, contentColor = if (isToday) colors.onPrimary else if (day.inPeriod) colors.onSurfaceVariant else colors.outline, shape = CircleShape) {
                            Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp), maxLines = 1)
                        }
                    }
                }
            }
            LinearWavyProgressIndicator(progress = { if (plan.target > 0) (plan.done.toFloat() / plan.target).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(10.dp), color = colors.primary, trackColor = colors.outlineVariant, waveSpeed = 0.dp)
        }
    }
}

@Composable
fun SessionRow(session: PracticeSession, state: CadenceState, showGoal: Boolean, showDate: Boolean = true, onClick: () -> Unit) {
    val goal = state.goal(session.goalId)
    val time = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault())
    Surface(onClick = tap(onClick), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(goalIcon(goal?.kind ?: GoalKind.OTHER), null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(translate(session.focus, LocalEnglish.current), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val clockText = if (session.recordedWithTimer) clock(time.hour * 60 + time.minute) else words("nachgetragen", "logged")
                val meta = listOfNotNull(if (showGoal) goal?.title else null, if (showDate) shortDate(time.toLocalDate()) else null, clockText).joinToString(", ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (session.note.isNotEmpty()) Text(session.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(if (session.durationSeconds < 60) words("${session.durationSeconds} Sek.", "${session.durationSeconds} sec") else minutesText((session.durationSeconds / 60).toInt()), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun HistoryScreen(state: CadenceState, streak: Int, onAdd: () -> Unit, onEdit: (PracticeSession) -> Unit, onDay: (LocalDate) -> Unit) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    val colors = MaterialTheme.colorScheme
    val today = LocalDate.now()
    val sessions = state.sessions.filter { filter == null || it.goalId == filter }
    val byDate = sessions.groupBy { it.date() }.mapValues { (it.value.sumOf { s -> s.durationSeconds } / 60).toInt() }
    val weekStart = today.with(java.time.DayOfWeek.MONDAY)
    val weekMinutes = byDate.filterKeys { it >= weekStart && it <= today }.values.sum()
    val monthMinutes = byDate.filterKeys { YearMonth.from(it) == YearMonth.now() }.values.sum()
    val total = (sessions.sumOf { it.durationSeconds } / 60).toInt()
    val monthSessions = sessions.filter { YearMonth.from(it.date()) == month }.sortedByDescending { it.startedAt }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 124.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader(words("Verlauf", "History")) { IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, words("Zeit nachtragen", "Log time")) } } }
        if (state.practiceGoals.size > 1) item {
            val options = listOf<PracticeGoal?>(null) + state.practiceGoals
            if (options.size <= 3) PillGrid(options, { it?.id == filter }, columns = options.size, onPick = { filter = it?.id }) { g ->
                Text(g?.title ?: words("Alle", "All"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else Row(Modifier.fillMaxWidth().horizontalScrollCompat(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(words("Alle Ziele", "All goals")) })
                state.practiceGoals.forEach { g -> FilterChip(selected = filter == g.id, onClick = { filter = g.id }, label = { Text(g.title, maxLines = 1) }, leadingIcon = { Icon(goalIcon(g.kind), null, Modifier.size(16.dp)) }) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(words("Diese Woche", "This week"), minutesText(weekMinutes), Modifier.weight(1f), colors.primaryContainer, colors.onPrimaryContainer)
                StatTile(words("Dieser Monat", "This month"), minutesText(monthMinutes), Modifier.weight(1f), colors.secondaryContainer, colors.onSecondaryContainer)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(words("Serie", "Streak"), if (streak == 1) words("1 Tag", "1 day") else words("$streak Tage", "$streak days"), Modifier.weight(1f), colors.tertiaryContainer, colors.onTertiaryContainer, Icons.Rounded.LocalFireDepartment)
                StatTile(words("Insgesamt", "All time"), minutesText(total), Modifier.weight(1f), colors.surfaceContainerHigh, colors.onSurface)
            }
        }
        item { WeeksChart(byDate, today) }
        item { MonthCalendar(month, byDate, today, onPrev = { month = month.minusMonths(1) }, onNext = { if (month < YearMonth.now()) month = month.plusMonths(1) }, onDay = onDay) }
        if (monthSessions.isEmpty()) item {
            Text(words("In diesem Monat gibt es noch keine Sessions.", "No sessions this month yet."), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        }
        monthSessions.groupBy { it.date() }.forEach { (date, list) ->
            item(key = "date-$date") {
                Row(Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (date == today) words("Heute", "Today") else longDate(date), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(minutesText((list.sumOf { it.durationSeconds } / 60).toInt()), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            items(list, key = { it.id }) { s -> SessionRow(s, state, showGoal = filter == null && state.practiceGoals.size > 1, showDate = false) { onEdit(s) } }
        }
    }
}

@Composable private fun Modifier.horizontalScrollCompat() = this.then(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()))

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, container: Color, content: Color, icon: ImageVector? = null) {
    Surface(modifier, color = container, contentColor = content, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { if (icon != null) { Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }; Text(label, style = MaterialTheme.typography.labelMedium) }
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        }
    }
}

/** Minutes per week for the last eight weeks. */
@Composable
private fun WeeksChart(byDate: Map<LocalDate, Int>, today: LocalDate) {
    val colors = MaterialTheme.colorScheme
    val start = today.with(java.time.DayOfWeek.MONDAY)
    val weeks = (7 downTo 0).map { i -> val ws = start.minusWeeks(i.toLong()); ws to byDate.filterKeys { it >= ws && it < ws.plusDays(7) }.values.sum() }
    val maxValue = max(30, weeks.maxOf { it.second }).toFloat()
    val average = weeks.dropLast(1).map { it.second }.average().toInt()
    Surface(color = colors.surfaceContainerLow, shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(words("Die letzten 8 Wochen", "Last 8 weeks"), style = MaterialTheme.typography.titleLarge)
            Text(words("Im Schnitt ${minutesText(average)} pro Woche", "On average ${minutesText(average)} per week"), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                weeks.forEachIndexed { i, (ws, minutes) ->
                    Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = "$minutes" }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (minutes > 0) Text("$minutes", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
                        Box(Modifier.fillMaxWidth().height(max(4f, minutes / maxValue * 80).dp).clip(RoundedCornerShape(8.dp)).background(if (i == weeks.lastIndex) colors.primary else colors.secondary.copy(alpha = 0.7f)))
                        Text(if (i % 2 == 1) ws.format(DateTimeFormatter.ofPattern(if (LocalEnglish.current) "d/M" else "d.M.", appLocale())) else " ", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCalendar(month: YearMonth, byDate: Map<LocalDate, Int>, today: LocalDate, onPrev: () -> Unit, onNext: () -> Unit, onDay: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val cells = offset + month.lengthOfMonth()
    val maxValue = max(20, (byDate.filterKeys { YearMonth.from(it) == month }.values.maxOrNull() ?: 0)).toFloat()
    Surface(color = colors.surfaceContainerLow, shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, words("Vorheriger Monat", "Previous month")) }
                Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", appLocale())), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = onNext, enabled = month < YearMonth.now()) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, words("Nächster Monat", "Next month")) }
            }
            Row(Modifier.fillMaxWidth()) { (0..6).forEach { Text(dayShortPlain(it, LocalEnglish.current).take(2), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center) } }
            (0 until (cells + 6) / 7).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0..6).forEach { col ->
                        val index = row * 7 + col - offset
                        if (index < 0 || index >= month.lengthOfMonth()) Spacer(Modifier.weight(1f).aspectRatio(1f))
                        else {
                            val date = month.atDay(index + 1)
                            val minutes = byDate[date] ?: 0
                            val intensity = if (minutes == 0) 0f else (0.25f + 0.75f * (minutes / maxValue).coerceAtMost(1f))
                            Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (minutes > 0) colors.primary.copy(alpha = intensity) else colors.surfaceContainerHigh.copy(alpha = if (date > today) 0.4f else 1f))
                                .then(if (date == today) Modifier.border(2.dp, colors.primary, RoundedCornerShape(10.dp)) else Modifier)
                                .clickable(enabled = date <= today, onClick = tap({ onDay(date) }))
                                .clearAndSetSemantics { contentDescription = "$date: $minutes" }, contentAlignment = Alignment.Center) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = if (intensity > 0.6f) colors.onPrimary else colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
