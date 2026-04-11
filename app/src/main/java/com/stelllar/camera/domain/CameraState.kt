package com.stelllar.camera.domain

sealed class CameraState {
    object Idle : CameraState()
    object Initializing : CameraState()
    object Ready : CameraState()
    object Capturing : CameraState()
    data class Error(val message: String, val cause: Throwable? = null) : CameraState()
}
