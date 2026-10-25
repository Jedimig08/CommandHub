package com.commandhub.viewmodel

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCharacteristics
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
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
import java.util.concurrent.atomic.AtomicBoolean
import android.hardware.camera2.CameraManager as Camera2Manager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import android.os.Build
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera

import kotlinx.serialization.Serializable
import java.util.Arrays
import java.util.concurrent.atomic.AtomicReference

@Serializable
data class CameraStats(
    val activeCameraId: String? = null,
    val resolution: String? = null,
    val targetFps: Int = 0,
    val currentFps: Float = 0f,
    val totalFramesCaptured: Long = 0L,
    val totalFramesDropped: Long = 0L,
    val totalBytesStreamed: Long = 0L,
    val bitrateBps: Long = 0L,
    val activeViewers: Int = 0,
    val zoomRatio: Float = 1.0f,
    val minZoomRatio: Float = 1.0f,
    val maxZoomRatio: Float = 1.0f,
    val exposureIndex: Int = 0,
    val minExposureIndex: Int = 0,
    val maxExposureIndex: Int = 0,
    val torchEnabled: Boolean = false,
)

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
    private val encodeExecutor = Executors.newSingleThreadExecutor()
    private val encoding = AtomicBoolean(false)
    private val recycledNv21Buffer = AtomicReference<ByteArray?>(null)
    private val encodeOutputStream = ByteArrayOutputStream(256 * 1024)
    private val cameraFlows = mutableMapOf<String, MutableSharedFlow<ByteArray>>()
    private val h264Flows = mutableMapOf<String, MutableSharedFlow<ByteArray>>()
    private var h264Encoder: HardwareH264Encoder? = null
    private var activeH264Config: String? = null
    private var lastLifecycleOwner: LifecycleOwner? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeCameraId: String? = null
    private var activeResolution: Resolution? = null
    private var activeFps: Int = 30
    private var activeGrayscale: Boolean = false
    private var activeQuality: Int = 50
    private var lastFrameTimeMs: Long = 0L
    private var boundCamera: Camera? = null
    private var isFlashlightOn = false

    private var totalFramesCaptured = 0L
    private var totalFramesDropped = 0L
    private var totalBytesStreamed = 0L
    private var lastFpsCalcTime = SystemClock.elapsedRealtime()
    private var framesInLastWindow = 0
    private var bytesInLastWindow = 0L
    private var currentCalculatedFps = 0f
    private var currentCalculatedBitrate = 0L

    fun recordFrameProcessed(bytes: Int) {
        totalFramesCaptured++
        totalBytesStreamed += bytes
        framesInLastWindow++
        bytesInLastWindow += bytes

        val now = SystemClock.elapsedRealtime()
        val dt = now - lastFpsCalcTime
        if (dt >= 1000L) {
            currentCalculatedFps = (framesInLastWindow * 1000f) / dt
            currentCalculatedBitrate = (bytesInLastWindow * 8 * 1000L) / dt
            framesInLastWindow = 0
            bytesInLastWindow = 0L
            lastFpsCalcTime = now
        }
    }

    fun recordFrameDropped() {
        totalFramesDropped++
    }

    fun setZoomRatio(ratio: Float): Boolean {
        return try {
            val camera = boundCamera ?: return false
            val zoomState = camera.cameraInfo.zoomState.value ?: return false
            val clampedRatio = ratio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
            camera.cameraControl.setZoomRatio(clampedRatio)
            true
        } catch (e: Exception) {
            Log.w("CameraManager", "Failed to set zoom ratio", e)
            false
        }
    }

    fun setExposureCompensation(index: Int): Boolean {
        return try {
            val camera = boundCamera ?: return false
            val exposureState = camera.cameraInfo.exposureState
            if (!exposureState.isExposureCompensationSupported) return false
            val clamped = index.coerceIn(exposureState.exposureCompensationRange.lower, exposureState.exposureCompensationRange.upper)
            camera.cameraControl.setExposureCompensationIndex(clamped)
            true
        } catch (e: Exception) {
            Log.w("CameraManager", "Failed to set exposure compensation", e)
            false
        }
    }

    fun getCameraStats(): CameraStats {
        val zoom = boundCamera?.cameraInfo?.zoomState?.value
        val exposure = boundCamera?.cameraInfo?.exposureState
        val totalViewers = cameraFlows.values.sumOf { it.subscriptionCount.value } +
                h264Flows.values.sumOf { it.subscriptionCount.value }

        return CameraStats(
            activeCameraId = activeCameraId,
            resolution = activeResolution?.toString(),
            targetFps = activeFps,
            currentFps = currentCalculatedFps,
            totalFramesCaptured = totalFramesCaptured,
            totalFramesDropped = totalFramesDropped,
            totalBytesStreamed = totalBytesStreamed,
            bitrateBps = currentCalculatedBitrate,
            activeViewers = totalViewers,
            zoomRatio = zoom?.zoomRatio ?: 1f,
            minZoomRatio = zoom?.minZoomRatio ?: 1f,
            maxZoomRatio = zoom?.maxZoomRatio ?: 1f,
            exposureIndex = exposure?.exposureCompensationIndex ?: 0,
            minExposureIndex = exposure?.exposureCompensationRange?.lower ?: 0,
            maxExposureIndex = exposure?.exposureCompensationRange?.upper ?: 0,
            torchEnabled = isFlashlightOn,
        )
    }

    fun setFlashlight(enabled: Boolean) {
        isFlashlightOn = enabled
        try {
            boundCamera?.cameraControl?.enableTorch(enabled)
        } catch (e: Exception) {
            Log.w("CameraManager", "Could not set torch via CameraX", e)
        }
        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as Camera2Manager
            val backCameraId = manager.cameraIdList.firstOrNull { id ->
                val c = manager.getCameraCharacteristics(id)
                c[CameraCharacteristics.FLASH_INFO_AVAILABLE] == true &&
                c[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull()

            backCameraId?.let { id ->
                manager.setTorchMode(id, enabled)
            }
        } catch (e: Exception) {
            Log.w("CameraManager", "Could not set torch via setTorchMode", e)
        }
    }

    fun isFlashlightOn(): Boolean = isFlashlightOn

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

    fun getFlow(
        cameraId: String,
        width: Int = 640,
        height: Int = 480,
        fps: Int = 30,
        quality: Int = 50,
        grayscale: Boolean = false
    ): SharedFlow<ByteArray> {
        cameraFlows.entries.removeAll { (_, f) -> f.subscriptionCount.value == 0 }
        val flowKey = "$cameraId:$width:$height:$fps:$quality:$grayscale"
        val flow = cameraFlows.getOrPut(flowKey) {
            MutableSharedFlow(
                replay = 0,
                extraBufferCapacity = 2,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        }
        
        mainHandler.post {
            lastLifecycleOwner?.let { owner ->
                if ((activeCameraId != cameraId) || (activeResolution?.width != width) || (activeResolution?.height != height) || (activeFps != fps) || (activeGrayscale != grayscale) || (activeQuality != quality)) {
                    startCamera(owner, cameraId, width, height, fps, quality, grayscale)
                }
            }
        }
        
        return flow
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        cameraId: String,
        width: Int = 640,
        height: Int = 480,
        fps: Int = 30,
        quality: Int = 50,
        grayscale: Boolean = false
    ) {
        if (boundCamera != null && activeCameraId == cameraId && activeResolution?.width == width && activeResolution?.height == height && activeFps == fps && activeGrayscale == grayscale && activeQuality == quality && lastLifecycleOwner == lifecycleOwner) {
            return
        }

        lastLifecycleOwner = lifecycleOwner
        activeCameraId = cameraId
        activeResolution = Resolution(width, height)
        activeFps = fps
        activeGrayscale = grayscale
        activeQuality = quality
        lastFrameTimeMs = 0L

        val provider = cameraProvider ?: return
        
        // Stop everything else first to avoid conflicts - ensures only ONE camera/res/fps is active
        provider.unbindAll()

        val flowKey = "$cameraId:$width:$height:$fps:$quality:$grayscale"
        val flow = cameraFlows.getOrPut(flowKey) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = 2, onBufferOverflow = BufferOverflow.DROP_OLDEST)
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
                Range(fps, maxOf(fps, 30))
            )
        } catch (e: Exception) {
            Log.w("CameraManager", "Could not set AE target FPS range", e)
        }

        val analysis = builder.build()
        analysis.setAnalyzer(cameraExecutor) { image ->
            processImage(image, flow, width, height, fps, quality, grayscale)
        }

        val selector = CameraSelector.Builder()
            .addCameraFilter { infos ->
                infos.filter { Camera2CameraInfo.from(it).cameraId == logicalId }
            }
            .build()

        try {
            boundCamera = provider.bindToLifecycle(lifecycleOwner, selector, analysis)
            if (isFlashlightOn) {
                try { boundCamera?.cameraControl?.enableTorch(true) } catch (_: Exception) {}
            }
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

    private fun processImage(
        imageProxy: ImageProxy,
        flow: MutableSharedFlow<ByteArray>,
        targetWidth: Int,
        targetHeight: Int,
        targetFps: Int,
        quality: Int = 30,
        grayscale: Boolean = false
    ) {
        try {
            if (flow.subscriptionCount.value == 0) {
                imageProxy.close()
                return
            }
            val now = SystemClock.elapsedRealtime()
            val minIntervalMs = if (targetFps > 0) 1000L / targetFps else 0L
            if (minIntervalMs > 0 && (now - lastFrameTimeMs) < minIntervalMs) {
                imageProxy.close()
                return
            }

            val imgW = imageProxy.width
            val imgH = imageProxy.height
            val cropW = minOf(targetWidth, imgW)
            val cropH = minOf(targetHeight, imgH)

            val requiredNv21Size = imgW * imgH * 3 / 2
            var nv21 = recycledNv21Buffer.getAndSet(null)
            if (nv21 == null || nv21.size != requiredNv21Size) {
                nv21 = ByteArray(requiredNv21Size)
            }

            val yPlane = imageProxy.planes[0]
            val uPlane = imageProxy.planes[1]
            val vPlane = imageProxy.planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val yRowStride = yPlane.rowStride
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            // Copy Y plane row by row to strip padding if present
            var nv21Offset = 0
            if (yRowStride == imgW) {
                yBuffer.get(nv21, 0, imgW * imgH)
                nv21Offset = imgW * imgH
            } else {
                for (row in 0 until imgH) {
                    yBuffer.position(row * yRowStride)
                    yBuffer.get(nv21, nv21Offset, imgW)
                    nv21Offset += imgW
                }
            }

            if (grayscale) {
                Arrays.fill(nv21, nv21Offset, requiredNv21Size, 128.toByte())
            } else {
                // Copy UV planes interleaving V then U for NV21
                val uvHeight = imgH / 2
                val uvWidth = imgW / 2

                if (uvPixelStride == 2 && uvRowStride == imgW && vBuffer.remaining() >= uvHeight * uvRowStride - 1) {
                    vBuffer.get(nv21, nv21Offset, uvHeight * uvRowStride - 1)
                    if (vBuffer.remaining() > 0) {
                        nv21[requiredNv21Size - 1] = uBuffer.get(uBuffer.limit() - 1)
                    }
                } else {
                    for (row in 0 until uvHeight) {
                        val rowStart = row * uvRowStride
                        for (col in 0 until uvWidth) {
                            val vIndex = rowStart + col * uvPixelStride
                            val uIndex = rowStart + col * uvPixelStride
                            nv21[nv21Offset++] = vBuffer.get(vIndex)
                            nv21[nv21Offset++] = uBuffer.get(uIndex)
                        }
                    }
                }
            }

            // Close imageProxy immediately so CameraX analyzer pipeline can proceed
            imageProxy.close()

            if (encoding.getAndSet(true)) {
                recycledNv21Buffer.set(nv21)
                recordFrameDropped()
                return // encoder busy -> drop this frame without updating lastFrameTimeMs
            }

            lastFrameTimeMs = now

            val nv21Buf = nv21
            val jpegQuality = quality.coerceIn(10, 100)
            encodeExecutor.execute {
                try {
                    val yuv = YuvImage(nv21Buf, ImageFormat.NV21, imgW, imgH, null)
                    encodeOutputStream.reset()
                    yuv.compressToJpeg(Rect(0, 0, cropW, cropH), jpegQuality, encodeOutputStream)
                    val bytes = encodeOutputStream.toByteArray()
                    recordFrameProcessed(bytes.size)
                    flow.tryEmit(bytes)
                } catch (e: Exception) {
                    Log.e("CameraManager", "Encoding error", e)
                } finally {
                    recycledNv21Buffer.set(nv21Buf)
                    encoding.set(false)
                }
            }
        } catch (e: Exception) {
            Log.e("CameraManager", "Processing error", e)
            try {
                imageProxy.close()
            } catch (_: Exception) {}
        }
    }

    fun getH264SpsPps(): ByteArray? = h264Encoder?.getSpsPps()

    fun getH264Flow(
        cameraId: String,
        width: Int = 640,
        height: Int = 480,
        fps: Int = 30,
        bitrate: Int = 2_000_000
    ): SharedFlow<ByteArray> {
        h264Flows.entries.removeAll { (_, f) -> f.subscriptionCount.value == 0 }
        val flowKey = "$cameraId:$width:$height:$fps:$bitrate"
        val flow = h264Flows.getOrPut(flowKey) {
            MutableSharedFlow(
                replay = 0,
                extraBufferCapacity = 10,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        }

        mainHandler.post {
            lastLifecycleOwner?.let { owner ->
                val config = "$cameraId:$width:$height:$fps:$bitrate"
                if (activeH264Config != config || boundCamera == null) {
                    startH264Camera(owner, cameraId, width, height, fps, bitrate, flow)
                }
            }
        }

        return flow
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun startH264Camera(
        lifecycleOwner: LifecycleOwner,
        cameraId: String,
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        flow: MutableSharedFlow<ByteArray>
    ) {
        val config = "$cameraId:$width:$height:$fps:$bitrate"
        if (activeH264Config == config && boundCamera != null) return

        h264Encoder?.stop()
        val encoder = HardwareH264Encoder(width, height, fps, bitrate) { nalBytes, _ ->
            flow.tryEmit(nalBytes)
        }
        encoder.start()
        h264Encoder = encoder

        lastLifecycleOwner = lifecycleOwner
        activeCameraId = cameraId
        activeResolution = Resolution(width, height)
        activeFps = fps
        activeH264Config = config
        lastFrameTimeMs = 0L

        val provider = cameraProvider ?: return
        provider.unbindAll()

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
                Range(fps, maxOf(fps, 30))
            )
        } catch (e: Exception) {
            Log.w("CameraManager", "Could not set AE target FPS range", e)
        }

        val analysis = builder.build()
        analysis.setAnalyzer(cameraExecutor) { image ->
            processH264Image(image, flow, fps, encoder)
        }

        val selector = CameraSelector.Builder()
            .addCameraFilter { infos ->
                infos.filter { Camera2CameraInfo.from(it).cameraId == logicalId }
            }
            .build()

        try {
            boundCamera = provider.bindToLifecycle(lifecycleOwner, selector, analysis)
            if (isFlashlightOn) {
                try { boundCamera?.cameraControl?.enableTorch(true) } catch (_: Exception) {}
            }
            Log.d("CameraManager", "Bound camera $cameraId for H.264 at ${width}x$height @ ${fps}FPS")
        } catch (e: Exception) {
            Log.e("CameraManager", "Failed to bind camera $cameraId for H.264", e)
        }
    }

    private fun processH264Image(
        imageProxy: ImageProxy,
        flow: MutableSharedFlow<ByteArray>,
        targetFps: Int,
        encoder: HardwareH264Encoder
    ) {
        try {
            if (flow.subscriptionCount.value == 0) {
                imageProxy.close()
                return
            }
            val now = SystemClock.elapsedRealtime()
            val minIntervalMs = if (targetFps > 0) 1000L / targetFps else 0L
            if (minIntervalMs > 0 && (now - lastFrameTimeMs) < minIntervalMs) {
                imageProxy.close()
                return
            }

            val imgW = imageProxy.width
            val imgH = imageProxy.height

            val requiredNv21Size = imgW * imgH * 3 / 2
            var nv21 = recycledNv21Buffer.getAndSet(null)
            if (nv21 == null || nv21.size != requiredNv21Size) {
                nv21 = ByteArray(requiredNv21Size)
            }

            val yPlane = imageProxy.planes[0]
            val uPlane = imageProxy.planes[1]
            val vPlane = imageProxy.planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val yRowStride = yPlane.rowStride
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            var nv21Offset = 0
            if (yRowStride == imgW) {
                yBuffer.get(nv21, 0, imgW * imgH)
                nv21Offset = imgW * imgH
            } else {
                for (row in 0 until imgH) {
                    yBuffer.position(row * yRowStride)
                    yBuffer.get(nv21, nv21Offset, imgW)
                    nv21Offset += imgW
                }
            }

            val uvHeight = imgH / 2
            val uvWidth = imgW / 2

            if (uvPixelStride == 2 && uvRowStride == imgW && vBuffer.remaining() >= uvHeight * uvRowStride - 1) {
                vBuffer.get(nv21, nv21Offset, uvHeight * uvRowStride - 1)
                if (vBuffer.remaining() > 0) {
                    nv21[requiredNv21Size - 1] = uBuffer.get(uBuffer.limit() - 1)
                }
            } else {
                for (row in 0 until uvHeight) {
                    val rowStart = row * uvRowStride
                    for (col in 0 until uvWidth) {
                        val vIndex = rowStart + col * uvPixelStride
                        val uIndex = rowStart + col * uvPixelStride
                        nv21[nv21Offset++] = vBuffer.get(vIndex)
                        nv21[nv21Offset++] = uBuffer.get(uIndex)
                    }
                }
            }

            imageProxy.close()

            if (encoding.getAndSet(true)) {
                recycledNv21Buffer.set(nv21)
                recordFrameDropped()
                return
            }

            lastFrameTimeMs = now
            val ptsUs = System.nanoTime() / 1000L
            val nv21Buf = nv21

            encodeExecutor.execute {
                try {
                    encoder.encodeNv21(nv21Buf, ptsUs) { bytesSent ->
                        recordFrameProcessed(bytesSent)
                    }
                } catch (e: Exception) {
                    Log.e("CameraManager", "H264 Encoding error", e)
                } finally {
                    recycledNv21Buffer.set(nv21Buf)
                    encoding.set(false)
                }
            }
        } catch (e: Exception) {
            Log.e("CameraManager", "Processing H264 error", e)
            try {
                imageProxy.close()
            } catch (_: Exception) {}
        }
    }

    fun stopStreaming() {
        h264Encoder?.stop()
        h264Encoder = null
        activeH264Config = null
        cameraProvider?.unbindAll()
        boundCamera = null
        activeCameraId = null
        activeResolution = null
        activeFps = 30
    }
}

@Suppress("DEPRECATION")
class HardwareH264Encoder(
    private val width: Int,
    private val height: Int,
    private val fps: Int = 30,
    private val bitrate: Int = 2_000_000,
    private val onOutputNal: (ByteArray, Boolean) -> Unit
) {
    private var mediaCodec: MediaCodec? = null
    @Volatile private var isRunning = false
    private var spsPpsData: ByteArray? = null
    private var nv12Buffer: ByteArray? = null

    private fun getOrAllocateNv12(size: Int): ByteArray {
        var buf = nv12Buffer
        if (buf == null || buf.size != size) {
            buf = ByteArray(size)
            nv12Buffer = buf
        }
        return buf
    }

    fun getSpsPps(): ByteArray? = spsPpsData

    fun start() {
        if (isRunning) return
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            mediaCodec = codec
            isRunning = true
            Log.d("H264Encoder", "Hardware MediaCodec H.264 encoder started at ${width}x${height} @ ${fps}FPS (${bitrate / 1000}kbps)")
        } catch (e: Exception) {
            Log.e("H264Encoder", "Failed to start MediaCodec H.264 encoder", e)
            stop()
        }
    }

    fun encodeNv21(nv21: ByteArray, ptsUs: Long, onBytesEncoded: ((Int) -> Unit)? = null) {
        val codec = mediaCodec ?: return
        if (!isRunning) return

        try {
            val inputIndex = codec.dequeueInputBuffer(10_000L)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    val nv12 = nv21ToNv12(nv21, width, height)
                    val sizeToCopy = minOf(nv12.size, inputBuffer.remaining())
                    inputBuffer.put(nv12, 0, sizeToCopy)
                    codec.queueInputBuffer(inputIndex, 0, sizeToCopy, ptsUs, 0)
                }
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
            while (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    val outData = ByteArray(bufferInfo.size)
                    outputBuffer.get(outData)

                    val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                    if (isConfig) {
                        spsPpsData = outData
                        onOutputNal(outData, true)
                    } else {
                        onBytesEncoded?.invoke(outData.size)
                        onOutputNal(outData, isKeyFrame)
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false)
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
            }
        } catch (e: Exception) {
            Log.e("H264Encoder", "Encoding error", e)
        }
    }

    fun stop() {
        isRunning = false
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (e: Exception) {
            Log.w("H264Encoder", "Error stopping MediaCodec", e)
        } finally {
            mediaCodec = null
        }
    }

    private fun nv21ToNv12(nv21: ByteArray, width: Int, height: Int): ByteArray {
        val size = width * height
        val nv12 = getOrAllocateNv12(nv21.size)
        System.arraycopy(nv21, 0, nv12, 0, minOf(size, nv21.size))
        var i = size
        val maxLen = nv21.size - 1
        while (i < maxLen) {
            nv12[i] = nv21[i + 1]
            nv12[i + 1] = nv21[i]
            i += 2
        }
        return nv12
    }
}
