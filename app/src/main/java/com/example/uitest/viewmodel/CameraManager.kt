package com.example.uitest.viewmodel

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
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
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop

import kotlinx.serialization.Serializable

@Serializable
data class Resolution(val width: Int, val height: Int) {
    override fun toString(): String = "${width}x$height"
}

@Serializable
data class CameraInfo(
    val id: String,
    val facing: String,
    val type: String,
    val focalLength: Float,
    val isPhysical: Boolean = true,
    val supportedResolutions: List<Resolution> = emptyList(),
)

class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val cameraFlows = mutableMapOf<String, MutableSharedFlow<ByteArray>>()
    private var lastLifecycleOwner: LifecycleOwner? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeCameraId: String? = null
    private var activeResolution: Resolution? = null
    private var activeFps: Int = 30
    private var lastFrameTimeMs: Long = 0L

    init {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                cameraProvider = future.get()
                lastLifecycleOwner?.let { owner ->
                    activeCameraId?.let { id ->
                        val res = activeResolution ?: Resolution(640, 480)
                        startCamera(owner, id, res.width, res.height, activeFps)
                    }
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun setLifecycleOwner(lifecycleOwner: LifecycleOwner) {
        lastLifecycleOwner = lifecycleOwner
        activeCameraId?.let { id ->
            val res = activeResolution ?: Resolution(640, 480)
            startCamera(lifecycleOwner, id, res.width, res.height, activeFps)
        }
    }

    fun isReady(): Boolean = cameraProvider != null

    fun getFlow(cameraId: String, width: Int = 640, height: Int = 480, fps: Int = 30): SharedFlow<ByteArray> {
        val flowKey = "$cameraId:$width:$height:$fps"
        val flow = cameraFlows.getOrPut(flowKey) {
            MutableSharedFlow(
                replay = 0,
                extraBufferCapacity = 5,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        }
        
        mainHandler.post {
            lastLifecycleOwner?.let { owner ->
                if ((activeCameraId != cameraId) || (activeResolution?.width != width) || (activeResolution?.height != height) || (activeFps != fps)) {
                    startCamera(owner, cameraId, width, height, fps)
                }
            }
        }
        
        return flow
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun startCamera(lifecycleOwner: LifecycleOwner, cameraId: String, width: Int = 640, height: Int = 480, fps: Int = 30) {
        lastLifecycleOwner = lifecycleOwner
        activeCameraId = cameraId
        activeResolution = Resolution(width, height)
        activeFps = fps
        lastFrameTimeMs = 0L

        val provider = cameraProvider ?: return
        
        // Stop everything else first to avoid conflicts - ensures only ONE camera/res/fps is active
        provider.unbindAll()

        val flowKey = "$cameraId:$width:$height:$fps"
        val flow = cameraFlows.getOrPut(flowKey) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = 5, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        }

        val parts = cameraId.split(":")
        val logicalId = parts[0]
        
        val builder = ImageAnalysis.Builder()
            .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(width, height), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
                    .build()
            )

        val extender = Camera2Interop.Extender(builder)
        if (parts.size == 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            extender.setPhysicalCameraId(parts[1])
        }
        try {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range(fps, fps)
            )
        } catch (e: Exception) {
            Log.w("CameraManager", "Could not set AE target FPS range", e)
        }

        val analysis = builder.build()
        analysis.setAnalyzer(cameraExecutor) { image ->
            processImage(image, flow, fps)
        }

        val selector = CameraSelector.Builder()
            .addCameraFilter { infos ->
                infos.filter { Camera2CameraInfo.from(it).cameraId == logicalId }
            }
            .build()

        try {
            provider.bindToLifecycle(lifecycleOwner, selector, analysis)
            Log.d("CameraManager", "Bound camera $cameraId at ${width}x$height @ ${fps}FPS")
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
                val facingInt = c[CameraCharacteristics.LENS_FACING]
                val facing = when (facingInt) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_BACK -> "Back"
                    else -> "External"
                }

                val focalLengths = c[CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]
                val focal = focalLengths?.maxOrNull() ?: 0f
                val type = when {
                    focal < 2.2f -> "UltraWide"
                    focal < 5.8f -> "Wide"
                    else -> "Telephoto"
                }

                val capabilities = c[CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES]
                val isLogical = capabilities?.any { it == 11 } == true

                val map = c[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]
                val resolutions = map?.getOutputSizes(ImageFormat.YUV_420_888)?.asSequence()?.map { 
                    Resolution(it.width, it.height) 
                }?.sortedByDescending { it.width * it.height }?.toList() ?: emptyList()

                result.add(CameraInfo(id, facing, type, focal, !isLogical, resolutions))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isLogical) {
                    c.physicalCameraIds.forEach { pId ->
                        val pC = manager.getCameraCharacteristics(pId)
                        val pFocal = pC[CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]?.maxOrNull() ?: 0f
                        val pType = when {
                            pFocal < 2.2f -> "UltraWide"
                            pFocal < 5.8f -> "Wide"
                            else -> "Telephoto"
                        }
                        val pMap = pC[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]
                        val pResolutions = pMap?.getOutputSizes(ImageFormat.YUV_420_888)?.asSequence()?.map { 
                            Resolution(it.width, it.height) 
                        }?.sortedByDescending { it.width * it.height }?.toList() ?: emptyList()
                        
                        result.add(CameraInfo("$id:$pId", facing, "$pType (Sensor $pId)", pFocal, isPhysical = true, pResolutions))
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraManager", "Error querying camera $id", e)
            }
        }
        return result.distinctBy { it.id }
    }

    @Suppress("Unused")
    fun startSupportedCameras(lifecycleOwner: LifecycleOwner) {
        lastLifecycleOwner = lifecycleOwner
        getCameraInfos().firstOrNull { it.facing == "Back" && !it.id.contains(":") }?.let {
            startCamera(lifecycleOwner, it.id)
        }
    }

    fun getDeviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    private fun processImage(imageProxy: ImageProxy, flow: MutableSharedFlow<ByteArray>, targetFps: Int) {
        try {
            if (flow.subscriptionCount.value == 0) {
                return
            }
            val now = System.currentTimeMillis()
            val minIntervalMs = if (targetFps > 0) 1000L / targetFps else 0L
            if (minIntervalMs > 0 && (now - lastFrameTimeMs) < minIntervalMs) {
                return
            }
            lastFrameTimeMs = now

            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 60, out)
            flow.tryEmit(out.toByteArray())
        } catch (e: Exception) {
            Log.e("CameraManager", "Processing error", e)
        } finally {
            imageProxy.close()
        }
    }

    fun stopStreaming() {
        cameraProvider?.unbindAll()
        activeCameraId = null
        activeResolution = null
        activeFps = 30
    }
}
