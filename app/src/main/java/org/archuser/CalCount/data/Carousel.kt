package org.archuser.CalCount.data

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.zip.CRC32

object Carousel {
    const val CHUNK_BYTES = 384
    const val MAX_FRAMES = 128
    const val DEFAULT_DURATION_MS = 1000L
    const val PREFIX = "CCQR1:"
    private val sha = Regex("[0-9a-f]{64}")
    private val crc = Regex("[0-9a-f]{8}")
    private val alphabet = Regex("[A-Za-z0-9_-]+")
    private val types = setOf("ccnl", "ccr")

    fun cycleDuration(frames: Int, durationMs: Long): Long {
        require(frames in 1..MAX_FRAMES && durationMs in listOf(1000L, 750L, 500L))
        return frames * durationMs
    }

    fun encode(type: String, payload: ByteArray, session: UUID = UUID.randomUUID()): List<String> {
        require(type in types)
        require(payload.size in 1..CHUNK_BYTES * MAX_FRAMES) { "This transfer is too large for QR. Share it as a file." }
        val id = session.toString()
        val count = (payload.size + CHUNK_BYTES - 1) / CHUNK_BYTES
        val checksum = digest(payload)
        return List(count) { index ->
            val start = index * CHUNK_BYTES
            val encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.copyOfRange(start, minOf(start + CHUNK_BYTES, payload.size)))
            val header = "CCQR1:$id:$type:$index:$count:$checksum"
            "$header:${crc32("$header:$encoded")}:$encoded"
        }
    }

    data class Payload(val type: String, val bytes: ByteArray)

    class Receiver {
        private var session: String? = null
        private var checksum: String? = null
        private var type: String? = null
        private val chunks = mutableMapOf<Int, ByteArray>()
        var expected: Int = 0
            private set
        val collected: Int get() = chunks.size

        fun reset() {
            session = null
            checksum = null
            type = null
            expected = 0
            chunks.clear()
        }

        fun accept(text: String): Payload? {
            require(text.length <= 656 && text.startsWith(PREFIX)) { "Unsupported QR transfer packet." }
            val parts = text.split(':', limit = 8)
            require(parts.size == 8) { "Incomplete QR packet." }
            val (_, id, payloadType, indexText, countText) = parts
            val hash = parts[5]
            val packetCrc = parts[6]
            val encoded = parts[7]
            if (session != null && id != session) return null
            val count = countText.toIntOrNull() ?: error("Invalid QR frame count.")
            val index = indexText.toIntOrNull() ?: error("Invalid QR frame index.")
            require(TransferJson.isUuid(id) && payloadType in types && count in 1..MAX_FRAMES &&
                index in 0 until count && indexText == index.toString() && countText == count.toString() &&
                sha.matches(hash) && crc.matches(packetCrc) && alphabet.matches(encoded)) { "Invalid QR packet fields." }
            require(crc32(parts.take(6).joinToString(":") + ":$encoded") == packetCrc) { "QR packet checksum failed." }
            val chunk = Base64.getUrlDecoder().decode(encoded)
            require(chunk.size in 1..CHUNK_BYTES && Base64.getUrlEncoder().withoutPadding().encodeToString(chunk) == encoded) {
                "Invalid QR chunk."
            }
            if (session == null) {
                session = id
                checksum = hash
                type = payloadType
                expected = count
            }
            require(expected == count && checksum == hash && type == payloadType) { "Inconsistent QR transfer packet." }
            val previous = chunks[index]
            require(previous == null || previous.contentEquals(chunk)) { "Conflicting QR frame data." }
            chunks[index] = chunk
            if (collected != expected) return null
            val assembled = ByteArrayOutputStream().apply {
                repeat(expected) { write(chunks.getValue(it)) }
            }.toByteArray()
            reset()
            require(digest(assembled) == hash) { "QR transfer failed its integrity check. Scan the sender again." }
            return Payload(payloadType, assembled)
        }
    }

    private fun crc32(value: String): String {
        val crc = CRC32().apply { update(value.encodeToByteArray()) }
        return crc.value.toString(16).padStart(8, '0')
    }
}
