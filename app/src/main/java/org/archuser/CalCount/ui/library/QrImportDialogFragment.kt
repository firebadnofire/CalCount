package org.archuser.CalCount.ui.library

import android.Manifest
import android.app.Dialog
import android.content.DialogInterface
import org.archuser.CalCount.MainActivity
import org.archuser.CalCount.data.Carousel
import org.archuser.CalCount.data.IncomingTransfer
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.archuser.CalCount.R
import org.archuser.CalCount.databinding.DialogQrImportBinding
import org.archuser.CalCount.ui.AppViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QrImportDialogFragment : DialogFragment() {

    private var _binding: DialogQrImportBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AppViewModel
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner? = null
    private val receiver get() = viewModel.qrReceiver
    private var scanGeneration = 0
    @Volatile private var hasHandledResult = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startScanner()
            } else {
                viewModel.setTransientMessage(getString(R.string.qr_import_permission_required))
                dismissAllowingStateLoss()
            }
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogQrImportBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider(requireActivity())[AppViewModel::class.java]
        cameraExecutor = Executors.newSingleThreadExecutor()
        showProgress()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.qr_import_title)
            .setView(binding.root)
            .setNegativeButton(R.string.qr_import_cancel, null)
            .setNeutralButton("Reset", null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        (dialog as? androidx.appcompat.app.AlertDialog)?.getButton(DialogInterface.BUTTON_NEUTRAL)?.setOnClickListener {
            receiver.reset()
            binding.qrImportMessage.setText(R.string.qr_import_message)
            showProgress()
        }
        ensureCameraPermissionAndStart()
    }

    override fun onStop() {
        stopCamera()
        super.onStop()
    }

    override fun onDestroy() {
        stopCamera()
        barcodeScanner?.close()
        barcodeScanner = null
        cameraExecutor.shutdown()
        _binding = null
        super.onDestroy()
    }

    private fun ensureCameraPermissionAndStart() {
        val permissionState = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        )
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            startScanner()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startScanner() {
        if (!isAdded || _binding == null) return
        val generation = ++scanGeneration
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                if (generation != scanGeneration || _binding == null) return@addListener
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                    bindCamera(provider)
                } catch (error: Exception) {
                    viewModel.setTransientMessage(
                        error.message ?: getString(R.string.qr_import_camera_unavailable)
                    )
                    dismissAllowingStateLoss()
                }
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    private fun bindCamera(provider: ProcessCameraProvider) {
        val preview = androidx.camera.core.Preview.Builder()
            .build()
            .also { it.surfaceProvider = binding.cameraPreview.surfaceProvider }

        barcodeScanner?.close()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
        barcodeScanner = scanner

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    analyzeImage(imageProxy, scanner)
                }
            }

        provider.unbindAll()
        provider.bindToLifecycle(
            this,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageAnalysis
        )
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun analyzeImage(
        imageProxy: ImageProxy,
        scanner: com.google.mlkit.vision.barcode.BarcodeScanner
    ) {
        if (hasHandledResult) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (hasHandledResult || _binding == null || cameraProvider == null ||
                    barcodeScanner !== scanner) return@addOnSuccessListener
                for (barcode in barcodes) {
                    val value = barcode.rawValue ?: continue
                    val host = activity as? MainActivity ?: return@addOnSuccessListener
                    if (value.startsWith(Carousel.PREFIX)) {
                        val completed = try {
                            receiver.accept(value)
                        } catch (error: Exception) {
                            binding.qrImportMessage.text = error.message ?: "Invalid QR transfer frame."
                            continue
                        }
                        if (completed != null) {
                            hasHandledResult = true
                            stopCamera()
                            dismissAllowingStateLoss()
                            host.importPacket(completed.bytes, completed.type)
                            break
                        }
                        showProgress()
                    } else if (receiver.expected == 0 && value.trimStart().startsWith('{')) {
                        val legacy = try {
                            IncomingTransfer.decode(value.encodeToByteArray())
                        } catch (error: Exception) {
                            binding.qrImportMessage.text = error.message ?: "Invalid nutritional label QR."
                            continue
                        }
                        hasHandledResult = true
                        stopCamera()
                        dismissAllowingStateLoss()
                        host.reviewIncoming(legacy)
                        break
                    }
                }
            }
            .addOnFailureListener { error ->
                _binding?.qrImportMessage?.text = error.message ?: "The camera frame could not be decoded."
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun showProgress() {
        binding.qrImportProgress.isVisible = receiver.expected > 0
        if (receiver.expected > 0) {
            binding.qrImportProgress.max = receiver.expected
            binding.qrImportProgress.setProgressCompat(receiver.collected, false)
        }
        if (receiver.expected > 0) {
            binding.qrImportMessage.text = "Collected ${receiver.collected} of ${receiver.expected} unique frames."
        }
    }

    private fun stopCamera() {
        scanGeneration++
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    companion object {
        fun newInstance(): QrImportDialogFragment = QrImportDialogFragment()
    }
}
