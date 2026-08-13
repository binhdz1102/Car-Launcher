package com.android.car.carlauncher.feature.appgrid.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.car.carlauncher.core.model.LauncherComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appGridPreferences by preferencesDataStore("launcher_app_grid")
private val orderedComponentsKey = stringPreferencesKey("ordered_components")

/**
 * Reads both the previous Gradle-launcher DataStore value and AOSP's `files/order.data`.
 * Every order write updates both stores so a rollback to the stock launcher retains app order.
 */
@Singleton
class DualAppGridOrderStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : AppGridOrderStore {
        override suspend fun read(): List<LauncherComponent> =
            withContext(Dispatchers.IO) {
                val dataStoreOrder =
                    context.appGridPreferences.data
                        .first()[orderedComponentsKey]
                        .toComponents()
                if (dataStoreOrder.isNotEmpty()) return@withContext dataStoreOrder

                val stockOrder = AppGridOrderProto.read(File(context.filesDir, STOCK_ORDER_FILE), currentUserId())
                if (stockOrder.isNotEmpty()) {
                    saveDataStore(stockOrder)
                }
                stockOrder
            }

        override suspend fun write(order: List<LauncherComponent>) {
            withContext(Dispatchers.IO) {
                val canonicalOrder = order.distinctBy(LauncherComponent::flattened)
                saveDataStore(canonicalOrder)
                AppGridOrderProto.write(File(context.filesDir, STOCK_ORDER_FILE), canonicalOrder)
            }
        }

        override suspend fun clear() {
            withContext(Dispatchers.IO) {
                context.appGridPreferences.edit { preferences -> preferences.remove(orderedComponentsKey) }
                File(context.filesDir, STOCK_ORDER_FILE).takeIf(File::exists)?.delete()
            }
        }

        private suspend fun saveDataStore(order: List<LauncherComponent>) {
            context.appGridPreferences.edit { preferences ->
                preferences[orderedComponentsKey] = order.joinToString("|") { component -> component.flattened }
            }
        }

        private fun String?.toComponents(): List<LauncherComponent> =
            this
                .orEmpty()
                .split('|')
                .mapNotNull { flattened ->
                    val separator = flattened.indexOf('/')
                    if (separator <= 0 || separator == flattened.lastIndex) {
                        null
                    } else {
                        LauncherComponent(
                            packageName = flattened.substring(0, separator),
                            className = flattened.substring(separator + 1),
                            userId = currentUserId(),
                        )
                    }
                }

        private fun currentUserId(): Int = android.os.Process.myUid() / PER_USER_RANGE

        private companion object {
            const val PER_USER_RANGE = 100_000
            const val STOCK_ORDER_FILE = "order.data"
        }
    }

/** Minimal, dependency-free codec for the stock delimited `LauncherItemListMessage` protobuf. */
private object AppGridOrderProto {
    private const val OUTER_ITEM_FIELD = 1
    private const val PACKAGE_FIELD = 1
    private const val DISPLAY_NAME_FIELD = 2
    private const val RELATIVE_POSITION_FIELD = 3
    private const val CONTAINER_ID_FIELD = 4
    private const val CLASS_FIELD = 5
    private const val LENGTH_DELIMITED = 2
    private const val VARINT = 0
    private const val VARINT_DATA_MASK = 0x7f
    private const val VARINT_CONTINUATION_BIT = 0x80
    private const val BYTE_MASK = 0xff
    private const val VARINT_SHIFT_BITS = 7
    private const val TAG_FIELD_SHIFT_BITS = 3
    private const val TAG_WIRE_TYPE_MASK = 0x07
    private const val FIXED_64_BIT_WIDTH = 8
    private const val FIXED_32_BIT_WIDTH = 4

    fun read(
        file: File,
        userId: Int,
    ): List<LauncherComponent> =
        runCatching {
            if (!file.isFile) return emptyList()
            val bytes = file.readBytes()
            val (messageSize, payloadStart) = bytes.readVarint(0)
            val payloadEnd = (payloadStart + messageSize.toInt()).coerceAtMost(bytes.size)
            var position = payloadStart
            buildList<ParsedOrder> {
                while (position < payloadEnd) {
                    val (tag, next) = bytes.readVarint(position)
                    position = next
                    if (tag.fieldNumber == OUTER_ITEM_FIELD && tag.wireType == LENGTH_DELIMITED) {
                        val (itemSize, itemStart) = bytes.readVarint(position)
                        val itemEnd = (itemStart + itemSize.toInt()).coerceAtMost(payloadEnd)
                        bytes.parseItem(itemStart, itemEnd, userId)?.let(::add)
                        position = itemEnd
                    } else {
                        position = bytes.skipField(position, tag.wireType, payloadEnd)
                    }
                }
            }.sortedWith(compareBy<ParsedOrder> { it.relativePosition ?: Int.MAX_VALUE })
                .map(ParsedOrder::component)
        }.getOrDefault(emptyList())

    fun write(
        file: File,
        order: List<LauncherComponent>,
    ) {
        val payload = ArrayList<Byte>()
        order.forEachIndexed { index, component ->
            val item = encodeItem(component, index)
            payload.writeTag(OUTER_ITEM_FIELD, LENGTH_DELIMITED)
            payload.writeVarint(item.size.toLong())
            payload.addAll(item)
        }
        val encoded = ArrayList<Byte>()
        encoded.writeVarint(payload.size.toLong())
        encoded.addAll(payload)
        val temporaryFile = File(file.parentFile, "${file.name}.new")
        temporaryFile.outputStream().use { output -> output.write(encoded.toByteArray()) }
        if (!temporaryFile.renameTo(file)) {
            temporaryFile.delete()
            error("Unable to atomically write ${file.name}")
        }
    }

    private fun encodeItem(
        component: LauncherComponent,
        position: Int,
    ): List<Byte> =
        ArrayList<Byte>().apply {
            writeString(PACKAGE_FIELD, component.packageName)
            // The stock reader only uses package/class for ordering. Keep all proto2 required fields.
            writeString(DISPLAY_NAME_FIELD, component.className)
            writeTag(RELATIVE_POSITION_FIELD, VARINT)
            writeVarint(position.toLong())
            writeTag(CONTAINER_ID_FIELD, VARINT)
            writeVarint(-1L)
            writeString(CLASS_FIELD, component.className)
        }

    private fun ByteArray.parseItem(
        start: Int,
        end: Int,
        userId: Int,
    ): ParsedOrder? {
        var position = start
        var packageName: String? = null
        var className: String? = null
        var relativePosition: Int? = null
        while (position < end) {
            val (tag, next) = readVarint(position)
            position = next
            when {
                tag.fieldNumber == PACKAGE_FIELD && tag.wireType == LENGTH_DELIMITED -> {
                    val (length, valueStart) = readVarint(position)
                    val valueEnd = (valueStart + length.toInt()).coerceAtMost(end)
                    packageName = copyOfRange(valueStart, valueEnd).decodeToString()
                    position = valueEnd
                }

                tag.fieldNumber == CLASS_FIELD && tag.wireType == LENGTH_DELIMITED -> {
                    val (length, valueStart) = readVarint(position)
                    val valueEnd = (valueStart + length.toInt()).coerceAtMost(end)
                    className = copyOfRange(valueStart, valueEnd).decodeToString()
                    position = valueEnd
                }

                tag.fieldNumber == RELATIVE_POSITION_FIELD && tag.wireType == VARINT -> {
                    val (value, valueEnd) = readVarint(position)
                    relativePosition = value.toInt()
                    position = valueEnd
                }

                else -> position = skipField(position, tag.wireType, end)
            }
        }
        return if (!packageName.isNullOrBlank() && !className.isNullOrBlank()) {
            ParsedOrder(LauncherComponent(packageName, className, userId), relativePosition)
        } else {
            null
        }
    }

    private data class ParsedOrder(
        val component: LauncherComponent,
        val relativePosition: Int?,
    )

    private fun ByteArray.readVarint(initialPosition: Int): Pair<Long, Int> {
        var position = initialPosition
        var shift = 0
        var value = 0L
        while (position < size && shift < Long.SIZE_BITS) {
            val current = this[position++].toInt() and BYTE_MASK
            value = value or ((current and VARINT_DATA_MASK).toLong() shl shift)
            if (current and VARINT_CONTINUATION_BIT == 0) return value to position
            shift += VARINT_SHIFT_BITS
        }
        error("Malformed protobuf varint")
    }

    private val Long.fieldNumber: Int
        get() = (this ushr TAG_FIELD_SHIFT_BITS).toInt()

    private val Long.wireType: Int
        get() = (this and TAG_WIRE_TYPE_MASK.toLong()).toInt()

    private fun ByteArray.skipField(
        initialPosition: Int,
        wireType: Int,
        limit: Int,
    ): Int =
        when (wireType) {
            VARINT -> readVarint(initialPosition).second
            LENGTH_DELIMITED -> {
                val (length, contentStart) = readVarint(initialPosition)
                (contentStart + length.toInt()).coerceAtMost(limit)
            }

            FIXED_64_BIT_WIDTH -> (initialPosition + Long.SIZE_BYTES).coerceAtMost(limit)
            FIXED_32_BIT_WIDTH -> (initialPosition + Int.SIZE_BYTES).coerceAtMost(limit)
            else -> error("Unsupported protobuf wire type=$wireType")
        }

    private fun ArrayList<Byte>.writeString(
        field: Int,
        value: String,
    ) {
        val bytes = value.encodeToByteArray()
        writeTag(field, LENGTH_DELIMITED)
        writeVarint(bytes.size.toLong())
        bytes.forEach(::add)
    }

    private fun ArrayList<Byte>.writeTag(
        field: Int,
        wireType: Int,
    ) = writeVarint(((field shl TAG_FIELD_SHIFT_BITS) or wireType).toLong())

    private fun ArrayList<Byte>.writeVarint(initialValue: Long) {
        var value = initialValue
        while (value and -VARINT_CONTINUATION_BIT.toLong() != 0L) {
            add(((value and VARINT_DATA_MASK.toLong()) or VARINT_CONTINUATION_BIT.toLong()).toByte())
            value = value ushr VARINT_SHIFT_BITS
        }
        add(value.toByte())
    }
}
