@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.professorengineergit.cadence

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import java.time.ZonedDateTime

private const val STEPS = 5

@Composable
fun Onboarding(model: PracticeViewModel) {
    val state = model.state
    val context = LocalContext.current
    val replay = state.sessions.isNotEmpty() || state.practiceGoals.size > 1
    var step by rememberSaveable { mutableIntStateOf(if (replay) 1 else 0) }
    var goal by remember { mutableStateOf(state.activeGoal.let { if (!replay && it.id == "primary" && state.sessions.isEmpty()) it.copy(title = "") else it }) }
    var availability by remember { mutableStateOf(state.availability) }
    var reminders by rememberSaveable { mutableStateOf(true) }
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }
    fun finish(allowed: Boolean) = model.finishOnboarding(goal.copy(title = goal.title.trim()), availability, reminders && allowed)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { finish(it) }
    BackHandler(enabled = step > 0) { step-- }
    val canContinue = when (step) {
        1 -> goal.title.isNotBlank()
        2 -> goal.type == GoalType.WEEKLY || (goal.dueDate != null && !goal.dueDate!!.isBefore(java.time.LocalDate.now()))
        3 -> availability.any { it.isNotEmpty() }
        else -> true
    }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Cadence", style = MaterialTheme.typography.headlineSmall)
                SegmentedChoice(listOf("DE", "EN"), if (LocalEnglish.current) 1 else 0, Modifier.width(150.dp)) { model.setLanguage(if (it == 0) AppLanguage.DE else AppLanguage.EN) }
            }
            LinearWavyProgressIndicator(progress = { (step + 1) / STEPS.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            AnimatedContent(step, modifier = Modifier.weight(1f), label = "Einrichtung", transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
            }) { page ->
                Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (page) {
                        0 -> Welcome()
                        1 -> {
                            Heading(words("Was möchtest du üben?", "What do you want to practise?"), words("Gib deinem Ziel einen Namen. Weitere Ziele kannst du später jederzeit hinzufügen.", "Give your goal a name. You can add more goals any time."))
                            OutlinedTextField(goal.title, { goal = goal.copy(title = it.take(40)) }, label = { Text(words("Name", "Name")) },
                                placeholder = { Text(words("z. B. Klavier, Referat Bio, Mathe", "e.g. Piano, Biology talk, Maths")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences, imeAction = androidx.compose.ui.text.input.ImeAction.Done))
                            KindChips(goal.kind) { kind ->
                                val type = if (kind == GoalKind.SPEECH || kind == GoalKind.STUDY) GoalType.DEADLINE else GoalType.WEEKLY
                                goal = if (type != goal.type) goal.copy(kind = kind, type = type, minutes = if (type == GoalType.WEEKLY) 100 else 120, perDay = false,
                                    deadline = goal.deadline ?: java.time.LocalDate.now().plusDays(7).toString()) else goal.copy(kind = kind)
                            }
                        }
                        2 -> {
                            Heading(words("Was ist dein Ziel?", "What's your goal?"), words("Regelmäßig oder bis zu einem bestimmten Tag. Cadence rechnet daraus aus, wie viel du an jedem Tag üben solltest.", "Regularly, or by a certain day. Cadence works out how much to practise each day."))
                            GoalTargetEditor(goal) { goal = it }
                        }
                        3 -> {
                            Heading(words("Wann hast du Zeit?", "When do you have time?"), words("Trag ein, wann du realistisch üben kannst. Nur dann erinnert dich Cadence, und nur diese Zeit wird verplant. Du musst nicht die ganze Zeit üben.",
                                "Add the times you can realistically practise. Cadence only reminds you then and only plans within this time. You don't have to use all of it."))
                            AvailabilityPresets { availability = it }
                            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { WeekAvailabilityEditor(availability, { availability = it }) }
                            }
                            Text(availabilitySummary(availability), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> {
                            val preview = state.copy(practiceGoals = listOf(goal.copy(title = goal.title.ifBlank { "…" })), availability = availability, sessions = state.sessionsFor(goal.id))
                            val plan = Planner.plan(preview, preview.practiceGoals.first(), ZonedDateTime.now())
                            Heading(words("So sieht dein Plan aus", "Here's your plan"), planIntro(plan))
                            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { WeekPlanList(plan, preview.availability); PlanHint(plan) }
                            }
                            Text(words("Übst du an einem Tag mehr, wird es an den nächsten Tagen weniger. Verpasst du einen Tag, verteilt Cadence den Rest neu.",
                                "Practise more one day and the next days get lighter. Miss a day and Cadence spreads the rest again."), style = MaterialTheme.typography.bodyMedium)
                            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.NotificationsActive, null); Spacer(Modifier.width(12.dp))
                                        Text(words("Erinnerungen", "Reminders"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        val toggle = tap({ reminders = !reminders }); Switch(reminders, { toggle() })
                                    }
                                    Text(words("Zu Beginn deiner freien Zeit, wenn heute noch etwas ansteht. Höchstens zweimal am Tag, nie wenn du schon fertig bist.",
                                        "When your free time starts and there's still something to do today. At most twice a day, never once you're done."), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0 && !(replay && step == 1)) OutlinedButton(onClick = { step-- }, modifier = Modifier.height(56.dp)) { Text(words("Zurück", "Back")) }
                Button(onClick = {
                    if (step < STEPS - 1) step++
                    else if (reminders && Build.VERSION.SDK_INT >= 33 && !ReminderScheduler.allowed(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else finish(ReminderScheduler.allowed(context))
                }, enabled = canContinue, modifier = Modifier.weight(1f).height(56.dp), feedback = if (step == STEPS - 1) androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm else androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick) {
                    Text(when (step) { 0 -> words("Los geht's", "Let's go"); STEPS - 1 -> words("Fertig", "Done"); else -> words("Weiter", "Next") })
                }
            }
        }
    }
}

@Composable
private fun planIntro(plan: GoalPlan): String = when (plan.goal.type) {
    GoalType.WEEKLY -> words("${plan.goal.title}, ${goalSummary(plan.goal)}. So verteilt sich diese Woche:", "${plan.goal.title}, ${goalSummary(plan.goal)}. This is how this week looks:")
    GoalType.DEADLINE -> words("${plan.goal.title}, ${goalSummary(plan.goal)}".trimEnd('.') + ". So sehen die nächsten Tage aus:", "${plan.goal.title}, ${goalSummary(plan.goal)}. These are the next days:")
}

@Composable
private fun Heading(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineLarge)
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Welcome() {
    Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(androidx.compose.ui.graphics.Color(0xFFE5DAF5))) { Image(painterResource(R.drawable.ic_cadence), "Cadence", Modifier.fillMaxSize()) }
    }
    Text(words("Regelmäßig üben, ohne ständig daran denken zu müssen.", "Practise regularly without having to keep it in your head."), style = MaterialTheme.typography.headlineMedium)
    IntroFeature(Icons.Rounded.Flag, words("Du legst ein Ziel fest", "You set a goal"), words("Zum Beispiel 100 Minuten Klavier pro Woche, oder 2 Stunden bis zum Referat am Donnerstag.", "For example 100 minutes of piano a week, or 2 hours before Thursday's talk."))
    IntroFeature(Icons.Rounded.Schedule, words("Und sagst, wann du Zeit hast", "And say when you have time"), words("Cadence verteilt das Ziel auf diese Zeiten und erinnert dich genau dann.", "Cadence spreads the goal over those times and reminds you right then."))
    IntroFeature(Icons.Rounded.AutoGraph, words("Der Plan passt sich an", "The plan adapts"), words("Gestern mehr geübt? Dann ist heute weniger dran, vielleicht sogar frei.", "Practised more yesterday? Then today is lighter, maybe even free."))
}

@Composable private fun IntroFeature(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 3.dp).size(24.dp), tint = MaterialTheme.colorScheme.secondary)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
