package io.github.professorengineergit.cadence

import android.Manifest
import android.app.AlarmManager
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
import org.json.JSONArray
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

internal class DeliveryStore(context: Context) {
    private val prefs = context.getSharedPreferences("reminder_delivery", Context.MODE_PRIVATE)
    fun read() = ReminderDelivery(prefs.getLong("last", 0), prefs.getString("date", "") ?: "", prefs.getInt("count", 0), prefs.getLong("snooze", 0),
        prefs.getString("skipDate", "") ?: "", prefs.getString("praiseDate", "") ?: "",
        runCatching { JSONArray(prefs.getString("sentDates", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList()))
    fun save(value: ReminderDelivery) {
        prefs.edit().putLong("last", value.lastSent).putString("date", value.date).putInt("count", value.count).putLong("snooze", value.snoozeUntil)
            .putString("skipDate", value.skipDate).putString("praiseDate", value.praiseDate).putString("sentDates", JSONArray(value.sentDates).toString()).commit()
    }
    companion object { val lock = Any() }
}

object ReminderScheduler {
    const val CHANNEL = "practice_reminders"
    const val NOTIFICATION_ID = 140
    const val START_ACTION = "io.github.professorengineergit.cadence.START"
    private const val ALARM_ACTION = "io.github.professorengineergit.cadence.REMINDER_ALARM"

    fun allowed(context: Context) = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun channel(context: Context, language: AppLanguage) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel("practice_rhythm")
        manager.createNotificationChannel(NotificationChannel(CHANNEL, wordsNow("Übe-Erinnerungen", "Practice reminders", language), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = wordsNow("Erinnert dich in deiner freien Zeit, wenn heute noch etwas ansteht", "Reminds you during your free time when there's practice left today", language)
        })
    }

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(context, 400, Intent(context, ReminderAlarmReceiver::class.java).setAction(ALARM_ACTION), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Called after every change: hides a reminder that no longer fits and sets the alarm for the next one. */
    fun refresh(context: Context, state: CadenceState) {
        synchronized(DeliveryStore.lock) {
            val now = ZonedDateTime.now()
            val manager = WorkManager.getInstance(context)
            if (!state.reminders.enabled) {
                manager.cancelUniqueWork("cadence-reminders")
                context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
                dismiss(context); return
            }
            // A safety net in case the system drops the alarm; the alarm does the actual timing.
            manager.enqueueUniquePeriodicWork("cadence-reminders", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS).build())
            if (state.timer != null || Planner.plans(state, now).none { it.todayRemaining > 0 }) dismiss(context)
            schedule(context, state, DeliveryStore(context).read(), now)
        }
    }

    private fun schedule(context: Context, state: CadenceState, delivery: ReminderDelivery, now: ZonedDateTime) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val next = ReminderEngine.next(state, now, delivery)
        if (next == null) { alarms.cancel(alarmIntent(context)); return }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at.toInstant().toEpochMilli(), alarmIntent(context))
    }

    /** Shows a reminder if one is due now, then schedules the next one. */
    fun check(context: Context) {
        synchronized(DeliveryStore.lock) {
            val state = runCatching { PracticeStore(context).load() }.getOrElse { return }
            val store = DeliveryStore(context)
            var delivery = store.read()
            val now = ZonedDateTime.now()
            if (state.reminders.enabled && allowed(context)) {
                ReminderEngine.due(state, now, delivery)?.let { copy ->
                    show(context, copy, state.language)
                    delivery = ReminderEngine.record(delivery, copy, now)
                    store.save(delivery)
                }
            }
            if (state.reminders.enabled) schedule(context, state, delivery, now)
        }
    }

    fun nextPreview(context: Context, state: CadenceState): PlannedReminder? = ReminderEngine.next(state, ZonedDateTime.now(), DeliveryStore(context).read())

    fun dismiss(context: Context) { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }

    fun show(context: Context, copy: ReminderCopy, language: AppLanguage, preview: Boolean = false) {
        if (!allowed(context)) return
        channel(context, language)
        fun activity(action: String?, code: Int) = PendingIntent.getActivity(context, code, Intent(context, MainActivity::class.java).apply { this.action = action; putExtra("goal_id", copy.goalId); flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(name: String, code: Int) = PendingIntent.getBroadcast(context, code, Intent(context, ReminderActionReceiver::class.java).setAction(name), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(copy.title).setContentText(copy.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(copy.body)).setContentIntent(activity(null, 100))
            .setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (copy.kind != ReminderKind.PRAISE) {
            notification.addAction(0, wordsNow("Jetzt üben", "Practise now", language), activity(START_ACTION, 101))
            if (!preview) notification.addAction(0, wordsNow("In 30 Min.", "In 30 min", language), action("SNOOZE", 102)).addAction(0, wordsNow("Heute nicht", "Not today", language), action("SKIP_TODAY", 103))
        }
        try { NotificationManagerCompat.from(context).notify(if (preview) 141 else NOTIFICATION_ID, notification.build()) } catch (_: SecurityException) { }
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread { try { ReminderScheduler.check(context); CadenceWidget.updateAll(context) } finally { pending.finish() } }.start()
    }
}

/** Re-arms the next reminder after a reboot, an update or a clock change. */
class ReminderBootReceiver : BroadcastReceiver() {
    private val handled = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in handled) return
        val pending = goAsync()
        Thread { try {
            runCatching { ReminderScheduler.refresh(context, PracticeStore(context).load()) }
            CadenceWidget.updateAll(context)
        } finally { pending.finish() } }.start()
    }
}

class ReminderWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        ReminderScheduler.check(applicationContext)
        CadenceWidget.updateAll(applicationContext)
        return Result.success()
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        synchronized(DeliveryStore.lock) {
            val store = DeliveryStore(context)
            val previous = store.read()
            when (intent.action) {
                "SNOOZE" -> store.save(previous.copy(snoozeUntil = System.currentTimeMillis() + 30 * 60_000))
                "SKIP_TODAY" -> {
                    // A conscious "not today" is not an ignored reminder.
                    val today = ZonedDateTime.now().toLocalDate().toString()
                    store.save(previous.copy(skipDate = today, snoozeUntil = 0, sentDates = previous.sentDates - today))
                }
            }
            ReminderScheduler.dismiss(context)
        }
        runCatching { ReminderScheduler.refresh(context, PracticeStore(context).load()) }
    }
}
