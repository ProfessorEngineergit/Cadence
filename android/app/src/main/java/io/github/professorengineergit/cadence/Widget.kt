package io.github.professorengineergit.cadence

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class CadenceWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { updateAll(context) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) { updateAll(context) }
    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CadenceWidget::class.java))
            if (ids.isEmpty()) return
            val state = runCatching { PracticeStore(context).load() }.getOrElse { return }
            val stats = PracticeStats(state.selectedSessions, state.goals)
            val progress = if (stats.weeklyGoal > 0) (stats.weekMinutes * 100 / stats.weeklyGoal).toInt().coerceIn(0, 100) else 0
            val window = state.reminders.windows[stats.today.dayOfWeek.value - 1]
            val views = RemoteViews(context.packageName, R.layout.cadence_widget).apply {
                setTextViewText(R.id.widget_progress, "${stats.weekMinutes} / ${stats.weeklyGoal} min")
                setProgressBar(R.id.widget_bar, 100, progress, false)
                val english = isEnglish(state.language)
                setTextViewText(R.id.widget_hint, state.activeGoal.title + " · " + if (stats.weeklyGoal > 0 && progress == 100) translate("Wochenziel erreicht. Stark!", english) else if (window.enabled) "${translate("Heute", english)}: ${stats.todayGoal} min · ${clock(window.start)}–${clock(window.end)}" else wordsNow("Heute frei", "Rest day", state.language))
                setTextViewText(R.id.widget_start, if (state.timer == null) wordsNow("▶  Üben starten", "▶  Start practising", state.language) else wordsNow("↗  Zur laufenden Session", "↗  Open current session", state.language))
                val open = PendingIntent.getActivity(context, 201, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val start = PendingIntent.getActivity(context, 202, Intent(context, MainActivity::class.java).setAction(ReminderScheduler.START_ACTION).putExtra("goal_id", state.timer?.goalId ?: state.activeGoal.id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                setOnClickPendingIntent(R.id.widget_root, open); setOnClickPendingIntent(R.id.widget_start, start)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
