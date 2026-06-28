package com.example.uitest.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import android.hardware.camera2.CameraManager as Camera2Manager
import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import android.os.Build

data class CameraInfo(
    val id: String,
    val facing: String,
    val type: String,
    val focalLength: Float,
    val isPhysical: Boolean = true
)

class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val cameraFlows = mutableMapOf<String, MutableSharedFlow<ByteArray>>()
    private var lastLifecycleOwner: LifecycleOwner? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeCameraId: String? = null

    init {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider = future.get()
        }, ContextCompat.getMainExecutor(context))
    }

    fun isReady(): Boolean = cameraProvider != null

    fun getFlow(cameraId: String): SharedFlow<ByteArray> {
        val flow = cameraFlows.getOrPut(cameraId) {
            MutableSharedFlow(
                replay = 0,
                extraBufferCapacity = 5,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }
        
        lastLifecycleOwner?.let { owner ->
            mainHandler.post {
                if (activeCameraId != cameraId) {
                    startCamera(owner, cameraId)
                }
            }
        }
        
        return flow
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun startCamera(lifecycleOwner: LifecycleOwner, cameraId: String) {
        val provider = cameraProvider ?: return
        lastLifecycleOwner = lifecycleOwner
        
        // Stop everything else first to avoid conflicts
        provider.unbindAll()
        activeCameraId = cameraId

        val flow = cameraFlows.getOrPut(cameraId) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = 5, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        }

        val parts = cameraId.split(":")
        val logicalId = parts[0]
        
        val builder = ImageAnalysis.Builder()
            .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(android.util.Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
                    .build()
            )

        // Target physical sensor if requested (e.g. 0:1 for Telephoto on S20 FE)
        if (parts.size == 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            androidx.camera.camera2.interop.Camera2Interop.Extender(builder)
                .setPhysicalCameraId(parts[1])
        }

        val analysis = builder.build()
        analysis.setAnalyzer(cameraExecutor) { image ->
            processImage(image, flow)
        }

        val selector = CameraSelector.Builder()
            .addCameraFilter { infos ->
                infos.filter { Camera2CameraInfo.from(it).cameraId == logicalId }
            }
            .build()

        try {
            provider.bindToLifecycle(lifecycleOwner, selector, analysis)
            Log.d("CameraManager", "Bound camera $cameraId")
        } catch (e: Exception) {
            Log.e("CameraManager", "Failed to bind camera $cameraId", e)
        }
    }

    fun getCameraInfos(): List<CameraInfo> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as Camera2Manager
        val result = mutableListOf<CameraInfo>()

        manager.cameraIdList.forEach { id ->
            try {
                val c = manager.getCameraCharacteristics(id)
                val facingInt = c.get(CameraCharacteristics.LENS_FACING)
                val facing = when (facingInt) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_BACK -> "Back"
                    else -> "External"
                }

                val focalLengths = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                val focal = focalLengths?.maxOrNull() ?: 0f
                val type = when {
                    focal < 2.2f -> "UltraWide"
                    focal < 5.8f -> "Wide"
                    else -> "Telephoto"
                }

                val capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                val isLogical = capabilities?.any { it == 11 } == true

                result.add(CameraInfo(id, facing, type, focal, !isLogical))

                // Expose sub-sensors - THIS IS WHERE THE REAL TELEPHOTO LIVES
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isLogical) {
                    c.physicalCameraIds.forEach { pId ->
                        val pC = manager.getCameraCharacteristics(pId)
                        val pFocal = pC.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.maxOrNull() ?: 0f
                        val pType = when {
                            pFocal < 2.2f -> "UltraWide"
                            pFocal < 5.8f -> "Wide"
                            else -> "Telephoto"
                        }
                        result.add(CameraInfo("$id:$pId", facing, "$pType (Sensor $pId)", pFocal, true))
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraManager", "Error querying camera $id", e)
            }
        }
        return result.distinctBy { it.id }
    }

    fun startSupportedCameras(lifecycleOwner: LifecycleOwner) {
        lastLifecycleOwner = lifecycleOwner
        // Start primary back camera by default
        getCameraInfos().firstOrNull { it.facing == "Back" && !it.id.contains(":") }?.let {
            startCamera(lifecycleOwner, it.id)
        }
    }

    fun getDeviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    private fun processImage(imageProxy: ImageProxy, flow: MutableSharedFlow<ByteArray>) {
        try {
            val bitmap = try {
                imageProxy.toBitmap().copy(Bitmap.Config.ARGB_8888, true)
            } catch (e: Exception) {
                imageProxyToBitmapManual(imageProxy)
            }
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
            flow.tryEmit(out.toByteArray())
        } catch (e: Exception) {
            Log.e("CameraManager", "Processing error", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmapManual(image: ImageProxy): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            .copy(Bitmap.Config.ARGB_8888, true)
    }

    fun stopStreaming() {
        cameraProvider?.unbindAll()
        activeCameraId = null
    }
}
