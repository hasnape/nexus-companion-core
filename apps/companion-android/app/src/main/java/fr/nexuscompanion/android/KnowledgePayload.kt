package fr.nexuscompanion.android

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.Deflater

/** Encodes the JSON inside the authenticated library envelope. */
internal object KnowledgePayload {
    // Independent total-byte budget: no record is discarded to make room.
    const val MAX_JSON_BYTES = NativeCapacity.MAX_LIBRARY_JSON_BYTES
    private const val BUFFER_BYTES = 8192

    fun encode(json: ByteArray): ByteArray {
        require(json.size <= MAX_JSON_BYTES) { "Library payload too large" }
        fun compress(level: Int): ByteArray {
            val output = ByteArrayOutputStream(minOf(json.size, BUFFER_BYTES))
            object : GZIPOutputStream(output) { init { def.setLevel(level) } }.use { it.write(json) }
            return output.toByteArray()
        }
        val standard = compress(Deflater.DEFAULT_COMPRESSION)
        // Save only on explicit writes. Never grow a payload compared with the previous codec.
        val best = if (json.size >= 4096) compress(Deflater.BEST_COMPRESSION).takeIf { it.size < standard.size } ?: standard else standard
        return if (best.size < json.size) best else json
    }

    fun decode(payload: ByteArray): ByteArray {
        require(payload.size <= MAX_JSON_BYTES) { "Library payload too large" }
        val gzip = payload.size >= 2 && payload[0] == 0x1f.toByte() && payload[1] == 0x8b.toByte()
        if (!gzip) return payload
        return GZIPInputStream(ByteArrayInputStream(payload)).use { readBounded(it, MAX_JSON_BYTES) }
    }

    fun readBounded(stream: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, BUFFER_BYTES))
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val count = stream.read(buffer)
            if (count == -1) break
            require(count <= maxBytes - output.size()) { "Library payload too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
