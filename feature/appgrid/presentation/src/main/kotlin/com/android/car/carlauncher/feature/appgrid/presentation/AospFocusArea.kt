package com.android.car.carlauncher.feature.appgrid.presentation

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * Small standalone equivalent of the CarUi FocusArea used by the stock App Grid layout.
 *
 * The migration cannot depend on the image's CarUi widget implementation, but the accessibility
 * class name is part of the public hierarchy contract used by rotary and parity tooling.
 */
class AospFocusArea
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : ConstraintLayout(context, attrs) {
        override fun getAccessibilityClassName(): CharSequence = "com.android.car.ui.FocusArea"
    }
