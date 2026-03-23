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
    var isCapturing: Boolean = false
    var isTransitioning: Boolean = false

    var onCaptureReadyListener: (() -> Unit)? = null

    fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("AstroCore").also { it.start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    fun stopBackgroundThread() {
        if (isCapturing) return
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
    }

    fun scanAvailableLenses(manager: CameraManager): List<AstroLensInfo> {
        this.manager = manager
        val lensList = mutableListOf<AstroLensInfo>()
        val seenIds = mutableSetOf<String>()

        Log.e(TAG, "===== ESCANEO DE PRECISIÓN V3 =====")
        
        try {
            manager.cameraIdList.forEach { id ->
                val chars = manager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    addLensToList(id, null, chars, lensList)
                    seenIds.add(id)
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        chars.physicalCameraIds.forEach { pId ->
                            if (!seenIds.contains(pId)) {
                                val pChars = manager.getCameraCharacteristics(pId)
                                addLensToList(id, pId, pChars, lensList)
                                seenIds.add(pId)
                            }
                        }
                    }
                }
            }
            
            for (i in 0..20) {
                val id = i.toString()
                if (seenIds.contains(id)) continue
                try {
                    val chars = manager.getCameraCharacteristics(id)
                    if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                        addLensToList(id, null, chars, lensList)
                        seenIds.add(id)
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) { Log.e(TAG, "Error scanner", e) }
        
        return lensList
    }

    private fun addLensToList(lId: String, pId: String?, chars: CameraCharacteristics, list: MutableList<AstroLensInfo>) {
        val level = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val hasRaw = map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.isNotEmpty() ?: false
        
        if (!hasRaw && level != 3 && level != 1) return

        val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0.0f
        val expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)

        val uniqueId = pId ?: lId
        val name = when {
            pId == null && lId == "0" -> "Principal (0)"
            focal < 3.0f -> "Ultra-Wide ($uniqueId)"
            focal > 6.0f -> "Tele ($uniqueId)"
            else -> "Auxiliar ($uniqueId)"
        }

        list.add(AstroLensInfo(
            logicalId = lId, physicalId = pId,
            name = name,
            maxExposureNs = expRange?.upper ?: 1_000_000_000L,
            maxAnalogIso = chars.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY) ?: isoRange?.upper ?: 800,
            aperture = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull() ?: 0.0f,
            focalLength = focal,
            hardwareLevel = level ?: 0,
            supportsLowLightBoost = false,
            hasRaw = true
        ))
    }

    @SuppressLint("MissingPermission")
    fun openLens(lens: AstroLensInfo, previewSurface: Surface) {
        if (isTransitioning) return
        isTransitioning = true
        this.currentPreviewSurface = previewSurface
        
        Log.d(TAG, "[TELEMETRÍA] Abriendo sensor: ${lens.name}")
        
        // Protocolo de cierre atómico
        closeCameraInternal()

        backgroundHandler?.postDelayed({
            manager?.openCamera(lens.logicalId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createSession(lens, previewSurface)
                }
                override fun onDisconnected(camera: CameraDevice) { 
                    Log.e(TAG, "Cámara desconectada")
                    closeCamera() 
                }
                override fun onError(camera: CameraDevice, error: Int) { 
                    Log.e(TAG, "Error de cámara: $error")
                    closeCamera() 
                }
                override fun onClosed(camera: CameraDevice) { 
                    Log.d(TAG, "Hardware cerrado.")
                }
            }, backgroundHandler)
        }, 600)
    }

    private fun createSession(lens: AstroLensInfo, previewSurface: Surface) {
        val targetId = lens.physicalId ?: lens.logicalId
        val chars = manager?.getCameraCharacteristics(targetId)
        val map = chars?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val rawSize = map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull { it.width * it.height }
        
        rawImageReader = ImageReader.newInstance(rawSize?.width ?: 1920, rawSize?.height ?: 1080, ImageFormat.RAW_SENSOR, 3).apply {
            setOnImageAvailableListener({ reader ->
                val img = reader.acquireNextImage()
                if (img != null) handleRawImage(img, lens)
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
                        updatePreviewSettings()
                        isTransitioning = false
                        Log.i(TAG, "[TELEMETRÍA] Pipeline listo.")
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Error al configurar sesión")
                        isTransitioning = false
                    }
                }
            )
            cameraDevice?.createCaptureSession(sessionConfig)
        }
    }

    /**
     * RE-ARRANQUE AGRESIVO: Fuerza al HAL a salir del estado de captura.
     */
    fun updatePreviewSettings() {
        val surface = currentPreviewSurface ?: return
        if (cameraDevice == null || captureSession == null) return
        
        try {
            Log.d(TAG, "[RECOV] Reiniciando flujo de video...")
            val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder?.addTarget(surface)
            
            // Forzar parámetros AUTO para "descongelar" el sensor
            builder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            builder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            builder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            
            captureSession?.setRepeatingRequest(builder!!.build(), null, backgroundHandler)
            Log.i(TAG, "[RECOV] Preview recuperado.")
        } catch (e: Exception) {
            Log.e(TAG, "[RECOV] Fallo: ${e.message}")
        }
    }

    fun takeBurst(count: Int, lens: AstroLensInfo) {
        if (cameraDevice == null || captureSession == null || isCapturing) return
        isCapturing = true
        
        Log.e(TAG, "[TELEMETRÍA] INICIO CAPTURA: ${count}x${currentExposureNs/1e9}s")

        try {
            context.startForegroundService(Intent(context, AstroCaptureService::class.java).apply {
                putExtra("LENS_NAME", lens.name)
                putExtra("EXPOSURE", "${currentExposureNs/1e9}s")
            })
        } catch (e: Exception) { Log.e(TAG, "Servicio bloqueado") }

        try {
            val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL)
            rawImageReader?.surface?.let { builder?.addTarget(it) }
            
            builder?.apply {
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF)
                set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposureNs)
                set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
                set(CaptureRequest.SENSOR_FRAME_DURATION, currentExposureNs)
                set(CaptureRequest.NOISE_REDUCTION_MODE, 0)
            }

            val requests = List(count) { builder!!.build() }
            captureSession?.stopRepeating()
            
            captureSession?.captureBurst(requests, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                    handleCaptureResult(result, lens)
                }
                override fun onCaptureFailed(s: CameraCaptureSession, r: CaptureRequest, f: CaptureFailure) {
                    Log.e(TAG, "Fallo en captura de hardware")
                    finalizeCapture()
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
                Log.i(TAG, "[TELEMETRÍA] Escribiendo archivo...")
                val targetId = lens.physicalId ?: lens.logicalId
                val chars = manager?.getCameraCharacteristics(targetId) ?: return@post
                val filename = "ASTRO_${lens.name.replace(" ","_")}_${System.currentTimeMillis()}.dng"
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
                    Log.i(TAG, "[TELEMETRÍA] ✅ GUARDADO: $filename")
                }
            } catch (e: Exception) { 
                Log.e(TAG, "Error disco: ${e.message}")
            } finally { 
                image.close() 
                finalizeCapture()
            }
        }
    }

    private fun finalizeCapture() {
        // Reset de estado inmediato
        isCapturing = false
        
        // Limpieza de buffers colgados
        try { captureSession?.abortCaptures() } catch (e: Exception) {}

        backgroundHandler?.postDelayed({
            context.stopService(Intent(context, AstroCaptureService::class.java))
            
            // RE-ARRANQUE AGRESIVO
            updatePreviewSettings()
            
            mainExecutor.execute { onCaptureReadyListener?.invoke() }
            Log.e(TAG, "[TELEMETRÍA] >>> SISTEMA LISTO.")
        }, 800) // Delay balanceado para el HAL
    }

    fun closeCamera() {
        if (isCapturing) return
        closeCameraInternal()
    }

    /**
     * CIERRE SECUENCIAL: Libera el hardware por pasos para evitar bloqueos.
     */
    private fun closeCameraInternal() {
        Log.d(TAG, "[TELEMETRÍA] Iniciando protocolo de cierre...")
        try {
            captureSession?.stopRepeating()
            captureSession?.abortCaptures()
            
            // Pausa síncrona en el hilo de fondo (segura)
            Thread.sleep(100)
            
            captureSession?.close()
            cameraDevice?.close()
            rawImageReader?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error en protocolo de cierre: ${e.message}")
        } finally {
            captureSession = null
            cameraDevice = null
            rawImageReader = null
        }
    }
}
