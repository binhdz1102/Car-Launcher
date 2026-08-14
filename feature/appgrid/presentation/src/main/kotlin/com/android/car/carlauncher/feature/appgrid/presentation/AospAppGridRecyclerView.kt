package com.android.car.carlauncher.feature.appgrid.presentation

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView implementation used by the migrated grid.
 *
 * The AOSP AppGridRecyclerView exposes GridView as its accessibility class so rotary and OEM
 * automation clients do not depend on the implementation detail that it is backed by RecyclerView.
 */
class AospAppGridRecyclerView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : RecyclerView(context, attrs) {
        override fun getAccessibilityClassName(): CharSequence = "android.widget.GridView"
    }
