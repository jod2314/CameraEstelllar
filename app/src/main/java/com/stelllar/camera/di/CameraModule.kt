package com.stelllar.camera.di

import com.stelllar.camera.data.camera.CameraControllerImpl
import com.stelllar.camera.domain.repository.CameraRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo de inyección de dependencias para Hilt que provee
 * la implementación concreta de CameraRepository.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CameraModule {

    @Binds
    @Singleton
    abstract fun bindCameraRepository(
        cameraControllerImpl: CameraControllerImpl
    ): CameraRepository
}
