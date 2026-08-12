package com.android.car.carlauncher.feature.home.data

import com.android.car.carlauncher.core.platform.TaskController

/** The HOME data implementation is the only layer allowed to depend on TaskView/task APIs. */
class HomeDataBoundary(
    val taskController: TaskController,
)
