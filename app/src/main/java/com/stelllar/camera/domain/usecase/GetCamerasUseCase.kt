package com.stelllar.camera.domain.usecase

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Log
import com.stelllar.camera.domain.model.CameraInfo
import com.stelllar.camera.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Caso de uso para obtener la lista de cámaras disponibles (lógicas y físicas).
 */
class GetCamerasUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    operator fun invoke(): List<CameraInfo> {
        val availableCameras = mutableListOf<CameraInfo>()

        // Obtener lista de IDs compatibles
        val cameraIds = cameraManager.cameraIdList.filter {
            val characteristics = cameraManager.getCameraCharacteristics(it)
            val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            capabilities?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) ?: false
        }

        cameraIds.forEach { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val physicalIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                characteristics.physicalCameraIds
            } else {
                emptySet()
            }

            // Agregar cámara lógica
            addCameraItems(availableCameras, id, characteristics, id, null, physicalIds.isNotEmpty())

            // Escaneo profundo: Agregar cámaras físicas
            physicalIds.forEach { physicalId ->
                try {
                    val physCharacteristics = cameraManager.getCameraCharacteristics(physicalId)
                    addCameraItems(availableCameras, id, physCharacteristics, physicalId, physicalId, false)
                } catch (e: Exception) {
                    Log.e("GetCamerasUseCase", "Error al consultar cámara física $physicalId", e)
                }
            }
        }

        return availableCameras
    }

    private fun addCameraItems(
        list: MutableList<CameraInfo>,
        logicalId: String,
        characteristics: CameraCharacteristics,
        displayId: String,
        physicalId: String?,
        isLogical: Boolean
    ) {
        val orientation = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_BACK -> "Back"
            CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
            else -> "Unknown"
        }

        val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
        val outputFormats = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.outputFormats ?: IntArray(0)

        // Hardware Level
        val hwLevel = when (characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "External"
            else -> "Unknown"
        }

        // ISO Range
        val isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val isoStr = isoRange?.let { "${it.lower}-${it.upper}" } ?: "N/A"

        // Exposición
        val savedMaxNs = settingsRepository.getMaxExposureNs(logicalId)
        val expStr = if (savedMaxNs > 0) {
            "Max: %.1fs (Real)".format(savedMaxNs / 1e9)
        } else {
            val theoretical = characteristics.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION)
            theoretical?.let { "Max: %.1fs (Teórico)".format(it / 1e9) } ?: "N/A"
        }

        // MegaPixels
        val pixelArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val mpStr = pixelArray?.let { "%.1f MP".format((it.width * it.height) / 1e6) } ?: "N/A"

        val typeTag = if (isLogical) "[Lógica]" else if (physicalId != null) "[Física]" else ""
        val subtitle = "$typeTag HW: $hwLevel | $mpStr\nISO: $isoStr | Exp: $expStr"

        if (outputFormats.contains(ImageFormat.JPEG)) {
            list.add(CameraInfo("$orientation JPEG ($displayId)", subtitle, logicalId, ImageFormat.JPEG, physicalId, isLogical))
        }
        if (capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) && 
            outputFormats.contains(ImageFormat.RAW_SENSOR)) {
            list.add(CameraInfo("$orientation RAW ($displayId)", subtitle, logicalId, ImageFormat.RAW_SENSOR, physicalId, isLogical))
        }
    }
}
