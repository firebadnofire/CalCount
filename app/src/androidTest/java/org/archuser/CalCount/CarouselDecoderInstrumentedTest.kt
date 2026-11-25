package org.archuser.CalCount

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.archuser.CalCount.data.Carousel
import org.archuser.CalCount.ui.library.QrTransferCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CarouselDecoderInstrumentedTest {
    @Test fun bundledDecoderReadsRenderedFramesAndReassemblesPayload() {
        val payload = ByteArray(900) { it.toByte() }
        val frames = Carousel.encode("ccr", payload)
        val receiver = Carousel.Receiver()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
        try {
            val decoded = (frames.reversed() + frames.first()).mapNotNull { text ->
                val size = 320
                val matrix = QrTransferCodec.encodeToMatrix(text, size)
                val pixels = IntArray(size * size) { index ->
                    if (matrix[index % size, index / size]) Color.BLACK else Color.WHITE
                }
                val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
                try {
                    val barcodes = Tasks.await(scanner.process(InputImage.fromBitmap(bitmap, 0)), 15, TimeUnit.SECONDS)
                    assertEquals(text, barcodes.single().rawValue)
                    receiver.accept(text)
                } finally {
                    bitmap.recycle()
                }
            }
            assertArrayEquals(payload, decoded.single().bytes)
        } finally {
            scanner.close()
        }
    }
}
