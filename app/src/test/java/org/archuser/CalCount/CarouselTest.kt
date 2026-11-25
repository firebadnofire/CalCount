package org.archuser.CalCount

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.archuser.CalCount.data.Carousel
import org.archuser.CalCount.ui.library.QrTransferCodec
import java.util.zip.CRC32
import org.junit.Assert.*
import org.junit.Test

class CarouselTest {
    @Test fun acceptsLateStartDuplicatesAndMissingFramesOnLaterCycles() {
        for (type in listOf("ccnl", "ccr")) {
            val payload = ByteArray(900) { it.toByte() }
            val frames = Carousel.encode(type, payload)
            val receiver = Carousel.Receiver()
            assertNull(receiver.accept(frames[2]))
            assertNull(receiver.accept(frames[2]))
            assertNull(receiver.accept(Carousel.encode(type, payload)[0]))
            assertNull(receiver.accept(frames[0]))
            assertEquals(2, receiver.collected)
            val result = requireNotNull(receiver.accept(frames[1]))
            assertEquals(type, result.type)
            assertArrayEquals(payload, result.bytes)
        }
    }

    @Test fun detectsCorruptionAndHasIndependentPlaybackTiming() {
        val frame = Carousel.encode("ccnl", byteArrayOf(1)).single()
        assertThrows(IllegalArgumentException::class.java) {
            Carousel.Receiver().accept(frame.dropLast(1) + "x")
        }
        assertEquals(128000L, Carousel.cycleDuration(128, 1000))
        assertEquals(96000L, Carousel.cycleDuration(128, 750))
        assertEquals(64000L, Carousel.cycleDuration(128, 500))
    }

    @Test fun largestHeaderAndChunkDecodeAtDisplaySize() {
        val payload = ByteArray(Carousel.CHUNK_BYTES * Carousel.MAX_FRAMES) { it.toByte() }
        val text = Carousel.encode("ccr", payload).last()
        val size = 300
        val matrix = QrTransferCodec.encodeToMatrix(text, size)
        val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xff000000.toInt() else -1 }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels)))
        assertEquals(text, QRCodeReader().decode(bitmap).text)
        val modules = QrTransferCodec.encodeToMatrix(text, 1)
        assertTrue(size / modules.width >= 2)
    }

    @Test fun validPacketChecksumsCannotBypassCompleteDigest() {
        val frame = Carousel.encode("ccnl", byteArrayOf(1)).single()
        val fields = frame.split(':').toMutableList()
        fields[5] = "0".repeat(64)
        val covered = fields.take(6).joinToString(":") + ":" + fields[7]
        fields[6] = CRC32().apply { update(covered.encodeToByteArray()) }.value.toString(16).padStart(8, '0')
        assertThrows(IllegalArgumentException::class.java) {
            Carousel.Receiver().accept(fields.joinToString(":"))
        }
    }
}
