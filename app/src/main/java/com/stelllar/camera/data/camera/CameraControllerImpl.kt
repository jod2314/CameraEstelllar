package com.stelllar.camera.data.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.exifinterface.media.ExifInterface
import com.stelllar.camera.domain.repository.CameraRepository
import com.stelllar.camera.domain.repository.PhotoResult
import com.stelllar.camera.utils.computeExifOrientation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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

    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }
    private val imageReaderHandler = Handler(imageReaderThread.looper)

    private var currentRotation: Int = 0

    override suspend fun initializeCamera(cameraId: String, pixelFormat: Int, previewSurface: Surface) {
        characteristics = cameraManager.getCameraCharacteristics(cameraId)
        camera = openCamera(cameraManager, cameraId, cameraHandler)

        val size = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            .getOutputSizes(pixelFormat).maxByOrNull { it.height * it.width }!!
            
        imageReader = ImageReader.newInstance(size.width, size.height, pixelFormat, IMAGE_BUFFER_SIZE)

        val targets = listOf(previewSurface, imageReader.surface)
        session = createCaptureSession(camera, targets, cameraHandler)

        val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(previewSurface)
        }
        session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
    }

    override fun updateDeviceRotation(rotation: Int) {
        currentRotation = rotation
    }

    override suspend fun takePhoto(onCaptureStarted: () -> Unit): PhotoResult = withContext(Dispatchers.IO) {
        val result = captureImage(onCaptureStarted)
        
        Log.d(TAG, "Result received: $result")
        val output = saveResult(result)
        Log.d(TAG, "Image saved: ${output.absolutePath}")

        if (output.extension == "jpg") {
            val exif = ExifInterface(output.absolutePath)
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, result.orientation.toString())
            exif.saveAttributes()
            Log.d(TAG, "EXIF metadata saved: ${output.absolutePath}")
        }
        
        val photoResult = PhotoResult(output, result.format, result.orientation)
        result.close()
        photoResult
    }

    override fun closeCamera() {
        try {
            if (::session.isInitialized) session.close()
            if (::camera.isInitialized) camera.close()
            if (::imageReader.isInitialized) imageReader.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
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
                Log.w(TAG, "Camera $cameraId has been disconnected")
                // Cannot finish activity from here, need a callback or Flow state
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
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)
            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    private suspend fun captureImage(onCaptureStarted: () -> Unit): CombinedCaptureResult = suspendCancellableCoroutine { cont ->
        @Suppress("ControlFlowWithEmptyBody")
        while (imageReader.acquireNextImage() != null) {}

        val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            imageQueue.add(image)
        }, imageReaderHandler)

        val captureRequest = session.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(imageReader.surface)
        }
        
        session.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {
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

                val exc = TimeoutException("Image dequeuing took too long")
                val timeoutRunnable = Runnable { cont.resumeWithException(exc) }
                imageReaderHandler.postDelayed(timeoutRunnable, IMAGE_CAPTURE_TIMEOUT_MILLIS)

                Thread {
                    try {
                        while (true) {
                            val image = imageQueue.take()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                image.format != ImageFormat.DEPTH_JPEG &&
                                image.timestamp != resultTimestamp) continue

                            imageReaderHandler.removeCallbacks(timeoutRunnable)
                            imageReader.setOnImageAvailableListener(null, null)

                            while (imageQueue.size > 0) {
                                imageQueue.take().close()
                            }

                            val mirrored = characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
                            val exifOrientation = computeExifOrientation(currentRotation, mirrored)

                            cont.resume(CombinedCaptureResult(image, result, exifOrientation, imageReader.imageFormat))
                            break
                        }
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                }.start()
            }
        }, cameraHandler)
    }

    private suspend fun saveResult(result: CombinedCaptureResult): File = suspendCoroutine { cont ->
        when (result.format) {
            ImageFormat.JPEG, ImageFormat.DEPTH_JPEG -> {
                val buffer = result.image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                try {
                    val output = createFile("jpg")
                    FileOutputStream(output).use { it.write(bytes) }
                    cont.resume(output)
                } catch (exc: IOException) {
                    cont.resumeWithException(exc)
                }
            }
            ImageFormat.RAW_SENSOR -> {
                val dngCreator = DngCreator(characteristics, result.metadata)
                try {
                    val output = createFile("dng")
                    FileOutputStream(output).use { dngCreator.writeImage(it, result.image) }
                    cont.resume(output)
                } catch (exc: IOException) {
                    cont.resumeWithException(exc)
                }
            }
            else -> {
                val exc = RuntimeException("Unknown image format: ${result.image.format}")
                cont.resumeWithException(exc)
            }
        }
    }

    private fun createFile(extension: String): File {
        val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "CameraStellar")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "IMG_${sdf.format(Date())}.$extension")
    }

    private fun scanFile(file: File) {
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null) { path, uri ->
            Log.d(TAG, "Scanned $path -> uri=$uri")
        }
    }

    companion object {
        private val TAG = CameraControllerImpl::class.java.simpleName
        private const val IMAGE_BUFFER_SIZE: Int = 3
        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000

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
