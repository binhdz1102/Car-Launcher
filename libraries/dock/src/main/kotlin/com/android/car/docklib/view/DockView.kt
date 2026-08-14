package com.android.car.docklib.view

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

/** XML host surface for Dock. Hosts may supply their own layout and item styling. */
class DockView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : RecyclerView(context, attrs) {
        fun setDockAdapter(adapter: DockAdapter) {
            setAdapter(adapter)
        }
    }
