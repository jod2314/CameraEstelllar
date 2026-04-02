package com.stellar.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.media.ExifInterface
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.view.Surface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import kotlin.math.abs

/**
 * CameraController V3.1: Motor de captura robusto con validación HAL y fallback automático.
 */
class CameraController(private val context: Context) {
    private val TAG = "AstroEngine"
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var manager: CameraManager? = null
    
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private val mainExecutor: Executor = context.mainExecutor
    
    private var rawImageReader: ImageReader? = null
    private var currentPreviewSurface: Surface? = null
    
    private val pendingRawImages = ConcurrentHashMap<Long, Image>()
    private val pendingCaptureResults = ConcurrentHashMap<Long, TotalCaptureResult>()

    @Volatile var currentIso: Int = 800
    @Volatile var currentExposureNs: Long = 100_000_000L
    @Volatile var currentAperture: Float = 0.0f
    
    @Volatile var isCapturing: Boolean = false
    @Volatile var isTransitioning: Boolean = false
    private var expectedBurstCount: Int = 0
    private var capturedBurstCount: Int = 0

    var onMetadataReceived: ((iso: Int, exposureNs: Long) -> Unit)? = null
    var onCaptureReadyListener: (() -> Unit)? = null

    fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("AstroCore").also { it.start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
    }

    /**
     * Escaneo Inclusivo: Detecta sensores con RAW o con Control Manual Avanzado.
     */
    fun scanAvailableLenses(manager: CameraManager): List<AstroLensInfo> {
        this.manager = manager
        val lensList = mutableListOf<AstroLensInfo>()
        Log.e(TAG, "===== AUDITORÍA PRO V3.1 (ROBUST SCAN) =====")
        
        try {
            manager.cameraIdList.forEach { id ->
                val chars = manager.getCameraCharacteristics(id)
                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                
                val hasRawCap = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
                val hasManualCap = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
                val hwLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: -1
                
                val isFullOrLevel3 = hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL || 
                                     hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
                
                // Priorizar trasera con RAW o Manual en nivel FULL/LEVEL_3
                val isEligible = (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) && 
                                (hasRawCap || (hasManualCap && isFullOrLevel3))

                if (isEligible) {
                    addLensToList(id, null, chars, lensList)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        chars.physicalCameraIds.forEach { pId ->
                            try {
                                val pChars = manager.getCameraCharacteristics(pId)
                                val pCaps = pChars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                                val pHasRaw = pCaps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
                                val pHasManual = pCaps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
                                
                                if (pHasRaw || pHasManual) {
                                    addLensToList(id, pId, pChars, lensList)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "No se pudo auditar sensor físico $pId: ${e.message}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error crítico en escaneo: ${e.message}")
        }
        return lensList
    }

    private fun addLensToList(lId: String, pId: String?, chars: CameraCharacteristics, list: MutableList<AstroLensInfo>) {
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val hasRawCap = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        
        val captureFormat = if (hasRawCap) ImageFormat.RAW_SENSOR else ImageFormat.JPEG
        val outputSizes = map?.getOutputSizes(captureFormat)
        if (outputSizes == null || outputSizes.isEmpty()) return

        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList() ?: emptyList()
        val level = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: 0
        
        val maxExp = expRange?.upper ?: 0L
        val maxIso = isoRange?.upper ?: chars.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY) ?: 3200
        val pureAnalogIso = chars.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY) ?: maxIso

        val levelStr = when(level) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            else -> "UNKNOWN"
        }

        Log.i(TAG, "=> Auditando Sensor Lóg:$lId Fís:${pId ?: "N/A"}")
        Log.i(TAG, "   - Nivel de Hardware: $levelStr ($level)")
        Log.i(TAG, "   - Rango Exposición (ns): ${expRange?.lower ?: 0} a $maxExp")
        Log.i(TAG, "   - Rango ISO Analógico Máx Puro: $pureAnalogIso")

        val uniqueId = pId ?: lId
        if (list.any { it.physicalId == pId && it.logicalId == lId }) return

        list.add(AstroLensInfo(
            logicalId = lId, physicalId = pId,
            name = "Sensor ${if (pId != null) "PHYS" else "LOG"}-$uniqueId",
            maxExposureNs = maxExp,
            maxAnalogIso = pureAnalogIso,
            aperture = apertures.firstOrNull() ?: 0.0f,
            availableApertures = apertures,
            focalLength = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0.0f,
            hardwareLevel = level,
            supportsLowLightBoost = false,
            hasRaw = hasRawCap,
            isPhysical = (pId != null)
        ))
    }

    /**
     * Helper para evitar excepciones por claves no soportadas en el request.
     */
    private fun <T> CaptureRequest.Builder.safeSet(key: CaptureRequest.Key<T>, value: T, chars: CameraCharacteristics?) {
        val availableKeys = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            chars?.getAvailableCaptureRequestKeys() ?: emptyList()
        } else {
            this.set(key, value)
            return
        }

        if (availableKeys.contains(key)) {
            this.set(key, value)
        } else {
            Log.w(TAG, "Parámetro no soportado: ${key.name}")
        }
    }

    @SuppressLint("MissingPermission")
    fun openLens(lens: AstroLensInfo, previewSurface: Surface) {
        if (isTransitioning) return
        isTransitioning = true
        this.currentPreviewSurface = previewSurface
        this.currentAperture = lens.aperture
        
        closeCameraInternal()

        backgroundHandler?.postDelayed({
            try {
                Log.i(TAG, "Abriendo: ${lens.logicalId} (${lens.name})")
                manager?.openCamera(lens.logicalId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        createSession(lens, previewSurface)
                    }
                    override fun onDisconnected(camera: CameraDevice) { forceReset() }
                    override fun onError(camera: CameraDevice, error: Int) { forceReset() }
                    override fun onClosed(camera: CameraDevice) { Log.d(TAG, "Cámara cerrada.") }
                }, backgroundHandler)
            } catch (e: Exception) { forceReset() }
        }, 300)
    }

    private fun createSession(lens: AstroLensInfo, previewSurface: Surface, useAdvancedParams: Boolean = true) {
        val targetId = lens.physicalId ?: lens.logicalId
        val chars = manager?.getCameraCharacteristics(targetId)
        val captureFormat = if (lens.hasRaw) ImageFormat.RAW_SENSOR else ImageFormat.JPEG
        
        val map = chars?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val captureSize = map?.getOutputSizes(captureFormat)?.maxByOrNull { it.width * it.height } ?: return

        rawImageReader = ImageReader.newInstance(captureSize.width, captureSize.height, captureFormat, 5).apply {
            setOnImageAvailableListener({ reader ->
                val img = try { reader.acquireNextImage() } catch (e: Exception) { null }
                if (img != null) handleRawImage(img, lens)
            }, backgroundHandler)
        }

        val outputs = mutableListOf<OutputConfiguration>()
        val previewConfig = OutputConfiguration(previewSurface)
        val rawConfig = OutputConfiguration(rawImageReader!!.surface)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && lens.physicalId != null) {
            previewConfig.setPhysicalCameraId(lens.physicalId)
            rawConfig.setPhysicalCameraId(lens.physicalId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            previewConfig.streamUseCase = CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW.toLong()
            rawConfig.streamUseCase = CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_STILL_CAPTURE.toLong()
        }

        outputs.add(previewConfig)
        outputs.add(rawConfig)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val sessionParamsBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL)
            
            if (useAdvancedParams) {
                sessionParamsBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                sessionParamsBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                sessionParamsBuilder?.safeSet(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f, chars)
                
                val availableFps = chars?.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
                val targetFps = Range(1, 15)
                if (availableFps.any { it.contains(targetFps.lower) || it.contains(targetFps.upper) }) {
                    sessionParamsBuilder?.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetFps)
                }
                
                val maxFrameDuration = chars?.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION) ?: 30_000_000_000L
                sessionParamsBuilder?.safeSet(CaptureRequest.SENSOR_FRAME_DURATION, maxFrameDuration, chars)
            }

            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputs,
                context.mainExecutor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        isTransitioning = false
                        updatePreviewSettings()
                        mainExecutor.execute { onCaptureReadyListener?.invoke() }
                        Log.i(TAG, "✅ SESIÓN OK")
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) { 
                        if (useAdvancedParams) {
                            Log.w(TAG, "Fallo ADV, reintentando BASIC...")
                            createSession(lens, previewSurface, false)
                        } else {
                            forceReset()
                        }
                    }
                }
            )
            
            sessionParamsBuilder?.let { sessionConfig.setSessionParameters(it.build()) }
            cameraDevice?.createCaptureSession(sessionConfig)
        }
    }

    fun updatePreviewSettings() {
        val surface = currentPreviewSurface ?: return
        if (cameraDevice == null || captureSession == null) return
        val chars = manager?.getCameraCharacteristics(cameraDevice!!.id)
        
        try {
            val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder?.addTarget(surface)
            builder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder?.safeSet(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f, chars)
            builder?.set(CaptureRequest.CONTROL_AE_LOCK, true)

            val previewExp = currentExposureNs.coerceAtMost(500_000_000L)
            builder?.set(CaptureRequest.SENSOR_EXPOSURE_TIME, previewExp)
            builder?.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
            
            val frameDuration = currentExposureNs.coerceAtLeast(previewExp) + 1_000_000L
            builder?.safeSet(CaptureRequest.SENSOR_FRAME_DURATION, frameDuration, chars)
            
            captureSession?.setRepeatingRequest(builder!!.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                    val realIso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: currentIso
                    val realExp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: currentExposureNs
                    onMetadataReceived?.invoke(realIso, realExp)
                }
            }, backgroundHandler)
        } catch (e: Exception) {}
    }

    fun takeBurst(count: Int, lens: AstroLensInfo) {
        if (cameraDevice == null || captureSession == null || isCapturing) return
        isCapturing = true
        expectedBurstCount = count
        capturedBurstCount = 0
        
        val chars = manager?.getCameraCharacteristics(lens.physicalId ?: lens.logicalId)
        
        try {
            val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE) ?: return
            rawImageReader?.surface?.let { builder.addTarget(it) }
            
            builder.apply {
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF)
                set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                
                safeSet(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_FAST, chars)
                safeSet(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_FAST, chars)
                safeSet(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_OFF, chars)
                safeSet(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF, chars)
                safeSet(CaptureRequest.SHADING_MODE, CameraMetadata.SHADING_MODE_OFF, chars)
                
                set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposureNs)
                set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
                safeSet(CaptureRequest.SENSOR_FRAME_DURATION, currentExposureNs + 100_000_000L, chars)
            }

            val requests = List(count) { builder.build() }
            captureSession?.stopRepeating()
            captureSession?.captureBurst(requests, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    Log.i(TAG, "Captura OK")
                    handleCaptureResult(result, lens)
                }
                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                    Log.e(TAG, "Captura fallida: ${failure.reason}")
                    capturedBurstCount++
                    checkFinalize()
                }
            }, backgroundHandler)
        } catch (e: Exception) { finalizeCapture() }
    }

    private fun handleRawImage(image: Image, lens: AstroLensInfo) {
        val ts = image.timestamp
        val result = findMatchingResult(ts)
        if (result != null) saveDng(image, result, lens) else pendingRawImages[ts] = image
    }

    private fun handleCaptureResult(result: TotalCaptureResult, lens: AstroLensInfo) {
        val ts = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
        val image = findMatchingImage(ts)
        if (image != null) saveDng(image, result, lens) else pendingCaptureResults[ts] = result
    }

    private fun findMatchingResult(ts: Long): TotalCaptureResult? = 
        pendingCaptureResults.keys.find { abs(it - ts) < 2_000_000 }?.let { pendingCaptureResults.remove(it) }

    private fun findMatchingImage(ts: Long): Image? = 
        pendingRawImages.keys.find { abs(it - ts) < 2_000_000 }?.let { pendingRawImages.remove(it) }

    private fun saveDng(image: Image, result: TotalCaptureResult, lens: AstroLensInfo) {
        backgroundHandler?.post {
            try {
                val targetId = lens.physicalId ?: lens.logicalId
                val chars = manager?.getCameraCharacteristics(targetId) ?: return@post
                val filename = "ASTRO_${System.currentTimeMillis()}.${if (lens.hasRaw) "dng" else "jpg"}"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, if (lens.hasRaw) "image/x-adobe-dng" else "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/CameraStellar")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        if (lens.hasRaw) {
                            DngCreator(chars, result).use { creator ->
                                creator.setOrientation(ExifInterface.ORIENTATION_NORMAL)
                                creator.writeImage(out, image)
                            }
                        } else {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            out.write(bytes)
                        }
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Error IO: ${e.message}") } 
            finally { 
                image.close() 
                capturedBurstCount++
                checkFinalize()
            }
        }
    }

    private fun checkFinalize() { if (capturedBurstCount >= expectedBurstCount) finalizeCapture() }

    private fun finalizeCapture() {
        isCapturing = false
        backgroundHandler?.postDelayed({ updatePreviewSettings(); onCaptureReadyListener?.invoke() }, 500)
    }

    fun closeCamera() { forceReset() }

    private fun forceReset() {
        isCapturing = false
        isTransitioning = false
        closeCameraInternal()
    }

    private fun closeCameraInternal() {
        try {
            captureSession?.stopRepeating()
            captureSession?.abortCaptures()
            captureSession?.close()
            cameraDevice?.close()
            rawImageReader?.close()
        } catch (e: Exception) {} 
        finally {
            captureSession = null
            cameraDevice = null
            rawImageReader = null
        }
    }
}
