package com.android.car.carlauncher.feature.launcher.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.carlauncher.feature.launcher.domain.LaunchableApp
import com.android.car.carlauncher.feature.launcher.domain.LauncherAppsRepository
import com.android.car.carlauncher.feature.launcher.domain.LauncherRestrictions
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

data class AppGridUiState(
    val apps: List<LaunchableApp> = emptyList(),
    val restrictions: LauncherRestrictions = LauncherRestrictions(),
    val query: String = "",
) {
    val canSearch: Boolean get() = !restrictions.noKeyboard
    val canReorder: Boolean get() = query.isBlank() && !restrictions.requiresDistractionOptimization
}

@HiltViewModel
class AppGridViewModel
    @Inject
    constructor(
        private val launcherAppsRepository: LauncherAppsRepository,
    ) : ViewModel() {
        private val query = MutableStateFlow("")
        private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)

        val errors = mutableErrors.asSharedFlow()

        val uiState: StateFlow<AppGridUiState> =
            combine(
                launcherAppsRepository.launchableApps,
                launcherAppsRepository.restrictions,
                query,
            ) { apps, restrictions, text ->
                val effectiveQuery = text.takeUnless { restrictions.noKeyboard }.orEmpty()
                AppGridUiState(
                    apps =
                        apps.filter {
                            effectiveQuery.isBlank() || it.label.contains(effectiveQuery, ignoreCase = true)
                        },
                    restrictions = restrictions,
                    query = effectiveQuery,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppGridUiState())

        fun updateQuery(value: String) {
            query.value = value.takeIf { !launcherAppsRepository.restrictions.value.noKeyboard }.orEmpty()
        }

        fun launch(app: LaunchableApp) {
            viewModelScope.launch {
                launcherAppsRepository.launch(app).onFailure {
                    mutableErrors.emit(it.message ?: "Unable to open the selected application.")
                }
            }
        }

        fun saveOrder(componentNames: List<String>) {
            if (!uiState.value.canReorder) return
            viewModelScope.launch { launcherAppsRepository.saveOrderedComponents(componentNames) }
        }
    }
