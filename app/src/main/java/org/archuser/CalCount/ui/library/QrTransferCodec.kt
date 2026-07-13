package org.archuser.CalCount.ui.library

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrTransferCodec {

    fun encodeToMatrix(rawJson: String, size: Int = 1024): BitMatrix {
        require(rawJson.isNotBlank()) { "QR export data cannot be blank." }
        require(size > 0) { "QR export size must be greater than zero." }

        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L
        )

        return try {
            QRCodeWriter().encode(rawJson, BarcodeFormat.QR_CODE, size, size, hints)
        } catch (error: WriterException) {
            throw IllegalArgumentException(
                "This export is too large for a single QR code.",
                error
            )
        }
    }
}
