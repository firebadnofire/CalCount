package org.archuser.CalCount.ui.library

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
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
    private var hasHandledResult = false

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

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.qr_import_title)
            .setView(binding.root)
            .setNegativeButton(R.string.qr_import_cancel, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
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
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                val provider = try {
                    cameraProviderFuture.get()
                } catch (_: Exception) {
                    null
                } ?: run {
                    viewModel.setTransientMessage(getString(R.string.qr_import_camera_unavailable))
                    dismissAllowingStateLoss()
                    return@addListener
                }

                cameraProvider = provider
                bindCamera(provider)
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
                val rawValue = barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                if (rawValue != null && !hasHandledResult) {
                    hasHandledResult = true
                    stopCamera()
                    val didImport = viewModel.importFoodJson(rawValue)
                    if (!didImport) {
                        viewModel.setTransientMessage(getString(R.string.qr_import_invalid))
                    }
                    dismissAllowingStateLoss()
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    companion object {
        fun newInstance(): QrImportDialogFragment = QrImportDialogFragment()
    }
}
