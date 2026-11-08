package io.github.professorengineergit.cadence

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

const val DEFAULT_FOCUS = "Freies Üben"

data class PracticeSession(
    val id: String = UUID.randomUUID().toString(),
    val startedAt: Long,
    val durationSeconds: Long,
    val focus: String = DEFAULT_FOCUS,
    val note: String = "",
    val recordedWithTimer: Boolean = true,
    val goalId: String = "primary",
) {
    fun date(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()
}

data class PracticeTimer(
    val sessionStartedAt: Long,
    val runningSince: Long?,
    val accumulatedMillis: Long = 0,
    val focus: String,
    val targetMinutes: Int,
    val goalId: String = "primary",
) {
    val isRunning get() = runningSince != null
    fun elapsedMillis(now: Long): Long = accumulatedMillis +
        (runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0)
    fun pause(now: Long) = copy(accumulatedMillis = elapsedMillis(now), runningSince = null)
    fun resume(now: Long) = copy(runningSince = now)
}

enum class AppTheme { SYSTEM, LIGHT, DARK }
enum class GoalKind { MUSIC, SPEECH, STUDY, OTHER }
enum class AppLanguage { SYSTEM, DE, EN }

/** WEEKLY repeats every week; DEADLINE works towards one date and then ends. */
enum class GoalType { WEEKLY, DEADLINE }

/** A stretch of free time on one weekday, in minutes after midnight. [end] may be 1440. */
data class TimeSlot(val start: Int, val end: Int) {
    val minutes get() = (end - start).coerceAtLeast(0)
    fun label() = "${clock(start)}–${clock(end)}"
}

data class PracticeGoal(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val kind: GoalKind = GoalKind.MUSIC,
    val type: GoalType = GoalType.WEEKLY,
    /** Minutes per week (WEEKLY) or in total (DEADLINE); per practice day when [perDay] is set. */
    val minutes: Int = 100,
    val perDay: Boolean = false,
    /** Weekdays (0 = Monday) this goal may use. Free time decides the rest. */
    val days: Set<Int> = (0..6).toSet(),
    /** A new week starts at this weekday and time, e.g. right after a Monday lesson at 16:00. */
    val resetDay: Int = 0,
    val resetMinute: Int = 0,
    val deadline: String? = null,
    /** Time of the event on the deadline day; null means the whole day still counts. */
    val deadlineMinute: Int? = null,
    val createdOn: String = LocalDate.now().toString(),
) {
    val dueDate: LocalDate? get() = if (type == GoalType.DEADLINE) deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() } else null
    val createdDate: LocalDate get() = runCatching { LocalDate.parse(createdOn) }.getOrElse { LocalDate.now() }
}

val DefaultAvailability: List<List<TimeSlot>> = List(7) { if (it < 5) listOf(TimeSlot(17 * 60, 19 * 60)) else listOf(TimeSlot(10 * 60, 12 * 60)) }

data class ReminderSettings(
    val enabled: Boolean = false,
    val dailyLimit: Int = 2,
    val learnTiming: Boolean = true,
    /** A friendly note on days you may skip because you practised ahead. */
    val praise: Boolean = true,
) {
    fun toJson() = JSONObject().apply { put("enabled", enabled); put("dailyLimit", dailyLimit); put("learnTiming", learnTiming); put("praise", praise) }
    companion object {
        fun fromJson(json: JSONObject?): ReminderSettings = if (json == null) ReminderSettings() else
            ReminderSettings(json.optBoolean("enabled"), json.optInt("dailyLimit", 2).coerceIn(1, 3), json.optBoolean("learnTiming", true), json.optBoolean("praise", true))
    }
}

data class CadenceState(
    val sessions: List<PracticeSession> = emptyList(),
    val practiceGoals: List<PracticeGoal> = listOf(PracticeGoal(id = "primary", title = "Klavier")),
    /** Free time per weekday (0 = Monday), shared by all goals. */
    val availability: List<List<TimeSlot>> = DefaultAvailability,
    val timer: PracticeTimer? = null,
    val focus: String = DEFAULT_FOCUS,
    val focusMinutes: Int = 25,
    val theme: AppTheme = AppTheme.SYSTEM,
    val dynamicColors: Boolean = false,
    val reminders: ReminderSettings = ReminderSettings(),
    val haptics: Boolean = true,
    val activeGoalId: String = "primary",
    val language: AppLanguage = AppLanguage.SYSTEM,
    val onboarded: Boolean = false,
    val completionSound: Boolean = true,
    val liveTimer: Boolean = true,
) {
    val activeGoal get() = practiceGoals.firstOrNull { it.id == activeGoalId } ?: practiceGoals.first()
    val selectedSessions get() = sessionsFor(activeGoal.id)
    fun sessionsFor(goalId: String) = sessions.filter { it.goalId == goalId }
    fun goal(id: String?) = practiceGoals.firstOrNull { it.id == id }
}

internal class PracticeStore(context: Context) {
    private val prefs = context.getSharedPreferences("cadence", Context.MODE_PRIVATE)
    fun observe(onChange: () -> Unit): java.io.Closeable {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "state") onChange() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return java.io.Closeable { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun load(): CadenceState = prefs.getString("state", null)?.let { decode(JSONObject(it)) } ?: CadenceState()
    fun save(state: CadenceState) { check(prefs.edit().putString("state", encode(state).toString()).commit()) { "Saving failed" } }

    companion object {
        private fun slot(start: Int, end: Int): TimeSlot? {
            val s = start.coerceIn(0, 1435)
            val e = end.coerceIn(s + 5, 1440)
            return if (end > start) TimeSlot(s, e) else null
        }

        fun decode(json: JSONObject): CadenceState {
            val entries = json.optJSONArray("sessions") ?: JSONArray()
            val sessions = (0 until entries.length()).map { i ->
                val s = entries.getJSONObject(i)
                PracticeSession(s.getString("id"), s.getLong("startedAt"), s.getLong("seconds"), s.optString("focus", DEFAULT_FOCUS), s.optString("note"), s.optBoolean("recordedWithTimer", true), s.optString("goalId", "primary"))
            }
            val t = json.optJSONObject("timer")?.let {
                PracticeTimer(it.getLong("startedAt"), if (it.isNull("runningSince")) null else it.getLong("runningSince"), it.optLong("accumulated"), it.getString("focus"), it.getInt("target"), it.optString("goalId", "primary"))
            }
            val legacyReminders = json.optJSONObject("reminders")

            // Free time: version 2 stores slot lists, version 1 one window per weekday inside the reminder settings.
            val availability = json.optJSONArray("availability")?.takeIf { it.length() == 7 }?.let { days ->
                (0..6).map { d -> val list = days.getJSONArray(d); (0 until list.length()).mapNotNull { i -> list.getJSONObject(i).let { slot(it.getInt("start"), it.getInt("end")) } }.sortedBy { it.start } }
            } ?: legacyReminders?.optJSONArray("windows")?.takeIf { it.length() == 7 }?.let { windows ->
                (0..6).map { d -> val w = windows.getJSONObject(d); if (w.optBoolean("enabled", true)) listOfNotNull(slot(w.optInt("start", 960), w.optInt("end", 1200))) else emptyList() }
            } ?: DefaultAvailability

            val legacyTargets = json.optJSONArray("goals")?.takeIf { it.length() == 7 }?.let { g -> (0..6).map { g.getInt(it).coerceIn(0, 240) } }
            val goals = json.optJSONArray("practiceGoals")?.let { array -> (0 until array.length()).mapNotNull { i -> runCatching { decodeGoal(array.getJSONObject(i)) }.getOrNull() } }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(legacyTargets?.let { migrateGoal(PracticeGoal("primary", "Klavier"), it, null, 240) } ?: PracticeGoal("primary", "Klavier"))

            return CadenceState(sessions = sessions, practiceGoals = goals, availability = availability, timer = t,
                focus = json.optString("focus", DEFAULT_FOCUS), focusMinutes = json.optInt("focusMinutes", 25).coerceIn(5, 180),
                theme = AppTheme.entries.firstOrNull { it.name == json.optString("theme") } ?: AppTheme.SYSTEM, dynamicColors = json.optBoolean("dynamic"),
                reminders = ReminderSettings.fromJson(legacyReminders), haptics = json.optBoolean("haptics", true), activeGoalId = json.optString("activeGoalId", goals.first().id),
                language = AppLanguage.entries.firstOrNull { it.name == json.optString("language") } ?: AppLanguage.SYSTEM, onboarded = json.optBoolean("onboarded"),
                completionSound = json.optBoolean("completionSound", true), liveTimer = json.optBoolean("liveTimer", true))
        }

        private fun decodeGoal(p: JSONObject): PracticeGoal {
            val base = PracticeGoal(p.getString("id"), p.getString("title"), GoalKind.entries.firstOrNull { it.name == p.optString("kind") } ?: GoalKind.OTHER, createdOn = p.optString("createdOn", LocalDate.now().toString()))
            val deadline = if (p.isNull("deadline")) null else p.optString("deadline").takeIf { it.isNotBlank() }
            if (!p.has("type")) {
                val targets = p.optJSONArray("targets")?.takeIf { it.length() == 7 }?.let { t -> (0..6).map { t.getInt(it).coerceIn(0, 240) } } ?: List(7) { 0 }
                return migrateGoal(base, targets, deadline, p.optInt("totalTarget", 240))
            }
            val days = p.optJSONArray("days")?.let { d -> (0 until d.length()).map { d.getInt(it) }.filter { it in 0..6 }.toSet() }?.takeIf { it.isNotEmpty() } ?: (0..6).toSet()
            return base.copy(type = GoalType.entries.firstOrNull { it.name == p.optString("type") } ?: GoalType.WEEKLY,
                minutes = p.optInt("minutes", 100).coerceIn(1, 99999), perDay = p.optBoolean("perDay"), days = days,
                resetDay = p.optInt("resetDay", 0).coerceIn(0, 6), resetMinute = p.optInt("resetMinute", 0).coerceIn(0, 1439),
                deadline = deadline, deadlineMinute = if (p.isNull("deadlineMinute") || !p.has("deadlineMinute")) null else p.getInt("deadlineMinute").coerceIn(0, 1439))
        }

        /** Version 1 kept a fixed target per weekday; the sum becomes the weekly goal and its days the practice days. */
        private fun migrateGoal(base: PracticeGoal, targets: List<Int>, deadline: String?, total: Int): PracticeGoal {
            val days = targets.indices.filter { targets[it] > 0 }.toSet().ifEmpty { (0..6).toSet() }
            return if (deadline != null) base.copy(type = GoalType.DEADLINE, minutes = total.coerceAtLeast(1), deadline = deadline, days = days)
            else base.copy(type = GoalType.WEEKLY, minutes = targets.sum().takeIf { it > 0 } ?: 100, days = days)
        }

        fun encode(state: CadenceState) = JSONObject().apply {
            put("schemaVersion", 2)
            put("sessions", JSONArray().apply { state.sessions.forEach { s -> put(JSONObject().apply {
                put("id", s.id); put("startedAt", s.startedAt); put("seconds", s.durationSeconds); put("focus", s.focus); put("note", s.note); put("recordedWithTimer", s.recordedWithTimer); put("goalId", s.goalId)
            }) } })
            put("availability", JSONArray().apply { state.availability.forEach { day -> put(JSONArray().apply { day.forEach { put(JSONObject().put("start", it.start).put("end", it.end)) } }) } })
            put("practiceGoals", JSONArray().apply { state.practiceGoals.forEach { p -> put(JSONObject().apply {
                put("id", p.id); put("title", p.title); put("kind", p.kind.name); put("type", p.type.name); put("minutes", p.minutes); put("perDay", p.perDay)
                put("days", JSONArray(p.days.sorted())); put("resetDay", p.resetDay); put("resetMinute", p.resetMinute)
                put("deadline", p.deadline ?: JSONObject.NULL); put("deadlineMinute", p.deadlineMinute ?: JSONObject.NULL); put("createdOn", p.createdOn)
            }) } })
            put("activeGoalId", state.activeGoalId); put("language", state.language.name); put("onboarded", state.onboarded)
            put("completionSound", state.completionSound); put("liveTimer", state.liveTimer)
            put("timer", state.timer?.let { t -> JSONObject().apply {
                put("startedAt", t.sessionStartedAt); put("runningSince", t.runningSince ?: JSONObject.NULL)
                put("accumulated", t.accumulatedMillis); put("focus", t.focus); put("target", t.targetMinutes); put("goalId", t.goalId)
            } } ?: JSONObject.NULL)
            put("focus", state.focus); put("focusMinutes", state.focusMinutes)
            put("theme", state.theme.name); put("dynamic", state.dynamicColors)
            put("reminders", state.reminders.toJson()); put("haptics", state.haptics)
        }
    }
}

class PracticeViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PracticeStore(application)
    private var readError = false
    var state by mutableStateOf(runCatching { store.load() }.getOrElse { readError = true; CadenceState() })
        private set
    var now by mutableLongStateOf(System.currentTimeMillis())
        private set
    var celebration by mutableStateOf<Celebration?>(null)
        private set
    fun clearCelebration() { celebration = null }
    var message by mutableStateOf(if (readError) say("Deine gespeicherten Daten konnten nicht geladen werden. Bitte starte die App neu.", "Your saved data could not be loaded. Please restart the app.") else null)
        private set
    private val observer = store.observe { runCatching { store.load() }.onSuccess { state = it; tick() } }
    override fun onCleared() { observer.close(); super.onCleared() }
    private fun say(de: String, en: String) = wordsNow(de, en, state.language)
    fun tick() { now = System.currentTimeMillis() }
    fun notify(message: String) { this.message = message }
    fun clearMessage() { message = null }
    private fun update(next: CadenceState): Boolean {
        if (readError) { notify(say("Deine Daten bleiben zum Schutz unverändert.", "Your data is left untouched for safety.")); return false }
        return runCatching { store.save(next) }.onSuccess {
            state = next
            val app = getApplication<Application>()
            runCatching { ReminderScheduler.refresh(app, next) }
            runCatching { CadenceWidget.updateAll(app) }
            runCatching { TimerNotifications.sync(app, next) }
        }.onFailure {
            notify(say("Speichern hat nicht geklappt. Bitte versuch es noch einmal.", "Saving didn't work. Please try again."))
        }.isSuccess
    }
    fun start(goalId: String? = null) {
        if (state.timer != null) return
        tick()
        val goal = state.goal(goalId) ?: state.activeGoal
        val remaining = Planner.plan(state, goal, java.time.ZonedDateTime.now()).todayRemaining
        val target = if (remaining in 5..180) remaining else state.focusMinutes
        update(state.copy(activeGoalId = goal.id, timer = PracticeTimer(now, now, focus = state.focus, targetMinutes = target, goalId = goal.id)))
    }
    fun togglePause() {
        tick()
        val timer = state.timer ?: return
        update(state.copy(timer = if (timer.isRunning) timer.pause(now) else timer.resume(now)))
    }
    fun finish() {
        tick()
        val timer = state.timer ?: return
        val seconds = timer.elapsedMillis(now) / 1000
        if (seconds < 1) { notify(say("Die Session ist noch keine Sekunde lang.", "The session is shorter than a second.")); return }
        val session = PracticeSession(startedAt = timer.sessionStartedAt, durationSeconds = seconds, focus = timer.focus, goalId = timer.goalId)
        val before = state
        if (update(state.copy(sessions = state.sessions + session, timer = null))) {
            CompletionSound.play(getApplication(), state.completionSound)
            celebration = celebrationFor(before, state, session)
            celebration?.let { Celebrate.vibrate(getApplication(), state.haptics, it.big) }
            notify(finishMessage(state, session))
        }
    }
    fun discardTimer() { update(state.copy(timer = null)) }
    fun setFocus(focus: String, minutes: Int) {
        update(state.copy(focus = focus.trim().ifEmpty { DEFAULT_FOCUS }.take(60), focusMinutes = minutes.coerceIn(5, 180)))
    }
    fun saveSession(existing: PracticeSession?, date: LocalDate, duration: Long, focus: String, note: String, goalId: String) {
        require(duration in 1..86400 && date <= LocalDate.now())
        val start = if (existing != null && existing.date() == date) existing.startedAt
            else date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli().coerceAtMost(System.currentTimeMillis() - duration * 1000)
        val session = PracticeSession(existing?.id ?: UUID.randomUUID().toString(), start, duration, focus.trim().ifEmpty { DEFAULT_FOCUS }.take(60), note.trim().take(500), existing?.recordedWithTimer ?: false, goalId)
        val before = state
        if (update(state.copy(sessions = state.sessions.filterNot { it.id == session.id } + session))) {
            if (existing == null && date == LocalDate.now()) {
                celebration = celebrationFor(before, state, session)
                celebration?.let { Celebrate.vibrate(getApplication(), state.haptics, it.big) }
                notify(finishMessage(state, session))
            } else notify(say("Gespeichert", "Saved"))
        }
    }
    fun deleteSession(session: PracticeSession) {
        if (update(state.copy(sessions = state.sessions.filterNot { it.id == session.id }))) notify(say("Session gelöscht", "Session deleted"))
    }
    fun setTheme(theme: AppTheme) { update(state.copy(theme = theme)) }
    fun setDynamicColors(enabled: Boolean) { update(state.copy(dynamicColors = enabled)) }
    fun setHaptics(enabled: Boolean) { update(state.copy(haptics = enabled)) }
    fun setReminders(settings: ReminderSettings) { update(state.copy(reminders = settings)) }
    fun selectGoal(id: String) { if (state.practiceGoals.any { it.id == id } && id != state.activeGoalId) update(state.copy(activeGoalId = id)) }
    fun setLanguage(language: AppLanguage) { update(state.copy(language = language)) }
    fun saveGoal(goal: PracticeGoal) {
        require(goal.title.isNotBlank())
        val exists = state.practiceGoals.any { it.id == goal.id }
        if (update(state.copy(practiceGoals = if (exists) state.practiceGoals.map { if (it.id == goal.id) goal else it } else state.practiceGoals + goal, activeGoalId = goal.id)))
            notify(say("Ziel gespeichert", "Goal saved"))
    }
    /** Removes a goal and its sessions. The last goal cannot be removed. */
    fun deleteGoal(goal: PracticeGoal) {
        if (state.practiceGoals.size < 2) return
        val rest = state.practiceGoals.filterNot { it.id == goal.id }
        if (update(state.copy(practiceGoals = rest, sessions = state.sessions.filterNot { it.goalId == goal.id },
                timer = state.timer?.takeIf { it.goalId != goal.id }, activeGoalId = if (state.activeGoalId == goal.id) rest.first().id else state.activeGoalId)))
            notify(say("„${goal.title}“ gelöscht", "Deleted “${goal.title}”"))
    }
    fun setAvailability(availability: List<List<TimeSlot>>) {
        require(availability.size == 7)
        update(state.copy(availability = availability.map { day -> day.filter { it.minutes > 0 }.sortedBy { it.start } }))
    }
    /** Saves the goals from setup: existing ones are replaced by id, new ones appended. The first becomes active. */
    fun finishOnboarding(goals: List<PracticeGoal>, availability: List<List<TimeSlot>>, reminders: Boolean) {
        require(goals.isNotEmpty())
        val ids = goals.map { it.id }.toSet()
        val merged = state.practiceGoals.map { old -> goals.firstOrNull { it.id == old.id } ?: old } + goals.filter { g -> state.practiceGoals.none { it.id == g.id } }
        update(state.copy(practiceGoals = merged.filter { it.title.isNotBlank() || it.id !in ids }, activeGoalId = goals.first().id, availability = availability, onboarded = true,
            reminders = state.reminders.copy(enabled = reminders)))
    }
    fun replayOnboarding() { update(state.copy(onboarded = false)) }
    fun setCompletionSound(enabled: Boolean) { update(state.copy(completionSound = enabled)) }
    fun setLiveTimer(enabled: Boolean) { update(state.copy(liveTimer = enabled)) }
}

data class Celebration(val title: String, val body: String, val big: Boolean, val streak: Int = 0)

private val StreakMilestones = setOf(3, 7, 14, 21, 30, 50, 75, 100, 150, 200, 365)

/** A moment worth marking: finishing today's share, reaching the goal, or a streak milestone. */
fun celebrationFor(before: CadenceState, after: CadenceState, session: PracticeSession): Celebration? {
    val goal = after.goal(session.goalId) ?: return null
    val now = java.time.ZonedDateTime.now()
    val old = Planner.plan(before, goal, now)
    val new = Planner.plan(after, goal, now)
    val today = now.toLocalDate()
    val oldStreak = Planner.streak(before, today)
    val newStreak = Planner.streak(after, today)
    val l = after.language
    return when {
        old.status != DayStatus.GOAL_REACHED && new.status == DayStatus.GOAL_REACHED -> if (goal.type == GoalType.WEEKLY)
            Celebration(wordsNow("Wochenziel geschafft!", "Weekly goal done!", l), wordsNow("${new.done} Minuten ${goal.title} diese Woche.", "${new.done} minutes of ${goal.title} this week.", l), true, newStreak)
            else Celebration(wordsNow("Ziel erreicht!", "Goal reached!", l), wordsNow("${new.done} Minuten für ${goal.title}. Du bist bereit.", "${new.done} minutes for ${goal.title}. You're ready.", l), true, newStreak)
        newStreak > oldStreak && newStreak in StreakMilestones -> Celebration(wordsNow("$newStreak Tage in Folge!", "$newStreak days in a row!", l), wordsNow("Dranbleiben zahlt sich aus.", "Showing up pays off.", l), true, newStreak)
        old.status == DayStatus.TODO && new.status == DayStatus.DONE_TODAY -> Celebration(wordsNow("Für heute geschafft", "Done for today", l),
            if (new.doneToday > new.todayTarget + 5) wordsNow("${new.doneToday} statt ${new.todayTarget} Minuten. Das nimmt dir morgen etwas ab.", "${new.doneToday} instead of ${new.todayTarget} minutes. Tomorrow gets lighter.", l)
            else wordsNow("${new.doneToday} Minuten ${goal.title}.", "${new.doneToday} minutes of ${goal.title}.", l), false, newStreak)
        else -> null
    }
}

/** Short, specific feedback after a session, based on what it changed in the plan. */
fun finishMessage(state: CadenceState, session: PracticeSession): String {
    val goal = state.goal(session.goalId) ?: return formatDuration(session.durationSeconds)
    val plan = Planner.plan(state, goal, java.time.ZonedDateTime.now())
    val minutes = (session.durationSeconds / 60).toInt()
    val language = state.language
    return when {
        plan.status == DayStatus.GOAL_REACHED -> if (goal.type == GoalType.WEEKLY) wordsNow("$minutes Min. gespeichert. Dein Wochenziel ist erreicht!", "$minutes min saved. You've reached your weekly goal!", language)
            else wordsNow("$minutes Min. gespeichert. Ziel erreicht!", "$minutes min saved. Goal reached!", language)
        plan.doneToday >= plan.todayTarget && plan.todayTarget > 0 && plan.doneToday >= plan.todayTarget + 10 -> wordsNow("$minutes Min. gespeichert. Mehr als geplant, das nimmt dir in den nächsten Tagen etwas ab.", "$minutes min saved. More than planned, so the next days get lighter.", language)
        plan.doneToday >= plan.todayTarget && plan.todayTarget > 0 -> wordsNow("$minutes Min. gespeichert. Für heute geschafft.", "$minutes min saved. Done for today.", language)
        plan.todayRemaining > 0 -> wordsNow("$minutes Min. gespeichert. Noch ${plan.todayRemaining} Min. für heute.", "$minutes min saved. ${plan.todayRemaining} min left for today.", language)
        else -> wordsNow("$minutes Min. gespeichert.", "$minutes min saved.", language)
    }.let { if (session.durationSeconds < 60) wordsNow("Gespeichert (${session.durationSeconds} Sek.)", "Saved (${session.durationSeconds} sec)", language) else it }
}

fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = seconds / 60 % 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%02d:%02d".format(minutes, rest)
}

fun clock(minute: Int) = "%02d:%02d".format((minute / 60) % 24, minute % 60).let { if (minute == 1440) "24:00" else it }

fun exportCsv(sessions: List<PracticeSession>, goals: List<PracticeGoal> = emptyList(), english: Boolean = false): String {
    fun quote(value: String) = "\"${value.replace("\"", "\"\"")}\""
    return (if (english) "\uFEFFDate,Start,Duration_seconds,Duration_minutes,Focus,Note,Goal\r\n" else "\uFEFFDatum,Startzeit,Dauer_Sekunden,Dauer_Minuten,Fokus,Notiz,Ziel\r\n") +
        sessions.sortedBy { it.startedAt }.joinToString("\r\n") { s ->
            val time = Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault())
            // Prevent spreadsheet applications from treating user notes as formulas.
            fun safe(value: String) = quote(if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value)
            listOf(time.toLocalDate().toString(), time.toLocalTime().withNano(0).toString(), s.durationSeconds.toString(),
                java.lang.String.format(java.util.Locale.ROOT, "%.2f", s.durationSeconds / 60.0), safe(translate(s.focus, english)), safe(s.note), safe(goals.firstOrNull { it.id == s.goalId }?.title ?: s.goalId)).joinToString(",")
        }
}
