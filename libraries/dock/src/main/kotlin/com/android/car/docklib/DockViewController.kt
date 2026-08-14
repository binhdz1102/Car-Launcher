package com.android.car.docklib

import com.android.car.docklib.data.DockAppItem
import com.android.car.docklib.view.DockAdapter
import com.android.car.docklib.view.DockView

/**
 * Lifecycle-safe host controller for [DockView].
 *
 * AOSP hosts own the business ViewModel and provide updates to this library. Keeping the
 * controller deliberately small makes that ownership explicit while preserving the stable
 * `DockViewController` entry point used by sample and SystemUI hosts.
 */
class DockViewController(
    private val dockView: DockView,
    private val dockInterface: DockInterface,
) {
    private val adapter = DockAdapter(dockInterface)
    private var destroyed = false

    init {
        dockView.setDockAdapter(adapter)
    }

    fun submitList(items: List<DockAppItem>) {
        check(!destroyed) { "DockViewController has been destroyed" }
        adapter.submitList(items)
    }

    fun adapter(): DockAdapter = adapter

    /** Detaches the adapter so a destroyed host cannot retain its view hierarchy. */
    fun destroy() {
        if (!destroyed) {
            dockView.adapter = null
            destroyed = true
        }
    }
}
