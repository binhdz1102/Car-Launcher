package com.android.car.dockutil.events

/** Events emitted by Dock clients. Values are part of the AAOS broadcast contract. */
enum class DockEvent(
    private val action: String,
) {
    LAUNCH("com.android.car.docklib.events.LAUNCH"),
    PIN("com.android.car.docklib.events.PIN"),
    UNPIN("com.android.car.docklib.events.UNPIN"),
    ;

    override fun toString(): String = action

    companion object {
        @JvmStatic
        fun toDockEvent(value: String?): DockEvent? = entries.firstOrNull { it.action == value }
    }
}
