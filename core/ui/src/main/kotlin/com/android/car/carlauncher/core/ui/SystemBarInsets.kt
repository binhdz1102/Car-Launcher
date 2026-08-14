package com.android.car.carlauncher.core.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

private data class InitialMargins(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/** Keeps full-screen Automotive surfaces clear of the status and control bars. */
fun Activity.applySystemBarInsets() {
    val content = findViewById<View>(android.R.id.content)
    val initialLeft = content.paddingLeft
    val initialTop = content.paddingTop
    val initialRight = content.paddingRight
    val initialBottom = content.paddingBottom
    // CarUiBaseLayout lays out AppCompat's action_bar_root inside the system-bar-safe area.
    // Applying only padding to android:id/content leaves the framework wrapper at [0,0][w,h],
    // which changes the observable UI hierarchy even though the feature content looks inset.
    val actionBarRootId = resources.getIdentifier("action_bar_root", "id", packageName)
    val actionBarRoot: View? =
        if (actionBarRootId == 0) {
            null
        } else {
            findViewById(actionBarRootId)
        }
    val initialActionMargins =
        (actionBarRoot?.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            InitialMargins(it.leftMargin, it.topMargin, it.rightMargin, it.bottomMargin)
        }
    ViewCompat.setOnApplyWindowInsetsListener(content) { view, windowInsets ->
        val insets =
            windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
        val actionLayoutParams = actionBarRoot?.layoutParams as? ViewGroup.MarginLayoutParams
        if (actionBarRoot != null && actionLayoutParams != null && initialActionMargins != null) {
            actionLayoutParams.leftMargin = initialActionMargins.left + insets.left
            actionLayoutParams.topMargin = initialActionMargins.top + insets.top
            actionLayoutParams.rightMargin = initialActionMargins.right + insets.right
            actionLayoutParams.bottomMargin = initialActionMargins.bottom + insets.bottom
            actionBarRoot.layoutParams = actionLayoutParams
            view.setPadding(initialLeft, initialTop, initialRight, initialBottom)
        } else {
            // Activities without an AppCompat/CarUi wrapper retain the original padding path.
            view.setPadding(
                initialLeft + insets.left,
                initialTop + insets.top,
                initialRight + insets.right,
                initialBottom + insets.bottom,
            )
        }
        windowInsets
    }
    ViewCompat.requestApplyInsets(content)
}
