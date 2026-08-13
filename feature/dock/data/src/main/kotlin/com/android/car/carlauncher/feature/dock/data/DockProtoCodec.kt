package com.android.car.carlauncher.feature.dock.data

import com.android.car.carlauncher.core.model.LauncherComponent
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/** Small protobuf-delimited codec matching docklib's proto2 DockAppItemListMessage. */
@Suppress("MagicNumber", "ReturnCount")
internal object DockProtoCodec {
    fun encode(items: List<LauncherComponent>): ByteArray =
        ByteArrayOutputStream()
            .also { output ->
                items.forEachIndexed { position, component ->
                    val message = encodeMessage(position, component)
                    writeVarint(output, message.size.toLong())
                    output.write(message)
                }
            }.toByteArray()

    fun decode(bytes: ByteArray): List<LauncherComponent> {
        val result = mutableListOf<Pair<Int, LauncherComponent>>()
        var offset = 0
        while (offset < bytes.size) {
            val (length, nextOffset) = readVarint(bytes, offset)
            offset = nextOffset
            val end = (offset + length.toInt()).coerceAtMost(bytes.size)
            val message = bytes.copyOfRange(offset, end)
            decodeMessage(message)?.let(result::add)
            offset = end
        }
        return result.sortedBy { it.first }.map { it.second }
    }

    private fun encodeMessage(
        position: Int,
        component: LauncherComponent,
    ): ByteArray =
        ByteArrayOutputStream()
            .also { output ->
                writeVarint(output, FIELD_POSITION)
                writeVarint(output, position.toLong())
                writeString(output, FIELD_PACKAGE, component.packageName)
                writeString(output, FIELD_CLASS, component.className)
            }.toByteArray()

    private fun decodeMessage(bytes: ByteArray): Pair<Int, LauncherComponent>? {
        var offset = 0
        var position: Int? = null
        var packageName: String? = null
        var className: String? = null
        while (offset < bytes.size) {
            val (tag, tagOffset) = readVarint(bytes, offset)
            offset = tagOffset
            when (tag.toInt()) {
                FIELD_POSITION.toInt() -> {
                    val (value, valueOffset) = readVarint(bytes, offset)
                    position = value.toInt()
                    offset = valueOffset
                }
                FIELD_PACKAGE.toInt(), FIELD_CLASS.toInt() -> {
                    val (length, lengthOffset) = readVarint(bytes, offset)
                    offset = lengthOffset
                    val end = (offset + length.toInt()).coerceAtMost(bytes.size)
                    val value = String(bytes, offset, end - offset, StandardCharsets.UTF_8)
                    if (tag.toInt() == FIELD_PACKAGE.toInt()) packageName = value else className = value
                    offset = end
                }
                else -> return null
            }
        }
        val validPosition = position ?: return null
        val validPackage = packageName ?: return null
        val validClass = className ?: return null
        return validPosition to LauncherComponent(validPackage, validClass, userId = 0)
    }

    private fun writeString(
        output: ByteArrayOutputStream,
        field: Long,
        value: String,
    ) {
        writeVarint(output, field)
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeVarint(output, bytes.size.toLong())
        output.write(bytes)
    }

    private fun writeVarint(
        output: ByteArrayOutputStream,
        value: Long,
    ) {
        var remaining = value
        while (remaining and VARINT_CONTINUATION != 0L) {
            output.write(((remaining and VARINT_VALUE_MASK) or VARINT_CONTINUATION).toInt())
            remaining = remaining ushr 7
        }
        output.write(remaining.toInt())
    }

    private fun readVarint(
        bytes: ByteArray,
        start: Int,
    ): Pair<Long, Int> {
        var value = 0L
        var shift = 0
        var offset = start
        while (offset < bytes.size && shift < MAX_VARINT_BITS) {
            val current = bytes[offset++].toInt() and 0xff
            value = value or ((current and VARINT_VALUE_MASK.toInt()).toLong() shl shift)
            if (current and VARINT_CONTINUATION.toInt() == 0) return value to offset
            shift += 7
        }
        return 0L to bytes.size
    }

    private const val FIELD_POSITION = 8L
    private const val FIELD_PACKAGE = 18L
    private const val FIELD_CLASS = 26L
    private const val VARINT_CONTINUATION = 0x80L
    private const val VARINT_VALUE_MASK = 0x7fL
    private const val MAX_VARINT_BITS = 64
}
