package io.github.professorengineergit.cadence

import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.work.WorkManager
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CadenceDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private val device get() = UiDevice.getInstance(instrumentation)
    private val allDay = List(7) { listOf(TimeSlot(0, 1440)) }

    @Before fun prepare() {
        assertTrue("Device tests must use the isolated QA package", context.packageName.endsWith(".qa"))
        WorkManager.getInstance(context).cancelAllWork().result.get()
        context.getSharedPreferences("reminder_delivery", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSystemService(NotificationManager::class.java).cancelAll()
        device.wakeUp()
    }
    @After fun finish() { scenario?.close(); WorkManager.getInstance(context).cancelAllWork().result.get() }

    private fun launch(state: CadenceState) {
        PracticeStore(context).save(state)
        scenario = ActivityScenario.launch(android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
        scenario!!.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true); it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        compose.waitForIdle()
    }
    private fun ready(text: String) = compose.waitUntil(10000) { runCatching { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    private fun base() = CadenceState(onboarded = true, language = AppLanguage.DE, availability = allDay,
        practiceGoals = listOf(PracticeGoal("primary", "Klavier", minutes = 70, createdOn = "2026-01-01")))

    @Test fun homeShowsTodaysShareAndTabs() {
        launch(base())
        ready("70 Min. pro Woche")
        compose.onNodeWithText("Minuten heute").assertIsDisplayed()
        compose.onNodeWithText("Klavier").assertIsDisplayed()
        compose.onNodeWithText("Üben starten").assertIsDisplayed()
        compose.onNodeWithContentDescription("Verlauf").performClick(); ready("Die letzten 8 Wochen")
        compose.onNodeWithContentDescription("Plan").performClick(); ready("Deine freie Zeit")
        compose.onNodeWithContentDescription("Einstellungen").performClick(); ready("Erinnerungen")
    }

    @Test fun timerPauseSurvivesRestartAndFinishSavesSession() {
        launch(base())
        ready("Üben starten")
        compose.onNodeWithText("Üben starten").performClick()
        compose.waitUntil(5000) { PracticeStore(context).load().timer != null }
        Thread.sleep(1500)
        compose.onNodeWithText("Pause").performClick()
        compose.waitUntil(5000) { PracticeStore(context).load().timer?.isRunning == false }
        val paused = PracticeStore(context).load().timer!!.accumulatedMillis
        scenario!!.recreate(); compose.waitForIdle(); ready("Pausiert")
        assertEquals(paused, PracticeStore(context).load().timer!!.accumulatedMillis)
        compose.onNodeWithText("Fertig").performClick()
        compose.waitUntil(5000) { PracticeStore(context).load().sessions.size == 1 }
        assertEquals(paused / 1000, PracticeStore(context).load().sessions.single().durationSeconds)
    }

    @Test fun goalTabsKeepSessionsSeparate() {
        val talk = PracticeGoal("talk", "Referat", GoalKind.SPEECH, GoalType.DEADLINE, minutes = 60, deadline = LocalDate.now().plusDays(5).toString(), createdOn = LocalDate.now().toString())
        launch(base().copy(practiceGoals = base().practiceGoals + talk, sessions = listOf(PracticeSession(startedAt = System.currentTimeMillis() - 3_600_000, durationSeconds = 600, goalId = "primary"))))
        ready("Referat")
        compose.onNodeWithText("Referat").performClick()
        compose.waitUntil(5000) { PracticeStore(context).load().activeGoalId == "talk" }
        ready("Noch 5 Tage")
    }

    @Test fun englishSetupCreatesGoalAndFreeTime() {
        launch(CadenceState(language = AppLanguage.EN, onboarded = false))
        ready("Let's go"); compose.onNodeWithText("Let's go").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Cello")
        compose.onNodeWithText("Next").performClick(); ready("What's your goal?")
        compose.onNodeWithText("Next").performClick(); ready("When do you have time?")
        compose.onNodeWithText("Evenings, weekend afternoons").performClick()
        compose.onNodeWithText("Next").performClick(); ready("Here's your plan")
        compose.onNodeWithText("Done").performClick()
        // A fresh test install has no notification permission yet; answer the system dialog.
        device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("Erlauben|Allow|Zulassen"))), 3000)?.click()
        compose.waitUntil(8000) { PracticeStore(context).load().onboarded }
        val saved = PracticeStore(context).load()
        assertEquals("Cello", saved.activeGoal.title)
        assertEquals(listOf(TimeSlot(17 * 60, 18 * 60)), saved.availability[0])
        assertEquals(listOf(TimeSlot(15 * 60, 16 * 60)), saved.availability[6])
    }

    @Test fun reminderCheckPostsAndSnoozes() {
        Assume.assumeTrue("Notification permission not granted on this install", ReminderScheduler.allowed(context))
        val now = java.time.ZonedDateTime.now()
        val minute = now.hour * 60 + now.minute
        val slot = TimeSlot((minute - 1).coerceAtLeast(0), (minute + 120).coerceAtMost(1440))
        PracticeStore(context).save(base().copy(availability = List(7) { listOf(slot) }, reminders = ReminderSettings(enabled = true, learnTiming = false)))
        ReminderScheduler.check(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertTrue(manager.activeNotifications.any { it.id == ReminderScheduler.NOTIFICATION_ID })
        context.sendBroadcast(android.content.Intent(context, ReminderActionReceiver::class.java).setAction("SNOOZE"))
        val deadline = System.currentTimeMillis() + 5000
        while (DeliveryStore(context).read().snoozeUntil == 0L && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue(DeliveryStore(context).read().snoozeUntil > System.currentTimeMillis())
    }
}
