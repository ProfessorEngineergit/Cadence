package io.github.professorengineergit.cadence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object TimerNotifications {
    const val ID = 150
    const val CHANNEL = "active_practice"
    fun sync(context: Context, state: CadenceState) {
        val timer = state.timer
        if (timer == null || !state.liveTimer || !ReminderScheduler.allowed(context)) { NotificationManagerCompat.from(context).cancel(ID); return }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, wordsNow("Laufende Übesession", "Active practice session", state.language), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false); setSound(null, null); enableVibration(false) })
        val goal = state.practiceGoals.firstOrNull { it.id == timer.goalId }
        val elapsed = timer.elapsedMillis(System.currentTimeMillis())
        val open = PendingIntent.getActivity(context, 301, Intent(context, MainActivity::class.java).putExtra("goal_id", timer.goalId), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(name: String, code: Int) = PendingIntent.getBroadcast(context, code, Intent(context, TimerActionReceiver::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val body = if (timer.isRunning) "${translate(timer.focus, isEnglish(state.language))} · ${timer.targetMinutes} min" else wordsNow("Pausiert", "Paused", state.language) + " · ${formatDuration(elapsed / 1000)}"
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${goal?.title ?: "Cadence"} · " + wordsNow("Übezeit", "Practice time", state.language))
            .setContentText(body).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setWhen(System.currentTimeMillis() - elapsed).setShowWhen(timer.isRunning).setUsesChronometer(timer.isRunning)
            .setRequestPromotedOngoing(timer.isRunning)
            .addAction(0, if (timer.isRunning) wordsNow("Pause", "Pause", state.language) else wordsNow("Weiter", "Resume", state.language), action("TOGGLE_TIMER", 302))
            .addAction(0, wordsNow("Beenden", "Finish", state.language), action("FINISH_TIMER", 303))
            .build()
        try { NotificationManagerCompat.from(context).notify(ID, notification) } catch (_: SecurityException) { }
    }
}

class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = PracticeStore(context)
        val state = runCatching { store.load() }.getOrElse { return }
        val timer = state.timer ?: return
        val now = System.currentTimeMillis()
        val next = when (intent.action) {
            "TOGGLE_TIMER" -> state.copy(timer = if (timer.isRunning) timer.pause(now) else timer.resume(now))
            "FINISH_TIMER" -> state.copy(timer = null, sessions = state.sessions + PracticeSession(startedAt = timer.sessionStartedAt,
                durationSeconds = (timer.elapsedMillis(now) / 1000).coerceAtLeast(1), focus = timer.focus, goalId = timer.goalId))
            else -> return
        }
        if (runCatching { store.save(next) }.isFailure) return
        TimerNotifications.sync(context, next)
        CadenceWidget.updateAll(context)
        if (intent.action == "FINISH_TIMER") CompletionSound.play(context, next.completionSound)
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
