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

/**
 * CameraController: Motor de captura avanzado basado en los estándares 2025.
 */
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

    // Parámetros de Captura
    var currentIso: Int = 800
    var currentExposureNs: Long = 100_000_000L

    fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("AstroEngine").also { it.start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
    }

    /**
     * ESCANEO PROFUNDO: Detecta hardware y componentes físicos.
     */
    fun scanAvailableLenses(manager: CameraManager): List<AstroLensInfo> {
        this.manager = manager
        val lensList = mutableListOf<AstroLensInfo>()
        
        try {
            for (logicalId in manager.cameraIdList) {
                val chars = manager.getCameraCharacteristics(logicalId)
                if (chars.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) continue

                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                val isMulti = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)

                if (isMulti && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    chars.physicalCameraIds.forEach { physId ->
                        evaluateAndAddLens(logicalId, physId, manager.getCameraCharacteristics(physId), lensList)
                    }
                } else {
                    evaluateAndAddLens(logicalId, null, chars, lensList)
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Error en Scan", e) }
        return lensList.distinctBy { it.logicalId + (it.physicalId ?: "") }
            .sortedByDescending { it.maxExposureNs }
    }

    private fun evaluateAndAddLens(lId: String, pId: String?, chars: CameraCharacteristics, list: MutableList<AstroLensInfo>) {
        val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        if (!caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) return

        val expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0.0f
        val aperture = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull() ?: 0.0f
        
        val supportsBoost = if (Build.VERSION.SDK_INT >= 35) {
            chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.contains(4) ?: false
        } else false

        val name = when {
            focal < 3.0f -> "Ultra-Wide"
            focal > 6.0f -> "Telephoto"
            else -> "Principal"
        }

        list.add(AstroLensInfo(
            logicalId = lId,
            physicalId = pId,
            name = name,
            maxExposureNs = expRange?.upper ?: 1_000_000_000L,
            maxAnalogIso = chars.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY) ?: isoRange?.upper ?: 800,
            aperture = aperture,
            focalLength = focal,
            hardwareLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!,
            supportsLowLightBoost = supportsBoost,
            hasRaw = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        ))
    }

    @SuppressLint("MissingPermission")
    fun openLens(lens: AstroLensInfo, previewSurface: Surface) {
        closeCamera()
        this.currentPreviewSurface = previewSurface
        
        try {
            manager?.openCamera(lens.logicalId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createSession2025(lens, previewSurface)
                }
                override fun onDisconnected(camera: CameraDevice) { closeCamera() }
                override fun onError(camera: CameraDevice, error: Int) { closeCamera() }
            }, backgroundHandler)
        } catch (e: Exception) { Log.e(TAG, "Error opening lens", e) }
    }

    private fun createSession2025(lens: AstroLensInfo, previewSurface: Surface) {
        val targetId = lens.physicalId ?: lens.logicalId
        val chars = manager?.getCameraCharacteristics(targetId)
        val map = chars?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val rawSize = map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull { it.width * it.height }
        
        rawSize?.let {
            rawImageReader = ImageReader.newInstance(it.width, it.height, ImageFormat.RAW_SENSOR, 2).apply {
                setOnImageAvailableListener({ reader ->
                    val img = reader.acquireNextImage()
                    if (img != null) handleRawImage(img, lens)
                }, backgroundHandler)
            }
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
            
            if (Build.VERSION.SDK_INT >= 35 && lens?.supportsLowLightBoost == true) {
                builder?.set(CaptureRequest.CONTROL_AE_MODE, 4) // Boost Priority
            } else {
                builder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                builder?.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            }
            
            captureSession?.setRepeatingRequest(builder!!.build(), null, backgroundHandler)
        } catch (e: Exception) {}
    }

    fun takeBurst(count: Int) {
        if (cameraDevice == null || captureSession == null) return
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
                    if (requests.indexOf(r) == count - 1) updatePreviewSettings()
                }
            }, backgroundHandler)
        } catch (e: Exception) {}
    }

    private fun handleRawImage(image: Image, lens: AstroLensInfo) {
        backgroundHandler?.post {
            try {
                val filename = "ASTRO_${lens.name}_${System.currentTimeMillis()}.dng"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/CameraStellar")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        // Reservado para DngCreator en Fase 3
                    }
                }
            } catch (e: Exception) {} finally { image.close() }
        }
    }

    fun closeCamera() {
        captureSession?.close()
        cameraDevice?.close()
        rawImageReader?.close()
        captureSession = null
        cameraDevice = null
        rawImageReader = null
    }
}
