package com.android.car.carlauncher

import androidx.annotation.IntDef

/**
 * Public AppGrid source contract retained while implementation moves into feature:appgrid.
 * Kotlin constants compile to Java static fields so existing source callers keep their syntax.
 */
interface AppGridConstants {
    @IntDef(PageOrientation.HORIZONTAL, PageOrientation.VERTICAL)
    @Retention(AnnotationRetention.SOURCE)
    annotation class PageOrientation {
        companion object {
            const val HORIZONTAL = 0
            const val VERTICAL = 1
        }
    }

    @IntDef(
        AppItemBoundDirection.NONE,
        AppItemBoundDirection.TOP,
        AppItemBoundDirection.BOTTOM,
        AppItemBoundDirection.LEFT,
        AppItemBoundDirection.RIGHT,
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class AppItemBoundDirection {
        companion object {
            const val NONE = 0
            const val TOP = 1
            const val BOTTOM = 2
            const val LEFT = 3
            const val RIGHT = 4
        }
    }

    companion object {
        @JvmStatic
        fun isHorizontal(pageOrientation: Int): Boolean = pageOrientation == PageOrientation.HORIZONTAL
    }
}
