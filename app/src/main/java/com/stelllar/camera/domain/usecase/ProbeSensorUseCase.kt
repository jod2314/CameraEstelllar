package com.stelllar.camera.domain.usecase

import android.util.Log
import com.stelllar.camera.data.camera.SensorProber
import com.stelllar.camera.domain.repository.SettingsRepository
import javax.inject.Inject


class ProbeSensorUseCase @Inject constructor(
    private val sensorProber: SensorProber,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(cameraId: String, physicalCameraId: String? = null): Long {
        val sensorId = physicalCameraId ?: cameraId
        Log.d("AUDIT", "Iniciando sondeo - cameraId: $cameraId, physicalCameraId: $physicalCameraId, sensorId unificado: $sensorId")
        val maxNs = sensorProber.runProbe(cameraId, physicalCameraId)
        if (maxNs > 0) {
            Log.d("AUDIT", "Guardando test exitoso - sensorId: $sensorId, maxExpNs: $maxNs")
            settingsRepository.saveMaxExposureNs(sensorId, maxNs)
        } else {
            Log.d("AUDIT", "Sondeo finalizado sin bypass soportado - sensorId: $sensorId")
        }
        return maxNs
    }
}

