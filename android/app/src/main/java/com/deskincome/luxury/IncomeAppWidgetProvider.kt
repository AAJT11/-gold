package com.deskincome.luxury

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.ZonedDateTime

class IncomeAppWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, IncomeAppWidgetProvider::class.java))
            if (ids != null && ids.isNotEmpty()) {
                for (id in ids) {
                    updateWidget(context, manager, id)
                }
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val repo = ConfigRepository(context)
            val settings = repo.loadSettings()
            val now = ZonedDateTime.now()
            val snap = IncomeMath.snapshot(settings, now)

            val views = RemoteViews(context.packageName, R.layout.widget_income)
            views.setTextViewText(R.id.widget_status, snap.statusLabel)
            views.setTextViewText(R.id.widget_amount, settings.money(snap.earned, 2))
            views.setTextViewText(
                R.id.widget_timing,
                "${snap.timingLabel(now)} · ${settings.paydayLabel(now)}"
            )

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
