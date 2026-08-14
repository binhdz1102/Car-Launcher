package com.android.car.carlauncher.core.common

/**
 * Source-compatible alias for clients that still import the pre-boundary dispatcher type.
 * Construction and Hilt binding are centralized in core:platform.
 */
@Deprecated("Use core.platform.CoroutineDispatchers")
typealias CoroutineDispatchers = com.android.car.carlauncher.core.platform.CoroutineDispatchers
