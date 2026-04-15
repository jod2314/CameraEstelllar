package com.stelllar.camera.di

import com.stelllar.camera.data.camera.CameraControllerImpl
import com.stelllar.camera.domain.repository.CameraRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import com.stelllar.camera.data.repository.SettingsRepositoryImpl
import com.stelllar.camera.domain.repository.SettingsRepository

import android.content.Context
import android.hardware.camera2.CameraManager
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Módulo de inyección de dependencias para Hilt que provee
 * la implementación concreta de los repositorios y servicios del sistema.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CameraModule {

    @Binds
    @Singleton
    abstract fun bindCameraRepository(
        cameraControllerImpl: CameraControllerImpl
    ): CameraRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository

    companion object {
        @Provides
        @Singleton
        fun provideCameraManager(@ApplicationContext context: Context): CameraManager {
            return context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        }
    }
}
