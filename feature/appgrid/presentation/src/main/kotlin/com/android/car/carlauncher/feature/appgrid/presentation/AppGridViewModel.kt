package com.android.car.carlauncher.feature.appgrid.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.carlauncher.core.model.DisplayTarget
import com.android.car.carlauncher.core.model.DrivingRestriction
import com.android.car.carlauncher.core.model.LauncherComponent
import com.android.car.carlauncher.feature.appgrid.domain.AppGridItem
import com.android.car.carlauncher.feature.appgrid.domain.AppGridMode
import com.android.car.carlauncher.feature.appgrid.domain.AppGridRepository
import com.android.car.carlauncher.feature.appgrid.domain.AppGridShortcut
import com.android.car.carlauncher.feature.appgrid.domain.AppGridStateReducer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AppGridEvent {
    data class Error(
        val message: String,
    ) : AppGridEvent

    data class ShowShortcuts(
        val item: AppGridItem,
        val shortcuts: List<AppGridShortcut>,
    ) : AppGridEvent
}

@HiltViewModel
class AppGridViewModel
    @Inject
    constructor(
        private val repository: AppGridRepository,
    ) : ViewModel() {
        private val mode = MutableStateFlow(AppGridMode.ALL_APPS)
        private val query = MutableStateFlow("")
        private val reorderMode = MutableStateFlow(false)
        private val mutableEvents = MutableSharedFlow<AppGridEvent>(extraBufferCapacity = 1)

        val events = mutableEvents.asSharedFlow()
        val uiState: StateFlow<AppGridUiState> =
            combine(repository.state, mode, query, reorderMode) { state, currentMode, currentQuery, isReorderMode ->
                val canSearch = state.restriction != DrivingRestriction.NO_KEYBOARD
                val effectiveQuery = currentQuery.takeIf { canSearch }.orEmpty()
                val canReorder = AppGridStateReducer.canReorder(state.restriction, effectiveQuery, currentMode)
                AppGridUiState(
                    state = state,
                    visibleItems = AppGridStateReducer.filter(state.items, currentMode, effectiveQuery),
                    mode = currentMode,
                    query = effectiveQuery,
                    canSearch = canSearch,
                    isReorderMode = isReorderMode && canReorder,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppGridUiState())

        fun updateMode(value: AppGridMode) {
            mode.value = value
            query.value = ""
            reorderMode.value = false
        }

        fun updateQuery(value: String) {
            if (uiState.value.canSearch) query.value = value
        }

        fun toggleReorderMode() {
            if (uiState.value.canReorder) reorderMode.value = !reorderMode.value
        }

        fun launch(
            item: AppGridItem,
            display: DisplayTarget,
        ) {
            viewModelScope.launch {
                repository.launch(item, display, uiState.value.mode).onFailure(::emitError)
            }
        }

        fun saveOrder(order: List<LauncherComponent>) {
            if (!uiState.value.canReorder) return
            viewModelScope.launch { repository.saveOrder(order).onFailure(::emitError) }
        }

        fun showShortcuts(item: AppGridItem) {
            if (uiState.value.isReorderMode) return
            viewModelScope.launch {
                repository.shortcuts(item).fold(
                    onSuccess = { shortcuts ->
                        if (shortcuts.isNotEmpty()) mutableEvents.emit(AppGridEvent.ShowShortcuts(item, shortcuts))
                    },
                    onFailure = ::emitError,
                )
            }
        }

        fun launchShortcut(
            item: AppGridItem,
            shortcut: AppGridShortcut,
            display: DisplayTarget,
        ) {
            viewModelScope.launch { repository.launchShortcut(item, shortcut, display).onFailure(::emitError) }
        }

        fun dismissTosBanner() {
            viewModelScope.launch { repository.dismissTosBanner() }
        }

        fun reviewTos(display: DisplayTarget) {
            viewModelScope.launch { repository.reviewTos(display).onFailure(::emitError) }
        }

        private fun emitError(throwable: Throwable) {
            mutableEvents.tryEmit(AppGridEvent.Error(throwable.message ?: "Unable to complete app-grid action."))
        }
    }
