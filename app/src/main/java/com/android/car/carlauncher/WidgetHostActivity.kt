package com.android.car.carlauncher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isEmpty
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.car.carlauncher.core.model.DisplayTarget
import com.android.car.carlauncher.core.ui.applySystemBarInsets
import com.android.car.carlauncher.feature.widgets.WidgetHostViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Persistent widget surface referenced by the scalable CarSystemUI configuration. */
@AndroidEntryPoint
open class WidgetHostActivity : AppCompatActivity() {
    private val viewModel: WidgetHostViewModel by viewModels()
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetManager: AppWidgetManager
    private lateinit var container: ViewGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_host)
        applySystemBarInsets()
        container = findViewById(R.id.widget_container)
        widgetManager = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(this, resources.getInteger(R.integer.config_appwidget_host_id))
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { loadConfiguredWidgets() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { widgetHost.startListening() }
        viewModel.bind(currentDisplayTarget())
    }

    override fun onStop() {
        runCatching { widgetHost.stopListening() }
        viewModel.unbind()
        super.onStop()
    }

    private fun currentDisplayTarget(): DisplayTarget {
        val displayId = display?.displayId ?: android.view.Display.DEFAULT_DISPLAY
        return DisplayTarget(
            displayId = displayId,
            isDefaultDisplay = displayId == android.view.Display.DEFAULT_DISPLAY,
        )
    }

    private fun loadConfiguredWidgets() {
        container.removeAllViews()
        val providers = widgetManager.installedProviders.associateBy(AppWidgetProviderInfo::provider)
        resources
            .getStringArray(R.array.config_initialAppWidgets)
            .mapNotNull(ComponentName::unflattenFromString)
            .mapNotNull(providers::get)
            .forEach(::addWidget)
        if (container.isEmpty()) {
            container.addView(
                TextView(this).apply {
                    setText(R.string.widget_empty)
                    textSize = WIDGET_EMPTY_TEXT_SIZE_SP
                    gravity = android.view.Gravity.CENTER
                },
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    private fun addWidget(info: AppWidgetProviderInfo) {
        val preferences = getSharedPreferences(WIDGET_PREFERENCES, MODE_PRIVATE)
        val preferenceKey = "widget:${info.provider.flattenToString()}"
        var widgetId = preferences.getInt(preferenceKey, AppWidgetManager.INVALID_APPWIDGET_ID)
        val existingProvider =
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                null
            } else {
                widgetManager.getAppWidgetInfo(widgetId)?.provider
            }
        if (existingProvider != info.provider) {
            widgetId = widgetHost.allocateAppWidgetId()
            if (!widgetManager.bindAppWidgetIdIfAllowed(widgetId, info.provider)) {
                widgetHost.deleteAppWidgetId(widgetId)
                return
            }
            preferences.edit { putInt(preferenceKey, widgetId) }
        }
        val hostView = widgetHost.createView(this, widgetId, info)
        container.addView(
            hostView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private companion object {
        const val WIDGET_PREFERENCES = "launcher_widget_host"
        const val WIDGET_EMPTY_TEXT_SIZE_SP = 20f
    }
}
