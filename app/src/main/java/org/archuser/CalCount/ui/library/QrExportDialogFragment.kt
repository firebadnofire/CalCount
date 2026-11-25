package org.archuser.CalCount.ui.library

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.archuser.CalCount.data.Carousel
import org.archuser.CalCount.databinding.DialogQrExportBinding
import org.archuser.CalCount.ui.AppViewModel

class QrExportDialogFragment : DialogFragment() {
    private val viewModel: AppViewModel by activityViewModels()
    private lateinit var binding: DialogQrExportBinding
    private var playback: Job? = null
    private var previousBrightness: Float? = null
    private val durations = listOf(1000L, 750L, 500L)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogQrExportBinding.inflate(layoutInflater)
        val broadcast = viewModel.pendingBroadcast
        val file = broadcast?.file
        val frameCount = file?.let { (it.bytes.size + Carousel.CHUNK_BYTES - 1) / Carousel.CHUNK_BYTES } ?: 0
        val speedOptions = listOf("1,000 ms (default)", "750 ms (faster)", "500 ms (fast, may be less reliable)")
        var duration = broadcast?.selectedDuration ?: Carousel.DEFAULT_DURATION_MS
        fun showEstimate() {
            binding.qrExportMessage.text = if (frameCount == 0) "Open the export again to start."
            else "$frameCount QR frames · $duration ms per frame"
            binding.qrExportHelp.text = if (frameCount == 0) "" else
                "One broadcast cycle: ${Carousel.cycleDuration(frameCount, duration) / 1000.0} seconds. Reception can take longer. The broadcast repeats until you press End."
        }
        showEstimate()
        binding.qrDuration.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, speedOptions))
        binding.qrDuration.setText(speedOptions[durations.indexOf(duration)], false)
        binding.qrDuration.setOnItemClickListener { _, _, position, _ ->
            duration = durations[position]
            broadcast?.selectedDuration = duration
            showEstimate()
        }

        return MaterialAlertDialogBuilder(requireContext()).setTitle("Share with QR")
            .setView(binding.root)
            .setNegativeButton("End", null)
            .setPositiveButton("Start", null)
            .create().apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).apply {
                        isEnabled = broadcast != null
                        isVisible = broadcast?.started != true
                        binding.qrDurationLayout.isVisible = broadcast?.started != true
                        setOnClickListener {
                            if (broadcast != null) {
                                isVisible = false
                                binding.qrDurationLayout.isVisible = false
                                broadcast.started = true
                                startPlayback(broadcast)
                            }
                        }
                    }
                }
            }
    }

    private fun startPlayback(broadcast: AppViewModel.Broadcast) {
        val file = broadcast.file
        playback = lifecycleScope.launch {
            binding.qrExportMessage.text = "Preparing QR frames…"
            binding.qrExportHelp.text = "Repeats until you press End."
            val result = withContext(Dispatchers.Default) {
                runCatching { Carousel.encode(file.type, file.bytes, broadcast.session).map { QrTransferCodec.encodeToMatrix(it, 1) } }
            }
            val matrices = result.getOrElse {
                binding.qrExportMessage.text = it.message ?: "QR frames could not be created."
                return@launch
            }
            binding.qrImage.isVisible = true
            var deadline = SystemClock.uptimeMillis()
            while (true) {
                matrices.forEachIndexed { index, matrix ->
                    binding.qrImage.setImageBitmap(matrix.toBitmap())
                    binding.qrExportMessage.text = "Frame ${index + 1} of ${matrices.size}"
                    deadline += broadcast.selectedDuration
                    val now = SystemClock.uptimeMillis()
                    if (deadline <= now) deadline = now + broadcast.selectedDuration
                    delay(deadline - now)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.pendingBroadcast?.takeIf { it.started }?.let(::startPlayback)
        dialog?.window?.let { window ->
            val attrs = window.attributes
            previousBrightness = attrs.screenBrightness
            attrs.screenBrightness = 1f
            window.attributes = attrs
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onStop() {
        playback?.cancel()
        dialog?.window?.let { window ->
            val attrs = window.attributes
            attrs.screenBrightness = previousBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = attrs
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        super.onStop()
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        if (activity?.isChangingConfigurations != true) viewModel.pendingBroadcast = null
        super.onDismiss(dialog)
    }

    private fun BitMatrix.toBitmap(): Bitmap {
        val scale = 8
        val pixels = IntArray(width * height * scale * scale) { index ->
            val x = index % (width * scale) / scale
            val y = index / (width * scale) / scale
            if (get(x, y)) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, width * scale, height * scale, Bitmap.Config.ARGB_8888)
    }
}
