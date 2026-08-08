package com.android.car.carlauncher.feature.launcher.domain

/**
 * Pure transition model for the embedded pane. Android TaskView callbacks are intentionally
 * translated into this class so timeout, vanish, and mismatched-task paths remain unit-testable.
 */
class LauncherPaneStateMachine {
    private var lastContentMode = LauncherPaneMode.NAVIGATION
    private var currentTarget: EmbeddedAppTarget? = null

    fun start(target: EmbeddedAppTarget): EmbeddedTaskState {
        currentTarget = target
        lastContentMode = modeFor(target)
        return EmbeddedTaskState.Loading(target)
    }

    fun select(target: EmbeddedAppTarget): EmbeddedTaskState {
        currentTarget = target
        lastContentMode = modeFor(target)
        return EmbeddedTaskState.Loading(target)
    }

    fun appeared(componentName: String): EmbeddedTaskState =
        currentTarget
            ?.takeIf { it.componentName == componentName }
            ?.let(EmbeddedTaskState::Running)
            ?: error(
                title = "Unexpected embedded activity",
                message = "The embedded activity does not match the selected application.",
                componentName = componentName,
            )

    fun failed(
        title: String,
        message: String,
    ): EmbeddedTaskState = error(title, message, currentTarget?.componentName)

    fun currentMode(): LauncherPaneMode = lastContentMode

    fun currentTarget(): EmbeddedAppTarget? = currentTarget

    private fun modeFor(target: EmbeddedAppTarget): LauncherPaneMode =
        if (target.type == EmbeddedTargetType.NAVIGATION) {
            LauncherPaneMode.NAVIGATION
        } else {
            LauncherPaneMode.EMBEDDED_APP
        }

    private fun error(
        title: String,
        message: String,
        componentName: String?,
    ): EmbeddedTaskState {
        lastContentMode = LauncherPaneMode.ERROR
        return EmbeddedTaskState.Error(EmbeddedTaskError(title, message, componentName))
    }
}
