package org.archuser.CalCount

import org.archuser.CalCount.ui.library.QrTransferCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrTransferCodecTest {

    @Test
    fun encodeToMatrix_buildsSquareQrMatrix() {
        val matrix = QrTransferCodec.encodeToMatrix("""{"type":"food","version":1}""", size = 256)

        assertEquals(256, matrix.width)
        assertEquals(256, matrix.height)
        assertTrue(matrix.getEnclosingRectangle().isNotEmpty())
    }
}
