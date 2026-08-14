package com.android.car.carlauncher.feature.appgrid.presentation

import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Explicit XML view contract for the migrated App Grid. */
class AppGridViews private constructor(
    val root: View,
    val appGrid: AospAppGridRecyclerView,
    val appGridTitle: TextView,
    val appGridClose: TextView,
    val appGridSearch: EditText,
    val appGridReorder: TextView,
    val tosBanner: LinearLayout,
    val tosReview: TextView,
    val tosDismiss: TextView,
    val appGridEmpty: TextView,
) {
    companion object {
        fun bind(root: View): AppGridViews =
            AppGridViews(
                root = root,
                appGrid = root.requireView(R.id.apps_grid),
                appGridTitle = root.requireView(R.id.app_grid_title),
                appGridClose = root.requireView(R.id.app_grid_close),
                appGridSearch = root.requireView(R.id.app_grid_search),
                appGridReorder = root.requireView(R.id.app_grid_reorder),
                tosBanner = root.requireView(R.id.tos_banner),
                tosReview = root.requireView(R.id.tos_review),
                tosDismiss = root.requireView(R.id.tos_dismiss),
                appGridEmpty = root.requireView(R.id.app_grid_empty),
            )

        private inline fun <reified T : View> View.requireView(id: Int): T = findViewById<T>(id) ?: missingView(id)

        private fun missingView(id: Int): Nothing = error("Missing App Grid view id=$id")
    }
}
