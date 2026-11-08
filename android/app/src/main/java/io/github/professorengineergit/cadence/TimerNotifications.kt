package io.github.professorengineergit.cadence

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.ZonedDateTime

/**
 * The running timer as an ongoing notification. On Android 16 it is a Live Update with a progress bar towards
 * today's goal and a short status chip; older versions get a regular ongoing notification with a chronometer.
 */
object TimerNotifications {
    const val ID = 150
    const val CHANNEL = "live_practice"
    private const val REFRESH = "REFRESH_TIMER"

    private fun refreshIntent(context: Context) = PendingIntent.getBroadcast(context, 304, Intent(context, TimerActionReceiver::class.java).setAction(REFRESH), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun sync(context: Context, state: CadenceState) {
        val timer = state.timer
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (timer == null || !state.liveTimer || !ReminderScheduler.allowed(context)) {
            alarms.cancel(refreshIntent(context))
            if (timer != null || !state.liveTimer) NotificationManagerCompat.from(context).cancel(ID)
            else cancelOngoingOnly(context)
            return
        }
        val l = state.language
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel("active_practice")
        // Live Updates need at least default importance; the channel stays silent.
        manager.createNotificationChannel(NotificationChannel(CHANNEL, wordsNow("Laufende Session", "Running session", l), NotificationManager.IMPORTANCE_DEFAULT).apply { setShowBadge(false); setSound(null, null); enableVibration(false) })
        val goal = state.goal(timer.goalId) ?: state.activeGoal
        val nowMillis = System.currentTimeMillis()
        val elapsed = timer.elapsedMillis(nowMillis)
        val plan = Planner.plan(state, goal, ZonedDateTime.now())
        val targetSeconds = (if (plan.todayTarget > 0) plan.todayTarget else timer.targetMinutes) * 60L
        val earlierSeconds = if (plan.todayTarget > 0) plan.doneToday * 60L else 0L
        val doneSeconds = earlierSeconds + elapsed / 1000
        val leftMinutes = ((targetSeconds - doneSeconds + 59) / 60).toInt()
        val text = when {
            !timer.isRunning -> wordsNow("Pausiert bei ${formatDuration(elapsed / 1000)}", "Paused at ${formatDuration(elapsed / 1000)}", l)
            leftMinutes > 0 -> wordsNow("Noch $leftMinutes Min. bis zum Tagesziel", "$leftMinutes min to today's goal", l)
            else -> wordsNow("Tagesziel geschafft. Weiter so oder beenden.", "Today's goal done. Keep going or finish.", l)
        }
        val chip = when { !timer.isRunning -> wordsNow("Pause", "Paused", l); leftMinutes > 0 -> wordsNow("$leftMinutes Min.", "$leftMinutes min", l); else -> "✓" }
        val open = PendingIntent.getActivity(context, 301, Intent(context, MainActivity::class.java).putExtra("goal_id", timer.goalId), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(name: String, code: Int) = PendingIntent.getBroadcast(context, code, Intent(context, TimerActionReceiver::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val toggleLabel = if (timer.isRunning) wordsNow("Pause", "Pause", l) else wordsNow("Weiter", "Resume", l)
        val finishLabel = wordsNow("Fertig", "Done", l)
        val title = goal.title + if (timer.focus != DEFAULT_FOCUS) ": ${translate(timer.focus, isEnglish(l))}" else ""

        val notification: Notification = if (Build.VERSION.SDK_INT >= 36) {
            val accent = 0xFFA83D2C.toInt()
            val done = 0xFF38634C.toInt()
            val max = targetSeconds.coerceAtLeast(60).toInt()
            val style = Notification.ProgressStyle().setStyledByProgress(true).setProgress(doneSeconds.coerceAtMost(max.toLong()).toInt())
                .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_notification))
                .setProgressSegments(buildList {
                    if (earlierSeconds in 1 until max) { add(Notification.ProgressStyle.Segment(earlierSeconds.toInt()).setColor(done)); add(Notification.ProgressStyle.Segment(max - earlierSeconds.toInt()).setColor(accent)) }
                    else add(Notification.ProgressStyle.Segment(max).setColor(accent))
                })
            Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_STOPWATCH).setVisibility(Notification.VISIBILITY_PUBLIC)
                .setWhen(nowMillis - elapsed).setShowWhen(timer.isRunning).setUsesChronometer(timer.isRunning).setShortCriticalText(chip).setStyle(style)
                .addAction(Notification.Action.Builder(null, toggleLabel, action("TOGGLE_TIMER", 302)).build())
                .addAction(Notification.Action.Builder(null, finishLabel, action("FINISH_TIMER", 303)).build())
                .addExtras(android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
                .build()
        } else NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(nowMillis - elapsed).setShowWhen(timer.isRunning).setUsesChronometer(timer.isRunning)
            .setProgress(targetSeconds.coerceAtLeast(60).toInt(), doneSeconds.coerceAtMost(targetSeconds).toInt(), false)
            .setRequestPromotedOngoing(timer.isRunning).setShortCriticalText(chip)
            .addAction(0, toggleLabel, action("TOGGLE_TIMER", 302)).addAction(0, finishLabel, action("FINISH_TIMER", 303))
            .build()
        try { NotificationManagerCompat.from(context).notify(ID, notification) } catch (_: SecurityException) { }

        // Progress bars don't move on their own: refresh every few minutes, and exactly when today's goal is reached.
        if (timer.isRunning) {
            val untilGoal = (targetSeconds - doneSeconds) * 1000
            val next = if (untilGoal in 1..5 * 60_000) nowMillis + untilGoal + 1000 else nowMillis + 5 * 60_000
            alarms.set(AlarmManager.RTC, next, refreshIntent(context))
        } else alarms.cancel(refreshIntent(context))
    }

    /** Keeps a just-posted "saved" note visible while removing a stale ongoing timer. */
    private fun cancelOngoingOnly(context: Context) {
        val active = context.getSystemService(NotificationManager::class.java).activeNotifications.firstOrNull { it.id == ID }
        if (active != null && active.notification.flags and Notification.FLAG_ONGOING_EVENT != 0) NotificationManagerCompat.from(context).cancel(ID)
    }

    /** A short confirmation after finishing from the notification, since the app may not be open to show it. */
    fun showSaved(context: Context, state: CadenceState, session: PracticeSession) {
        if (!ReminderScheduler.allowed(context)) return
        val goal = state.goal(session.goalId)
        val open = PendingIntent.getActivity(context, 305, Intent(context, MainActivity::class.java).putExtra("goal_id", session.goalId), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(goal?.title ?: "Cadence").setContentText(finishMessage(state, session)).setContentIntent(open)
            .setAutoCancel(true).setSilent(true).setTimeoutAfter(20_000).build()
        try { NotificationManagerCompat.from(context).notify(ID, notification) } catch (_: SecurityException) { }
    }
}

class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = PracticeStore(context)
        val state = runCatching { store.load() }.getOrElse { return }
        val timer = state.timer ?: run { TimerNotifications.sync(context, state); return }
        val now = System.currentTimeMillis()
        if (intent.action == "REFRESH_TIMER") { TimerNotifications.sync(context, state); CadenceWidget.updateAll(context); return }
        var saved: PracticeSession? = null
        val next = when (intent.action) {
            "TOGGLE_TIMER" -> state.copy(timer = if (timer.isRunning) timer.pause(now) else timer.resume(now))
            "FINISH_TIMER" -> {
                val session = PracticeSession(startedAt = timer.sessionStartedAt, durationSeconds = (timer.elapsedMillis(now) / 1000).coerceAtLeast(1), focus = timer.focus, goalId = timer.goalId)
                saved = session
                state.copy(timer = null, sessions = state.sessions + session)
            }
            else -> return
        }
        if (runCatching { store.save(next) }.isFailure) return
        saved?.let { session ->
            val celebration = celebrationFor(state, next, session)
            if (celebration != null) Celebrate.vibrate(context, next.haptics, celebration.big)
            TimerNotifications.showSaved(context, next, session)
            CompletionSound.play(context, next.completionSound)
        } ?: TimerNotifications.sync(context, next)
        CadenceWidget.updateAll(context)
        runCatching { ReminderScheduler.refresh(context, next) }
    }
}

object CompletionSound {
    fun canPlay(context: Context): Boolean = context.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL && context.getSystemService(NotificationManager::class.java).currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
    fun play(context: Context, enabled: Boolean) {
        if (!enabled || !canPlay(context)) return
        runCatching {
            MediaPlayer.create(context, R.raw.completion, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(), 0)?.apply {
                setVolume(0.45f, 0.45f)
                setOnCompletionListener { it.release() }
                setOnErrorListener { player, _, _ -> player.release(); true }
                start()
            }
        }
    }
}
