package com.android.car.carlauncher.feature.home.domain

import com.android.car.carlauncher.core.model.DisplayTarget
import com.android.car.carlauncher.core.model.LauncherComponent
import com.android.car.carlauncher.core.model.LauncherTask
import kotlinx.coroutines.flow.Flow

enum class HomePane {
    MAP,
    MEDIA,
}

data class HomePaneState(
    val pane: HomePane,
    val task: LauncherTask?,
    val targetComponent: LauncherComponent?,
    val display: DisplayTarget,
)

interface HomeTaskRepository {
    val panes: Flow<List<HomePaneState>>

    suspend fun requestEmbed(pane: HomePane): Result<Unit>

    suspend fun removeTask(pane: HomePane): Result<Unit>
}

/**
 * The content mode for the embedded side of HOME.  This is deliberately independent from
 * TaskView so it can be driven and verified without a car-service connection.
 */
enum class HomeTaskPaneMode {
    NAVIGATION,
    EMBEDDED_APP,
    ERROR,
}

enum class HomeEmbeddedTargetType {
    NAVIGATION,
    APPLICATION,
}

/**
 * A resolved activity to host inside the controlled TaskView.
 *
 * [launchIntentUri] preserves an OEM preferred maps intent (including its action and categories)
 * while keeping the component as the stable identity used by the state machine.
 */
data class HomeEmbeddedTaskTarget(
    val componentName: String,
    val label: String,
    val type: HomeEmbeddedTargetType,
    val launchIntentUri: String? = null,
)

sealed interface HomeEmbeddedTaskState {
    data object Idle : HomeEmbeddedTaskState

    data class Loading(
        val target: HomeEmbeddedTaskTarget,
    ) : HomeEmbeddedTaskState

    data class Running(
        val target: HomeEmbeddedTaskTarget,
    ) : HomeEmbeddedTaskState

    data class Error(
        val error: HomeEmbeddedTaskError,
    ) : HomeEmbeddedTaskState
}

data class HomeEmbeddedTaskError(
    val title: String,
    val message: String,
    val componentName: String? = null,
)

/** Resolves the map activity without applying App Grid's driving safety gate. */
interface NavigationTargetResolver {
    suspend fun resolve(): Result<HomeEmbeddedTaskTarget>
}

/** Emits when the system Terms-of-Service state changes and maps must be resolved again. */
interface NavigationTargetInvalidation {
    val changes: Flow<Unit>
}
