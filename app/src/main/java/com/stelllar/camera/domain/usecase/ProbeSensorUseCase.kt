package com.stelllar.camera.domain.usecase

import com.stelllar.camera.data.camera.SensorProber
import com.stelllar.camera.domain.repository.SettingsRepository
import javax.inject.Inject

class ProbeSensorUseCase @Inject constructor(
    private val sensorProber: SensorProber,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(cameraId: String, physicalCameraId: String? = null): Long {
        val maxNs = sensorProber.runProbe(cameraId, physicalCameraId)
        if (maxNs > 0) {
            settingsRepository.saveMaxExposureNs(cameraId, maxNs)
        }
        return maxNs
    }
}
