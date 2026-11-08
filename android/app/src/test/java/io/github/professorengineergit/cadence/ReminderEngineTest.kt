package io.github.professorengineergit.cadence

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderEngineTest {
    private val zone = ZoneId.systemDefault()
    private val monday = LocalDate.of(2026, 11, 2)
    private fun at(day: LocalDate, hour: Int, minute: Int = 0): ZonedDateTime = day.atTime(hour, minute).atZone(zone)
    private fun ms(t: ZonedDateTime) = t.toInstant().toEpochMilli()
    private fun session(day: LocalDate, minutes: Int, hour: Int = 17, minute: Int = 0, timed: Boolean = true, goal: String = "primary") =
        PracticeSession(startedAt = ms(at(day, hour, minute)), durationSeconds = minutes * 60L, goalId = goal, recordedWithTimer = timed)
    private val evenings = List(7) { listOf(TimeSlot(17 * 60, 19 * 60)) }
    private fun state(sessions: List<PracticeSession> = emptyList(), availability: List<List<TimeSlot>> = evenings, language: AppLanguage = AppLanguage.DE, goals: List<PracticeGoal> = listOf(PracticeGoal("primary", "Klavier", minutes = 70, createdOn = "2026-10-01"))) =
        CadenceState(sessions = sessions, practiceGoals = goals, availability = availability, reminders = ReminderSettings(enabled = true), language = language, activeGoalId = goals.first().id)

    @Test fun firstReminderComesWhenFreeTimeStartsWithFollowUpLater() {
        val c = ReminderEngine.candidates(state(), monday, at(monday, 9))
        assertEquals(listOf(at(monday, 17) to ReminderKind.START, at(monday, 18) to ReminderKind.FOLLOW_UP), c.map { it.at to it.kind })
        assertEquals(at(monday, 17), ReminderEngine.next(state(), at(monday, 9), ReminderDelivery())!!.at)
    }

    @Test fun dueOnlyWithinToleranceAndNotTwice() {
        val s = state()
        val copy = ReminderEngine.due(s, at(monday, 17, 2), ReminderDelivery())!!
        assertEquals("Klavier: heute 10 Minuten", copy.title)
        assertTrue(copy.body.contains("19:00"))
        assertNull(ReminderEngine.due(s, at(monday, 17, 40), ReminderDelivery()))
        val sent = ReminderEngine.record(ReminderDelivery(), copy, at(monday, 17, 2))
        assertNull(ReminderEngine.due(s, at(monday, 17, 5), sent))
        assertEquals(ReminderKind.FOLLOW_UP, ReminderEngine.next(s, at(monday, 17, 3), sent)!!.kind)
    }

    @Test fun nothingWhenDoneTimerRunningOrJustPractised() {
        assertNull(ReminderEngine.due(state(listOf(session(monday, 15, hour = 8))), at(monday, 17), ReminderDelivery()))
        assertNull(ReminderEngine.due(state().copy(timer = PracticeTimer(1, 1, focus = "x", targetMinutes = 10)), at(monday, 17), ReminderDelivery()))
        // Five minutes of practice that ended twenty minutes ago: still something left, but give it an hour.
        assertNull(ReminderEngine.due(state(listOf(session(monday, 5, hour = 16, minute = 35))), at(monday, 17), ReminderDelivery()))
    }

    @Test fun restDaysAreQuietAndNextReminderMovesToTheNextFreeDay() {
        val slots = List(7) { if (it == 0) emptyList() else listOf(TimeSlot(17 * 60, 18 * 60)) }
        assertTrue(ReminderEngine.candidates(state(availability = slots), monday, at(monday, 9)).isEmpty())
        assertEquals(at(monday.plusDays(1), 17), ReminderEngine.next(state(availability = slots), at(monday, 9), ReminderDelivery())!!.at)
    }

    @Test fun aheadDayGetsOnePraiseNoteInsteadOfAReminder() {
        val tuesday = monday.plusDays(1)
        val s = state(listOf(session(monday, 30)))
        val c = ReminderEngine.candidates(s, tuesday, at(tuesday, 9))
        assertEquals(listOf(ReminderKind.PRAISE), c.map { it.kind })
        val copy = ReminderEngine.due(s, at(tuesday, 17), ReminderDelivery())!!
        assertEquals("Heute ist frei", copy.title)
        assertTrue(copy.body.contains("30 Minuten"))
        val after = ReminderEngine.record(ReminderDelivery(), copy, at(tuesday, 17))
        assertNull(ReminderEngine.due(s, at(tuesday, 17, 1), after))
        assertNull(ReminderEngine.due(s.copy(reminders = s.reminders.copy(praise = false)), at(tuesday, 17), ReminderDelivery()))
    }

    @Test fun snoozeAndNotTodayAreHonoured() {
        val s = state()
        val snooze = ReminderDelivery(snoozeUntil = ms(at(monday, 17, 30)))
        assertEquals(ReminderKind.SNOOZED, ReminderEngine.next(s, at(monday, 17, 5), snooze)!!.kind)
        assertNotNull(ReminderEngine.due(s, at(monday, 17, 31), snooze.copy(date = monday.toString(), count = 2)))
        val skip = ReminderDelivery(skipDate = monday.toString())
        assertNull(ReminderEngine.due(s, at(monday, 17), skip))
        assertEquals(monday.plusDays(1), ReminderEngine.next(s, at(monday, 9), skip)!!.at.toLocalDate())
    }

    @Test fun ignoredRemindersReduceToOnePerDay() {
        val sent = (1..3).map { monday.plusDays(it.toLong()).toString() }
        val friday = monday.plusDays(4)
        val delivery = ReminderDelivery(sentDates = sent, date = friday.toString(), count = 1, lastSent = ms(at(friday, 17)))
        assertEquals(3, ReminderEngine.ignoredDays(state(), delivery, friday))
        assertEquals(1, ReminderEngine.dailyLimit(state(), delivery, friday))
        assertNull(ReminderEngine.due(state(), at(friday, 18), delivery))
        // Practising once resets it.
        assertEquals(0, ReminderEngine.ignoredDays(state(listOf(session(monday.plusDays(3), 10))), delivery, friday))
    }

    @Test fun learnsUsualStartFromTimedSessionsOnly() {
        val wed = monday.plusDays(2)
        val history = listOf(session(monday.minusDays(7), 10, 18, 30), session(monday.minusDays(6), 10, 18, 15), session(monday.minusDays(5), 10, 18, 45), session(monday.minusDays(4), 10, 9, timed = false))
        val c = ReminderEngine.candidates(state(history), wed, at(wed, 9))
        assertEquals(at(wed, 18, 30), c.first().at)
    }

    @Test fun deadlineCopyMentionsTheDate() {
        val talk = PracticeGoal("talk", "Referat", GoalKind.SPEECH, GoalType.DEADLINE, minutes = 60, deadline = monday.plusDays(1).toString(), createdOn = monday.toString())
        val copy = ReminderEngine.due(state(goals = listOf(talk)), at(monday, 17), ReminderDelivery())!!
        assertTrue(copy.body, copy.body.startsWith("Morgen ist es so weit"))
        assertEquals("talk", copy.goalId)
    }

    @Test fun severalGoalsShareOneReminder() {
        val goals = listOf(PracticeGoal("primary", "Klavier", minutes = 70, createdOn = "2026-10-01"), PracticeGoal("b", "Gitarre", minutes = 140, createdOn = "2026-10-01"))
        val copy = ReminderEngine.due(state(goals = goals), at(monday, 17), ReminderDelivery())!!
        assertTrue(copy.title, copy.title.contains("Klavier 10 Min.") && copy.title.contains("Gitarre 20 Min."))
    }

    @Test fun englishCopy() {
        val copy = ReminderEngine.due(state(language = AppLanguage.EN), at(monday, 17), ReminderDelivery())!!
        assertEquals("Klavier: 10 minutes today", copy.title)
        assertFalse(copy.body.contains("Zeit"))
    }

    @Test fun disabledMeansNothing() {
        val s = state().copy(reminders = ReminderSettings(enabled = false))
        assertNull(ReminderEngine.next(s, at(monday, 9), ReminderDelivery()))
        assertNull(ReminderEngine.due(s, at(monday, 17), ReminderDelivery()))
    }
}
