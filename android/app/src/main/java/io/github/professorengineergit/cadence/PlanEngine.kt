package io.github.professorengineergit.cadence

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil

/** The stretch of time a goal's target applies to: one week, or creation until the deadline. [end] is exclusive. */
data class Period(val start: LocalDateTime, val end: LocalDateTime)

enum class DayStatus {
    /** No free time for this goal today. */
    REST,
    /** Something left to do today. */
    TODO,
    /** Today's share is done. */
    DONE_TODAY,
    /** Free day: earlier practice already covers today. */
    AHEAD,
    /** The whole week or deadline target is reached. */
    GOAL_REACHED,
    /** The deadline has passed. */
    OVER,
}

data class DayPlan(val date: LocalDate, val capacity: Int, val planned: Int, val done: Int, val inPeriod: Boolean)

data class GoalPlan(
    val goal: PracticeGoal,
    val period: Period,
    val target: Int,
    val done: Int,
    val doneBefore: Int,
    val doneToday: Int,
    val todayTarget: Int,
    /** What an even plan would have asked for today, ignoring earlier practice. */
    val usualToday: Int,
    val status: DayStatus,
    val days: List<DayPlan>,
    /** Minutes that no longer fit into the free time left in this period. */
    val shortfall: Int,
    val nextPracticeDay: LocalDate?,
    val yesterdayMinutes: Int,
    val todaySlots: List<TimeSlot>,
    val practiceDaysLeft: Int,
    val daysUntilDeadline: Int?,
) {
    val remaining get() = (target - done).coerceAtLeast(0)
    val todayRemaining get() = if (status == DayStatus.TODO) (todayTarget - doneToday).coerceAtLeast(0) else 0
    val isLastPracticeDay get() = practiceDaysLeft == 1 && status == DayStatus.TODO
}

object Planner {
    fun period(goal: PracticeGoal, now: LocalDateTime): Period = when (goal.type) {
        GoalType.WEEKLY -> {
            val reset = LocalTime.of(goal.resetMinute / 60, goal.resetMinute % 60)
            var start = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.of(goal.resetDay + 1))).atTime(reset)
            if (start.isAfter(now)) start = start.minusWeeks(1)
            Period(start, start.plusWeeks(1))
        }
        GoalType.DEADLINE -> {
            val due = goal.dueDate ?: now.toLocalDate()
            val end = goal.deadlineMinute?.let { due.atTime(it / 60, it % 60) } ?: due.plusDays(1).atStartOfDay()
            val created = goal.createdDate.atStartOfDay()
            Period(minOf(created, now.toLocalDate().atStartOfDay(), end), end)
        }
    }

    /** Free minutes a goal can use on [date], clipped to [period] and limited to the goal's days. */
    fun capacity(goal: PracticeGoal, availability: List<List<TimeSlot>>, date: LocalDate, period: Period): Int {
        if (date.dayOfWeek.value - 1 !in goal.days) return 0
        return availability[date.dayOfWeek.value - 1].sumOf { slot ->
            val from = maxOf(date.atStartOfDay().plusMinutes(slot.start.toLong()), period.start)
            val to = minOf(date.atStartOfDay().plusMinutes(slot.end.toLong()), period.end)
            if (to.isAfter(from)) Duration.between(from, to).toMinutes().toInt() else 0
        }.coerceAtMost(240)
    }

    /** Spreads [total] minutes evenly over days in 5-minute steps without exceeding each day's capacity. */
    fun spread(total: Int, caps: List<Int>): List<Int> {
        val result = MutableList(caps.size) { 0 }
        var units = ceil(total.coerceAtLeast(0) / 5.0).toInt()
        val limit = caps.map { if (it in 1..4) 1 else it / 5 }
        while (units > 0) {
            val open = caps.indices.filter { result[it] / 5 < limit[it] }
            if (open.isEmpty()) break
            val share = (units / open.size).coerceAtLeast(1)
            for (i in open) {
                if (units == 0) break
                val add = minOf(share, limit[i] - result[i] / 5, units)
                result[i] += add * 5; units -= add
            }
        }
        return result
    }

    private fun ceil5(value: Int) = if (value <= 0) 0 else ((value + 4) / 5) * 5

    fun plan(state: CadenceState, goal: PracticeGoal, now: ZonedDateTime): GoalPlan {
        val zone = now.zone
        val local = now.toLocalDateTime()
        val today = local.toLocalDate()
        val period = period(goal, local)
        val sessions = state.sessionsFor(goal.id)
        fun startOf(s: PracticeSession) = java.time.Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDateTime()
        val inPeriod = sessions.filter { val t = startOf(it); !t.isBefore(period.start) && t.isBefore(period.end) }
        fun minutes(list: List<PracticeSession>) = (list.sumOf { it.durationSeconds } / 60).toInt()
        val done = minutes(inPeriod)
        val doneToday = minutes(inPeriod.filter { startOf(it).toLocalDate() == today })
        val doneBefore = minutes(inPeriod.filter { startOf(it).toLocalDate() < today })

        val lastDay = period.end.minusMinutes(1).toLocalDate()
        val periodDates = generateSequence(period.start.toLocalDate()) { it.plusDays(1) }.takeWhile { !it.isAfter(lastDay) }.take(800).toList()
        val caps = periodDates.map { capacity(goal, state.availability, it, period) }
        val practiceDates = periodDates.indices.filter { caps[it] > 0 }.map { periodDates[it] }
        val target = if (goal.perDay) goal.minutes * practiceDates.size.coerceAtLeast(1) else goal.minutes

        val ideal = spread(target, caps)
        val todayIndex = periodDates.indexOf(today)
        val idealThroughToday = if (todayIndex < 0) 0 else ideal.take(todayIndex + 1).sum()
        val usualToday = if (todayIndex < 0) 0 else ideal[todayIndex]
        val remainingBefore = (target - doneBefore).coerceAtLeast(0)
        val futureIdx = periodDates.indices.filter { periodDates[it] >= today }
        val replan = spread(remainingBefore, futureIdx.map { caps[it] })
        val todayCap = if (todayIndex < 0) 0 else caps[todayIndex]
        val replanToday = if (todayIndex < 0 || futureIdx.isEmpty()) 0 else replan[0]
        // Behind: spread the gap over the remaining days. Ahead: only what keeps you on the even plan.
        val todayTarget = if (todayCap == 0) 0 else ceil5(minOf(replanToday, idealThroughToday - doneBefore)).coerceAtMost(remainingBefore.coerceAtLeast(0).let { ceil5(it) })

        val status = when {
            !local.isBefore(period.end) && goal.type == GoalType.DEADLINE -> DayStatus.OVER
            done >= target -> DayStatus.GOAL_REACHED
            todayCap == 0 -> DayStatus.REST
            todayTarget == 0 -> DayStatus.AHEAD
            doneToday >= todayTarget -> DayStatus.DONE_TODAY
            else -> DayStatus.TODO
        }

        // Days after today share whatever is left once today's part is done.
        val afterToday = (remainingBefore - maxOf(todayTarget, doneToday)).coerceAtLeast(0)
        val laterIdx = periodDates.indices.filter { periodDates[it] > today }
        val later = spread(afterToday, laterIdx.map { caps[it] })
        val laterByDate = laterIdx.mapIndexed { i, idx -> periodDates[idx] to later[i] }.toMap()
        val doneByDate = inPeriod.groupBy { startOf(it).toLocalDate() }.mapValues { minutes(it.value) }

        val shown = when (goal.type) {
            GoalType.WEEKLY -> periodDates
            GoalType.DEADLINE -> (0..6L).map { today.plusDays(it) }.filter { !it.isAfter(lastDay) }.ifEmpty { periodDates.takeLast(7) }
        }
        val days = shown.map { date ->
            val idx = periodDates.indexOf(date)
            val cap = if (idx >= 0) caps[idx] else 0
            val planned = when {
                idx < 0 -> 0
                date < today -> ideal[idx]
                date == today -> todayTarget
                else -> laterByDate[date] ?: 0
            }
            DayPlan(date, cap, planned, doneByDate[date] ?: 0, idx >= 0)
        }

        // Today only counts with the free time that is still ahead.
        val restOfToday = if (todayIndex < 0) 0 else capacity(goal, state.availability, today, Period(maxOf(period.start, local), period.end))
        val capacityLeft = futureIdx.sumOf { if (periodDates[it] == today) restOfToday else caps[it] }
        val nextDay = generateSequence(today.plusDays(1)) { it.plusDays(1) }.take(14).firstOrNull { date ->
            date.dayOfWeek.value - 1 in goal.days && state.availability[date.dayOfWeek.value - 1].isNotEmpty() &&
                (goal.type == GoalType.WEEKLY || !date.isAfter(lastDay))
        }
        val yesterday = minutes(sessions.filter { startOf(it).toLocalDate() == today.minusDays(1) })
        return GoalPlan(goal, period, target, done, doneBefore, doneToday, todayTarget, usualToday, status, days,
            (remainingBefore - capacityLeft).coerceAtLeast(0), nextDay, yesterday,
            if (today.dayOfWeek.value - 1 in goal.days) state.availability[today.dayOfWeek.value - 1] else emptyList(),
            futureIdx.count { caps[it] > 0 }, goal.dueDate?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it).toInt() })
    }

    fun plans(state: CadenceState, now: ZonedDateTime) = state.practiceGoals.map { plan(state, it, now) }

    /**
     * Days in a row with practice. Days without any free time for any goal neither count nor break it,
     * so a planned rest day never costs you your streak.
     */
    fun streak(state: CadenceState, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int {
        val practised = state.sessions.filter { it.durationSeconds > 0 }.map { it.date(zone) }.toSet()
        val usable = (0..6).filter { d -> state.availability[d].isNotEmpty() && state.practiceGoals.any { d in it.days } }.toSet()
        var cursor = today
        var count = 0
        if (today !in practised) cursor = today.minusDays(1)
        repeat(400) {
            when {
                cursor in practised -> count++
                cursor.dayOfWeek.value - 1 in usable -> return count
            }
            cursor = cursor.minusDays(1)
        }
        return count
    }

    fun minutesOn(state: CadenceState, date: LocalDate, goalId: String? = null, zone: ZoneId = ZoneId.systemDefault()) =
        (state.sessions.filter { (goalId == null || it.goalId == goalId) && it.date(zone) == date }.sumOf { it.durationSeconds } / 60).toInt()
}
