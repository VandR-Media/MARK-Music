package com.markmusic.player.dance

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Validate container before handing bytes to the future Filament/glTF renderer. */
object GlbAssetValidator {
    const val MAX_BYTES = 64 * 1024 * 1024

    fun validate(bytes: ByteArray): Boolean {
        if (bytes.size < 20 || bytes.size > MAX_BYTES) return false
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.int != 0x46546C67 || buffer.int != 2 || buffer.int != bytes.size) return false
        val jsonLength = buffer.int
        if (jsonLength <= 0 || jsonLength % 4 != 0 ||
            jsonLength.toLong() + 20L > bytes.size) return false
        if (buffer.int != 0x4E4F534A) return false
        val json = bytes.copyOfRange(20, 20 + jsonLength)
        if (!json.decodeToString().trim().startsWith("{")) return false
        var cursor = 20 + jsonLength
        while (cursor < bytes.size) {
            if (bytes.size - cursor < 8) return false
            buffer.position(cursor)
            val length = buffer.int
            val type = buffer.int
            if (length < 0 || length % 4 != 0 || length.toLong() + 8L > bytes.size - cursor)
                return false
            if (type != 0x004E4942) return false
            cursor += 8 + length
        }
        return cursor == bytes.size
    }

    fun load(input: InputStream): ByteArray {
        val bytes = input.use { it.readNBytes(MAX_BYTES + 1) }
        require(validate(bytes)) { "Invalid glTF 2.0 GLB container" }
        return bytes
    }
}
