package com.stelllar.camera.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.stelllar.camera.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun saveMaxExposureNs(cameraId: String, exposureNs: Long) {
        prefs.edit().putLong(KEY_MAX_EXPOSURE + cameraId, exposureNs).apply()
    }

    override fun getMaxExposureNs(cameraId: String): Long {
        return prefs.getLong(KEY_MAX_EXPOSURE + cameraId, 0L)
    }

    override fun getIsRawEnabled(): Boolean {
        return prefs.getBoolean(KEY_RAW_ENABLED, true)
    }

    override fun setIsRawEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RAW_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "camera_settings"
        private const val KEY_MAX_EXPOSURE = "max_exposure_ns_"
        private const val KEY_RAW_ENABLED = "raw_enabled"
    }
}
