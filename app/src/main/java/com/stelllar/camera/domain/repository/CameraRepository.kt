package com.stelllar.camera.domain.repository

import android.view.Surface
import com.stelllar.camera.domain.CameraParameters

interface CameraRepository {
    
    suspend fun initializeCamera(
        cameraId: String, 
        physicalCameraId: String?,
        pixelFormat: Int, 
        previewSurface: Surface
    )

    fun updateDeviceRotation(rotation: Int)

    suspend fun takePhoto(
        onCaptureStarted: () -> Unit,
        parameters: CameraParameters = CameraParameters()
    ): PhotoResult

    fun closeCamera()
}
