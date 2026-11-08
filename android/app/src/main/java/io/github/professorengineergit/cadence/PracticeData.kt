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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class PracticeSession(
    val id: String = UUID.randomUUID().toString(),
    val startedAt: Long,
    val durationSeconds: Long,
    val focus: String = "Freies Üben",
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
enum class GoalKind { MUSIC, STUDY, OTHER }
enum class AppLanguage { SYSTEM, DE, EN }

data class PracticeGoal(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val kind: GoalKind = GoalKind.MUSIC,
    val dayTargets: List<Int> = listOf(0, 0, 30, 0, 30, 10, 30),
    val deadline: String? = null,
    val totalTarget: Int = 240,
    val createdOn: String = LocalDate.now().toString(),
) {
    val dueDate get() = deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

data class CadenceState(
    val sessions: List<PracticeSession> = emptyList(),
    // Monday first; the original app's 100-minute practice plan.
    val practiceGoals: List<PracticeGoal> = listOf(PracticeGoal(id = "primary", title = "Klavier")),
    val timer: PracticeTimer? = null,
    val focus: String = "Freies Üben",
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
    val goals get() = activeGoal.dayTargets
    val selectedSessions get() = sessions.filter { it.goalId == activeGoal.id }
}

class PracticeStats(
    val sessions: List<PracticeSession>,
    val goals: List<Int>,
    val today: LocalDate = LocalDate.now(),
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val weekStart: LocalDate = today.with(DayOfWeek.MONDAY)
    fun secondsOn(date: LocalDate): Long = sessions.filter { it.date(zone) == date }.sumOf { it.durationSeconds }
    fun minutesOn(date: LocalDate): Long = secondsOn(date) / 60
    fun goalOn(date: LocalDate): Int = goals[date.dayOfWeek.value - 1]
    val todayMinutes get() = minutesOn(today)
    val todayGoal get() = goalOn(today)
    val weekSessions get() = sessions.filter { it.date(zone) >= weekStart && it.date(zone) < weekStart.plusDays(7) }
    val weekMinutes get() = weekSessions.sumOf { it.durationSeconds } / 60
    val weeklyGoal get() = goals.sum()
    val lifetimeMinutes get() = sessions.sumOf { it.durationSeconds } / 60
    val activeDaysThisWeek get() = weekSessions.filter { it.durationSeconds > 0 }.map { it.date(zone) }.distinct().size
    val balanceMinutes: Long get() {
        val days = (0 until today.dayOfWeek.value - 1).map { weekStart.plusDays(it.toLong()) }
        return days.sumOf { secondsOn(it) } / 60 - days.sumOf { goalOn(it) }
    }
    val streak: Int get() {
        val dates = sessions.filter { it.durationSeconds > 0 }.map { it.date(zone) }.toSet()
        var cursor = if (today in dates) today else today.minusDays(1)
        var count = 0
        while (cursor in dates) { count++; cursor = cursor.minusDays(1) }
        return count
    }
}

internal class PracticeStore(context: Context) {
    private val prefs = context.getSharedPreferences("cadence", Context.MODE_PRIVATE)
    fun observe(onChange: () -> Unit): java.io.Closeable {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "state") onChange() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return java.io.Closeable { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun load(): CadenceState {
        val raw = prefs.getString("state", null) ?: return CadenceState()
        val json = JSONObject(raw)
        val entries = json.optJSONArray("sessions") ?: JSONArray()
        val sessions = (0 until entries.length()).map { i ->
            val s = entries.getJSONObject(i)
            PracticeSession(s.getString("id"), s.getLong("startedAt"), s.getLong("seconds"), s.optString("focus", "Freies Üben"), s.optString("note"), s.optBoolean("recordedWithTimer", true), s.optString("goalId", "primary"))
        }
        val g = json.optJSONArray("goals")
        val goals = if (g != null && g.length() == 7) (0..6).map { g.getInt(it).coerceIn(0, 240) } else CadenceState().goals
        val t = json.optJSONObject("timer")?.let {
            PracticeTimer(it.getLong("startedAt"), if (it.isNull("runningSince")) null else it.getLong("runningSince"), it.optLong("accumulated"), it.getString("focus"), it.getInt("target"), it.optString("goalId", "primary"))
        }
        val projects = json.optJSONArray("practiceGoals")?.let { array -> (0 until array.length()).map { i ->
            val p = array.getJSONObject(i)
            val targets = p.getJSONArray("targets")
            require(targets.length() == 7)
            PracticeGoal(p.getString("id"), p.getString("title"), GoalKind.entries.firstOrNull { it.name == p.optString("kind") } ?: GoalKind.MUSIC,
                (0..6).map { targets.getInt(it).coerceIn(0, 240) }, if (p.isNull("deadline")) null else p.getString("deadline"), p.optInt("totalTarget", 240), p.optString("createdOn", LocalDate.now().toString()))
        } }?.takeIf { it.isNotEmpty() } ?: listOf(PracticeGoal("primary", "Klavier", dayTargets = goals))
        return CadenceState(sessions = sessions, practiceGoals = projects, timer = t, focus = json.optString("focus", "Freies Üben"), focusMinutes = json.optInt("focusMinutes", 25),
            theme = AppTheme.entries.firstOrNull { it.name == json.optString("theme") } ?: AppTheme.SYSTEM, dynamicColors = json.optBoolean("dynamic"),
            reminders = ReminderSettings.fromJson(json.optJSONObject("reminders")), haptics = json.optBoolean("haptics", true), activeGoalId = json.optString("activeGoalId", "primary"),
            language = AppLanguage.entries.firstOrNull { it.name == json.optString("language") } ?: AppLanguage.SYSTEM, onboarded = json.optBoolean("onboarded"), completionSound = json.optBoolean("completionSound", true), liveTimer = json.optBoolean("liveTimer", true))
    }
    fun save(state: CadenceState) {
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("sessions", JSONArray().apply { state.sessions.forEach { s -> put(JSONObject().apply {
                put("id", s.id); put("startedAt", s.startedAt); put("seconds", s.durationSeconds); put("focus", s.focus); put("note", s.note); put("recordedWithTimer", s.recordedWithTimer); put("goalId", s.goalId)
            }) } })
            put("goals", JSONArray(state.goals))
            put("practiceGoals", JSONArray().apply { state.practiceGoals.forEach { p -> put(JSONObject().apply {
                put("id", p.id); put("title", p.title); put("kind", p.kind.name); put("targets", JSONArray(p.dayTargets)); put("deadline", p.deadline ?: JSONObject.NULL); put("totalTarget", p.totalTarget); put("createdOn", p.createdOn)
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
        check(prefs.edit().putString("state", json.toString()).commit()) { "Speichern fehlgeschlagen" }
    }
}

class PracticeViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PracticeStore(application)
    private var readError = false
    var state by mutableStateOf(runCatching { store.load() }.getOrElse { readError = true; CadenceState() })
        private set
    var now by mutableLongStateOf(System.currentTimeMillis())
        private set
    var message by mutableStateOf(if (readError) "Die gespeicherten Daten konnten nicht geladen werden. Bitte die App neu starten." else null)
        private set
    private val observer = store.observe { runCatching { store.load() }.onSuccess { state = it; tick() } }
    override fun onCleared() { observer.close(); super.onCleared() }
    fun tick() { now = System.currentTimeMillis() }
    fun notify(message: String) { this.message = message }
    fun clearMessage() { message = null }
    private fun update(next: CadenceState): Boolean {
        if (readError) { notify("Die gespeicherten Daten bleiben zum Schutz unverändert."); return false }
        val previous = state
        return runCatching { store.save(next) }.onSuccess {
            state = next
            runCatching { ReminderScheduler.ensure(getApplication(), next.reminders.enabled) }
            runCatching { CadenceWidget.updateAll(getApplication()) }
            runCatching { TimerNotifications.sync(getApplication(), next) }
            // Re-evaluate the visible reminder immediately after practising or changing the plan.
            if (next.sessions != previous.sessions || next.timer != null || !next.reminders.enabled) ReminderScheduler.dismiss(getApplication())
        }.onFailure {
            notify("Speichern fehlgeschlagen. Bitte erneut versuchen.")
        }.isSuccess
    }
    fun start() {
        if (state.timer != null) return
        tick()
        update(state.copy(timer = PracticeTimer(now, now, focus = state.focus, targetMinutes = state.focusMinutes, goalId = state.activeGoal.id)))
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
        if (seconds < 1) { notify("Deine Session ist noch kürzer als eine Sekunde."); return }
        val session = PracticeSession(startedAt = timer.sessionStartedAt, durationSeconds = seconds, focus = timer.focus, goalId = timer.goalId)
        if (update(state.copy(sessions = state.sessions + session, timer = null))) { CompletionSound.play(getApplication(), state.completionSound); notify("Session gespeichert · ${formatDuration(seconds)}") }
    }
    fun discardTimer() { update(state.copy(timer = null)) }
    fun setFocus(focus: String, minutes: Int) {
        update(state.copy(focus = focus.trim().ifEmpty { "Freies Üben" }.take(60), focusMinutes = minutes.coerceIn(5, 120)))
    }
    fun saveSession(existing: PracticeSession?, date: LocalDate, duration: Long, focus: String, note: String) {
        require(duration in 1..86400 && date <= LocalDate.now())
        val start = if (existing != null && existing.date() == date) existing.startedAt
            else date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val session = PracticeSession(existing?.id ?: UUID.randomUUID().toString(), start, duration, focus.trim().ifEmpty { "Freies Üben" }.take(60), note.trim().take(500), existing?.recordedWithTimer ?: false, existing?.goalId ?: state.activeGoal.id)
        if (update(state.copy(sessions = state.sessions.filterNot { it.id == session.id } + session))) notify("Session gespeichert")
    }
    fun deleteSession(session: PracticeSession) {
        if (update(state.copy(sessions = state.sessions.filterNot { it.id == session.id }))) notify("Session gelöscht")
    }
    fun setGoals(goals: List<Int>) {
        require(goals.size == 7 && goals.all { it in 0..240 })
        if (update(state.copy(practiceGoals = state.practiceGoals.map { if (it.id == state.activeGoal.id) it.copy(dayTargets = goals.toList()) else it }))) notify("Dein Wochenrhythmus ist gespeichert")
    }
    fun setTheme(theme: AppTheme) { update(state.copy(theme = theme)) }
    fun setDynamicColors(enabled: Boolean) { update(state.copy(dynamicColors = enabled)) }
    fun setHaptics(enabled: Boolean) { update(state.copy(haptics = enabled)) }
    fun setReminders(settings: ReminderSettings) { update(state.copy(reminders = settings)) }
    fun selectGoal(id: String) { if (state.practiceGoals.any { it.id == id }) update(state.copy(activeGoalId = id)) }
    fun setLanguage(language: AppLanguage) { update(state.copy(language = language)) }
    fun saveGoal(goal: PracticeGoal) {
        require(goal.title.isNotBlank() && goal.dayTargets.size == 7)
        val exists = state.practiceGoals.any { it.id == goal.id }
        if (update(state.copy(practiceGoals = if (exists) state.practiceGoals.map { if (it.id == goal.id) goal else it } else state.practiceGoals + goal, activeGoalId = goal.id))) notify("Übeziel gespeichert")
    }
    fun setDayPlan(index: Int, minutes: Int, window: Availability) {
        require(index in 0..6 && minutes in 0..240 && minutes <= window.minutes)
        val goals = state.goals.toMutableList().also { it[index] = minutes }
        val windows = state.reminders.windows.toMutableList().also { it[index] = window }
        if (update(state.copy(practiceGoals = state.practiceGoals.map { if (it.id == state.activeGoal.id) it.copy(dayTargets = goals) else it }, reminders = state.reminders.copy(windows = windows)))) notify("Tagesplan gespeichert")
    }
    fun finishOnboarding(title: String, kind: GoalKind, weeklyMinutes: Int, days: Set<Int>, weekday: Availability, weekend: Availability) {
        val windows = List(7) { if (it < 5) weekday else weekend }
        val first = state.activeGoal.copy(title = title.trim().ifEmpty { "Klavier" }, kind = kind, dayTargets = distributeWeeklyGoal(weeklyMinutes, days, windows))
        update(state.copy(practiceGoals = state.practiceGoals.map { if (it.id == first.id) first else it }, reminders = state.reminders.copy(windows = windows), onboarded = true))
    }
    fun replayOnboarding() { update(state.copy(onboarded = false)) }
    fun setCompletionSound(enabled: Boolean) { update(state.copy(completionSound = enabled)) }
    fun setLiveTimer(enabled: Boolean) { update(state.copy(liveTimer = enabled)) }
    fun setWeeklyPlan(total: Int, days: Set<Int>) {
        val windows = state.reminders.windows.mapIndexed { i, w -> if (i in days && !w.enabled) w.copy(enabled = true) else w }
        val targets = distributeWeeklyGoal(total, days, windows)
        if (update(state.copy(practiceGoals = state.practiceGoals.map { if (it.id == state.activeGoal.id) it.copy(dayTargets = targets) else it }, reminders = state.reminders.copy(windows = windows)))) notify("Dein Wochenrhythmus ist gespeichert")
    }
}

fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = seconds / 60 % 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%02d:%02d".format(minutes, rest)
}

fun distributeWeeklyGoal(total: Int, days: Set<Int>, windows: List<Availability>): List<Int> {
    require(windows.size == 7 && days.all { it in 0..6 })
    val capacity = List(7) { (windows[it].end - windows[it].start).coerceIn(0, 240) }
    require(total >= 0 && total <= days.sumOf { capacity[it] })
    val result = MutableList(7) { 0 }
    var remaining = total
    while (remaining > 0) for (day in days.sorted()) {
        val add = minOf(5, remaining, capacity[day] - result[day])
        result[day] += add; remaining -= add
    }
    return result
}

fun exportCsv(sessions: List<PracticeSession>, goals: List<PracticeGoal> = emptyList(), english: Boolean = false): String {
    fun quote(value: String) = "\"${value.replace("\"", "\"\"")}\""
    return (if (english) "\uFEFFDate,Start,Duration_seconds,Duration_minutes,Focus,Note,Goal\r\n" else "\uFEFFDatum,Startzeit,Dauer_Sekunden,Dauer_Minuten,Fokus,Notiz,Ziel\r\n") +
        sessions.sortedBy { it.startedAt }.joinToString("\r\n") { s ->
            val time = Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault())
            // Prevent spreadsheet applications from treating user notes as formulas.
            fun safe(value: String) = quote(if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value)
            listOf(time.toLocalDate().toString(), time.toLocalTime().withNano(0).toString(), s.durationSeconds.toString(),
                java.lang.String.format(java.util.Locale.ROOT, "%.2f", s.durationSeconds / 60.0), safe(s.focus), safe(s.note), safe(goals.firstOrNull { it.id == s.goalId }?.title ?: s.goalId)).joinToString(",")
        }
}
