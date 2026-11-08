@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.professorengineergit.cadence

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZonedDateTime

private const val STEPS = 5

/**
 * Setup in five short steps, one question each: what, how much, when, then the plan.
 * Several goals can be added before finishing, because most people won't add them later.
 */
@Composable
fun Onboarding(model: PracticeViewModel) {
    val state = model.state
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val replay = state.sessions.isNotEmpty() || state.practiceGoals.size > 1
    var step by rememberSaveable { mutableIntStateOf(if (replay) 1 else 0) }
    var done by remember { mutableStateOf(listOf<PracticeGoal>()) }
    var goal by remember { mutableStateOf(state.activeGoal.let { if (!replay && it.id == "primary" && state.sessions.isEmpty()) it.copy(title = "") else it }) }
    var availability by remember { mutableStateOf(state.availability) }
    var reminders by rememberSaveable { mutableStateOf(true) }
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }
    // Setup stays upright: tilting the phone moves the objects, not the layout.
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val previous = activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity?.requestedOrientation = previous }
    }
    val allGoals = done + goal
    // New goals (and every goal on a first run) start with the next free time that is still ahead.
    fun started(list: List<PracticeGoal>) = list.map { g -> if (!replay || state.practiceGoals.none { it.id == g.id }) g.copy(createdOn = Planner.freshStart(availability).toString()) else g }
    fun finish(allowed: Boolean) = model.finishOnboarding(started(allGoals).map { it.copy(title = it.title.trim()) }, availability, reminders && allowed)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { finish(it) }
    fun go(to: Int) { focus.clearFocus(); haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); step = to }

    val canContinue = when (step) {
        1 -> goal.title.isNotBlank()
        2 -> goal.type == GoalType.WEEKLY || (goal.dueDate != null && !goal.dueDate!!.isBefore(LocalDate.now()))
        3 -> availability.any { it.isNotEmpty() }
        else -> true
    }
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val keyboard = WindowInsets.isImeVisible
    // With the keyboard open, back only closes the keyboard.
    BackHandler(enabled = step > (if (replay) 1 else 0) && !keyboard) { go(step - 1) }
    val sceneHeight by animateDpAsState(if (keyboard) 0.dp else if (step == 0) 270.dp else 150.dp, spring(dampingRatio = 0.9f, stiffness = 120f), label = "scene")
    val haze = remember { dev.chrisbanes.haze.HazeState() }
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(colors.background)) { CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
        val ceiling = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
        IntroScene(step, fill = step / (STEPS - 1f), modifier = Modifier.fillMaxSize().hazeSource(haze), ceiling = ceiling)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (step == 0) "Cadence" else words("Schritt $step von ${STEPS - 1}", "Step $step of ${STEPS - 1}"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                SegmentedChoice(listOf("DE", "EN"), if (LocalEnglish.current) 1 else 0, Modifier.width(132.dp)) { model.setLanguage(if (it == 0) AppLanguage.DE else AppLanguage.EN) }
            }
            Spacer(Modifier.height(sceneHeight))
            // Frosted glass sheet: the scene stays visible behind it, softly blurred.
            Column(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .hazeEffect(haze, dev.chrisbanes.haze.HazeStyle(backgroundColor = colors.surface, tints = listOf(dev.chrisbanes.haze.HazeTint(colors.surface.copy(alpha = 0.55f))), blurRadius = 30.dp, noiseFactor = 0f))
                .background(colors.surfaceContainerHigh.copy(alpha = 0.18f)).navigationBarsPadding().imePadding().padding(top = 8.dp)) {
                Box(Modifier.padding(top = 4.dp, bottom = 10.dp).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(colors.outlineVariant).align(Alignment.CenterHorizontally))
            AnimatedContent(step, modifier = Modifier.weight(1f), label = "Einrichtung", transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 380f)) { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally(spring(dampingRatio = 0.85f, stiffness = 380f)) { if (forward) -it / 3 else it / 3 } + fadeOut())
            }) { page ->
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (page) {
                        0 -> Welcome()
                        1 -> {
                            Heading(if (done.isEmpty()) words("Was möchtest du üben?", "What do you want to practise?") else words("Und was noch?", "What else?"),
                                if (done.isEmpty()) words("Ein Name reicht, zum Beispiel „Klavier“ oder „Referat Bio“.", "A name is enough, like “Piano” or “Biology talk”.")
                                else words("Zum Beispiel ein zweites Instrument, ein Referat oder eine Prüfung.", "For example a second instrument, a talk or an exam."))
                            OutlinedTextField(goal.title, { goal = goal.copy(title = it.take(40)) }, label = { Text(words("Name", "Name")) },
                                placeholder = { Text(words("z. B. Klavier", "e.g. Piano")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
                            Text(words("Worum geht es?", "What kind of thing is it?"), style = MaterialTheme.typography.titleMedium)
                            KindGrid(goal.kind) { kind ->
                                val type = if (kind == GoalKind.SPEECH || kind == GoalKind.STUDY) GoalType.DEADLINE else GoalType.WEEKLY
                                goal = if (type != goal.type) goal.copy(kind = kind, type = type, minutes = if (type == GoalType.WEEKLY) 100 else 120, perDay = false,
                                    deadline = goal.deadline ?: LocalDate.now().plusDays(7).toString()) else goal.copy(kind = kind)
                            }
                        }
                        2 -> {
                            Heading(words("Was ist dein Ziel?", "What's your goal?"), words("Regelmäßig oder bis zu einem Tag. Cadence rechnet daraus aus, wie viel jeden Tag dran ist.", "Regularly, or by a certain day. Cadence works out how much is due each day."))
                            GoalTargetEditor(goal) { goal = it }
                        }
                        3 -> {
                            Heading(words("Wann hast du Zeit?", "When do you have time?"), words("Nur in diesen Zeiten plant und erinnert Cadence. Du musst sie nicht ganz ausnutzen.", "Cadence only plans and reminds you within these times. You don't have to use all of it."))
                            AvailabilityPresets { availability = it }
                            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) { WeekAvailabilityEditor(availability, { availability = it }) }
                            }
                            Text(availabilitySummary(availability), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> {
                            val previewGoals = started(allGoals).map { it.copy(title = it.title.ifBlank { "…" }) }
                            val preview = state.copy(practiceGoals = previewGoals, availability = availability)
                            val now = ZonedDateTime.now()
                            val plan = Planner.plan(preview, previewGoals.last(), now)
                            Heading(words("So sieht dein Plan aus", "Here's your plan"), words("Übst du mehr, wird es an den nächsten Tagen weniger. Fällt ein Tag aus, wird der Rest neu verteilt.",
                                "Practise more and the next days get lighter. Miss a day and the rest is spread out again."))
                            if (done.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                allGoals.forEach { g -> AssistChip(onClick = {}, label = { Text(g.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = { Icon(goalIcon(g.kind), null, Modifier.size(18.dp)) }) }
                            }
                            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(goalIcon(plan.goal.kind), null, tint = MaterialTheme.colorScheme.secondary)
                                        Column(Modifier.weight(1f)) {
                                            Text(plan.goal.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(goalSummary(plan.goal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    val start = plan.goal.createdDate
                                    val tomorrow = start == LocalDate.now().plusDays(1)
                                    if (start.isAfter(LocalDate.now())) Text(if (tomorrow) words("Los geht's morgen (${dayName(start.dayOfWeek.value - 1)}).", "It starts tomorrow (${dayName(start.dayOfWeek.value - 1)}).")
                                        else words("Los geht's am ${dayName(start.dayOfWeek.value - 1)}.", "It starts on ${dayName(start.dayOfWeek.value - 1)}."),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                    WeekPlanList(plan, availability)
                                    PlanHint(plan)
                                }
                            }
                            val overbooked = overbookedDays(preview, Planner.plans(preview, now))
                            if (overbooked.isNotEmpty()) Text(words("Zusammen brauchen deine Ziele an ${overbooked.joinToString { dayShortPlain(it.dayOfWeek.value - 1, false) }} mehr Zeit, als frei ist.",
                                "Together your goals need more time than you have on ${overbooked.joinToString { dayShortPlain(it.dayOfWeek.value - 1, true) }}."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = {
                                done = allGoals
                                goal = PracticeGoal(title = "", kind = GoalKind.MUSIC)
                                go(1)
                            }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text(words("Noch ein Ziel hinzufügen", "Add another goal"))
                            }
                            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.NotificationsActive, null); Spacer(Modifier.width(12.dp))
                                        Text(words("Erinnerungen", "Reminders"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        val toggle = tap({ reminders = !reminders }, if (reminders) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn); Switch(reminders, { toggle() })
                                    }
                                    Text(words("Wenn deine freie Zeit beginnt und heute noch etwas ansteht. Höchstens zweimal am Tag, nie wenn du schon fertig bist.",
                                        "When your free time starts and something is still due today. At most twice a day, never once you're done."), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > (if (replay) 1 else 0)) OutlinedButton(onClick = { go(step - 1) }, modifier = Modifier.height(56.dp)) { Text(words("Zurück", "Back")) }
                Button(onClick = {
                    if (step < STEPS - 1) go(step + 1)
                    else if (reminders && Build.VERSION.SDK_INT >= 33 && !ReminderScheduler.allowed(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else finish(ReminderScheduler.allowed(context))
                }, enabled = canContinue, modifier = Modifier.weight(1f).height(56.dp), feedback = if (step == STEPS - 1) HapticFeedbackType.Confirm else HapticFeedbackType.SegmentTick) {
                    Text(when (step) { 0 -> words("Los geht's", "Let's go"); STEPS - 1 -> words("Fertig", "Done"); else -> words("Weiter", "Next") })
                }
            }
            }
        }
    } }
}

@Composable
private fun KindGrid(selected: GoalKind, onSelect: (GoalKind) -> Unit) {
    val items = listOf(
        Triple(GoalKind.MUSIC, words("Instrument", "Instrument"), words("Klavier, Gitarre …", "Piano, guitar …")),
        Triple(GoalKind.SPEECH, words("Vortrag", "Talk"), words("Referat, Rede …", "Presentation, speech …")),
        Triple(GoalKind.STUDY, words("Lernen", "Studying"), words("Prüfung, Vokabeln …", "Exam, vocabulary …")),
        Triple(GoalKind.OTHER, words("Anderes", "Other"), words("Sport, Zeichnen …", "Sport, drawing …")),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (kind, title, hint) -> KindTile(kind == selected, goalIcon(kind), title, hint, Modifier.weight(1f)) { onSelect(kind) } }
            }
        }
    }
}

@Composable
private fun KindTile(selected: Boolean, icon: ImageVector, title: String, hint: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val radius by animateDpAsState(if (selected) 28.dp else 16.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "tile")
    Surface(onClick = tap(onClick, HapticFeedbackType.SegmentFrequentTick), modifier = modifier.heightIn(min = 84.dp), shape = RoundedCornerShape(radius),
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLow, contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(shape = CircleShape, color = if (selected) colors.primary else colors.surfaceContainerHighest, contentColor = if (selected) colors.onPrimary else colors.secondary) {
                Icon(icon, null, Modifier.padding(8.dp).size(20.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = if (selected) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Heading(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Welcome() {
    Text(words("Üben, ohne ständig daran denken zu müssen.", "Practise without having to keep it in your head."), style = MaterialTheme.typography.headlineMedium)
    IntroFeature(Icons.Rounded.Flag, words("Du legst ein Ziel fest", "You set a goal"), words("Etwa 100 Min. Klavier pro Woche oder 2 Std. bis zum Referat.", "Say 100 min of piano a week, or 2 h before your talk."))
    IntroFeature(Icons.Rounded.Schedule, words("Du sagst, wann du Zeit hast", "You say when you have time"), words("Genau dann erinnert dich Cadence.", "That's exactly when Cadence reminds you."))
    IntroFeature(Icons.Rounded.AutoGraph, words("Der Plan passt sich an", "The plan adapts"), words("Gestern mehr geübt? Dann ist heute weniger dran.", "Practised more yesterday? Then today is lighter."))
}

@Composable private fun IntroFeature(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) { Icon(icon, null, Modifier.padding(8.dp).size(20.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
