package io.github.professorengineergit.cadence

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
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
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CadenceDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private val device get() = UiDevice.getInstance(instrumentation)

    @Before fun prepare() {
        assertTrue("Device tests must use the isolated QA package", context.packageName.endsWith(".qa"))
        WorkManager.getInstance(context).cancelAllWork().result.get()
        context.getSharedPreferences("reminder_delivery", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSystemService(NotificationManager::class.java).cancelAll()
        device.wakeUp()
    }
    @After fun finish() { scenario?.close(); WorkManager.getInstance(context).cancelAllWork().result.get() }
    private fun launch(state: CadenceState = CadenceState(onboarded = true, language = AppLanguage.DE)) {
        PracticeStore(context).save(state)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(android.app.LocaleManager::class.java)
            val locale = android.os.LocaleList.forLanguageTags(if (state.language == AppLanguage.EN) "en" else "de")
            if (manager.applicationLocales != locale) manager.applicationLocales = locale
        }
        scenario = ActivityScenario.launch(android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
        scenario!!.onActivity { activity ->
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.waitForIdle()
        compose.waitUntil(10000) { runCatching { compose.onAllNodesWithText("Cadence").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    }
    private fun shot(name: String) {
        device.waitForIdle()
        instrumentation.uiAutomation.takeScreenshot().let { bitmap -> File(context.filesDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    private fun tab(label: String) { compose.onNodeWithContentDescription(label).performClick(); compose.waitForIdle() }
    private fun swipeSheetDown() {
        compose.waitForIdle(); device.waitForIdle()
        assertTrue(device.swipe(device.displayWidth / 2, device.displayHeight / 2, device.displayWidth / 2, (device.displayHeight * 0.91).toInt(), 18))
        compose.waitForIdle()
        compose.waitUntil(5000) { runCatching { compose.onAllNodesWithText("Cadence").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    }

    @Test fun homeAndDownlinkStyleTabsRenderWithoutClipping() {
        launch()
        compose.onNodeWithText("00:00").assertIsDisplayed()
        compose.onNodeWithText("Übezeit-Tracker").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Einstellungen").assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("Cadence")).assertCountEquals(1)
        shot("home-de")
        tab("Rhythmus")
        compose.onNodeWithText("Zeit haben. Zeit nehmen.").performScrollTo().assertIsDisplayed()
        shot("plan-de")
        tab("Optionen")
        compose.onNodeWithText("Ganz dein\nCadence.").assertIsDisplayed()
        shot("settings-de")
    }

    @Test fun focusAndEntrySheetsDismissWhenSwipingTheirBody() {
        launch()
        compose.onNodeWithText("Freies Üben").performClick()
        compose.onNodeWithText("Woran möchtest du üben?").assertIsDisplayed()
        shot("focus-de")
        swipeSheetDown()
        compose.onNodeWithText("Woran möchtest du üben?").assertDoesNotExist()
        compose.onNodeWithText("Übezeit nachtragen").performScrollTo().performClick()
        compose.waitForIdle()
        shot("entry-de")
        compose.onNodeWithText("Ein Moment Fortschritt.").assertIsDisplayed()
        swipeSheetDown()
        compose.onNodeWithText("Ein Moment Fortschritt.").assertDoesNotExist()
    }

    @Test fun dayPlannerSupportsHorizontalWindowDragAndVerticalDismiss() {
        launch()
        tab("Rhythmus")
        compose.onNodeWithContentDescription("Mittwoch, 30 Minuten Ziel, 240 Minuten verfügbar").performScrollTo()
        device.swipe(device.displayWidth / 2, (device.displayHeight * .73).toInt(), device.displayWidth / 2, (device.displayHeight * .53).toInt(), 15)
        compose.onNodeWithContentDescription("Mittwoch, 30 Minuten Ziel, 240 Minuten verfügbar").performClick()
        compose.onNodeWithText("Tagesplan speichern").assertExists()
        shot("day-plan-de")
        compose.onNodeWithTag("availability-timeline").performTouchInput {
            swipe(start = Offset(width * 0.66f, height / 2f), end = Offset(width * 0.73f, height / 2f), durationMillis = 500)
        }
        compose.onNodeWithText("Tagesplan speichern").performScrollTo().performClick()
        compose.runOnIdle {
            val saved = PracticeStore(context).load()
            assertTrue(saved.reminders.windows[2].start > 960)
            assertEquals(240, saved.reminders.windows[2].minutes)
        }
        compose.onNodeWithContentDescription("Mittwoch, 30 Minuten Ziel, 240 Minuten verfügbar").performClick()
        swipeSheetDown()
        compose.onNodeWithText("Tagesplan speichern").assertDoesNotExist()
    }

    @Test fun addingDatedStudyGoalKeepsPianoSessionsSeparate() {
        val oldSession = PracticeSession(startedAt = System.currentTimeMillis() - 86400_000, durationSeconds = 1200)
        launch(CadenceState(onboarded = true, language = AppLanguage.DE, sessions = listOf(oldSession)))
        compose.onNodeWithText("Klavier").performClick()
        compose.onNodeWithText("Übeziel hinzufügen").performScrollTo().performClick()
        compose.onNodeWithText("Name deines Ziels").performTextInput("Mathetest")
        compose.onNodeWithText("Lernen").performClick()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Übeziel speichern").performScrollTo().performClick()
        compose.runOnIdle {
            val saved = PracticeStore(context).load()
            assertEquals(2, saved.practiceGoals.size)
            assertEquals("Mathetest", saved.activeGoal.title)
            assertEquals(GoalKind.STUDY, saved.activeGoal.kind)
            assertEquals(LocalDate.now().plusDays(14), saved.activeGoal.dueDate)
            assertEquals(0, saved.selectedSessions.size)
            assertEquals(1, saved.sessions.size)
        }
        shot("study-goal-de")
    }

    @Test fun timerPauseSurvivesActivityRestartAndSavesPreciseSeconds() {
        launch()
        compose.onNodeWithText("Üben starten").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("00:02").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Pause").performClick()
        val elapsed = PracticeStore(context).load().timer!!.elapsedMillis(System.currentTimeMillis())
        scenario!!.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("Session pausiert").assertIsDisplayed()
        assertEquals(elapsed, PracticeStore(context).load().timer!!.elapsedMillis(System.currentTimeMillis()))
        compose.onNodeWithText("Beenden").performClick()
        compose.runOnIdle { val saved = PracticeStore(context).load(); assertNull(saved.timer); assertEquals(1, saved.sessions.size); assertTrue(saved.sessions.single().durationSeconds >= 2) }
    }

    @Test fun englishIntroSetsUpGoalsAndAvailability() {
        launch(CadenceState(onboarded = false, language = AppLanguage.EN))
        compose.onNodeWithText("Turn practice\ninto progress.").assertIsDisplayed()
        shot("intro-en")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("What's your\nfirst practice goal?").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("When does practice\nfit your life?").assertIsDisplayed()
        shot("availability-en")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Motivation\nthat pays attention.").assertIsDisplayed()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Start with Cadence").performClick()
        compose.onNodeWithText("Practice tracker").assertIsDisplayed()
        compose.onNodeWithText("Start practising").assertIsDisplayed()
        shot("home-en")
        val saved = PracticeStore(context).load()
        assertTrue(saved.onboarded)
        assertEquals(100, saved.goals.sum())
        assertFalse(saved.reminders.enabled)
    }

    @Test fun notificationWorkerDeliversAndSnoozesRealNotification() {
        val current = java.time.ZonedDateTime.now()
        // Use a broad current-day window, without changing the user's real Cadence data.
        val window = Availability(360, 1380)
        val goal = PracticeGoal("primary", "Klavier", dayTargets = List(7) { 30 })
        val settings = ReminderSettings(enabled = true, windows = List(7) { window })
        launch(CadenceState(onboarded = true, language = AppLanguage.DE, reminders = settings, practiceGoals = listOf(goal)))
        if (!ReminderScheduler.allowed(context)) {
            scenario!!.onActivity { it.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 500) }
            val allow = device.wait(Until.findObject(By.res("com.android.permissioncontroller", "permission_allow_button")), 5000)
                ?: device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)allow|zulassen"))), 5000)
            assertNotNull("Expected notification permission dialog", allow)
            allow!!.click()
            compose.waitUntil(5000) { ReminderScheduler.allowed(context) }
            ReminderScheduler.ensure(context, true)
        }
        WorkManager.getInstance(context).enqueue(androidx.work.OneTimeWorkRequestBuilder<ReminderWorker>().build())
        val manager = context.getSystemService(NotificationManager::class.java)
        compose.waitUntil(20000) { manager.activeNotifications.any { it.id == ReminderScheduler.NOTIFICATION_ID } }
        val notification = manager.activeNotifications.single { it.id == ReminderScheduler.NOTIFICATION_ID }.notification
        assertTrue(notification.extras.getCharSequence("android.bigText").toString().contains("%"))
        notification.actions.single { it.title.toString() == "30 Min. später" }.actionIntent.send()
        compose.waitUntil(5000) { DeliveryStore(context).read().snoozeUntil > System.currentTimeMillis() }
        assertFalse(manager.activeNotifications.any { it.id == ReminderScheduler.NOTIFICATION_ID })
        assertTrue(AppWidgetManager.getInstance(context).installedProviders.any { it.provider.packageName == context.packageName })
    }

    @Test fun liveTimerPauseResumeAndFinishActionsUpdateTheOpenApp() {
        launch(CadenceState(onboarded = true, language = AppLanguage.DE, completionSound = false))
        if (!ReminderScheduler.allowed(context)) {
            scenario!!.onActivity { it.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 501) }
            device.wait(Until.findObject(By.res("com.android.permissioncontroller", "permission_allow_button")), 5000)!!.click()
            compose.waitUntil(5000) { ReminderScheduler.allowed(context) }
        }
        compose.onNodeWithText("Üben starten").performClick()
        val manager = context.getSystemService(NotificationManager::class.java)
        compose.waitUntil(5000) { manager.activeNotifications.any { it.id == TimerNotifications.ID } }
        fun active() = manager.activeNotifications.single { it.id == TimerNotifications.ID }.notification
        assertTrue(active().extras.getBoolean("android.showChronometer"))
        active().actions.single { it.title.toString() == "Pause" }.actionIntent.send()
        compose.waitUntil(5000) { PracticeStore(context).load().timer?.isRunning == false }
        compose.onNodeWithText("Session pausiert").assertIsDisplayed()
        active().actions.single { it.title.toString() == "Weiter" }.actionIntent.send()
        compose.waitUntil(5000) { PracticeStore(context).load().timer?.isRunning == true }
        val finish = active().actions.single { it.title.toString() == "Beenden" }.actionIntent
        finish.send()
        compose.waitUntil(5000) { PracticeStore(context).load().timer == null }
        finish.send()
        compose.waitForIdle()
        assertEquals(1, PracticeStore(context).load().sessions.size)
        assertFalse(manager.activeNotifications.any { it.id == TimerNotifications.ID })
    }

    @Test fun overallWeeklyGoalCanBeEditedDirectly() {
        launch()
        tab("Rhythmus")
        compose.onNodeWithText("WOCHENZIEL · ANTIPPEN").performClick()
        compose.onNodeWithText("Minuten pro Woche").performTextReplacement("120")
        compose.onNodeWithText("Wochenplan übernehmen").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(120, PracticeStore(context).load().goals.sum()) }
    }
    @Test fun notificationStartSelectsItsGoalAndIsConsumedBeforeRecreation() {
        val exam = PracticeGoal("exam", "Mathetest", GoalKind.STUDY)
        launch(CadenceState(onboarded = true, language = AppLanguage.DE, practiceGoals = CadenceState().practiceGoals + exam, completionSound = false))
        scenario!!.close()
        scenario = ActivityScenario.launch(android.content.Intent(context, MainActivity::class.java).setAction(ReminderScheduler.START_ACTION).putExtra("goal_id", "exam"))
        scenario!!.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true); it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        compose.waitUntil(5000) { PracticeStore(context).load().timer?.goalId == "exam" }
        compose.waitUntil(5000) { compose.onAllNodesWithText("00:02").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Beenden").performClick()
        scenario!!.recreate()
        compose.waitForIdle()
        assertNull(PracticeStore(context).load().timer)
        assertEquals("exam", PracticeStore(context).load().activeGoalId)
    }
}
