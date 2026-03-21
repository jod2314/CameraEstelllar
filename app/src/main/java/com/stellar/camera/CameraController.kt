package com.stellar.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
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

/**
 * CameraController: Motor de captura avanzado para astrofotografía.
 * Implementa control manual absoluto y ráfagas RAW (DNG).
 */
class CameraController(private val context: Context) {

    private val TAG = "CameraController"
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var cameraCharacteristics: CameraCharacteristics? = null
    
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    
    private var rawImageReader: ImageReader? = null
    
    // Almacenamiento temporal para sincronización de RAW y metadatos
    private val pendingRawImages = ConcurrentHashMap<Long, Image>()
    private val pendingCaptureResults = ConcurrentHashMap<Long, TotalCaptureResult>()

    // Parámetros de captura (Valores por defecto)
    var currentIso: Int = 800
    var currentExposureNs: Long = 100_000_000L // 0.1s
    var currentFocusDistance: Float = 0.0f // Infinito

    fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error deteniendo hilo de fondo", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(manager: CameraManager, previewSurface: Surface) {
        try {
            val cameraId = manager.cameraIdList[0] // Usamos la principal por ahora
            cameraCharacteristics = manager.getCameraCharacteristics(cameraId)
            
            // Configurar ImageReader para RAW
            val map = cameraCharacteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val rawSize = map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull { it.width * it.height }
            
            rawSize?.let {
                rawImageReader = ImageReader.newInstance(it.width, it.height, ImageFormat.RAW_SENSOR, 5).apply {
                    setOnImageAvailableListener({ reader ->
                        val image = reader.acquireNextImage()
                        handleRawImage(image)
                    }, backgroundHandler)
                }
            }

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession(previewSurface)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error accediendo a la cámara", e)
        }
    }

    private fun createCaptureSession(previewSurface: Surface) {
        val targets = mutableListOf(previewSurface)
        rawImageReader?.surface?.let { targets.add(it) }

        cameraDevice?.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                startPreview(previewSurface)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(TAG, "Fallo al configurar la sesión")
            }
        }, backgroundHandler)
    }

    private fun startPreview(previewSurface: Surface) {
        updatePreviewSettings(previewSurface)
    }

    /**
     * Actualiza los parámetros de la cámara (ISO, Exp) en tiempo real para la vista previa.
     */
    fun updatePreviewSettings(previewSurface: Surface? = null) {
        try {
            val surface = previewSurface ?: return // O usar la guardada si se implementa persistencia de surface
            val requestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            requestBuilder?.addTarget(surface)
            
            // Forzar parámetros manuales en la vista previa
            requestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            requestBuilder?.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
            
            // Limitamos la exposición de preview para que la pantalla no se congele si pones 30s
            val previewExp = currentExposureNs.coerceAtMost(100_000_000L) // Máximo 0.1s para preview
            requestBuilder?.set(CaptureRequest.SENSOR_EXPOSURE_TIME, previewExp)
            requestBuilder?.set(CaptureRequest.LENS_FOCUS_DISTANCE, currentFocusDistance)

            captureSession?.setRepeatingRequest(requestBuilder!!.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando configuración de preview", e)
        }
    }

    /**
     * Ejecuta una ráfaga manual de fotos RAW.
     */
    fun takeBurst(count: Int) {
        try {
            val requests = mutableListOf<CaptureRequest>()
            val requestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL)
            
            rawImageReader?.surface?.let { requestBuilder?.addTarget(it) }
            
            // Configuración Manual Extrema
            requestBuilder?.apply {
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
                set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
                set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposureNs)
                set(CaptureRequest.LENS_FOCUS_DISTANCE, currentFocusDistance)
                // Desactivar estabilización para trípode
                set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF)
            }

            for (i in 0 until count) {
                requests.add(requestBuilder!!.build())
            }

            captureSession?.captureBurst(requests, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    handleCaptureResult(result)
                }
            }, backgroundHandler)

        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error en ráfaga", e)
        }
    }

    private fun handleRawImage(image: Image) {
        val timestamp = image.timestamp
        val result = pendingCaptureResults.remove(timestamp)
        if (result != null) {
            saveDng(image, result)
        } else {
            pendingRawImages[timestamp] = image
        }
    }

    private fun handleCaptureResult(result: TotalCaptureResult) {
        val timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
        val image = pendingRawImages.remove(timestamp)
        if (image != null) {
            saveDng(image, result)
        } else {
            pendingCaptureResults[timestamp] = result
        }
    }

    private fun saveDng(image: Image, result: TotalCaptureResult) {
        try {
            val filename = "STELLAR_${System.currentTimeMillis()}.dng"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/CameraStellar")
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { output ->
                    DngCreator(cameraCharacteristics!!, result).writeImage(output, image)
                }
                Log.d(TAG, "DNG Guardado: $filename")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error guardando DNG", e)
        } finally {
            image.close()
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
