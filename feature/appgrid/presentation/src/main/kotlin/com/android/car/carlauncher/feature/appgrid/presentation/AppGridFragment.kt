package com.android.car.carlauncher.feature.appgrid.presentation

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.android.car.carlauncher.core.model.DisplayTarget
import com.android.car.carlauncher.feature.appgrid.domain.AppGridMode
import com.android.car.carlauncher.feature.appgrid.domain.AppGridOrientation
import com.android.car.carlauncher.feature.appgrid.presentation.databinding.FragmentAppGridBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Feature-owned XML App Grid hosted by the stable app-level `AppGridActivity` component. */
@AndroidEntryPoint
class AppGridFragment : Fragment(R.layout.fragment_app_grid) {
    private val viewModel: AppGridViewModel by viewModels()
    private val showToolbar: Boolean by lazy { resources.getBoolean(R.bool.app_grid_show_toolbar) }
    private var binding: FragmentAppGridBinding? = null
    private lateinit var adapter: AppGridAdapter
    private var initialFocusRequested = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val currentBinding = FragmentAppGridBinding.bind(view)
        binding = currentBinding
        if (!showToolbar) {
            currentBinding.appGridTitle.visibility = View.GONE
            currentBinding.appGridClose.visibility = View.GONE
            currentBinding.appGridSearch.visibility = View.GONE
            currentBinding.appGridReorder.visibility = View.GONE
        }

        adapter =
            AppGridAdapter(
                onClick = { item -> viewModel.launch(item, displayTarget()) },
                onLongClick = { item ->
                    if (viewModel.uiState.value.isReorderMode) {
                        false
                    } else {
                        viewModel.showShortcuts(item)
                        true
                    }
                },
            )
        currentBinding.appGrid.adapter = adapter
        currentBinding.appGrid.layoutManager =
            GridLayoutManager(requireContext(), GRID_COLUMNS, RecyclerView.VERTICAL, false)
        attachReorderController(currentBinding.appGrid)

        currentBinding.appGridClose.setOnClickListener { requireActivity().finish() }
        currentBinding.appGridReorder.setOnClickListener { viewModel.toggleReorderMode() }
        currentBinding.tosReview.setOnClickListener { viewModel.reviewTos(displayTarget()) }
        currentBinding.tosDismiss.setOnClickListener { viewModel.dismissTosBanner() }
        currentBinding.appGridSearch.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    sequence: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    sequence: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) = viewModel.updateQuery(sequence?.toString().orEmpty())

                override fun afterTextChanged(sequence: Editable?) = Unit
            },
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
        updateMode(modeFromArguments())
    }

    override fun onDestroyView() {
        binding = null
        initialFocusRequested = false
        super.onDestroyView()
    }

    fun updateMode(mode: AppGridMode) {
        viewModel.updateMode(mode)
    }

    private fun render(state: AppGridUiState) {
        val currentBinding = binding ?: return
        adapter.submitItems(state.visibleItems)
        if (!initialFocusRequested && state.visibleItems.isNotEmpty()) {
            initialFocusRequested = true
            currentBinding.appGrid.post {
                currentBinding.appGrid.getChildAt(0)?.requestFocus()
            }
        }
        currentBinding.appGridEmpty.visibility =
            if (state.visibleItems.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        currentBinding.appGridSearch.visibility =
            if (showToolbar && state.canSearch) View.VISIBLE else View.GONE
        if (!state.canSearch && currentBinding.appGridSearch.text.isNotEmpty()) {
            currentBinding.appGridSearch.text?.clear()
        }
        currentBinding.appGridReorder.visibility =
            if (showToolbar && state.canReorder) View.VISIBLE else View.GONE
        currentBinding.appGridReorder.text =
            getString(if (state.isReorderMode) R.string.app_grid_done_reordering else R.string.app_grid_reorder)
        currentBinding.tosBanner.visibility =
            if (state.state?.shouldShowTosBanner == true) View.VISIBLE else View.GONE
        currentBinding.appGridTitle.setText(
            when (state.mode) {
                AppGridMode.ALL_APPS -> R.string.app_grid_title
                AppGridMode.MEDIA_ONLY,
                AppGridMode.MEDIA_POPUP,
                -> R.string.app_grid_media_title
            },
        )
        configureGrid(state.state?.orientation ?: AppGridOrientation.HORIZONTAL)
    }

    private fun configureGrid(orientation: AppGridOrientation) {
        val recycler = binding?.appGrid ?: return
        val layoutManager = recycler.layoutManager as? GridLayoutManager ?: return
        layoutManager.orientation =
            if (orientation == AppGridOrientation.VERTICAL) {
                RecyclerView.HORIZONTAL
            } else {
                // Stock horizontal App Grid fills five columns before advancing a row.
                RecyclerView.VERTICAL
            }
        layoutManager.spanCount = if (orientation == AppGridOrientation.VERTICAL) GRID_ROWS else GRID_COLUMNS
        recycler.post {
            val availableWidth = recycler.width - recycler.paddingLeft - recycler.paddingRight
            val availableHeight = recycler.height - recycler.paddingTop - recycler.paddingBottom
            adapter.updateCellSize(availableWidth / GRID_COLUMNS, availableHeight / GRID_ROWS)
        }
    }

    private fun handleEvent(event: AppGridEvent) {
        when (event) {
            is AppGridEvent.Error ->
                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
            is AppGridEvent.ShowShortcuts -> showShortcuts(event)
        }
    }

    private fun showShortcuts(event: AppGridEvent.ShowShortcuts) {
        val anchor = binding?.appGrid ?: return
        PopupMenu(requireContext(), anchor).apply {
            event.shortcuts.forEachIndexed { index, shortcut ->
                menu.add(0, index, index, shortcut.longLabel ?: shortcut.shortLabel)
            }
            setOnMenuItemClickListener { menuItem ->
                event.shortcuts.getOrNull(menuItem.itemId)?.let { shortcut ->
                    viewModel.launchShortcut(event.item, shortcut, displayTarget())
                }
                true
            }
            show()
        }
    }

    private fun attachReorderController(recycler: RecyclerView) {
        ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                0,
            ) {
                override fun isLongPressDragEnabled(): Boolean {
                    val state = viewModel.uiState.value
                    return state.isReorderMode && state.canReorder
                }

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
        ).attachToRecyclerView(recycler)
    }

    private fun displayTarget(): DisplayTarget = DisplayTarget(activityDisplayId())

    private fun activityDisplayId(): Int = requireActivity().display?.displayId ?: DEFAULT_DISPLAY_ID

    private fun modeFromArguments(): AppGridMode = AppGridMode.fromIntentValue(arguments?.getString(ARG_MODE))

    companion object {
        private const val ARG_MODE = "mode"
        private const val GRID_COLUMNS = 5
        // The stock API 37 launcher allocates four rows in the 1920x1080 app-grid surface.
        private const val GRID_ROWS = 4
        private const val DEFAULT_DISPLAY_ID = 0

        fun newInstance(mode: AppGridMode): AppGridFragment = fragment(newArguments(mode))

        private fun newArguments(mode: AppGridMode): Bundle = Bundle().apply { putString(ARG_MODE, mode.name) }

        private fun fragment(args: Bundle): AppGridFragment = AppGridFragment().apply { arguments = args }
    }
}
