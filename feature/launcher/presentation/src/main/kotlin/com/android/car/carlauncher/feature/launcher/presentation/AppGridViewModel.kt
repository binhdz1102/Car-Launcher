package com.android.car.carlauncher.feature.launcher.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.carlauncher.feature.launcher.domain.LaunchableApp
import com.android.car.carlauncher.feature.launcher.domain.LauncherAppsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppGridViewModel @Inject constructor(
    launcherAppsRepository: LauncherAppsRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")

    val apps: StateFlow<List<LaunchableApp>> = combine(
        launcherAppsRepository.launchableApps,
        query,
    ) { apps, text ->
        apps.filter { text.isBlank() || it.label.contains(text, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateQuery(value: String) {
        query.value = value
    }
}
