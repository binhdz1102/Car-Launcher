package com.android.car.carlauncher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DateAppWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { widgetId ->
            val now = Date()
            val views =
                RemoteViews(context.packageName, R.layout.date_widget).apply {
                    setTextViewText(
                        R.id.day_of_week_textview,
                        SimpleDateFormat("EEEE", Locale.getDefault()).format(now),
                    )
                    setTextViewText(
                        R.id.date_textview,
                        SimpleDateFormat("MMMM d", Locale.getDefault()).format(now),
                    )
                }
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        if (intent.action in DATE_ACTIONS) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, DateAppWidgetProvider::class.java)
            onUpdate(context, manager, manager.getAppWidgetIds(component))
        }
    }

    private companion object {
        val DATE_ACTIONS =
            setOf(
                Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
            )
    }
}
