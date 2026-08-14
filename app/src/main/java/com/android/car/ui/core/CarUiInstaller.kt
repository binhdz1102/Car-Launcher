package com.android.car.ui.core

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor

/**
 * Standalone compatibility endpoint for the CarUi installer provider in the stock merge.
 *
 * The real CarUi implementation is supplied by the platform image. A replacement APK must still
 * publish the authority so framework/provider resolution remains deterministic; no-op behavior is
 * intentional when the optional implementation is not available.
 */
class CarUiInstaller : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor? = null
}
