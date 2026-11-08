package io.github.professorengineergit.cadence

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PracticeDataTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 11, 8) // Sunday
    private fun session(day: LocalDate, seconds: Long = 600) = PracticeSession(
        startedAt = day.atTime(12, 0).atZone(berlin).toInstant().toEpochMilli(), durationSeconds = seconds)

    @Test fun weekBeginsOnMondayAndExcludesAdjacentWeeks() {
        val monday = LocalDate.of(2026, 11, 2)
        val stats = PracticeStats(listOf(session(monday.minusDays(1)), session(monday), session(today), session(monday.plusDays(7))), CadenceState().goals, today, berlin)
        assertEquals(monday, stats.weekStart)
        assertEquals(2, stats.weekSessions.size)
        assertEquals(20L, stats.weekMinutes)
    }

    @Test fun secondsAreSummedBeforeRoundingMinutes() {
        val stats = PracticeStats(listOf(session(today, 35), session(today, 35)), CadenceState().goals, today, berlin)
        assertEquals(1L, stats.todayMinutes)
        assertEquals(1L, stats.weekMinutes)
        assertEquals(1L, stats.lifetimeMinutes)
    }

    @Test fun weeklyGoalReflectsDailyPlanAndBalanceExcludesToday() {
        val stats = PracticeStats(emptyList(), listOf(10, 10, 10, 10, 10, 10, 60), today, berlin)
        assertEquals(120, stats.weeklyGoal)
        assertEquals(60, stats.todayGoal)
        assertEquals(-60L, stats.balanceMinutes)
    }

    @Test fun pausedTimerExcludesTimeSpentPausedAndRestoresByTimestamp() {
        val initial = PracticeTimer(1000, 1000, focus = "Technik", targetMinutes = 25)
        val paused = initial.pause(31000)
        assertEquals(30000L, paused.elapsedMillis(99000))
        val restored = paused.copy().resume(100000)
        assertEquals(45000L, restored.elapsedMillis(115000))
        assertEquals(1000L, restored.sessionStartedAt)
    }

    @Test fun timerDoesNotProduceNegativeElapsedIfClockMovesBackward() {
        val timer = PracticeTimer(1000, 1000, accumulatedMillis = 30000, focus = "Technik", targetMinutes = 25)
        assertEquals(30000L, timer.elapsedMillis(500))
    }

    @Test fun streakAllowsUnfinishedTodayAndDoesNotCountDuplicateSessions() {
        val days = listOf(today.minusDays(1), today.minusDays(1), today.minusDays(2), today.minusDays(4))
        val stats = PracticeStats(days.map { session(it) }, CadenceState().goals, today, berlin)
        assertEquals(2, stats.streak)
        assertEquals(3, stats.activeDaysThisWeek)
        assertEquals(3, PracticeStats(days.map { session(it) } + session(today), CadenceState().goals, today, berlin).streak)
    }

    @Test fun datesAreGroupedInLocalTimezoneAcrossDstBoundary() {
        val timestamp = java.time.Instant.parse("2026-10-25T23:30:00Z").toEpochMilli()
        val entry = PracticeSession(startedAt = timestamp, durationSeconds = 600)
        assertEquals(LocalDate.of(2026, 10, 26), entry.date(berlin))
        val stats = PracticeStats(listOf(entry), CadenceState().goals, LocalDate.of(2026, 10, 26), berlin)
        assertEquals(10L, stats.weekMinutes)
    }

    @Test fun exportQuotesNotesPreservesSecondsAndNeutralizesFormulas() {
        val csv = exportCsv(listOf(session(today, 65).copy(focus = "=1+1", note = "Takt 1, \"langsam\"\nweiter")))
        assertTrue(csv.startsWith("\uFEFFDatum,"))
        assertTrue(csv.contains(",65,1.08,"))
        assertTrue(csv.contains("\"'=1+1\""))
        assertTrue(csv.contains("\"Takt 1, \"\"langsam\"\"\nweiter\""))
    }
    @Test fun weeklyGoalIsSharedAcrossSelectedDaysInFiveMinuteBlocks() {
        val plan = distributeWeeklyGoal(100, setOf(0, 2, 4), List(7) { Availability() })
        assertEquals(listOf(35, 0, 35, 0, 30, 0, 0), plan)
        assertEquals(100, plan.sum())
    }
    @Test fun weeklyDistributionRespectsEachDaysAvailableTime() {
        val windows = List(7) { if (it == 0) Availability(960, 990) else Availability() }
        assertEquals(listOf(30, 70, 0, 0, 0, 0, 0), distributeWeeklyGoal(100, setOf(0, 1), windows))
        assertEquals(List(7) { 0 }, distributeWeeklyGoal(0, emptySet(), windows))
    }
    @Test(expected = IllegalArgumentException::class) fun impossibleWeeklyGoalIsRejected() {
        distributeWeeklyGoal(250, setOf(0), List(7) { Availability() })
    }
}
