package com.stelllar.camera.domain.repository

interface SettingsRepository {
    fun saveMaxExposureNs(cameraId: String, exposureNs: Long)
    fun getMaxExposureNs(cameraId: String): Long
    fun getIsRawEnabled(): Boolean
    fun setIsRawEnabled(enabled: Boolean)
}
