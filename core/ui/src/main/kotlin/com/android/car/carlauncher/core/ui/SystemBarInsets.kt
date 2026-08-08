package com.android.car.carlauncher.core.ui

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Keeps full-screen Automotive surfaces clear of the status and control bars. */
fun Activity.applySystemBarInsets() {
    val content = findViewById<View>(android.R.id.content)
    val initialLeft = content.paddingLeft
    val initialTop = content.paddingTop
    val initialRight = content.paddingRight
    val initialBottom = content.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(content) { view, windowInsets ->
        val insets =
            windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
        view.setPadding(
            initialLeft + insets.left,
            initialTop + insets.top,
            initialRight + insets.right,
            initialBottom + insets.bottom,
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(content)
}
