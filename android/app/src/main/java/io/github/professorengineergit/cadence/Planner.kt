@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.professorengineergit.cadence

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZonedDateTime

/** Goals as tabs; the last chip adds a new one. */
@Composable
fun GoalTabs(state: CadenceState, onSelect: (String) -> Unit, onAdd: () -> Unit, onEdit: (PracticeGoal) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        state.practiceGoals.forEach { goal ->
            val selected = goal.id == state.activeGoal.id
            Surface(onClick = tap({ if (selected) onEdit(goal) else onSelect(goal.id) }), shape = CircleShape,
                color = if (selected) colors.secondaryContainer else colors.surfaceContainerLow, contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(goalIcon(goal.kind), null, Modifier.size(18.dp))
                    Text(goal.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                    if (selected) Icon(Icons.Rounded.Edit, words("Ziel bearbeiten", "Edit goal"), Modifier.size(15.dp))
                }
            }
        }
        Surface(onClick = tap(onAdd), shape = CircleShape, color = colors.surfaceContainerLow) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                if (state.practiceGoals.size == 1) Text(words("Ziel", "Goal"), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** One row per day: free time, plan and what you did. */
@Composable
fun WeekPlanList(plan: GoalPlan, availability: List<List<TimeSlot>>) {
    val colors = MaterialTheme.colorScheme
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        plan.days.forEach { day ->
            val isToday = day.date == today
            val past = day.date < today
            val slots = availability[day.date.dayOfWeek.value - 1]
            val reached = day.planned > 0 && day.done >= day.planned
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (isToday) colors.primaryContainer.copy(alpha = 0.55f) else androidx.compose.ui.graphics.Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(62.dp)) {
                    Text(if (isToday) words("Heute", "Today") else dayShort(day.date.dayOfWeek.value - 1), style = MaterialTheme.typography.titleSmall, color = if (day.inPeriod) colors.onSurface else colors.onSurfaceVariant)
                    Text(day.date.format(java.time.format.DateTimeFormatter.ofPattern(if (LocalEnglish.current) "d MMM" else "d. MMM", appLocale())), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                Text(when {
                    !day.inPeriod -> if (day.date > (plan.goal.dueDate ?: day.date)) words("nach dem Termin", "after the date") else ""
                    day.capacity == 0 -> words("keine Zeit", "no time")
                    else -> slots.joinToString(", ") { it.label() }
                }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 2)
                when {
                    past && day.done > 0 -> Text(words("${day.done} Min.", "${day.done} min"), style = MaterialTheme.typography.titleSmall, color = if (reached) colors.tertiary else colors.onSurface)
                    past -> Text(if (day.planned > 0) "–" else "", color = colors.onSurfaceVariant)
                    isToday && day.planned == 0 && day.done == 0 && day.capacity > 0 -> Text(words("frei", "free"), style = MaterialTheme.typography.titleSmall, color = colors.tertiary)
                    isToday && day.done > 0 -> Text(if (day.planned > 0) "${day.done} / ${day.planned}" else words("${day.done} Min.", "${day.done} min"), style = MaterialTheme.typography.titleSmall, color = if (reached || day.planned == 0) colors.tertiary else colors.primary)
                    day.planned > 0 -> Text(words("${day.planned} Min.", "${day.planned} min"), style = MaterialTheme.typography.titleSmall, color = if (isToday) colors.primary else colors.onSurface)
                    day.capacity > 0 && day.inPeriod -> Text(words("frei", "free"), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                if (reached && (past || isToday)) Icon(Icons.Rounded.Check, null, Modifier.padding(start = 4.dp).size(16.dp), tint = colors.tertiary)
            }
        }
    }
}

@Composable
fun PlanScreen(state: CadenceState, onGoal: (PracticeGoal?) -> Unit, onSelect: (String) -> Unit, onAvailability: (List<List<TimeSlot>>) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val now = ZonedDateTime.now()
    val plans = Planner.plans(state, now)
    val active = plans.first { it.goal.id == state.activeGoal.id }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 124.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader(words("Plan", "Plan"))
        GoalTabs(state, onSelect, onAdd = { onGoal(null) }, onEdit = { onGoal(it) })
        Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerLow) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (active.goal.type == GoalType.WEEKLY) periodTitle(active) else words("Bis zum Termin", "Until the date"), style = MaterialTheme.typography.titleLarge)
                        Text(goalSummary(active.goal, withReset = false), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = { onGoal(active.goal) }) { Icon(Icons.Rounded.Edit, words("Ziel bearbeiten", "Edit goal"), Modifier.size(20.dp)) }
                }
                Text(words("${active.done} von ${active.target} Min. geschafft", "${active.done} of ${active.target} min done"), style = MaterialTheme.typography.titleMedium, color = colors.primary)
                LinearWavyProgressIndicator(progress = { if (active.target > 0) (active.done.toFloat() / active.target).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth(), color = colors.primary, trackColor = colors.outlineVariant, waveSpeed = 0.dp)
                WeekPlanList(active, state.availability)
                PlanHint(active)
            }
        }

        val overbooked = overbookedDays(state, plans)
        if (overbooked.isNotEmpty()) Surface(shape = RoundedCornerShape(20.dp), color = colors.errorContainer, contentColor = colors.onErrorContainer) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.WarningAmber, null)
                Text(words("An ${overbooked.joinToString { dayShortPlain(it.dayOfWeek.value - 1, false) }} planen deine Ziele zusammen mehr ein, als du Zeit hast. Trag mehr Zeit ein oder senke ein Ziel.",
                    "On ${overbooked.joinToString { dayShortPlain(it.dayOfWeek.value - 1, true) }} your goals together need more time than you have. Add free time or lower a goal."), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerLow) {
            Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 18.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(words("Deine freie Zeit", "Your free time"), style = MaterialTheme.typography.titleLarge)
                Text(words("Gilt für alle Ziele. Nur in dieser Zeit wird geplant und erinnert. ${availabilitySummary(state.availability)}.",
                    "Shared by all goals. Planning and reminders only use this time. ${availabilitySummary(state.availability)}."), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp))
                WeekAvailabilityEditor(state.availability, onAvailability, highlightToday = true)
            }
        }
    }
}

@Composable
fun periodTitle(plan: GoalPlan): String {
    val goal = plan.goal
    if (goal.type == GoalType.DEADLINE) return words("Bis zum Termin", "Until the date")
    if (goal.resetDay == 0 && goal.resetMinute == 0) return words("Diese Woche", "This week")
    val end = plan.period.end
    return words("Bis ${dayShort(end.dayOfWeek.value - 1)} ${clock(end.hour * 60 + end.minute)}", "Until ${dayShort(end.dayOfWeek.value - 1)} ${clock(end.hour * 60 + end.minute)}")
}

/** Days this calendar week where the goals together plan more than the free time allows. */
fun overbookedDays(state: CadenceState, plans: List<GoalPlan>): List<LocalDate> {
    val today = LocalDate.now()
    return (0..6L).map { today.with(java.time.DayOfWeek.MONDAY).plusDays(it) }.filter { date ->
        date >= today && plans.sumOf { p -> p.days.firstOrNull { it.date == date }?.planned ?: 0 } > state.availability[date.dayOfWeek.value - 1].sumOf { it.minutes }
    }
}
