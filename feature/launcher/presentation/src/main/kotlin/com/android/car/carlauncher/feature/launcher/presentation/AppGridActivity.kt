package com.android.car.carlauncher.feature.launcher.presentation

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

/** XML app drawer retained under the AOSP action consumed by CarSystemUI. */
@AndroidEntryPoint
class AppGridActivity : AppCompatActivity() {
    private val viewModel: AppGridViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_grid)

        val search = findViewById<EditText>(R.id.app_search)
        val apps = findViewById<RecyclerView>(R.id.app_grid)
        val empty = findViewById<TextView>(R.id.app_grid_empty)
        val adapter = AppGridAdapter(::selectApp)
        apps.layoutManager = GridLayoutManager(this, GRID_COLUMNS)
        apps.adapter = adapter

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.updateQuery(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        findViewById<android.view.View>(R.id.app_grid_close).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.app_navigation).setOnClickListener {
            returnToLauncher(selectNavigation = true)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.apps.collect { items ->
                    adapter.submitList(items)
                    empty.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                }
            }
        }
        Timber.tag(TAG).d("App grid created")
    }

    private fun selectApp(app: com.android.car.carlauncher.feature.launcher.domain.LaunchableApp) {
        if (!app.isEnabled) return
        returnToLauncher(
            componentName = app.componentName,
            label = app.label,
        )
    }

    private fun returnToLauncher(
        componentName: String? = null,
        label: String? = null,
        selectNavigation: Boolean = false,
    ) {
        val intent = Intent().apply {
            component = android.content.ComponentName(packageName, CAR_LAUNCHER_COMPONENT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (componentName != null) {
                putExtra(EXTRA_EMBEDDED_COMPONENT, componentName)
                putExtra(EXTRA_EMBEDDED_LABEL, label)
            }
            if (selectNavigation) putExtra(EXTRA_SELECT_NAVIGATION, true)
        }
        startActivity(intent)
        finish()
        Timber.tag(TAG).i("Returned to launcher; component=%s navigation=%s", componentName, selectNavigation)
    }

    private companion object {
        const val TAG = "CarLauncher.AppGridActivity"
        const val GRID_COLUMNS = 4
    }
}
