package com.android.car.carlauncher.feature.home.domain

/**
 * Pure transition model for a controlled HOME task. Android callbacks are translated here so
 * timeouts, task removal, and component mismatches are deterministic unit-test cases.
 */
class HomeTaskStateMachine {
    private var lastContentMode = HomeTaskPaneMode.NAVIGATION
    private var currentTarget: HomeEmbeddedTaskTarget? = null

    fun start(target: HomeEmbeddedTaskTarget): HomeEmbeddedTaskState = select(target)

    fun select(target: HomeEmbeddedTaskTarget): HomeEmbeddedTaskState {
        currentTarget = target
        lastContentMode = modeFor(target)
        return HomeEmbeddedTaskState.Loading(target)
    }

    fun appeared(componentName: String): HomeEmbeddedTaskState =
        currentTarget
            ?.takeIf { it.componentName == componentName }
            ?.let(HomeEmbeddedTaskState::Running)
            ?: error(
                title = "Unexpected embedded activity",
                message = "The embedded activity does not match the selected application.",
                componentName = componentName,
            )

    fun failed(
        title: String,
        message: String,
    ): HomeEmbeddedTaskState = error(title, message, currentTarget?.componentName)

    fun currentMode(): HomeTaskPaneMode = lastContentMode

    fun currentTarget(): HomeEmbeddedTaskTarget? = currentTarget

    private fun modeFor(target: HomeEmbeddedTaskTarget): HomeTaskPaneMode =
        if (target.type == HomeEmbeddedTargetType.NAVIGATION) {
            HomeTaskPaneMode.NAVIGATION
        } else {
            HomeTaskPaneMode.EMBEDDED_APP
        }

    private fun error(
        title: String,
        message: String,
        componentName: String?,
    ): HomeEmbeddedTaskState {
        lastContentMode = HomeTaskPaneMode.ERROR
        return HomeEmbeddedTaskState.Error(HomeEmbeddedTaskError(title, message, componentName))
    }
}
