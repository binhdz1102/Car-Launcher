package com.android.car.carlauncher

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.car.carlauncher.appgridlib.R

/** AOSP two-action banner used by the TOS and restricted-app surfaces. */
class Banner
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : ConstraintLayout(context, attrs, defStyleAttr) {
        private val firstButton: TextView
        private val secondButton: TextView
        private val titleTextView: TextView

        init {
            LayoutInflater.from(context).inflate(R.layout.banner, this)
            firstButton = findViewById(R.id.banner_first_button)
            secondButton = findViewById(R.id.banner_second_button)
            titleTextView = findViewById(R.id.banner_title)
            context.obtainStyledAttributes(attrs, R.styleable.Banner, defStyleAttr, 0).use { array ->
                setFirstButtonText(array.getString(R.styleable.Banner_first_button_text))
                setSecondButtonText(array.getString(R.styleable.Banner_second_button_text))
                setTitleText(array.getString(R.styleable.Banner_title_text))
            }
        }

        fun setFirstButtonText(text: String?) {
            firstButton.text = text
        }

        fun setFirstButtonOnClickListener(listener: View.OnClickListener?) {
            firstButton.setOnClickListener(listener)
        }

        fun setSecondButtonText(text: String?) {
            secondButton.text = text
        }

        fun setSecondButtonOnClickListener(listener: View.OnClickListener?) {
            secondButton.setOnClickListener(listener)
        }

        fun setTitleText(text: String?) {
            titleTextView.text = text
        }
    }
