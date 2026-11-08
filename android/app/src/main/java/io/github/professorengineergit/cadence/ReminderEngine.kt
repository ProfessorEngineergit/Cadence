package io.github.professorengineergit.cadence

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** What has been sent so far. Kept separately from the practice data. */
data class ReminderDelivery(
    val lastSent: Long = 0,
    val date: String = "",
    val count: Int = 0,
    val snoozeUntil: Long = 0,
    /** Reminders are off for this date ("Not today"). */
    val skipDate: String = "",
    val praiseDate: String = "",
    /** Dates on which at least one practice reminder was sent, newest last. */
    val sentDates: List<String> = emptyList(),
)

enum class ReminderKind { START, FOLLOW_UP, SNOOZED, PRAISE }
data class PlannedReminder(val at: ZonedDateTime, val kind: ReminderKind)
data class ReminderCopy(val title: String, val body: String, val goalId: String? = null, val kind: ReminderKind = ReminderKind.START)

/**
 * Decides when to remind and what to say.
 *
 * - The first reminder comes when a free-time slot starts (or at the time you usually begin, once Cadence has
 *   seen at least three timed sessions in that slot).
 * - Later slots on the same day get a reminder of their own; the last slot gets one follow-up while there is
 *   still enough time left in it.
 * - Nothing is sent once today's share is done, on rest days, while a timer runs or within an hour of practice.
 * - If reminders are ignored on three days in a row, only one per day is sent until you practise again.
 * - On a practice day you may skip because you practised ahead, one friendly note can be sent instead.
 */
object ReminderEngine {
    private const val LATE_TOLERANCE_MS = 20 * 60_000L
    private const val MIN_GAP_MS = 45 * 60_000L

    fun ignoredDays(state: CadenceState, delivery: ReminderDelivery, today: LocalDate): Int {
        val practised = state.sessions.map { it.date() }.toSet()
        var count = 0
        var day = today.minusDays(1)
        repeat(14) {
            if (day.toString() !in delivery.sentDates || day in practised) return count
            count++; day = day.minusDays(1)
        }
        return count
    }

    fun dailyLimit(state: CadenceState, delivery: ReminderDelivery, today: LocalDate) =
        if (ignoredDays(state, delivery, today) >= 3) 1 else state.reminders.dailyLimit

    /** Median start of recent timed sessions on similar days (weekday or weekend), if there are at least three. */
    fun usualStart(state: CadenceState, day: LocalDate, zone: java.time.ZoneId): Int? {
        if (!state.reminders.learnTiming) return null
        val weekend = day.dayOfWeek.value >= 6
        val starts = state.sessions.filter {
            val d = it.date(zone)
            it.recordedWithTimer && d >= day.minusDays(28) && d < day && (d.dayOfWeek.value >= 6) == weekend
        }.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).let { t -> t.hour * 60 + t.minute } }.sorted()
        return if (starts.size >= 3) starts[starts.size / 2] else null
    }

    /** Candidate reminder times for [date], judged from the situation at [now] (start of day for future days). */
    fun candidates(state: CadenceState, date: LocalDate, now: ZonedDateTime): List<PlannedReminder> {
        val zone = now.zone
        val plans = Planner.plans(state, now)
        val weekday = date.dayOfWeek.value - 1
        val needing = plans.filter { it.status == DayStatus.TODO && it.todayRemaining > 0 }
        val slots = state.availability[weekday]
        fun at(minute: Int) = date.atStartOfDay(zone).plusMinutes(minute.toLong())
        if (needing.isEmpty()) {
            val restful = plans.any { it.status == DayStatus.AHEAD && it.todaySlots.isNotEmpty() }
            return if (state.reminders.praise && restful && slots.isNotEmpty()) listOf(PlannedReminder(at(slots.first().start), ReminderKind.PRAISE)) else emptyList()
        }
        val used = slots.filter { slot -> needing.any { weekday in it.goal.days } }
        if (used.isEmpty()) return emptyList()
        val need = needing.maxOf { it.todayRemaining }
        val usual = usualStart(state, date, zone)
        val result = used.map { slot ->
            val start = if (usual != null && usual > slot.start + 10 && usual <= slot.end - minOf(need, slot.minutes)) usual else slot.start
            PlannedReminder(at(start), ReminderKind.START)
        }.toMutableList()
        val last = used.last()
        val base = result.last().at.let { it.hour * 60 + it.minute }
        val latestUseful = last.end - maxOf(minOf(need, last.minutes), 15)
        val followUp = maxOf(base + 30, minOf(base + (last.end - base) / 2, latestUseful))
        if (followUp <= latestUseful) result += PlannedReminder(at(followUp), ReminderKind.FOLLOW_UP)
        return result.distinctBy { it.at }.sortedBy { it.at }
    }

    private fun recentPractice(state: CadenceState, millis: Long) =
        state.sessions.any { val end = it.startedAt + it.durationSeconds * 1000; end <= millis + 60_000 && millis - end < 60 * 60_000L }

    /** The next moment worth waking up for, or null if there is nothing to remind about in the coming week. */
    fun next(state: CadenceState, now: ZonedDateTime, delivery: ReminderDelivery): PlannedReminder? {
        if (!state.reminders.enabled) return null
        val millis = now.toInstant().toEpochMilli()
        if (delivery.snoozeUntil > millis) return PlannedReminder(Instant.ofEpochMilli(delivery.snoozeUntil).atZone(now.zone), ReminderKind.SNOOZED)
        val today = now.toLocalDate()
        for (offset in 0..7L) {
            val date = today.plusDays(offset)
            if (date.toString() == delivery.skipDate) continue
            val reference = if (offset == 0L) now else date.atStartOfDay(now.zone)
            val sentToday = if (offset == 0L && delivery.date == date.toString()) delivery.count else 0
            val limit = dailyLimit(state, delivery, date)
            val found = candidates(state, date, reference).firstOrNull { c ->
                c.at.isAfter(now) && c.at.toInstant().toEpochMilli() >= delivery.lastSent + MIN_GAP_MS &&
                    (if (c.kind == ReminderKind.PRAISE) delivery.praiseDate != date.toString() else sentToday < limit)
            }
            if (found != null) return found
        }
        return null
    }

    /** The reminder to show right now, if one is due. */
    fun due(state: CadenceState, now: ZonedDateTime, delivery: ReminderDelivery): ReminderCopy? {
        if (!state.reminders.enabled || state.timer != null) return null
        val today = now.toLocalDate()
        if (delivery.skipDate == today.toString()) return null
        val millis = now.toInstant().toEpochMilli()
        val snoozeDue = delivery.snoozeUntil in (millis - LATE_TOLERANCE_MS)..millis
        if (recentPractice(state, millis)) return null
        val plans = Planner.plans(state, now)
        if (snoozeDue) return if (plans.any { it.todayRemaining > 0 }) copy(state, plans, now, ReminderKind.SNOOZED) else null
        val sentToday = if (delivery.date == today.toString()) delivery.count else 0
        val candidate = candidates(state, today, now).lastOrNull { c ->
            val t = c.at.toInstant().toEpochMilli()
            t <= millis && t > millis - LATE_TOLERANCE_MS && t > delivery.lastSent
        } ?: return null
        return when (candidate.kind) {
            ReminderKind.PRAISE -> if (delivery.praiseDate == today.toString()) null else copy(state, plans, now, ReminderKind.PRAISE)
            else -> if (sentToday >= dailyLimit(state, delivery, today) || millis - delivery.lastSent < MIN_GAP_MS) null else copy(state, plans, now, candidate.kind)
        }
    }

    fun record(delivery: ReminderDelivery, copy: ReminderCopy, now: ZonedDateTime): ReminderDelivery {
        val today = now.toLocalDate().toString()
        val millis = now.toInstant().toEpochMilli()
        if (copy.kind == ReminderKind.PRAISE) return delivery.copy(praiseDate = today)
        return delivery.copy(lastSent = millis, date = today, count = if (delivery.date == today) delivery.count + 1 else 1, snoozeUntil = 0,
            sentDates = (delivery.sentDates.filter { it != today } + today).takeLast(14))
    }

    fun copy(state: CadenceState, plans: List<GoalPlan>, now: ZonedDateTime, kind: ReminderKind): ReminderCopy {
        val l = state.language
        fun w(de: String, en: String) = wordsNow(de, en, l)
        val minute = now.hour * 60 + now.minute
        val slots = state.availability[now.dayOfWeek.value - 1]
        val slotEnd = (slots.firstOrNull { minute in it.start until it.end } ?: slots.firstOrNull { it.start > minute })?.end
        val until = slotEnd?.let { w(" Zeit hast du bis ${clock(it)}.", " You have time until ${clock(it)}.") } ?: ""

        if (kind == ReminderKind.PRAISE) {
            val ahead = plans.filter { it.status == DayStatus.AHEAD }
            val p = ahead.firstOrNull()
            val body = when {
                p != null && p.yesterdayMinutes > 0 -> w("Gestern hast du ${p.yesterdayMinutes} Minuten ${p.goal.title} geübt. Damit bist du vorne und kannst heute frei machen. Gut gemacht!",
                    "You practised ${p.goal.title} for ${p.yesterdayMinutes} minutes yesterday, so you're ahead and can take today off. Well done!")
                p != null -> w("Bei ${p.goal.title} bist du deinem Plan voraus. Heute musst du nichts machen.", "You're ahead of plan with ${p.goal.title}. Nothing to do today.")
                else -> w("Heute musst du nichts machen.", "Nothing to do today.")
            }
            return ReminderCopy(w("Heute ist frei", "You've got today off"), body, p?.goal?.id, kind)
        }

        val needing = plans.filter { it.todayRemaining > 0 }.sortedWith(compareBy<GoalPlan> { it.daysUntilDeadline ?: Int.MAX_VALUE }.thenByDescending { it.todayRemaining })
        val p = needing.firstOrNull() ?: plans.first()
        if (needing.size > 1) {
            val list = needing.joinToString(", ") { "${it.goal.title} ${it.todayRemaining} Min." }
            val title = w("Heute: $list", "Today: ${needing.joinToString(", ") { "${it.goal.title} ${it.todayRemaining} min" }}")
            return ReminderCopy(title, (if (kind == ReminderKind.FOLLOW_UP) w("Noch ist Zeit dafür.", "There's still time.") else w("Dein Zeitfenster beginnt.", "Your free time starts now.")) + until, p.goal.id, kind)
        }
        val g = p.goal.title
        val left = p.todayRemaining
        val title = when (kind) {
            ReminderKind.FOLLOW_UP -> w("Noch Zeit für $g?", "Still time for $g?")
            else -> if (p.doneToday > 0) w("$g: noch $left Minuten", "$g: $left minutes to go") else w("$g: heute $left Minuten", "$g: $left minutes today")
        }
        val context = when {
            p.daysUntilDeadline == 0 -> w("Heute ist es so weit. Ein letzter Durchgang hilft.", "Today's the day. One last run-through helps.")
            p.daysUntilDeadline == 1 -> w("Morgen ist es so weit. Es fehlen noch ${p.remaining} Minuten.", "It's tomorrow. ${p.remaining} minutes to go.")
            p.daysUntilDeadline != null && p.daysUntilDeadline <= 7 -> w("Noch ${p.daysUntilDeadline} Tage bis zum Termin, ${p.remaining} Minuten fehlen.", "${p.daysUntilDeadline} days to go, ${p.remaining} minutes left.")
            p.shortfall > 0 -> w("Diese Woche wird es knapp: ${p.remaining} Minuten fehlen noch.", "This week is getting tight: ${p.remaining} minutes left.")
            p.isLastPracticeDay && p.goal.type == GoalType.WEEKLY -> w("Heute ist dein letzter Übetag dieser Woche.", "Today is your last practice day this week.")
            p.doneToday > 0 && kind == ReminderKind.FOLLOW_UP -> w("${p.doneToday} von ${p.todayTarget} Minuten hast du schon.", "You've done ${p.doneToday} of ${p.todayTarget} minutes.")
            p.yesterdayMinutes > 0 && p.todayTarget < p.usualToday -> w("Weil du gestern ${p.yesterdayMinutes} Minuten geübt hast, reicht heute weniger.", "Because you practised ${p.yesterdayMinutes} minutes yesterday, less is enough today.")
            p.todayTarget > p.usualToday && p.usualToday > 0 -> w("Etwas mehr als sonst, damit du dein Ziel noch schaffst.", "A bit more than usual so you still reach your goal.")
            kind == ReminderKind.FOLLOW_UP -> w("Die $left Minuten passen noch gut rein.", "The $left minutes still fit in nicely.")
            else -> w("Dein Zeitfenster beginnt.", "Your free time starts now.")
        }
        return ReminderCopy(title, context + until, p.goal.id, kind)
    }
}
