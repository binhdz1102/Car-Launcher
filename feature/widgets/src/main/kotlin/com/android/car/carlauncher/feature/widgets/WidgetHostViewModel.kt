package com.android.car.carlauncher.feature.widgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.carlauncher.core.model.DisplayTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WidgetHostViewModel
    @Inject
    constructor(
        private val controller: WidgetHostController,
    ) : ViewModel() {
        val state: StateFlow<WidgetHostState> = controller.state

        fun bind(display: DisplayTarget) {
            viewModelScope.launch {
                controller.bind(display).onFailure { error ->
                    Timber.tag(TAG).w(error, "Unable to bind configured widgets")
                }
            }
        }

        fun unbind() {
            viewModelScope.launch { controller.unbind() }
        }

        private companion object {
            const val TAG = "CarLauncher.WidgetHostViewModel"
        }
    }
