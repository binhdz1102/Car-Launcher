package com.android.car.carlauncher

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.car.carlauncher.feature.appgrid.domain.AppGridRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Automotive Settings entry that restores the application grid's default A-Z ordering. */
@AndroidEntryPoint
class ResetLauncherActivity : AppCompatActivity() {
    @Inject lateinit var appGridRepository: AppGridRepository

    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.reset_appgrid_title)
        builder.setMessage(R.string.reset_appgrid_dialogue_message)
        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            lifecycleScope.launch {
                appGridRepository.clearOrder()
                finish()
            }
        }
        builder.setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
        builder.setOnCancelListener { finish() }
        dialog = builder.create().also(AlertDialog::show)
    }

    override fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }
}
