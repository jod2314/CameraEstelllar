package com.stelllar.camera.domain.usecase

import com.stelllar.camera.domain.CameraParameters
import com.stelllar.camera.domain.repository.CameraRepository
import com.stelllar.camera.domain.repository.PhotoResult
import javax.inject.Inject

class CapturePhotoUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    suspend operator fun invoke(
        onCaptureStarted: () -> Unit,
        parameters: CameraParameters = CameraParameters()
    ): PhotoResult {
        return cameraRepository.takePhoto(onCaptureStarted, parameters)
    }
}
