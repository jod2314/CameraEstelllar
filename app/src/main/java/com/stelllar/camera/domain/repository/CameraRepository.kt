package com.stelllar.camera.domain.repository

import android.view.Surface
import java.io.File

interface CameraRepository {
    
    suspend fun initializeCamera(
        cameraId: String, 
        pixelFormat: Int, 
        previewSurface: Surface
    )

    fun updateDeviceRotation(rotation: Int)

    suspend fun takePhoto(onCaptureStarted: () -> Unit): PhotoResult

    fun closeCamera()
}
