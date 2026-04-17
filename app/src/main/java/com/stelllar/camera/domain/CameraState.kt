package com.stelllar.camera.domain

data class CameraParameters(
    val iso: Int? = null,
    val exposureTimeNs: Long? = null,
    val focusDistance: Float? = null,
    val frameCount: Int = 1,
    val timerSeconds: Int = 0
)

sealed class CameraState {
    object Idle : CameraState()
    object Initializing : CameraState()
    data class Ready(val parameters: CameraParameters = CameraParameters()) : CameraState()
    object Capturing : CameraState()
    data class Error(val message: String, val cause: Throwable? = null) : CameraState()
}
