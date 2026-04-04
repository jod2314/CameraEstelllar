package com.stellar.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.media.ExifInterface
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.view.Surface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * CameraController V3.7: Versión final con metadatos científicos de Google y Samsung Shutter Fix.
 */
class CameraController(private val context: Context) {
    private val TAG = "AstroEngine"
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var manager: CameraManager? = null
    
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private val mainExecutor: Executor = context.mainExecutor
    
    private val writeExecutor = Executors.newFixedThreadPool(2)
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var rawImageReader: ImageReader? = null
    private var currentPreviewSurface: Surface? = null
    
    private val pendingRawImages = ConcurrentHashMap<Long, Image>()
    private val pendingCaptureResults = ConcurrentHashMap<Long, CaptureResult>()

    @Volatile var currentIso: Int = 800
    @Volatile var currentExposureNs: Long = 100_000_000L
    @Volatile var currentAperture: Float = 0.0f
    
    private var lastUiUpdateMs: Long = 0L
    
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
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CameraStellar:CaptureLock")
        }
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    fun scanAvailableLenses(manager: CameraManager): List<AstroLensInfo> {
        this.manager = manager
        val lensList = mutableListOf<AstroLensInfo>()
        Log.e(TAG, "===== AUDITORÍA PRO V3.7 (GOOGLE RAW PATTERN) =====")
        
        try {
            manager.cameraIdList.forEach { id ->
                val chars = manager.getCameraCharacteristics(id)
                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                val hasRaw = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
                val hasManual = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
                val level = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: -1
                
                if ((chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) && 
                    (hasRaw || (hasManual && level >= 1))) {
                    addLensToList(id, null, chars, lensList)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        chars.physicalCameraIds.forEach { pId ->
                            try {
                                val pChars = manager.getCameraCharacteristics(pId)
                                addLensToList(id, pId, pChars, lensList)
                            } catch (e: Exception) {}
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return lensList
    }

    private fun addLensToList(lId: String, pId: String?, chars: CameraCharacteristics, list: MutableList<AstroLensInfo>) {
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return
        val format = if (chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true) 
                     ImageFormat.RAW_SENSOR else ImageFormat.JPEG
        if (map.getOutputSizes(format).isNullOrEmpty()) return

        val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList() ?: emptyList()
        val expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)

        if (list.any { it.physicalId == pId && it.logicalId == lId }) return

        list.add(AstroLensInfo(
            logicalId = lId, physicalId = pId,
            name = "Sensor ${if (pId != null) "PHYS" else "LOG"}-${pId ?: lId}",
            maxExposureNs = expRange?.upper ?: 0L,
            maxAnalogIso = chars.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY) ?: isoRange?.upper ?: 3200,
            aperture = apertures.firstOrNull() ?: 0.0f,
            availableApertures = apertures,
            focalLength = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0.0f,
            hardwareLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: 0,
            supportsLowLightBoost = false,
            hasRaw = format == ImageFormat.RAW_SENSOR,
            isPhysical = (pId != null)
        ))
    }

    private fun <T> CaptureRequest.Builder.safeSet(key: CaptureRequest.Key<T>, value: T, chars: CameraCharacteristics?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (chars?.availableCaptureRequestKeys?.contains(key) == true) this.set(key, value)
        } else {
            try { this.set(key, value) } catch (e: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    fun openLens(lens: AstroLensInfo, previewSurface: Surface) {
        if (isTransitioning) return
        isTransitioning = true
        this.currentPreviewSurface = previewSurface
        closeCameraInternal()

        backgroundHandler?.postDelayed({
            try {
                manager?.openCamera(lens.logicalId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        createSession(lens, previewSurface)
                    }
                    override fun onDisconnected(camera: CameraDevice) { forceReset() }
                    override fun onError(camera: CameraDevice, error: Int) { forceReset() }
                }, backgroundHandler)
            } catch (e: Exception) { forceReset() }
        }, 300)
    }

    private fun createSession(lens: AstroLensInfo, previewSurface: Surface, useAdvanced: Boolean = true) {
        val targetId = lens.physicalId ?: lens.logicalId
        val chars = manager?.getCameraCharacteristics(targetId)
        val format = if (lens.hasRaw) ImageFormat.RAW_SENSOR else ImageFormat.JPEG
        val map = chars?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val size = map?.getOutputSizes(format)?.maxByOrNull { it.width * it.height } ?: return

        rawImageReader = ImageReader.newInstance(size.width, size.height, format, 10).apply {
            setOnImageAvailableListener({ reader ->
                val img = try { reader.acquireNextImage() } catch (e: Exception) { null }
                if (img != null) handleRawImage(img, lens)
            }, backgroundHandler)
        }

        val outputs = mutableListOf(OutputConfiguration(previewSurface), OutputConfiguration(rawImageReader!!.surface))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && lens.physicalId != null) {
            outputs.forEach { it.setPhysicalCameraId(lens.physicalId) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val sessionParams = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL)
            if (useAdvanced && sessionParams != null) {
                sessionParams.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF)
                sessionParams.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                sessionParams.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                
                if (Build.MANUFACTURER.contains("samsung", ignoreCase = true)) {
                    try {
                        val samsungOpMode = CaptureRequest.Key<Int>("samsung.android.control.operation_mode", Int::class.java)
                        sessionParams.set(samsungOpMode, 0x9201) 
                    } catch (e: Exception) {}
                }

                val maxFD = chars?.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION) ?: 30_000_000_000L
                sessionParams.safeSet(CaptureRequest.SENSOR_FRAME_DURATION, maxFD, chars)
            }

            val sessionConfig = SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, mainExecutor, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    isTransitioning = false
                    updatePreviewSettings()
                    mainExecutor.execute { onCaptureReadyListener?.invoke() }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) { forceReset() }
            })
            sessionParams?.let { sessionConfig.setSessionParameters(it.build()) }
            cameraDevice?.createCaptureSession(sessionConfig)
        }
    }

    fun updatePreviewSettings() {
        val surface = currentPreviewSurface ?: return
        val camera = cameraDevice ?: return
        val session = captureSession ?: return
        val chars = manager?.getCameraCharacteristics(camera.id)
        
        try {
            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL)
            builder.addTarget(surface)
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF)
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            builder.safeSet(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f, chars)
            
            if (Build.MANUFACTURER.contains("samsung", ignoreCase = true)) {
                try {
                    val samsungOpMode = CaptureRequest.Key<Int>("samsung.android.control.operation_mode", Int::class.java)
                    builder.set(samsungOpMode, 0x9201) 
                } catch (e: Exception) {}
            }

            val previewExp = currentExposureNs.coerceAtMost(100_000_000L)
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, previewExp)
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
            builder.safeSet(CaptureRequest.SENSOR_FRAME_DURATION, 150_000_000L, chars)
            
            session.setRepeatingRequest(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdateMs > 150) {
                        lastUiUpdateMs = now
                        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: currentIso
                        val exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: currentExposureNs
                        mainExecutor.execute { onMetadataReceived?.invoke(iso, exp) }
                    }
                }
            }, backgroundHandler)
        } catch (e: Exception) {}
    }

    fun takeBurst(count: Int, lens: AstroLensInfo) {
        val camera = cameraDevice ?: return
        val session = captureSession ?: return
        if (isCapturing) return
        isCapturing = true
        expectedBurstCount = count
        capturedBurstCount = 0
        
        try {
            val serviceIntent = Intent(context, AstroCaptureService::class.java).apply {
                putExtra("LENS_NAME", lens.name); putExtra("EXPOSURE", "${currentExposureNs/1e9}s")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
            if (wakeLock?.isHeld == false) wakeLock?.acquire(10 * 60 * 1000L)
        } catch (e: Exception) {}

        val chars = manager?.getCameraCharacteristics(lens.physicalId ?: lens.logicalId)
        try {
            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL)
            rawImageReader?.surface?.let { builder.addTarget(it) }
            builder.apply {
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF)
                set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                safeSet(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_FAST, chars)
                safeSet(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_OFF, chars)
                safeSet(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_OFF, chars)
                set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposureNs)
                set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
                safeSet(CaptureRequest.SENSOR_FRAME_DURATION, currentExposureNs + 100_000_000L, chars)
                safeSet(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f, chars)
            }

            session.stopRepeating()
            session.captureBurst(List(count) { builder.build() }, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureStarted(s: CameraCaptureSession, r: CaptureRequest, ts: Long, fn: Long) {
                    Log.d(TAG, "➔ INICIO: Frame $fn | TS: $ts")
                }
                override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                    val pId = lens.physicalId
                    val realResult: CaptureResult = if (pId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        result.getPhysicalCameraResults()[pId] ?: result
                    } else {
                        result
                    }
                    val exp = realResult.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
                    Log.i(TAG, "✔ Frame OK | Exp: ${exp/1e6}ms | Delta: ${abs(currentExposureNs - exp)/1e6}ms")
                    handleCaptureResult(realResult, lens)
                }
                override fun onCaptureFailed(s: CameraCaptureSession, r: CaptureRequest, f: CaptureFailure) {
                    capturedBurstCount++; checkFinalize()
                }
            }, backgroundHandler)
        } catch (e: Exception) { finalizeCapture() }
    }

    private fun handleRawImage(image: Image, lens: AstroLensInfo) {
        val ts = image.timestamp
        val result = findMatchingResult(ts)
        if (result != null) processAndSave(image, result, lens) else pendingRawImages[ts] = image
    }

    private fun handleCaptureResult(result: CaptureResult, lens: AstroLensInfo) {
        val ts = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
        val image = findMatchingImage(ts)
        if (image != null) processAndSave(image, result, lens) else pendingCaptureResults[ts] = result
    }

    private fun findMatchingResult(ts: Long): CaptureResult? = 
        pendingCaptureResults.keys.find { abs(it - ts) < 2_000_000 }?.let { pendingCaptureResults.remove(it) }

    private fun findMatchingImage(ts: Long): Image? = 
        pendingRawImages.keys.find { abs(it - ts) < 2_000_000 }?.let { pendingRawImages.remove(it) }

    private fun processAndSave(image: Image, result: CaptureResult, lens: AstroLensInfo) {
        writeExecutor.execute {
            try {
                val targetId = lens.physicalId ?: lens.logicalId
                val chars = manager?.getCameraCharacteristics(targetId) ?: return@execute
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
                                // INTEGRACIÓN CIENTÍFICA (Google Pattern):
                                // Usar la orientación física del sensor para que el DNG sea correcto
                                val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                                creator.setOrientation(sensorOrientation)
                                creator.writeImage(out, image)
                            }
                        } else {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining()); buffer.get(bytes); out.write(bytes)
                        }
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Error IO: ${e.message}") } 
            finally { image.close(); mainExecutor.execute { capturedBurstCount++; checkFinalize() } }
        }
    }

    private fun checkFinalize() { if (capturedBurstCount >= expectedBurstCount) finalizeCapture() }

    private fun finalizeCapture() {
        isCapturing = false
        if (wakeLock?.isHeld == true) wakeLock?.release()
        context.stopService(Intent(context, AstroCaptureService::class.java))
        backgroundHandler?.postDelayed({ updatePreviewSettings(); onCaptureReadyListener?.invoke() }, 500)
    }

    fun closeCamera() { forceReset() }

    private fun forceReset() {
        isCapturing = false; isTransitioning = false
        if (wakeLock?.isHeld == true) wakeLock?.release()
        closeCameraInternal()
    }

    private fun closeCameraInternal() {
        try {
            captureSession?.stopRepeating(); captureSession?.abortCaptures()
            captureSession?.close(); cameraDevice?.close(); rawImageReader?.close()
        } catch (e: Exception) {} 
        finally { captureSession = null; cameraDevice = null; rawImageReader = null }
    }
}
