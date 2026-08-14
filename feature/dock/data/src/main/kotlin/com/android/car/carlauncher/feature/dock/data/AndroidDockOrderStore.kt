package com.android.car.carlauncher.feature.dock.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.car.carlauncher.core.model.LauncherComponent
import com.android.car.carlauncher.core.platform.ApplicationScope
import com.android.car.carlauncher.core.platform.CoroutineDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dockPreferences by preferencesDataStore(name = "dock_order")

@Singleton
class AndroidDockOrderStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:ApplicationScope private val scope: CoroutineScope,
        private val dispatchers: CoroutineDispatchers,
    ) : DockOrderStore {
        private val mutableOrder = MutableStateFlow(readStockOrder())

        init {
            scope.launch {
                context.dockPreferences.data
                    .map { preferences -> preferences[ORDER_KEY] }
                    .catch { emit(null) }
                    .collect { encoded ->
                        if (encoded != null) mutableOrder.value = decodeText(encoded)
                    }
            }
        }

        override val order: StateFlow<List<LauncherComponent>> = mutableOrder.asStateFlow()

        override suspend fun writeOrder(order: List<LauncherComponent>) {
            val proto = DockProtoCodec.encode(order)
            writeStockAndCurrent(proto)
        }

        override suspend fun writeStockAndCurrent(serializedOrder: ByteArray) {
            withContext(dispatchers.io) {
                stockFile().apply {
                    parentFile?.mkdirs()
                    writeBytes(serializedOrder)
                }
                context.dockPreferences.edit { preferences ->
                    preferences[ORDER_KEY] = Base64.getEncoder().encodeToString(serializedOrder)
                }
                mutableOrder.value = DockProtoCodec.decode(serializedOrder)
            }
        }

        private fun readStockOrder(): List<LauncherComponent> =
            runCatching {
                DockProtoCodec.decode(stockFile().takeIf(File::exists)?.readBytes() ?: ByteArray(0))
            }.getOrDefault(emptyList())

        private fun decodeText(encoded: String): List<LauncherComponent> =
            runCatching { DockProtoCodec.decode(Base64.getDecoder().decode(encoded)) }
                .getOrDefault(emptyList())

        private fun stockFile(): File = context.filesDir.resolve(STOCK_FILE_NAME)

        private companion object {
            val ORDER_KEY = stringPreferencesKey("order")
            const val STOCK_FILE_NAME = "dock_item_data"
        }
    }
