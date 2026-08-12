package com.android.car.carlauncher

/**
 * Public AppGrid source contract retained while implementation moves into feature:appgrid.
 * Kotlin constants compile to Java static fields so existing source callers keep their syntax.
 */
interface AppGridConstants {
    object PageOrientation {
        const val HORIZONTAL = 0
        const val VERTICAL = 1
    }

    object AppItemBoundDirection {
        const val NONE = 0
        const val TOP = 1
        const val BOTTOM = 2
        const val LEFT = 3
        const val RIGHT = 4
    }

    companion object {
        @JvmStatic
        fun isHorizontal(pageOrientation: Int): Boolean = pageOrientation == PageOrientation.HORIZONTAL
    }
}
