package com.android.car.carlauncher.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Dispatchers are injectable at module boundaries so repository work stays testable. */
data class CoroutineDispatchers(
    val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
)
