package com.android.car.docklib.sample

import android.app.Activity
import android.content.ComponentName
import android.os.Bundle
import com.android.car.docklib.DockInterface
import com.android.car.docklib.data.DockAppItem
import com.android.car.docklib.view.DockAdapter
import com.android.car.docklib.view.DockView
import java.util.UUID

/** Deterministic host used for Dock UI/event instrumentation without packaging it in CarLauncher. */
class DockHostActivity : Activity() {
    private val dockController =
        object : DockInterface {
            override fun appPinned(componentName: ComponentName) = Unit

            override fun appPinned(
                componentName: ComponentName,
                index: Int,
            ) = Unit

            override fun appPinned(id: UUID) = Unit

            override fun appUnpinned(componentName: ComponentName) = Unit

            override fun appUnpinned(id: UUID) = Unit

            override fun appLaunched(componentName: ComponentName) = Unit

            override fun packageRemoved(packageName: String) = Unit

            override fun packageAdded(packageName: String) = Unit

            override fun launchApp(
                componentName: ComponentName,
                isMediaApp: Boolean,
            ) = Unit

            override fun getIconColorWithScrim(componentName: ComponentName): Int = 0xFF3F51B5.toInt()

            override fun getMediaServiceComponents(): Set<ComponentName> = emptySet()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dock_host)
        val dockView = findViewById<DockView>(R.id.dock_view)
        val adapter = DockAdapter(dockController)
        dockView.setDockAdapter(adapter)
        adapter.submitList(
            listOf(
                DockAppItem(
                    id = UUID.nameUUIDFromBytes("dock-sample".toByteArray()),
                    type = DockAppItem.Type.STATIC,
                    component = ComponentName(this, DockHostActivity::class.java),
                    name = "Dock sample",
                    icon = getDrawable(android.R.drawable.ic_menu_view)!!,
                    iconColor = 0xFF3F51B5.toInt(),
                    isDistractionOptimized = true,
                    isMediaApp = false,
                ),
            ),
        )
    }
}
