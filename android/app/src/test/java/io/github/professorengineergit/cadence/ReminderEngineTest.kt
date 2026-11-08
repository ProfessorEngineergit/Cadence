package io.github.professorengineergit.cadence

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderEngineTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val sunday = LocalDate.of(2026, 11, 8)
    private val now = sunday.atTime(16, 0).atZone(zone)
    private fun state() = CadenceState(language = AppLanguage.DE, reminders = ReminderSettings(enabled = true))
    private fun session(day: LocalDate, minutes: Int, hour: Int = 12, goal: String = "primary", timed: Boolean = true) = PracticeSession(startedAt = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(), durationSeconds = minutes * 60L, goalId = goal, recordedWithTimer = timed)
    private fun decision(s: CadenceState = state(), at: ZonedDateTime = now, delivered: ReminderDelivery = ReminderDelivery()) = ReminderEngine.evaluateAll(s, at, delivered)

    @Test fun remindsInsideAvailableHoursWithRealMissingPercentage() {
        val result = decision(state().copy(sessions = listOf(session(sunday.minusDays(2), 70)))).notification!!
        assertTrue(result.body.contains("30 %"))
        assertTrue(result.body.contains("30 Min."))
        assertTrue(result.suggestion in 1..25)
    }
    @Test fun neverRemindsOutsideWindowOrWhenDisabled() {
        assertNull(decision(at = now.withHour(23)).notification)
        assertNull(decision(state().copy(reminders = ReminderSettings(enabled = false))).notification)
    }
    @Test fun stopsWhenWeeklyGoalIsMet() {
        assertNull(decision(state().copy(sessions = listOf(session(sunday.minusDays(1), 100)))).notification)
    }
    @Test fun activeAndPausedTimersBothSuppressReminders() {
        val active = PracticeTimer(10, 10, focus = "Freies Üben", targetMinutes = 25)
        assertNull(decision(state().copy(timer = active)).notification)
        assertNull(decision(state().copy(timer = active.copy(runningSince = null))).notification)
    }
    @Test fun recentPracticeForAnotherGoalAlsoSuppressesReminders() {
        val s = state().copy(sessions = listOf(session(sunday, 20, 15, goal = "other")))
        assertNull(decision(s).notification)
    }
    @Test fun dailyCapIsSharedAndCooldownIsThreeHours() {
        val timestamp = now.minusHours(1).toInstant().toEpochMilli()
        assertNull(decision(delivered = ReminderDelivery(lastSent = timestamp, date = sunday.toString(), count = 1)).notification)
        assertNull(decision(delivered = ReminderDelivery(date = sunday.toString(), count = 2)).notification)
        assertNotNull(decision(delivered = ReminderDelivery(date = sunday.minusDays(1).toString(), count = 2)).notification)
    }
    @Test fun snoozeAndRestDayActionsAreHonoured() {
        val stamp = now.toInstant().toEpochMilli()
        assertNull(decision(delivered = ReminderDelivery(snoozeUntil = stamp + 1000)).notification)
        assertNull(decision(delivered = ReminderDelivery(skipUntil = stamp + 1000)).notification)
        assertNotNull(decision(delivered = ReminderDelivery(snoozeUntil = stamp - 1000, date = sunday.toString(), count = 2)).notification)
    }
    @Test fun extraPracticeYesterdayGetsEncouragingCopyAndCountsTowardToday() {
        val result = decision(state().copy(sessions = listOf(session(sunday.minusDays(1), 45)))).notification!!
        assertTrue(result.title.contains("Gestern stark"))
        assertTrue(result.body.contains("45 Minuten"))
    }
    @Test fun aheadOfPlanMeansNoPressureOnFollowingPracticeDay() {
        val day = LocalDate.of(2026, 11, 4) // Wednesday's 30-minute target
        val s = state().copy(sessions = listOf(session(day.minusDays(1), 40)))
        assertNull(decision(s, day.atTime(17, 0).atZone(zone)).notification)
    }
    @Test fun restDaysAreQuietUnlessCheckInIsClose() {
        val monday = sunday.minusDays(6)
        assertNull(decision(at = monday.atTime(17, 0).atZone(zone)).notification)
    }
    @Test fun learnsMedianPracticeTimeOnlyFromTimerSessions() {
        val day = sunday.minusDays(4)
        val s = state().copy(sessions = listOf(session(day, 10, 18), session(day.minusDays(1), 10, 17), session(day.minusDays(2), 10, 19), session(day.minusDays(3), 10, 12, timed = false)))
        assertEquals(18 * 60, ReminderEngine.preferredMinute(s, day, Availability(16 * 60, 20 * 60), zone))
    }
    @Test fun goalsHaveIndependentProgressAndClosestDeadlineWins() {
        val exam = PracticeGoal("exam", "Mathetest", GoalKind.STUDY, dayTargets = List(7) { 15 }, deadline = sunday.plusDays(1).toString(), totalTarget = 200)
        val s = state().copy(practiceGoals = state().practiceGoals + exam, sessions = listOf(session(sunday.minusDays(1), 100)))
        val result = decision(s).notification!!
        assertTrue(result.title.startsWith("Mathetest"))
        assertTrue(result.body.contains("100 %"))
        assertEquals(0, s.copy(activeGoalId = "exam").selectedSessions.size)
    }
    @Test fun completedAndExpiredDatedGoalsDoNotNotify() {
        val goal = PracticeGoal("exam", "Test", GoalKind.STUDY, dayTargets = List(7) { 15 }, deadline = sunday.toString(), totalTarget = 30)
        val s = state().copy(practiceGoals = listOf(goal), activeGoalId = "exam", sessions = listOf(session(sunday.minusDays(1), 30, goal = "exam")))
        assertNull(decision(s).notification)
        assertNull(decision(s.copy(sessions = emptyList(), practiceGoals = listOf(goal.copy(deadline = sunday.minusDays(1).toString())))).notification)
    }
    @Test fun englishRemindersContainEnglishCopy() {
        val result = decision(state().copy(language = AppLanguage.EN)).notification!!
        assertTrue(result.body.contains("weekly goal"))
        assertFalse(result.body.contains("Minuten"))
    }
    @Test fun datedGoalUsesRemainingPaceRatherThanStoppingAtTooSmallDailyTarget() {
        val monday = LocalDate.of(2026, 11, 9)
        val goal = PracticeGoal("exam", "Test", GoalKind.STUDY, List(7) { 20 }, monday.plusDays(2).toString(), 240)
        val s = state().copy(practiceGoals = listOf(goal), activeGoalId = "exam", sessions = listOf(session(monday, 20, 12, "exam")))
        assertNotNull(decision(s, monday.atTime(19, 0).atZone(zone)).notification)
    }
}
