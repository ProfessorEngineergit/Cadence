package io.github.professorengineergit.cadence

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import java.time.ZonedDateTime

class CadenceWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { updateAll(context) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) { updateAll(context) }

    private class Palette(val bg: Int, val button: Int, val text: Int, val sub: Int, val buttonText: Int, val progress: Int, val track: Int)

    companion object {
        private val Light = Palette(R.drawable.widget_background_light, R.drawable.widget_button_light, 0xFF30243D.toInt(), 0xFF5A4A68.toInt(), 0xFFF3EAFB.toInt(), 0xFFA83D2C.toInt(), 0xFFCBBED6.toInt())
        private val Dark = Palette(R.drawable.widget_background_dark, R.drawable.widget_button_dark, 0xFFEEDCFF.toInt(), 0xFFCDBFD6.toInt(), 0xFFF3EAFB.toInt(), 0xFFFFB4A3.toInt(), 0xFF4C4449.toInt())

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CadenceWidget::class.java))
            if (ids.isEmpty()) return
            val state = runCatching { PracticeStore(context).load() }.getOrElse { return }
            // Follow the app's theme; "System" lets the launcher pick light or dark resources itself.
            val palette = when (state.theme) { AppTheme.LIGHT -> Light; AppTheme.DARK -> Dark; AppTheme.SYSTEM -> null }
            for (id in ids) {
                val views = if (Build.VERSION.SDK_INT >= 31) {
                    RemoteViews(mapOf(SizeF(100f, 40f) to build(context, state, palette, Shape.SMALL), SizeF(240f, 40f) to build(context, state, palette, Shape.WIDE),
                        SizeF(100f, 95f) to build(context, state, palette, Shape.SQUARE), SizeF(200f, 105f) to build(context, state, palette, Shape.FULL)))
                } else {
                    val options = manager.getAppWidgetOptions(id)
                    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
                    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
                    build(context, state, palette, when { height < 95 && width >= 240 -> Shape.WIDE; height < 95 -> Shape.SMALL; width < 200 -> Shape.SQUARE; else -> Shape.FULL })
                }
                manager.updateAppWidget(id, views)
            }
        }

        private enum class Shape { SMALL, WIDE, SQUARE, FULL }

        private fun build(context: Context, state: CadenceState, palette: Palette?, shape: Shape): RemoteViews {
            val small = shape == Shape.SMALL
            val english = isEnglish(state.language)
            fun w(de: String, en: String) = if (english) en else de
            val goal = state.goal(state.timer?.goalId) ?: state.activeGoal
            val plan = Planner.plan(state, goal, ZonedDateTime.now())
            val streak = Planner.streak(state, java.time.LocalDate.now())
            val timer = state.timer
            val headline = when {
                timer != null && !timer.isRunning -> w("Pausiert", "Paused")
                plan.status == DayStatus.TODO && plan.doneToday > 0 -> w("Noch ${plan.todayRemaining} Min.", "${plan.todayRemaining} min left")
                plan.status == DayStatus.TODO -> w("Heute ${plan.todayTarget} Min.", "${plan.todayTarget} min today")
                plan.status == DayStatus.DONE_TODAY -> w("Heute geschafft", "Done today")
                plan.status == DayStatus.AHEAD -> w("Heute frei", "Day off")
                plan.status == DayStatus.GOAL_REACHED -> w("Ziel erreicht", "Goal reached")
                plan.status == DayStatus.OVER -> w("Termin vorbei", "Date passed")
                else -> w("Kein Übetag", "Rest day")
            }
            val slots = plan.todaySlots.joinToString(", ") { it.label() }
            val progressText = if (goal.type == GoalType.WEEKLY) w("${plan.done} von ${plan.target} Min. diese Woche", "${plan.done} of ${plan.target} min this week")
                else w("${plan.done} von ${plan.target} Min. bis zum Termin", "${plan.done} of ${plan.target} min so far")
            val detail = listOf(if (plan.status == DayStatus.TODO && slots.isNotEmpty()) slots else "", progressText).filter { it.isNotEmpty() }.joinToString("\n")
            val views = RemoteViews(context.packageName, when (shape) { Shape.SMALL -> R.layout.cadence_widget_small; Shape.WIDE -> R.layout.cadence_widget_wide; Shape.SQUARE -> R.layout.cadence_widget_square; Shape.FULL -> R.layout.cadence_widget })
            views.setTextViewText(R.id.widget_headline, headline)
            views.setTextViewText(R.id.widget_title, goal.title)
            if (timer != null && timer.isRunning) {
                views.setViewVisibility(R.id.widget_chronometer, View.VISIBLE)
                views.setViewVisibility(R.id.widget_headline, View.GONE)
                views.setChronometer(R.id.widget_chronometer, SystemClock.elapsedRealtime() - timer.elapsedMillis(System.currentTimeMillis()), null, true)
            } else {
                views.setViewVisibility(R.id.widget_chronometer, View.GONE)
                views.setViewVisibility(R.id.widget_headline, View.VISIBLE)
                views.setChronometer(R.id.widget_chronometer, SystemClock.elapsedRealtime(), null, false)
            }
            val percent = if (plan.target > 0) (plan.done * 100 / plan.target).coerceIn(0, 100) else 0
            if (shape != Shape.SMALL) views.setProgressBar(R.id.widget_bar, 100, percent, false)
            if (shape == Shape.FULL || shape == Shape.SQUARE) views.setTextViewText(R.id.widget_streak, if (streak > 0) "🔥 $streak" else "")
            if (shape == Shape.FULL) views.setTextViewText(R.id.widget_detail, detail)
            views.setTextViewText(R.id.widget_start, when (shape) {
                Shape.SMALL -> if (timer != null) "↗" else "▶"
                Shape.SQUARE, Shape.WIDE -> if (timer != null) w("Öffnen", "Open") else w("▶  Üben", "▶  Practise")
                Shape.FULL -> when { timer != null -> w("Zur Session", "Open session"); plan.status == DayStatus.TODO -> w("▶  Üben starten", "▶  Start practising"); else -> w("▶  Trotzdem üben", "▶  Practise anyway") }
            })
            palette?.let { p ->
                views.setInt(R.id.widget_root, "setBackgroundResource", p.bg)
                views.setInt(R.id.widget_start, "setBackgroundResource", p.button)
                listOf(R.id.widget_headline, R.id.widget_chronometer).forEach { views.setTextColor(it, p.text) }
                views.setTextColor(R.id.widget_title, p.sub)
                views.setTextColor(R.id.widget_start, p.buttonText)
                if (shape == Shape.FULL) views.setTextColor(R.id.widget_detail, p.sub)
                if (shape == Shape.FULL || shape == Shape.SQUARE) views.setTextColor(R.id.widget_streak, p.text)
                if (!small) {
                    if (Build.VERSION.SDK_INT >= 31) {
                        views.setColorStateList(R.id.widget_bar, "setProgressTintList", ColorStateList.valueOf(p.progress))
                        views.setColorStateList(R.id.widget_bar, "setProgressBackgroundTintList", ColorStateList.valueOf(p.track))
                    }
                }
            }
            val open = PendingIntent.getActivity(context, 201, Intent(context, MainActivity::class.java).putExtra("goal_id", goal.id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val start = if (timer != null) open else PendingIntent.getActivity(context, 202, Intent(context, MainActivity::class.java).setAction(ReminderScheduler.START_ACTION).putExtra("goal_id", goal.id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_root, open)
            views.setOnClickPendingIntent(R.id.widget_start, start)
            return views
        }
    }
}
