package com.android.car.carlauncher.core.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorInt

/** Small XML/View helpers shared by launcher feature screens. */
object CarUi {
    @ColorInt val SURFACE: Int = Color.rgb(24, 29, 32)
    @ColorInt val CARD: Int = Color.rgb(39, 45, 49)
    @ColorInt val CARD_HIGH: Int = Color.rgb(53, 60, 65)
    @ColorInt val TEXT_PRIMARY: Int = Color.rgb(238, 240, 241)
    @ColorInt val TEXT_SECONDARY: Int = Color.rgb(190, 196, 200)
    @ColorInt val ACCENT: Int = Color.rgb(139, 205, 244)

    fun roundedBackground(
        context: Context,
        @ColorInt color: Int,
        radiusDp: Float = 24f,
    ): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * context.resources.displayMetrics.density
    }

    fun styleText(textView: TextView, @ColorInt color: Int = TEXT_PRIMARY) {
        textView.setTextColor(color)
        textView.includeFontPadding = false
    }

    fun show(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
