package io.github.professorengineergit.cadence

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.ceil
import kotlin.math.min

data class Availability(val start: Int = 16 * 60, val end: Int = 20 * 60, val enabled: Boolean = true) {
    val minutes get() = if (enabled) (end - start).coerceAtLeast(0) else 0
}

data class ReminderSettings(
    val enabled: Boolean = false,
    val windows: List<Availability> = List(7) { if (it < 5) Availability() else Availability(10 * 60, 18 * 60) },
    val deadlineDay: Int = 7,
    val deadlineMinute: Int = 18 * 60,
    val dailyLimit: Int = 2,
    val learnTiming: Boolean = true,
) {
    fun toJson() = JSONObject().apply {
        put("enabled", enabled); put("deadlineDay", deadlineDay); put("deadlineMinute", deadlineMinute)
        put("dailyLimit", dailyLimit); put("learnTiming", learnTiming)
        put("windows", JSONArray().apply { windows.forEach { w -> put(JSONObject().apply { put("start", w.start); put("end", w.end); put("enabled", w.enabled) }) } })
    }
    companion object {
        fun fromJson(json: JSONObject?): ReminderSettings {
            if (json == null) return ReminderSettings()
            val array = json.optJSONArray("windows")
            val windows = if (array != null && array.length() == 7) (0..6).map { i ->
                val w = array.getJSONObject(i)
                val start = w.optInt("start", 960).coerceIn(360, 1350)
                Availability(start, w.optInt("end", 1200).coerceIn(start + 30, 1380), w.optBoolean("enabled", true))
            } else ReminderSettings().windows
            return ReminderSettings(json.optBoolean("enabled"), windows, json.optInt("deadlineDay", 7).coerceIn(1, 7),
                json.optInt("deadlineMinute", 1080).coerceIn(0, 1439), json.optInt("dailyLimit", 2).coerceIn(1, 3), json.optBoolean("learnTiming", true))
        }
    }
}

data class ReminderDelivery(val lastSent: Long = 0, val date: String = "", val count: Int = 0, val snoozeUntil: Long = 0, val skipUntil: Long = 0)
data class ReminderCopy(val title: String, val body: String, val suggestion: Int, val goalId: String? = null)
data class ReminderEvaluation(val notification: ReminderCopy?, val explanation: String)

object ReminderEngine {
    fun evaluateAll(state: CadenceState, now: ZonedDateTime, delivery: ReminderDelivery = ReminderDelivery()): ReminderEvaluation {
        if (state.timer != null) return evaluate(state, now, delivery)
        val evaluations = state.practiceGoals.sortedWith(compareBy<PracticeGoal> { it.dueDate ?: LocalDate.MAX }.thenBy { it.id }).map { goal ->
            val result = evaluate(state.copy(activeGoalId = goal.id), now, delivery)
            if (result.notification != null) result.copy(notification = result.notification.copy(title = "${goal.title} · ${result.notification.title}", goalId = goal.id)) else result
        }
        return evaluations.firstOrNull { it.notification != null } ?: evaluations.firstOrNull() ?: ReminderEvaluation(null, "")
    }
    fun preferredMinute(state: CadenceState, day: LocalDate, window: Availability, zone: java.time.ZoneId): Int {
        val weekend = day.dayOfWeek.value >= 6
        val starts = if (state.reminders.learnTiming) state.selectedSessions.filter {
            it.recordedWithTimer && it.date(zone) >= day.minusDays(28) && it.date(zone) <= day && (it.date(zone).dayOfWeek.value >= 6) == weekend
        }.map { java.time.Instant.ofEpochMilli(it.startedAt).atZone(zone).let { t -> t.hour * 60 + t.minute } }.sorted() else emptyList()
        val preferred = if (starts.size >= 3) starts[starts.size / 2] else window.start + 30
        return preferred.coerceIn(window.start, (window.end - 15).coerceAtLeast(window.start))
    }

    fun evaluate(state: CadenceState, now: ZonedDateTime, delivery: ReminderDelivery = ReminderDelivery()): ReminderEvaluation {
        val english = isEnglish(state.language)
        fun quiet(reason: String) = ReminderEvaluation(null, translate(reason, english))
        val r = state.reminders
        val day = now.toLocalDate()
        val millis = now.toInstant().toEpochMilli()
        val stats = PracticeStats(state.selectedSessions, state.goals, day, now.zone)
        val goal = state.activeGoal
        if (goal.dueDate != null && goal.dueDate!! < day) return quiet(wordsNow("Der Zieltermin ist vorbei. Passe den Termin an oder starte ein neues Ziel.", "This goal's deadline has passed. Update the date or start a new goal.", state.language))
        val weekSeconds = stats.weekSessions.sumOf { it.durationSeconds }
        val target = if (goal.dueDate != null) goal.totalTarget else stats.weeklyGoal
        val achievedSeconds = if (goal.dueDate != null) state.selectedSessions.sumOf { it.durationSeconds } else weekSeconds
        val remainingSeconds = (target * 60L - achievedSeconds).coerceAtLeast(0)
        val remaining = ceil(remainingSeconds / 60.0).toInt()
        if (!r.enabled) return quiet("Erinnerungen sind ausgeschaltet.")
        if (state.timer != null) return quiet("Du bist schon in einer Session. Viel Spaß beim Üben!")
        if (stats.weeklyGoal == 0) return quiet("Lege zuerst ein Übeziel in deinem Wochenplan fest.")
        if (remaining == 0) return quiet(if (goal.dueDate == null) "Wochenziel erreicht! Diese Woche lassen wir dich in Ruhe." else wordsNow("Ziel erreicht! Für dieses Ziel gibt es keine weiteren Erinnerungen.", "Goal reached! No more reminders for this goal.", state.language))
        if (delivery.skipUntil > millis) return quiet("Heute ist Erinnerungspause. Morgen geht es entspannt weiter.")
        if (delivery.snoozeUntil > millis) return quiet("Deine Erinnerung ist um 30 Minuten verschoben.")
        val window = r.windows[day.dayOfWeek.value - 1]
        val minute = now.hour * 60 + now.minute
        if (!window.enabled) return quiet("Heute hast du dir keinen Zeitraum zum Üben freigehalten.")
        if (minute !in window.start until window.end) return quiet("Wir erinnern dich nur in deinem Zeitfenster: ${clock(window.start)}–${clock(window.end)}.")
        val lastPracticeEnd = state.sessions.filter { it.startedAt <= millis }.maxOfOrNull { it.startedAt + it.durationSeconds * 1000 }
        if (lastPracticeEnd != null && millis - lastPracticeEnd < 3 * 3600_000L) return quiet("Gerade geübt – jetzt ist erst einmal Ruhe. Mindestens drei Stunden ohne Erinnerung.")
        val daysToCheckIn = goal.dueDate?.let { java.time.temporal.ChronoUnit.DAYS.between(day, it).toInt() }
            ?: (r.deadlineDay - day.dayOfWeek.value + 7) % 7
        val expectedThroughToday = state.goals.take(day.dayOfWeek.value).sum()
        if (goal.dueDate == null && weekSeconds >= expectedThroughToday * 60L && expectedThroughToday > 0) return quiet("Du liegst im Wochenplan. Gestern mehr geübt? Das zählt heute mit.")
        val remainingPracticeDays = goal.dueDate?.let { due ->
            java.time.temporal.ChronoUnit.DAYS.between(day, due).coerceIn(0, 3650).let { count -> (0..count).count { offset ->
                val d = day.plusDays(offset)
                goal.dayTargets[d.dayOfWeek.value - 1] > 0 && r.windows[d.dayOfWeek.value - 1].enabled
            } }.coerceAtLeast(1)
        } ?: 1
        val neededToday = if (goal.dueDate != null) ceil((remaining + stats.todayMinutes) / remainingPracticeDays.toDouble()).toInt() else stats.todayGoal
        if (goal.dueDate != null && stats.todayGoal > 0 && stats.todayMinutes >= maxOf(stats.todayGoal, neededToday)) return quiet("Du liegst im Wochenplan. Gestern mehr geübt? Das zählt heute mit.")
        if (stats.todayGoal == 0 && daysToCheckIn > 1) return quiet("Heute ist ein freier Tag. Dein Rhythmus darf auch Pausen haben.")
        val snoozedFollowUp = delivery.snoozeUntil in 1..millis
        val count = if (delivery.date == day.toString()) delivery.count else 0
        if (!snoozedFollowUp && count >= r.dailyLimit) return quiet("Dein Erinnerungslimit für heute ist erreicht.")
        if (!snoozedFollowUp && delivery.lastSent > 0 && millis - delivery.lastSent < 3 * 3600_000L) return quiet("Eine Erinnerung reicht erst einmal. Die nächste frühestens nach drei Stunden.")
        val preferred = preferredMinute(state, day, window, now.zone)
        val trigger = when (count) { 0 -> preferred; 1 -> (window.end - 60).coerceAtLeast(preferred + 180); else -> (window.end - 15).coerceAtLeast(preferred + 360) }
        if (!snoozedFollowUp && minute < trigger) return quiet("Deine nächste Gelegenheit liegt ungefähr bei ${clock(trigger.coerceAtMost(window.end))}.")
        val available = window.end - minute
        if (available < 5) return quiet("Dein Zeitfenster endet gleich. Heute kein Drängeln mehr.")
        val remainingPercent = ceil(remainingSeconds * 100.0 / (target * 60)).toInt().coerceIn(1, 100)
        val todayRemaining = if (goal.dueDate != null) (neededToday - stats.todayMinutes).coerceAtLeast(5).toInt() else (expectedThroughToday - weekSeconds / 60).coerceAtLeast(5).toInt()
        val suggestion = min(min(remaining, todayRemaining), min(25, available)).coerceAtLeast(1)
        val yesterday = stats.minutesOn(day.minusDays(1))
        val bigYesterday = yesterday >= maxOf(20, stats.goalOn(day.minusDays(1)) * 3 / 2)
        val tomorrowPlan = state.goals[(day.dayOfWeek.value) % 7]
        val title = when {
            bigYesterday -> "Gestern stark. Heute ganz entspannt."
            daysToCheckIn == 1 -> if (goal.dueDate != null) wordsNow("Dein Zieltermin ist morgen.", "Your goal date is tomorrow.", state.language) else "Dein Wochen-Check-in ist morgen."
            daysToCheckIn == 0 -> if (goal.dueDate != null) wordsNow("Heute ist dein Zieltag.", "Today's your goal day.", state.language) else "Ein guter Moment für dein Wochenziel."
            stats.todayMinutes > 0 -> "Ein kleiner Schritt fehlt noch."
            stats.streak >= 2 -> "Dein Rhythmus hat Schwung."
            else -> "${suggestion} Minuten nur für dich?"
        }
        val intro = if (bigYesterday) "Gestern schon $yesterday Minuten – stark! " else ""
        val deadline = if (goal.dueDate != null) "" else when (daysToCheckIn) { 0 -> "Heute um ${clock(r.deadlineMinute)} ist dein Check-in. "; 1 -> "Morgen um ${clock(r.deadlineMinute)} ist dein Check-in. "; else -> "" }
        val tomorrow = if (daysToCheckIn > 1 && tomorrowPlan > 0) " Morgen stehen $tomorrowPlan Minuten im Plan." else ""
        val goalPhrase = if (goal.dueDate != null) "bis zu deinem Ziel am ${goal.dueDate}" else "bis zum Wochenziel"
        val body = if (english) {
            val introEn = if (bigYesterday) "You practised $yesterday minutes yesterday. Great work! " else ""
            val dueEn = if (goal.dueDate != null) "until your goal on ${goal.dueDate}" else "to your weekly goal"
            val deadlineEn = if (goal.dueDate != null) "" else when (daysToCheckIn) { 0 -> "Your check-in is today at ${clock(r.deadlineMinute)}. "; 1 -> "Your check-in is tomorrow at ${clock(r.deadlineMinute)}. "; else -> "" }
            "${introEn}$remainingPercent% ($remaining min) left $dueEn. $deadlineEn${suggestion} minutes fits right now." + if (daysToCheckIn > 1 && tomorrowPlan > 0) " Tomorrow's plan is $tomorrowPlan minutes." else ""
        } else "${intro}Noch $remainingPercent % ($remaining Min.) $goalPhrase. $deadline${suggestion} Minuten passen jetzt gut.$tomorrow"
        return ReminderEvaluation(ReminderCopy(translate(title, english), body, suggestion), translate("Die Erinnerung passt zu deinem aktuellen Fortschritt und Zeitfenster.", english))
    }
}

fun clock(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
