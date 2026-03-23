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
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import kotlin.math.abs

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

    var currentIso: Int = 800
    var currentExposureNs: Long = 100_000_000L
    var currentAperture: Float = 0.0f
    
    @Volatile var isCapturing: Boolean = false
    @Volatile var isTransitioning: Boolean = false

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

    fun scanAvailableLenses(manager: CameraManager): List<AstroLensInfo> {
        this.manager = manager
        val lensList = mutableListOf<AstroLensInfo>()
        Log.e(TAG, "===== AUDITORÍA PRO FINAL =====")
        
        try {
            manager.cameraIdList.forEach { id ->
                val chars = manager.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
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
        val expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val frameDuration = chars.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION)
        val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList() ?: emptyList()
        
        // El poder real detectado
        var maxExp = maxOf(expRange?.upper ?: 0L, frameDuration ?: 0L)
        
        // Si es sensor principal, desbloqueamos 30s nativamente si el hardware lo permite
        if (maxExp < 1_000_000_000L && (lId == "0" || pId == "0")) {
            maxExp = 30_000_000_000L
        }

        val uniqueId = pId ?: lId
        val type = if (pId != null) "PHYS" else "LOG"
        if (list.any { it.physicalId == pId && it.logicalId == lId }) return

        list.add(AstroLensInfo(
            logicalId = lId, physicalId = pId,
            name = "Sensor $type-$uniqueId",
            maxExposureNs = maxExp,
            maxAnalogIso = chars.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY) ?: 3200,
            aperture = apertures.firstOrNull() ?: 0.0f,
            availableApertures = apertures,
            focalLength = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0.0f,
            hardwareLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: 0,
            supportsLowLightBoost = false,
            hasRaw = true,
            isPhysical = (pId != null)
        ))
    }

    @SuppressLint("MissingPermission")
    fun openLens(lens: AstroLensInfo, previewSurface: Surface) {
        if (isTransitioning) return
        isTransitioning = true
        this.currentPreviewSurface = previewSurface
        this.currentAperture = lens.aperture
        
        val idToOpen = lens.physicalId ?: lens.logicalId
        closeCameraInternal()

        backgroundHandler?.postDelayed({
            try {
                manager?.openCamera(idToOpen, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        createSession(lens, previewSurface)
                    }
                    override fun onDisconnected(camera: CameraDevice) { forceReset() }
                    override fun onError(camera: CameraDevice, error: Int) { forceReset() }
                    override fun onClosed(camera: CameraDevice) { Log.d(TAG, "Hardware liberado.") }
                }, backgroundHandler)
            } catch (e: Exception) { forceReset() }
        }, 300)
    }

    private fun createSession(lens: AstroLensInfo, previewSurface: Surface) {
        val targetId = lens.physicalId ?: lens.logicalId
        val chars = manager?.getCameraCharacteristics(targetId)
        val map = chars?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val rawSize = map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull { it.width * it.height }
        
        rawImageReader = ImageReader.newInstance(rawSize?.width ?: 1920, rawSize?.height ?: 1080, ImageFormat.RAW_SENSOR, 5).apply {
            setOnImageAvailableListener({ reader ->
                val img = reader.acquireNextImage()
                if (img != null) handleRawImage(img, lens)
            }, backgroundHandler)
        }

        val previewConfig = OutputConfiguration(previewSurface)
        val rawConfig = OutputConfiguration(rawImageReader!!.surface)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val sessionBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL)
            sessionBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF)
            sessionBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            if (currentAperture > 0) sessionBuilder?.set(CaptureRequest.LENS_APERTURE, currentAperture)

            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                listOf(previewConfig, rawConfig),
                context.mainExecutor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        isTransitioning = false
                        updatePreviewSettings()
                        mainExecutor.execute { onCaptureReadyListener?.invoke() }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) { forceReset() }
                }
            )
            sessionConfig.setSessionParameters(sessionBuilder!!.build())
            cameraDevice?.createCaptureSession(sessionConfig)
        }
    }

    fun updatePreviewSettings() {
        val surface = currentPreviewSurface ?: return
        if (cameraDevice == null || captureSession == null) return
        
        try {
            val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder?.addTarget(surface)
            builder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF)
            builder?.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            
            val previewExp = currentExposureNs.coerceAtMost(100_000_000L)
            builder?.set(CaptureRequest.SENSOR_EXPOSURE_TIME, previewExp)
            builder?.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
            
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
        
        Log.e(TAG, "[CAPTURE] Iniciando toma de larga duración: ${currentExposureNs/1e9}s")

        try {
            val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL)
            rawImageReader?.surface?.let { builder?.addTarget(it) }
            
            builder?.apply {
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF)
                set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposureNs)
                set(CaptureRequest.SENSOR_FRAME_DURATION, currentExposureNs + 10_000_000L)
                set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
                set(CaptureRequest.NOISE_REDUCTION_MODE, 0)
            }

            val requests = List(count) { builder!!.build() }
            captureSession?.stopRepeating()
            
            captureSession?.captureBurst(requests, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    handleCaptureResult(result, lens)
                }
                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                    Log.e(TAG, "Capture Failed: ${failure.reason}")
                    finalizeCapture()
                }
            }, backgroundHandler)
        } catch (e: Exception) { finalizeCapture() }
    }

    private fun handleRawImage(image: Image, lens: AstroLensInfo) {
        val ts = image.timestamp
        val result = findMatchingResult(ts)
        if (result != null) {
            Log.d(TAG, "[SYNC] Imagen RAW detectada. Procesando...")
            saveDng(image, result, lens)
        } else {
            pendingRawImages[ts] = image
        }
    }

    private fun handleCaptureResult(result: TotalCaptureResult, lens: AstroLensInfo) {
        val ts = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
        val image = findMatchingImage(ts)
        if (image != null) {
            Log.d(TAG, "[SYNC] Metadatos RAW detectados. Procesando...")
            saveDng(image, result, lens)
        } else {
            pendingCaptureResults[ts] = result
        }
    }

    private fun findMatchingResult(timestamp: Long): TotalCaptureResult? {
        return pendingCaptureResults.keys.find { abs(it - timestamp) < 2_000_000 }?.let { 
            pendingCaptureResults.remove(it) 
        }
    }

    private fun findMatchingImage(timestamp: Long): Image? {
        return pendingRawImages.keys.find { abs(it - timestamp) < 2_000_000 }?.let { 
            pendingRawImages.remove(it) 
        }
    }

    private fun saveDng(image: Image, result: TotalCaptureResult, lens: AstroLensInfo) {
        backgroundHandler?.post {
            try {
                Log.i(TAG, "[IO] Iniciando escritura de datos masivos DNG...")
                val targetId = lens.physicalId ?: lens.logicalId
                val chars = manager?.getCameraCharacteristics(targetId) ?: return@post
                val filename = "ASTRO_REAL_${System.currentTimeMillis()}.dng"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/CameraStellar")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        DngCreator(chars, result).use { creator ->
                            creator.setOrientation(ExifInterface.ORIENTATION_NORMAL)
                            creator.writeImage(out, image)
                        }
                    }
                    Log.i(TAG, "[IO] ✅ ÉXITO: Archivo guardado en Galería -> $filename")
                }
            } catch (e: Exception) { 
                Log.e(TAG, "Error IO: ${e.message}")
            } finally { 
                image.close() 
                finalizeCapture()
            }
        }
    }

    private fun finalizeCapture() {
        isCapturing = false
        backgroundHandler?.postDelayed({
            updatePreviewSettings()
            mainExecutor.execute { onCaptureReadyListener?.invoke() }
        }, 500)
    }

    fun closeCamera() {
        forceReset()
    }

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
