package com.stelllar.camera.data.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import com.stelllar.camera.domain.CameraParameters
import com.stelllar.camera.domain.repository.CameraRepository
import com.stelllar.camera.domain.repository.PhotoResult
import com.stelllar.camera.utils.computeExifOrientation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.Closeable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import timber.log.Timber

@Singleton
class CameraControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : CameraRepository {

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private lateinit var characteristics: CameraCharacteristics
    private lateinit var camera: CameraDevice
    private lateinit var session: CameraCaptureSession
    private lateinit var imageReader: ImageReader

    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    private var imageReaderThread: HandlerThread? = null
    private var imageReaderHandler: Handler? = null
    
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var currentRotation: Int = 0
    private var activePhysicalCameraId: String? = null

    private fun initThreads() {
        if (cameraThread == null) {
            cameraThread = HandlerThread("CameraThread").apply { start() }
            cameraHandler = Handler(cameraThread!!.looper)
        }
        if (imageReaderThread == null) {
            imageReaderThread = HandlerThread("imageReaderThread").apply { start() }
            imageReaderHandler = Handler(imageReaderThread!!.looper)
        }
    }

    override suspend fun initializeCamera(
        cameraId: String, 
        physicalCameraId: String?, 
        pixelFormat: Int, 
        previewSurface: Surface
    ): android.util.Size {
        initThreads()
        this.activePhysicalCameraId = physicalCameraId
        characteristics = if (physicalCameraId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cameraManager.getCameraCharacteristics(physicalCameraId)
        } else {
            cameraManager.getCameraCharacteristics(cameraId)
        }
        
        camera = openCamera(cameraManager, cameraId, cameraHandler)

        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        
        // Tamaño máximo para captura (RAW o JPEG)
        val captureSize = map.getOutputSizes(pixelFormat).maxByOrNull { it.height * it.width }!!
        imageReader = ImageReader.newInstance(captureSize.width, captureSize.height, pixelFormat, IMAGE_BUFFER_SIZE)

        // Tamaño inteligente para Preview (No exceder 1080p y que coincida con el aspecto)
        val previewSize = map.getOutputSizes(SurfaceHolder::class.java)
            .filter { it.width <= 1920 && it.height <= 1080 }
            .maxByOrNull { it.width * it.height } ?: captureSize
            
        Timber.i("Configurando cámara: Captura=${captureSize.width}x${captureSize.height}, Preview=${previewSize.width}x${previewSize.height}")

        val targets = listOf(previewSurface, imageReader.surface)
        session = createCaptureSession(camera, targets, physicalCameraId, cameraHandler)

        val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(previewSurface)
        }
        session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
        
        return previewSize
    }

    override fun updateDeviceRotation(rotation: Int) {
        currentRotation = rotation
    }

    override suspend fun takePhoto(
        onCaptureStarted: () -> Unit,
        parameters: CameraParameters
    ): PhotoResult = withContext(Dispatchers.IO) {
        try {
            // Iniciar servicio en primer plano para evitar que el SO cierre la app
            com.stelllar.camera.services.CameraForegroundService.start(context)
            
            val result = captureImage(onCaptureStarted, parameters)
            
            Timber.d("Result received: $result")
            val photoResult = saveResult(result)
            Timber.d("Image saved to MediaStore: ${photoResult.uriString}")

            result.close()
            photoResult
        } finally {
            // Detener el servicio independientemente del resultado
            com.stelllar.camera.services.CameraForegroundService.stop(context)
        }
    }

    override fun closeCamera() {
        try {
            if (::session.isInitialized) {
                try { session.abortCaptures() } catch(e: Exception) {}
                session.close()
            }
            if (::camera.isInitialized) camera.close()
            if (::imageReader.isInitialized) imageReader.close()
            
            cameraThread?.quitSafely()
            cameraThread = null
            cameraHandler = null
            
            imageReaderThread?.quitSafely()
            imageReaderThread = null
            imageReaderHandler = null
        } catch (exc: Throwable) {
            Timber.e(exc, "Error closing camera")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(device: CameraDevice) {
                Timber.w("Camera $cameraId has been disconnected")
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Timber.e(exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        physicalCameraId: String?,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->
        val stateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)
            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                cont.resumeWithException(exc)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val availableSessionKeys = characteristics.availableSessionKeys ?: emptyList<CaptureRequest.Key<*>>()
            
            val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            val lowestFpsRange = fpsRanges?.minByOrNull { it.upper }
            val maxFrameDuration = characteristics.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION)

            val sessionParamsBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                
                if (availableSessionKeys.contains(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE) && lowestFpsRange != null) {
                    set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, lowestFpsRange)
                    Timber.d("Aplicando CONTROL_AE_TARGET_FPS_RANGE como parámetro de sesión")
                }
                
                if (availableSessionKeys.contains(CaptureRequest.SENSOR_FRAME_DURATION) && maxFrameDuration != null) {
                    set(CaptureRequest.SENSOR_FRAME_DURATION, maxFrameDuration)
                    Timber.d("Aplicando SENSOR_FRAME_DURATION como parámetro de sesión")
                }
            }
            val sessionParams = sessionParamsBuilder.build()

            val outputConfigs = targets.map { 
                android.hardware.camera2.params.OutputConfiguration(it).apply {
                    if (physicalCameraId != null) {
                        setPhysicalCameraId(physicalCameraId)
                    }
                }
            }
            val sessionConfig = android.hardware.camera2.params.SessionConfiguration(
                android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR,
                outputConfigs,
                { runnable -> handler?.post(runnable) ?: runnable.run() },
                stateCallback
            )
            sessionConfig.sessionParameters = sessionParams
            
            try {
                device.createCaptureSession(sessionConfig)
            } catch (e: Exception) {
                Timber.e(e, "Failed to create session with HAL v2 params, falling back to legacy")
                device.createCaptureSession(targets, stateCallback, handler)
            }
        } else {
            device.createCaptureSession(targets, stateCallback, handler)
        }
    }

    private suspend fun captureImage(
        onCaptureStarted: () -> Unit,
        parameters: CameraParameters
    ): CombinedCaptureResult = suspendCancellableCoroutine { cont ->
        @Suppress("ControlFlowWithEmptyBody")
        while (imageReader.acquireNextImage() != null) {}

        val imageChannel = Channel<Image>(Channel.UNLIMITED)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            if (image != null) {
                imageChannel.trySend(image)
            }
        }, imageReaderHandler)

        var captureRequest = session.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(imageReader.surface)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            
            // Bypass logic for 30s exposure
            val requestedExposure = parameters.exposureTimeNs ?: 30_000_000_000L // 30s by default if manual
            val maxFrameDuration = characteristics.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION) ?: 100000000L
            val maxExposure = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.upper ?: maxFrameDuration
            
            // We force the requested exposure even if it exceeds maxExposure (Bypass). 
            set(CaptureRequest.SENSOR_EXPOSURE_TIME, requestedExposure)
            set(CaptureRequest.SENSOR_FRAME_DURATION, maxOf(requestedExposure, maxFrameDuration))
            
            parameters.iso?.let { set(CaptureRequest.SENSOR_SENSITIVITY, it) }
            parameters.focusDistance?.let { 
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                set(CaptureRequest.LENS_FOCUS_DISTANCE, it)
            }
        }
        
        val captureCallback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long
            ) {
                super.onCaptureStarted(session, request, timestamp, frameNumber)
                onCaptureStarted()
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
                val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                val appliedExposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                Timber.d("Exposición aplicada por hardware: $appliedExposure ns")

                val exc = TimeoutException("Image dequeuing took too long")
                val timeoutRunnable = Runnable { cont.resumeWithException(exc) }
                imageReaderHandler?.postDelayed(timeoutRunnable, IMAGE_CAPTURE_TIMEOUT_MILLIS)

                controllerScope.launch {
                    try {
                        while (true) {
                            val image = imageChannel.receive()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                image.format != ImageFormat.DEPTH_JPEG &&
                                image.timestamp != resultTimestamp) {
                                image.close()
                                continue
                            }

                            imageReaderHandler?.removeCallbacks(timeoutRunnable)
                            imageReader.setOnImageAvailableListener(null, null)

                            // Clean up remaining images
                            imageChannel.close()
                            for (remainingImage in imageChannel) {
                                remainingImage.close()
                            }

                            val mirrored = characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
                            val exifOrientation = computeExifOrientation(currentRotation, mirrored)

                            cont.resume(CombinedCaptureResult(image, result, exifOrientation, imageReader.imageFormat))
                            break
                        }
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                }
            }
            
            override fun onCaptureFailed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                failure: CaptureFailure
            ) {
                Timber.e("Capture failed. Reason: ${failure.reason}")
                if (cont.isActive) cont.resumeWithException(RuntimeException("Capture failed: ${failure.reason}"))
            }
        }
        
        try {
            session.capture(captureRequest.build(), captureCallback, cameraHandler)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Hardware rechazó la exposición extrema. Aplicando Fallback.")
            // Fallback: Usar el máximo reportado por la API
            val fallbackRequest = session.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(imageReader.surface)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                val maxFrameDuration = characteristics.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION) ?: 100000000L
                val maxExposure = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.upper ?: maxFrameDuration
                set(CaptureRequest.SENSOR_EXPOSURE_TIME, maxExposure)
                set(CaptureRequest.SENSOR_FRAME_DURATION, maxFrameDuration)
                parameters.iso?.let { set(CaptureRequest.SENSOR_SENSITIVITY, it) }
            }
            try {
                session.capture(fallbackRequest.build(), captureCallback, cameraHandler)
            } catch (fallbackEx: Exception) {
                if (cont.isActive) cont.resumeWithException(fallbackEx)
            }
        } catch (e: Exception) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    }

    private suspend fun saveResult(result: CombinedCaptureResult): PhotoResult = suspendCoroutine { cont ->
        val extension = if (result.format == ImageFormat.RAW_SENSOR) "dng" else "jpg"
        val mimeType = if (result.format == ImageFormat.RAW_SENSOR) "image/x-adobe-dng" else "image/jpeg"
        val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
        val fileName = "IMG_${sdf.format(Date())}.$extension"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/CameraStellar")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        if (uri == null) {
            cont.resumeWithException(IOException("No se pudo crear el URI en MediaStore para $fileName"))
            return@suspendCoroutine
        }

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                if (result.format == ImageFormat.RAW_SENSOR) {
                    val dngCreator = DngCreator(characteristics, result.metadata)
                    dngCreator.writeImage(outputStream, result.image)
                } else {
                    val buffer = result.image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                    outputStream.write(bytes)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            cont.resume(PhotoResult(uri.toString(), fileName, result.format, result.orientation))
        } catch (exc: IOException) {
            if (uri != null) {
                resolver.delete(uri, null, null)
            }
            cont.resumeWithException(exc)
        }
    }

    companion object {
        private const val IMAGE_BUFFER_SIZE: Int = 5
        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 60000 // Aumentado para exposiciones largas

        data class CombinedCaptureResult(
            val image: Image,
            val metadata: CaptureResult,
            val orientation: Int,
            val format: Int
        ) : Closeable {
            override fun close() = image.close()
        }
    }
}