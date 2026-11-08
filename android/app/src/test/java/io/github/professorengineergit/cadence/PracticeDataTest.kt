package io.github.professorengineergit.cadence

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class PracticeDataTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val monday = LocalDate.of(2026, 11, 2)
    private val everyDay = List(7) { listOf(TimeSlot(17 * 60, 18 * 60)) }
    private fun at(day: LocalDate, hour: Int, minute: Int = 0): ZonedDateTime = day.atTime(hour, minute).atZone(zone)
    private fun session(day: LocalDate, minutes: Int, hour: Int = 17, goal: String = "primary", timed: Boolean = true) =
        PracticeSession(startedAt = at(day, hour).toInstant().toEpochMilli(), durationSeconds = minutes * 60L, goalId = goal, recordedWithTimer = timed)
    private fun weekly(minutes: Int = 70, perDay: Boolean = false) = PracticeGoal("primary", "Klavier", minutes = minutes, perDay = perDay, createdOn = "2026-10-01")
    private fun state(goal: PracticeGoal = weekly(), sessions: List<PracticeSession> = emptyList(), availability: List<List<TimeSlot>> = everyDay) =
        CadenceState(sessions = sessions, practiceGoals = listOf(goal), availability = availability, activeGoalId = goal.id, language = AppLanguage.DE)
    private fun plan(s: CadenceState, now: ZonedDateTime) = Planner.plan(s, s.practiceGoals.first(), now)

    @Test fun weeklyGoalIsSpreadEvenlyOverFreeDays() {
        val p = plan(state(), at(monday, 9))
        assertEquals(DayStatus.TODO, p.status)
        assertEquals(10, p.todayTarget)
        assertEquals(70, p.days.sumOf { it.planned })
        assertEquals(7, p.practiceDaysLeft)
    }

    @Test fun extraPracticeYesterdayMakesTodayLighterOrFree() {
        val tuesday = monday.plusDays(1)
        val lighter = plan(state(sessions = listOf(session(monday, 15))), at(tuesday, 9))
        assertEquals(5, lighter.todayTarget)
        assertEquals(DayStatus.TODO, lighter.status)
        val free = plan(state(sessions = listOf(session(monday, 25))), at(tuesday, 9))
        assertEquals(DayStatus.AHEAD, free.status)
        assertEquals(0, free.todayTarget)
        assertEquals(25, free.yesterdayMinutes)
    }

    @Test fun missedDaysAreSpreadOverTheRestOfTheWeekNotDumpedOnToday() {
        val thursday = monday.plusDays(3)
        val p = plan(state(), at(thursday, 9))
        // 70 minutes over Thursday to Sunday instead of 40 today.
        assertEquals(20, p.todayTarget)
        assertEquals(0, p.shortfall)
    }

    @Test fun practisingTodayDoesNotChangeTodaysTarget() {
        val p = plan(state(sessions = listOf(session(monday, 4))), at(monday, 18))
        assertEquals(10, p.todayTarget)
        assertEquals(6, p.todayRemaining)
        val done = plan(state(sessions = listOf(session(monday, 12))), at(monday, 18))
        assertEquals(DayStatus.DONE_TODAY, done.status)
    }

    @Test fun lessonBasedWeekCountsPracticeBeforeTheLessonForTheOldWeek() {
        val goal = weekly(60).copy(resetDay = 0, resetMinute = 16 * 60)
        val slots = List(7) { listOf(TimeSlot(15 * 60, 18 * 60)) }
        val before = state(goal, listOf(session(monday.plusDays(7), 30, hour = 15)), slots)
        val p = plan(before, at(monday.plusDays(7), 15, 40))
        assertEquals(at(monday, 16).toLocalDateTime(), p.period.start)
        assertEquals(30, p.done)
        // After the lesson a new week starts and the earlier session no longer counts.
        val after = plan(before, at(monday.plusDays(7), 17))
        assertEquals(0, after.done)
        assertEquals(at(monday.plusDays(7), 16).toLocalDateTime(), after.period.start)
    }

    @Test fun lessonDayOnlyOffersTheFreeTimeBeforeTheLesson() {
        val goal = weekly(60).copy(resetMinute = 16 * 60)
        val slots = List(7) { listOf(TimeSlot(15 * 60, 18 * 60)) }
        val p = plan(state(goal, availability = slots), at(monday.plusDays(7), 9))
        val lessonDay = p.days.last()
        assertEquals(monday.plusDays(7), lessonDay.date)
        assertEquals(60, lessonDay.capacity)
    }

    @Test fun deadlineWithTimeIgnoresFreeTimeAfterTheEvent() {
        val thursday = monday.plusDays(3)
        val talk = PracticeGoal("talk", "Referat", GoalKind.SPEECH, GoalType.DEADLINE, minutes = 60, deadline = thursday.toString(), deadlineMinute = 10 * 60, createdOn = monday.toString())
        val slots = List(7) { listOf(TimeSlot(8 * 60, 9 * 60), TimeSlot(17 * 60, 18 * 60)) }
        val p = plan(state(talk, availability = slots), at(monday, 7))
        assertEquals(60, p.days.first { it.date == thursday }.capacity)
        assertTrue(p.days.none { it.date > thursday })
        assertEquals(15, p.todayTarget)
        assertEquals(3, p.daysUntilDeadline)
    }

    @Test fun userExampleFourEveningsPlusWeekendAfternoons() {
        // Thu-Sun 17-18, weekend also 15-16, 20 minutes on every practice day.
        val slots = List(7) { d -> when (d) { 3, 4 -> listOf(TimeSlot(17 * 60, 18 * 60)); 5, 6 -> listOf(TimeSlot(15 * 60, 16 * 60), TimeSlot(17 * 60, 18 * 60)); else -> emptyList() } }
        val s = state(weekly(20, perDay = true), availability = slots)
        assertEquals(DayStatus.REST, plan(s, at(monday, 17)).status)
        val thursday = plan(s, at(monday.plusDays(3), 16))
        assertEquals(80, thursday.target)
        assertEquals(20, thursday.todayTarget)
        assertEquals(monday.plusDays(4), thursday.nextPracticeDay)
    }

    @Test fun shortfallIsReportedWhenFreeTimeIsNotEnough() {
        val slots = List(7) { if (it == 6) listOf(TimeSlot(17 * 60, 17 * 60 + 30)) else emptyList() }
        val p = plan(state(weekly(100), availability = slots), at(monday.plusDays(6), 9))
        assertEquals(70, p.shortfall)
        assertEquals(30, p.todayTarget)
    }

    @Test fun goalCreatedMidWeekOnlyAsksForTheRemainingShare() {
        // Created on Friday: Fri-Sun are 3 of 7 equal days, so 70 minutes become 30.
        val p = plan(state(weekly(70).copy(createdOn = monday.plusDays(4).toString())), at(monday.plusDays(4), 9))
        assertEquals(30, p.target)
        assertFalse(p.days.first().inPeriod)
        assertEquals(10, p.todayTarget)
    }

    @Test fun goalStartingTomorrowIsPlannedFromItsFirstWeekAndRestsToday() {
        val sunday = monday.plusDays(6)
        val p = plan(state(weekly(70).copy(createdOn = monday.plusDays(7).toString())), at(sunday, 23))
        assertEquals(DayStatus.REST, p.status)
        assertEquals(70, p.target)
        assertEquals(monday.plusDays(7), p.days.first().date)
        assertEquals(Planner.freshStart(everyDay, at(sunday, 23)), monday.plusDays(7))
        assertEquals(sunday, Planner.freshStart(everyDay, at(sunday, 9)))
    }

    @Test fun goalReachedAndExpiredDeadlinesAreDetected() {
        assertEquals(DayStatus.GOAL_REACHED, plan(state(sessions = listOf(session(monday, 70))), at(monday.plusDays(1), 9)).status)
        val talk = PracticeGoal("t", "T", type = GoalType.DEADLINE, minutes = 30, deadline = monday.toString(), createdOn = "2026-10-20")
        assertEquals(DayStatus.OVER, plan(state(talk), at(monday.plusDays(1), 9)).status)
    }

    @Test fun spreadUsesFiveMinuteStepsAndRespectsCapacity() {
        assertEquals(listOf(35, 0, 35, 0, 30), Planner.spread(100, listOf(60, 0, 60, 0, 60)))
        assertEquals(listOf(30, 60), Planner.spread(100, listOf(30, 60)))
        assertEquals(listOf(0, 0), Planner.spread(0, listOf(30, 60)))
    }

    @Test fun streakSkipsDaysWithoutFreeTimeButBreaksOnMissedPracticeDays() {
        val slots = List(7) { if (it == 2) emptyList() else listOf(TimeSlot(17 * 60, 18 * 60)) }
        // Tue and Thu practised, Wednesday has no free time.
        val s = state(sessions = listOf(session(monday.plusDays(1), 10), session(monday.plusDays(3), 10)), availability = slots)
        assertEquals(2, Planner.streak(s, monday.plusDays(3), zone))
        assertEquals(2, Planner.streak(s, monday.plusDays(4), zone))
        assertEquals(0, Planner.streak(s, monday.plusDays(5), zone))
    }

    @Test fun versionOneDataIsMigrated() {
        val legacy = JSONObject("""{"sessions":[{"id":"a","startedAt":1,"seconds":600,"goalId":"primary"}],
            "practiceGoals":[{"id":"primary","title":"Klavier","kind":"MUSIC","targets":[0,0,15,0,15,10,10],"deadline":null,"totalTarget":240},
                              {"id":"x","title":"Test","kind":"STUDY","targets":[20,20,20,20,20,0,0],"deadline":"2026-12-01","totalTarget":300}],
            "reminders":{"enabled":true,"windows":[{"start":960,"end":1200,"enabled":false},{"start":960,"end":1200},{"start":960,"end":1200},{"start":960,"end":1200},{"start":960,"end":1200},{"start":600,"end":1080},{"start":600,"end":1080}],"dailyLimit":2},
            "onboarded":true}""")
        val s = PracticeStore.decode(legacy)
        assertEquals(GoalType.WEEKLY, s.practiceGoals[0].type)
        assertEquals(50, s.practiceGoals[0].minutes)
        assertEquals(setOf(2, 4, 5, 6), s.practiceGoals[0].days)
        assertEquals(GoalType.DEADLINE, s.practiceGoals[1].type)
        assertEquals(300, s.practiceGoals[1].minutes)
        assertTrue(s.availability[0].isEmpty())
        assertEquals(listOf(TimeSlot(600, 1080)), s.availability[5])
        assertTrue(s.reminders.enabled)
        assertEquals(1, s.sessions.size)
        // And it survives a round trip in the new format.
        assertEquals(s, PracticeStore.decode(PracticeStore.encode(s)))
    }

    @Test fun pausedTimerExcludesPausedTimeAndNeverGoesNegative() {
        val paused = PracticeTimer(1000, 1000, focus = "Technik", targetMinutes = 25).pause(31000)
        assertEquals(30000L, paused.elapsedMillis(99000))
        assertEquals(45000L, paused.resume(100000).elapsedMillis(115000))
        assertEquals(30000L, PracticeTimer(1000, 1000, accumulatedMillis = 30000, focus = "T", targetMinutes = 25).elapsedMillis(500))
    }

    @Test fun normalizeJoinsOverlappingSlots() {
        assertEquals(listOf(TimeSlot(600, 720), TimeSlot(900, 960)), normalize(listOf(TimeSlot(900, 960), TimeSlot(660, 720), TimeSlot(600, 680))))
    }

    @Test fun exportQuotesNotesPreservesSecondsAndNeutralizesFormulas() {
        val csv = exportCsv(listOf(session(monday, 1).copy(durationSeconds = 65, focus = "=1+1", note = "Takt 1, \"langsam\"\nweiter")))
        assertTrue(csv.startsWith("﻿Datum,"))
        assertTrue(csv.contains(",65,1.08,"))
        assertTrue(csv.contains("\"'=1+1\""))
        assertTrue(csv.contains("\"Takt 1, \"\"langsam\"\"\nweiter\""))
    }
}
