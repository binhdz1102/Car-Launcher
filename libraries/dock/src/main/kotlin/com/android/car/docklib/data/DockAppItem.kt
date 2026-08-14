package com.android.car.docklib.data

import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.Drawable
import java.util.UUID

/** Public item model used by Dock hosts and adapters. */
data class DockAppItem(
    val id: UUID = UUID.randomUUID(),
    val type: Type,
    val component: ComponentName,
    val name: String,
    val icon: Drawable,
    val iconColor: Int,
    val iconColorScrim: Int = DEFAULT_ICON_COLOR_SCRIM,
    val isDistractionOptimized: Boolean,
    val isMediaApp: Boolean,
) {
    val iconColorWithScrim: Int = getIconColorWithScrim(iconColor, iconColorScrim)

    enum class Type(
        val value: String,
    ) {
        DYNAMIC("DYNAMIC"),
        STATIC("STATIC"),
        ;

        override fun toString(): String = value
    }

    companion object {
        private const val DEFAULT_ICON_COLOR_SCRIM = 0x66FFFFFF

        /** Composes an icon color with the supplied scrim without depending on Launcher3. */
        @JvmStatic
        fun getIconColorWithScrim(
            iconColor: Int,
            iconColorScrim: Int = DEFAULT_ICON_COLOR_SCRIM,
        ): Int {
            val alpha = Color.alpha(iconColorScrim)
            val inverse = 255 - alpha
            return Color.argb(
                255,
                (Color.red(iconColorScrim) * alpha + Color.red(iconColor) * inverse) / 255,
                (Color.green(iconColorScrim) * alpha + Color.green(iconColor) * inverse) / 255,
                (Color.blue(iconColorScrim) * alpha + Color.blue(iconColor) * inverse) / 255,
            )
        }
    }
}
