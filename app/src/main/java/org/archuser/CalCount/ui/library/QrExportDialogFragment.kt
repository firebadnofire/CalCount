package org.archuser.CalCount.ui.library

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.archuser.CalCount.R
import org.archuser.CalCount.databinding.DialogQrExportBinding

class QrExportDialogFragment : DialogFragment() {

    private var _binding: DialogQrExportBinding? = null
    private val binding get() = _binding!!
    private var previousBrightness: Float? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogQrExportBinding.inflate(layoutInflater)
        val rawJson = requireArguments().getString(ARG_RAW_JSON).orEmpty()
        val matrix = QrTransferCodec.encodeToMatrix(rawJson)
        binding.qrImage.setImageBitmap(matrix.toBitmap())

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.qr_export_title)
            .setView(binding.root)
            .setPositiveButton(R.string.ok, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        val attrs = window.attributes
        previousBrightness = attrs.screenBrightness
        attrs.screenBrightness = 1f
        window.attributes = attrs
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStop() {
        restoreBrightness()
        super.onStop()
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    private fun restoreBrightness() {
        val window = dialog?.window ?: return
        val attrs = window.attributes
        attrs.screenBrightness = previousBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = attrs
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun com.google.zxing.common.BitMatrix.toBitmap(): Bitmap {
        val width = width
        val height = height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                pixels[rowOffset + x] = if (get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    companion object {
        private const val ARG_RAW_JSON = "raw_json"

        fun newInstance(rawJson: String): QrExportDialogFragment {
            return QrExportDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_RAW_JSON, rawJson)
                }
            }
        }
    }
}
