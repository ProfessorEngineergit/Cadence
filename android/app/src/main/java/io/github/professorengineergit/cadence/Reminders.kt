package io.github.professorengineergit.cadence

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

internal class DeliveryStore(context: Context) {
    private val prefs = context.getSharedPreferences("reminder_delivery", Context.MODE_PRIVATE)
    fun read() = ReminderDelivery(prefs.getLong("last", 0), prefs.getString("date", "") ?: "", prefs.getInt("count", 0), prefs.getLong("snooze", 0), prefs.getLong("skip", 0))
    fun save(value: ReminderDelivery) { prefs.edit().putLong("last", value.lastSent).putString("date", value.date).putInt("count", value.count).putLong("snooze", value.snoozeUntil).putLong("skip", value.skipUntil).commit() }
    companion object { val lock = Any() }
}

object ReminderScheduler {
    const val CHANNEL = "practice_rhythm"
    const val NOTIFICATION_ID = 140
    const val START_ACTION = "io.github.professorengineergit.cadence.START"
    fun ensure(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (enabled) manager.enqueueUniquePeriodicWork("cadence-reminders", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(30, TimeUnit.MINUTES, 15, TimeUnit.MINUTES).build())
        else { manager.cancelUniqueWork("cadence-reminders"); manager.cancelUniqueWork("cadence-snooze"); dismiss(context) }
    }
    fun allowed(context: Context) = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun channel(context: Context) {
        val language = runCatching { PracticeStore(context).load().language }.getOrDefault(AppLanguage.SYSTEM)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, wordsNow("Dein Übe-Rhythmus", "Your practice rhythm", language), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = wordsNow("Motivierende Erinnerungen passend zu deinem Wochenplan und deiner verfügbaren Zeit", "Encouragement that adapts to your weekly plan and availability", language)
        })
    }
    fun dismiss(context: Context) { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    fun show(context: Context, copy: ReminderCopy, preview: Boolean = false) {
        if (!allowed(context)) return
        channel(context)
        val language = PracticeStore(context).load().language
        fun activity(action: String?, code: Int) = PendingIntent.getActivity(context, code, Intent(context, MainActivity::class.java).apply { this.action = action; putExtra("goal_id", copy.goalId); flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(name: String, code: Int) = PendingIntent.getBroadcast(context, code, Intent(context, ReminderActionReceiver::class.java).setAction(name), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (preview) wordsNow("Vorschau · ", "Preview · ", language) + copy.title else copy.title).setContentText(copy.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(copy.body)).setContentIntent(activity(null, 100))
            .setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, wordsNow("Üben starten", "Start practising", PracticeStore(context).load().language), activity(START_ACTION, 101))
        if (!preview) notification.addAction(0, wordsNow("30 Min. später", "In 30 min", language), action("SNOOZE", 102)).addAction(0, wordsNow("Heute Pause", "Pause today", language), action("SKIP_TODAY", 103))
        try { NotificationManagerCompat.from(context).notify(if (preview) 141 else NOTIFICATION_ID, notification.build()) } catch (_: SecurityException) { }
    }
}

class ReminderWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        synchronized(DeliveryStore.lock) {
            val state = runCatching { PracticeStore(applicationContext).load() }.getOrElse { return Result.failure() }
            CadenceWidget.updateAll(applicationContext)
            if (!ReminderScheduler.allowed(applicationContext)) return Result.success()
            val delivery = DeliveryStore(applicationContext)
            val previous = delivery.read()
            val now = ZonedDateTime.now()
            val copy = ReminderEngine.evaluateAll(state, now, previous).notification ?: run {
                val window = state.reminders.windows[now.dayOfWeek.value - 1]
                val minute = now.hour * 60 + now.minute
                if (!state.reminders.enabled || state.timer != null || !window.enabled || minute !in window.start until window.end || previous.skipUntil > now.toInstant().toEpochMilli()) ReminderScheduler.dismiss(applicationContext)
                return Result.success()
            }
            ReminderScheduler.show(applicationContext, copy)
            delivery.save(previous.copy(lastSent = now.toInstant().toEpochMilli(), date = now.toLocalDate().toString(), count = if (previous.date == now.toLocalDate().toString()) previous.count + 1 else 1, snoozeUntil = 0))
        }
        return Result.success()
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        synchronized(DeliveryStore.lock) {
            val store = DeliveryStore(context)
            val previous = store.read()
            when (intent.action) {
                "SNOOZE" -> {
                    store.save(previous.copy(snoozeUntil = System.currentTimeMillis() + 30 * 60_000))
                    WorkManager.getInstance(context).enqueueUniqueWork("cadence-snooze", ExistingWorkPolicy.REPLACE,
                        OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(30, TimeUnit.MINUTES).build())
                }
                "SKIP_TODAY" -> store.save(previous.copy(skipUntil = ZonedDateTime.now().toLocalDate().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), snoozeUntil = 0))
            }
            ReminderScheduler.dismiss(context)
        }
    }
}
