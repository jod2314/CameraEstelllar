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
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import kotlin.math.abs

class CameraController(private val context: Context) {
    private val TAG = "CameraController"
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
    var isCapturing: Boolean = false

    fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("AstroCore").also { it.start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    fun stopBackgroundThread() {
        if (isCapturing) return // No matar el hilo si estamos procesando 20s
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
    }

    fun scanAvailableLenses(manager: CameraManager): List<AstroLensInfo> {
        this.manager = manager
        val lensList = mutableListOf<AstroLensInfo>()
        try {
            val logicalChars = manager.getCameraCharacteristics("0")
            val physicalIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                logicalChars.physicalCameraIds
            } else emptySet()

            addLensToList("0", null, logicalChars, lensList)
            physicalIds.forEach { id ->
                addLensToList("0", id, manager.getCameraCharacteristics(id), lensList)
            }
        } catch (e: Exception) { Log.e(TAG, "Scan fail", e) }
        return lensList
    }

    private fun addLensToList(lId: String, pId: String?, chars: CameraCharacteristics, list: MutableList<AstroLensInfo>) {
        val expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0.0f
        
        list.add(AstroLensInfo(
            logicalId = lId, physicalId = pId,
            name = if (focal < 3.0f) "Ultra-Wide" else if (focal > 6.0f) "Tele" else "Main",
            maxExposureNs = expRange?.upper ?: 1_000_000_000L,
            maxAnalogIso = chars.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY) ?: isoRange?.upper ?: 800,
            aperture = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull() ?: 0.0f,
            focalLength = focal,
            hardwareLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: 0,
            supportsLowLightBoost = if (Build.VERSION.SDK_INT >= 35) chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.contains(4) == true else false,
            hasRaw = true
        ))
    }

    @SuppressLint("MissingPermission")
    fun openLens(lens: AstroLensInfo, previewSurface: Surface) {
        this.currentPreviewSurface = previewSurface
        manager?.openCamera("0", object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                createSession(lens, previewSurface)
            }
            override fun onDisconnected(camera: CameraDevice) { closeCamera() }
            override fun onError(camera: CameraDevice, error: Int) { closeCamera() }
        }, backgroundHandler)
    }

    private fun createSession(lens: AstroLensInfo, previewSurface: Surface) {
        val targetId = lens.physicalId ?: "0"
        val chars = manager?.getCameraCharacteristics(targetId)
        val map = chars?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val rawSize = map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull { it.width * it.height }
        
        rawImageReader = ImageReader.newInstance(rawSize?.width ?: 1920, rawSize?.height ?: 1080, ImageFormat.RAW_SENSOR, 3).apply {
            setOnImageAvailableListener({ reader ->
                val img = reader.acquireNextImage()
                if (img != null) {
                    Log.d(TAG, "RAW Image Arrived: ${img.timestamp}")
                    handleRawImage(img, lens)
                }
            }, backgroundHandler)
        }

        val previewConfig = OutputConfiguration(previewSurface)
        val rawConfig = OutputConfiguration(rawImageReader!!.surface)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && lens.physicalId != null) {
            previewConfig.setPhysicalCameraId(lens.physicalId)
            rawConfig.setPhysicalCameraId(lens.physicalId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                listOf(previewConfig, rawConfig),
                context.mainExecutor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        updatePreviewSettings(lens)
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                }
            )
            cameraDevice?.createCaptureSession(sessionConfig)
        }
    }

    fun updatePreviewSettings(lens: AstroLensInfo? = null) {
        val surface = currentPreviewSurface ?: return
        if (cameraDevice == null || captureSession == null) return
        try {
            val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder?.addTarget(surface)
            builder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            captureSession?.setRepeatingRequest(builder!!.build(), null, backgroundHandler)
        } catch (e: Exception) {}
    }

    fun takeBurst(count: Int, lens: AstroLensInfo) {
        if (cameraDevice == null || captureSession == null || isCapturing) return
        isCapturing = true
        try {
            val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL)
            rawImageReader?.surface?.let { builder?.addTarget(it) }
            
            builder?.apply {
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF)
                set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposureNs)
                set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
                set(CaptureRequest.SENSOR_FRAME_DURATION, currentExposureNs)
                set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f)
                set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_OFF)
            }

            val requests = List(count) { builder!!.build() }
            captureSession?.stopRepeating()
            
            captureSession?.captureBurst(requests, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                    val realExp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
                    Log.w(TAG, "BYPASS RESULT: Requerido=${currentExposureNs/1e9}s | Real=${realExp/1e9}s")
                    handleCaptureResult(result, lens)
                    if (requests.indexOf(r) == count - 1) {
                        isCapturing = false
                        updatePreviewSettings(lens)
                    }
                }
                override fun onCaptureFailed(s: CameraCaptureSession, r: CaptureRequest, f: CaptureFailure) {
                    isCapturing = false
                    updatePreviewSettings(lens)
                }
            }, backgroundHandler)
        } catch (e: Exception) { isCapturing = false }
    }

    private fun handleRawImage(image: Image, lens: AstroLensInfo) {
        val ts = image.timestamp
        // REPARACIÓN: Búsqueda con tolerancia de 1ms para largas exposiciones
        val result = findMatchingResult(ts)
        if (result != null) {
            saveDng(image, result, lens)
        } else {
            pendingRawImages[ts] = image
        }
    }

    private fun handleCaptureResult(result: TotalCaptureResult, lens: AstroLensInfo) {
        val ts = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
        Log.d(TAG, "Metadata Arrived: $ts")
        val image = findMatchingImage(ts)
        if (image != null) {
            saveDng(image, result, lens)
        } else {
            pendingCaptureResults[ts] = result
        }
    }

    private fun findMatchingResult(timestamp: Long): TotalCaptureResult? {
        return pendingCaptureResults.keys.find { abs(it - timestamp) < 1_000_000 }?.let { 
            pendingCaptureResults.remove(it) 
        }
    }

    private fun findMatchingImage(timestamp: Long): Image? {
        return pendingRawImages.keys.find { abs(it - timestamp) < 1_000_000 }?.let { 
            pendingRawImages.remove(it) 
        }
    }

    private fun saveDng(image: Image, result: TotalCaptureResult, lens: AstroLensInfo) {
        backgroundHandler?.post {
            try {
                val targetId = lens.physicalId ?: "0"
                val chars = manager?.getCameraCharacteristics(targetId) ?: return@post
                val filename = "ASTRO_${lens.name}_${System.currentTimeMillis()}.dng"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/CameraStellar")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        DngCreator(chars, result).use { creator ->
                            creator.writeImage(out, image)
                        }
                    }
                    Log.i(TAG, "✅ ARCHIVO GUARDADO: $filename")
                }
            } catch (e: Exception) { 
                Log.e(TAG, "❌ Error guardando DNG: ${e.message}")
            } finally { 
                image.close() 
            }
        }
    }

    fun closeCamera() {
        if (isCapturing) return
        try {
            captureSession?.close()
            cameraDevice?.close()
            rawImageReader?.close()
        } catch (e: Exception) {}
        captureSession = null
        cameraDevice = null
        rawImageReader = null
    }
}
