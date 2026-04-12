package com.stelllar.camera.data.camera

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
import android.util.Log
import android.util.Range
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Motor de Sondeo Robusto para descubrir las capacidades reales del hardware,
 * saltándose las limitaciones teóricas reportadas por la API genérica.
 */
class SensorProber(private val context: Context, private val cameraManager: CameraManager) {

    private val probeThread = HandlerThread("ProbeThread").apply { start() }
    private val probeHandler = Handler(probeThread.looper)
    private val probeExecutor = Executor { command -> probeHandler.post(command) }

    suspend fun runProbe(cameraId: String) {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "🚀 INICIANDO MOTOR DE SONDEO EN CÁMARA: $cameraId")
        Log.i(TAG, "==================================================")

        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        
        // Fase 1: Sondeo de Parámetros de Sesión
        val theoreticalMaxExp = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.upper ?: 0L
        Log.i(TAG, "[Fase 1] Exposición máxima teórica reportada: ${theoreticalMaxExp / 1_000_000_000.0}s")

        var camera: CameraDevice? = null
        var imageReader: ImageReader? = null
        var session: CameraCaptureSession? = null

        try {
            camera = openCamera(cameraId)
            Log.d(TAG, "[Fase 1] Cámara abierta exitosamente para sondeo oculto.")

            // Usar una resolución pequeña para no saturar la memoria durante el sondeo
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            val sizes = map.getOutputSizes(ImageFormat.YUV_420_888)
            val smallSize = sizes.minByOrNull { it.width * it.height } ?: sizes[0]
            imageReader = ImageReader.newInstance(smallSize.width, smallSize.height, ImageFormat.YUV_420_888, 2)

            val outputConfig = OutputConfiguration(imageReader.surface)
            
            // Construir SessionParameters para intentar destrabar el límite (Fase 1)
            val sessionParamsBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            sessionParamsBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            sessionParamsBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(1, 15)) // FPS Bajo

            val sessionConfig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    listOf(outputConfig),
                    probeExecutor,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) {}
                        override fun onConfigureFailed(s: CameraCaptureSession) {}
                    }
                ).apply {
                    sessionParameters = sessionParamsBuilder.build()
                }
            } else null

            // Verificar si la configuración es soportada antes de crearla (API 29+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sessionConfig != null) {
                val isSupported = camera.isSessionConfigurationSupported(sessionConfig)
                Log.i(TAG, "[Fase 1] ¿Soporta SessionConfiguration con FPS bajos?: $isSupported")
            }

            session = createSession(camera, listOf(outputConfig))
            Log.d(TAG, "[Fase 1] Sesión de prueba creada.")

            // Fase 2: Sondeo de Captura de Frame Individual ("El Ataque")
            // Intentaremos forzar 5 segundos (5_000_000_000 nanosegundos)
            val targetExposureNs = 5_000_000_000L 
            Log.i(TAG, "[Fase 2] Iniciando Ataque: Solicitando exposición de ${targetExposureNs / 1_000_000_000.0}s")

            val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(imageReader.surface)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                set(CaptureRequest.SENSOR_EXPOSURE_TIME, targetExposureNs)
                set(CaptureRequest.SENSOR_FRAME_DURATION, targetExposureNs) // Emparejar frame duration
            }

            val result = captureSingleFrame(session, captureBuilder.build())
            
            // Fase 3: Análisis y Registro de Resultados
            val realExposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
            val realFrameDuration = result.get(CaptureResult.SENSOR_FRAME_DURATION) ?: 0L
            
            Log.i(TAG, "[Fase 3] RESULTADOS DEL SONDEO:")
            Log.i(TAG, " -> Solicitado : ${targetExposureNs / 1_000_000_000.0}s")
            Log.i(TAG, " -> Real Aplicado : ${realExposure / 1_000_000_000.0}s")
            Log.i(TAG, " -> Frame Duration: ${realFrameDuration / 1_000_000_000.0}s")

            if (realExposure >= targetExposureNs) {
                Log.i(TAG, "✅ ¡ÉXITO! El hardware permite exposición manual sin límite de OEM.")
            } else if (realExposure > theoreticalMaxExp) {
                Log.i(TAG, "⚠️ ÉXITO PARCIAL. Superamos el límite teórico, pero el HAL recortó la toma a ${realExposure / 1_000_000_000.0}s.")
            } else {
                Log.e(TAG, "❌ BLOQUEO CONFIRMADO. El hardware/HAL recortó la toma a ${realExposure / 1_000_000_000.0}s.")
                Log.i(TAG, "💡 Conclusión: Deberemos usar Apilamiento de Imágenes (Stacking) vía NDK en este dispositivo.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error durante el sondeo: ${e.message}")
        } finally {
            session?.close()
            camera?.close()
            imageReader?.close()
            Log.i(TAG, "==================================================")
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

    private suspend fun createSession(device: CameraDevice, outputs: List<OutputConfiguration>): CameraCaptureSession = suspendCoroutine { cont ->
        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)
            override fun onConfigureFailed(session: CameraCaptureSession) = cont.resumeWithException(RuntimeException("Session failed"))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val config = SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, probeExecutor, callback)
            device.createCaptureSession(config)
        } else {
            @Suppress("DEPRECATION")
            device.createCaptureSessionByOutputConfigurations(outputs, callback, probeHandler)
        }
    }

    private suspend fun captureSingleFrame(session: CameraCaptureSession, request: CaptureRequest): TotalCaptureResult = suspendCancellableCoroutine { cont ->
        session.capture(request, object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(s: CameraCaptureSession, req: CaptureRequest, result: TotalCaptureResult) {
                if (cont.isActive) cont.resume(result)
            }
            override fun onCaptureFailed(s: CameraCaptureSession, req: CaptureRequest, failure: CaptureFailure) {
                if (cont.isActive) cont.resumeWithException(RuntimeException("Capture failed: reason ${failure.reason}"))
            }
        }, probeHandler)
    }

    companion object {
        private const val TAG = "SensorProber"
    }
}
