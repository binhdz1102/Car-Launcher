package com.android.car.carlauncher.fixture

/** Stable actions used by the local parity runner; never consumed by production code. */
object FixtureContract {
    const val PACKAGE_NAME = "com.android.car.carlauncher.fixture"
    const val ACTION_MEDIA_PLAY = "$PACKAGE_NAME.action.MEDIA_PLAY"
    const val ACTION_MEDIA_PAUSE = "$PACKAGE_NAME.action.MEDIA_PAUSE"
    const val ACTION_MEDIA_NEXT = "$PACKAGE_NAME.action.MEDIA_NEXT"
    const val ACTION_MEDIA_PREVIOUS = "$PACKAGE_NAME.action.MEDIA_PREVIOUS"
    const val ACTION_MEDIA_RESET = "$PACKAGE_NAME.action.MEDIA_RESET"
    const val EXTRA_SCENARIO = "$PACKAGE_NAME.extra.SCENARIO"
}
