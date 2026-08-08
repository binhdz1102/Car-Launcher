package com.android.car.carlauncher.homescreen

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.android.car.carlauncher.R
import com.android.car.carlauncher.core.ui.applySystemBarInsets

/** Safe embedded fallback requested by the scalable SystemUI map panel before map TOS is ready. */
class MapTosActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_fallback)
        applySystemBarInsets()
        findViewById<View>(R.id.map_fallback_apps).setOnClickListener {
            startActivity(
                Intent("com.android.car.carlauncher.ACTION_APP_GRID")
                    .setPackage(packageName),
            )
        }
    }
}
