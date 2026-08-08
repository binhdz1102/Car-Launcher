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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.android.car.carlauncher.core.ui.applySystemBarInsets
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
        applySystemBarInsets()

        val search = findViewById<EditText>(R.id.app_search)
        val apps = findViewById<RecyclerView>(R.id.app_grid)
        val empty = findViewById<TextView>(R.id.app_grid_empty)
        val adapter = AppGridAdapter(::selectApp)
        apps.layoutManager = GridLayoutManager(this, GRID_COLUMNS)
        apps.adapter = adapter
        ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                0,
            ) {
                override fun isLongPressDragEnabled(): Boolean = viewModel.uiState.value.canReorder

                override fun onMove(
                    recyclerView: RecyclerView,
                    source: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean = adapter.move(source.bindingAdapterPosition, target.bindingAdapterPosition)

                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder,
                    direction: Int,
                ) = Unit

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ) {
                    super.clearView(recyclerView, viewHolder)
                    viewModel.saveOrder(adapter.componentOrder())
                }
            },
        ).attachToRecyclerView(apps)

        search.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    viewModel.updateQuery(s?.toString().orEmpty())
                }

                override fun afterTextChanged(s: Editable?) = Unit
            },
        )
        findViewById<android.view.View>(R.id.app_grid_close).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.app_navigation).setOnClickListener {
            returnToLauncher(selectNavigation = true)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitItems(state.apps)
                    empty.visibility =
                        if (state.apps.isEmpty()) {
                            android.view.View.VISIBLE
                        } else {
                            android.view.View.GONE
                        }
                    search.visibility =
                        if (state.canSearch) {
                            android.view.View.VISIBLE
                        } else {
                            android.view.View.GONE
                        }
                    if (!state.canSearch && search.text.isNotEmpty()) search.text.clear()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errors.collect { message ->
                    android.widget.Toast
                        .makeText(
                            this@AppGridActivity,
                            message,
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }
        Timber.tag(TAG).d("App grid created")
    }

    private fun selectApp(app: com.android.car.carlauncher.feature.launcher.domain.LaunchableApp) {
        if (!app.isEnabled) return
        viewModel.launch(app)
    }

    private fun returnToLauncher(selectNavigation: Boolean = false) {
        val intent =
            Intent().apply {
                component = android.content.ComponentName(packageName, CAR_LAUNCHER_COMPONENT)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                if (selectNavigation) putExtra(EXTRA_SELECT_NAVIGATION, true)
            }
        startActivity(intent)
        finish()
        Timber.tag(TAG).i("Returned to launcher; navigation=%s", selectNavigation)
    }

    private companion object {
        const val TAG = "CarLauncher.AppGridActivity"
        const val GRID_COLUMNS = 4
    }
}
