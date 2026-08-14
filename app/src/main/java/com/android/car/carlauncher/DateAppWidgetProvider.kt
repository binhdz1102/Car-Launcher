package com.android.car.carlauncher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.android.car.carlauncher.feature.widgets.DateWidgetFormatter
import java.time.Clock
import java.time.ZoneId
import java.util.Locale

class DateAppWidgetProvider : AppWidgetProvider() {
    private val clock: Clock = Clock.systemUTC()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { widgetId ->
            val text =
                DateWidgetFormatter.format(
                    instant = clock.instant(),
                    zoneId = ZoneId.systemDefault(),
                    locale = Locale.getDefault(),
                )
            val views =
                RemoteViews(context.packageName, R.layout.date_widget).apply {
                    setTextViewText(
                        R.id.day_of_week_textview,
                        text.dayOfWeek,
                    )
                    setTextViewText(
                        R.id.date_textview,
                        text.date,
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
                Intent.ACTION_LOCALE_CHANGED,
                Intent.ACTION_CONFIGURATION_CHANGED,
            )
    }
}
