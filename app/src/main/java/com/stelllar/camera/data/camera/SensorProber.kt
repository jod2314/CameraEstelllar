package com.stelllar.camera.data.camera

import android.util.Log
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import timber.log.Timber
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.cancel

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Motor de Sondeo Robusto para descubrir las capacidades reales del hardware.
 * Realiza una prueba iterativa de exposición máxima (5s, 10s, 15s...)
 * para determinar el límite real del dispositivo.
 */
@Singleton
@SuppressLint("NewApi")
class SensorProber @Inject constructor(
    @ApplicationContext private val context: Context, 
    private val cameraManager: CameraManager
) {

    private var probeThread: HandlerThread? = null
    private var probeHandler: Handler? = null
    private var probeExecutor: Executor? = null

    private fun initThread() {
        probeThread = HandlerThread("ProbeThread").apply { start() }
        probeHandler = Handler(probeThread!!.looper)
        probeExecutor = Executor { command -> 
            probeHandler?.post(command) ?: Timber.w("Executor invoked but probeHandler is null")
        }
    }

    /**
     * Ejecuta la prueba iterativa y devuelve la exposición máxima real soportada en nanosegundos.
     * Retorna 0L si no se pudo determinar un valor válido.
     */
    suspend fun runProbe(cameraId: String, physicalCameraId: String? = null): Long {
        initThread()
        val activeId = physicalCameraId ?: cameraId
        Log.d("AUDIT", "SensorProber.runProbe - Iniciando para logical cameraId: $cameraId, physicalCameraId: $physicalCameraId, active sensorId: $activeId")
        Timber.i("==================================================")
        Timber.i("🚀 INICIANDO TEST DE EXPOSICIÓN EN CÁMARA: $activeId")
        Timber.i("==================================================")

        val characteristics = cameraManager.getCameraCharacteristics(activeId)

        val theoreticalMaxExp = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.upper ?: 0L
        Timber.i("Exposición máxima teórica ($activeId): ${theoreticalMaxExp / 1e9}s")

        var camera: CameraDevice? = null
        var imageReader: ImageReader? = null
        var session: CameraCaptureSession? = null
        var maxSuccessfulExposureNs = 0L

        try {
            camera = openCamera(cameraId) // Always open the logical camera
            Timber.d("Cámara lógica abierta para prueba.")

            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            val sizes = map.getOutputSizes(ImageFormat.YUV_420_888)
            val smallSize = sizes.minByOrNull { it.width * it.height } ?: sizes[0]
            imageReader = ImageReader.newInstance(smallSize.width, smallSize.height, ImageFormat.YUV_420_888, 2)

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                image?.close()
                Timber.d("Imagen de prueba capturada y liberada.")
            }, probeHandler)

            val outputConfig = OutputConfiguration(imageReader.surface).apply {
                if (physicalCameraId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setPhysicalCameraId(physicalCameraId)
                }
            }
            
            // === CONFIGURACIÓN DE BYPASS PARA HAL v2 ===
            val availableSessionKeys = characteristics.availableSessionKeys ?: emptyList<CaptureRequest.Key<*>>()
            val sessionParamsBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            sessionParamsBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            
            // Usar el rango de FPS más bajo disponible dinámicamente
            val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            val lowestFpsRange = fpsRanges?.minByOrNull { it.upper }
            if (lowestFpsRange != null && availableSessionKeys.contains(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE)) {
                sessionParamsBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, lowestFpsRange)
                Timber.d("Prober: Aplicando CONTROL_AE_TARGET_FPS_RANGE como parámetro de sesión")
            }
            
            // CRÍTICO: Declarar el Frame Duration Máximo al crear la sesión
            val maxFrameDuration = characteristics.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION)
            if (maxFrameDuration != null && availableSessionKeys.contains(CaptureRequest.SENSOR_FRAME_DURATION)) {
                sessionParamsBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, maxFrameDuration)
                Timber.d("Prober: Aplicando SENSOR_FRAME_DURATION como parámetro de sesión")
            }

            session = createSession(camera, listOf(outputConfig), sessionParamsBuilder.build())
            Timber.d("Sesión de prueba creada con parámetros de bypass.")

            // Prueba iterativa (Sin límite de seguridad, forzando el hardware hasta que falle)
            val startingSeconds = 5L
            var currentSeconds = startingSeconds

            while (true) {
                val exposureNs = currentSeconds * 1_000_000_000L
                val timeoutSeconds = (currentSeconds * 2) + 15 

                Timber.i("--------------------------------------------------")
                Timber.i("🔥 FORZANDO exposición de $currentSeconds segundos (Timeout: ${timeoutSeconds}s)...")
                
                val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(imageReader.surface)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureNs)
                    set(CaptureRequest.SENSOR_FRAME_DURATION, exposureNs) // Emparejar frame duration
                    // Apagar otras automatizaciones para evitar interferencias
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                    set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
                }

                val success = tryCaptureWithTimeout(session, captureBuilder.build(), exposureNs, timeoutSeconds)

                if (success) {
                    maxSuccessfulExposureNs = exposureNs
                    Timber.i("✅ Hardware SOPORTÓ $currentSeconds segundos reales.")
                    currentSeconds += 5 // Incrementar y seguir golpeando el HAL
                } else {
                    Timber.e("❌ LÍMITE ALCANZADO. El sensor o el HAL colapsó/recortó la toma al pedir $currentSeconds segundos.")
                    break
                }
            }

            Timber.i("==================================================")
            Timber.i("🎯 MÁXIMA EXPOSICIÓN REAL: ${maxSuccessfulExposureNs / 1e9} segundos")
            Timber.i("==================================================")

        } catch (e: Exception) {
            Timber.e("❌ Error crítico durante la prueba: ${e.message}")
        } finally {
            // CRÍTICO: Garantizar que la sesión de prueba se limpie de forma sincrónica y segura 
            // antes de devolver el control y reabrir la app principal, para evitar pantalla negra.
            try {
                session?.abortCaptures()
            } catch (e: Exception) {
                Timber.e("Ignorando error al abortar captures del prober: ${e.message}")
            }
            session?.close()
            camera?.close()
            imageReader?.close()
            
            probeThread?.quitSafely()
            probeThread = null
            probeHandler = null
        }
        
        Log.d("AUDIT", "SensorProber.runProbe - Terminando. Resultado: $maxSuccessfulExposureNs ns (${maxSuccessfulExposureNs / 1e9}s)")
        return maxSuccessfulExposureNs
    }


    private suspend fun tryCaptureWithTimeout(
        session: CameraCaptureSession, 
        request: CaptureRequest, 
        expectedExposureNs: Long,
        timeoutSeconds: Long
    ): Boolean = suspendCancellableCoroutine { continuation ->
        var responded = false

        val timeoutRunnable = Runnable {
            if (!responded) {
                responded = true
                Timber.w("Timeout alcanzado para ${expectedExposureNs / 1e9}s.")
                if (continuation.isActive) continuation.resume(false)
            }
        }
        
        // Configurar timeout
        probeHandler?.postDelayed(timeoutRunnable, timeoutSeconds * 1000L)

        try {
            session.capture(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession, req: CaptureRequest, result: TotalCaptureResult) {
                    if (responded) return
                    responded = true
                    probeHandler?.removeCallbacks(timeoutRunnable)

                    val realExposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
                    Timber.d("onCaptureCompleted: Real ${realExposure / 1e9}s (Esperado ${expectedExposureNs / 1e9}s)")
                    
                    val isSuccess = realExposure >= (expectedExposureNs * 0.95).toLong()
                    if (continuation.isActive) continuation.resume(isSuccess)
                }

                override fun onCaptureFailed(s: CameraCaptureSession, req: CaptureRequest, failure: CaptureFailure) {
                    if (responded) return
                    responded = true
                    probeHandler?.removeCallbacks(timeoutRunnable)
                    Timber.e("onCaptureFailed: reason ${failure.reason}")
                    if (continuation.isActive) continuation.resume(false)
                }
            }, probeHandler)
        } catch (e: Exception) {
            if (!responded) {
                responded = true
                probeHandler?.removeCallbacks(timeoutRunnable)
                Timber.e("Excepción al invocar capture: ${e.message}")
                if (continuation.isActive) continuation.resume(false)
            }
        }
        
        continuation.invokeOnCancellation {
            probeHandler?.removeCallbacks(timeoutRunnable)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(cameraId: String): CameraDevice = suspendCancellableCoroutine { cont ->
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)
            override fun onDisconnected(device: CameraDevice) {
                if (cont.isActive) cont.resumeWithException(RuntimeException("Camera disconnected"))
            }
            override fun onError(device: CameraDevice, error: Int) {
                if (cont.isActive) cont.resumeWithException(RuntimeException("Camera error: $error"))
            }
        }, probeHandler)
    }

    private suspend fun createSession(
        device: CameraDevice, 
        outputs: List<OutputConfiguration>, 
        sessionParams: CaptureRequest? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->
        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)
            override fun onConfigureFailed(session: CameraCaptureSession) = cont.resumeWithException(RuntimeException("Session failed"))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val config = SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, probeExecutor!!, callback)
            if (sessionParams != null) {
                config.sessionParameters = sessionParams
            }
            device.createCaptureSession(config)
        } else {
            @Suppress("DEPRECATION")
            device.createCaptureSessionByOutputConfigurations(outputs, callback, probeHandler)
        }
    }
}
