package com.stelllar.camera.presentation

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelllar.camera.domain.CameraParameters
import com.stelllar.camera.domain.CameraState
import com.stelllar.camera.domain.repository.CameraRepository
import com.stelllar.camera.domain.repository.PhotoResult
import com.stelllar.camera.domain.repository.SettingsRepository
import com.stelllar.camera.domain.usecase.CapturePhotoUseCase
import com.stelllar.camera.domain.usecase.ProbeSensorUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val capturePhotoUseCase: CapturePhotoUseCase,
    private val probeSensorUseCase: ProbeSensorUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CameraState>(CameraState.Idle)
    val uiState: StateFlow<CameraState> = _uiState.asStateFlow()

    suspend fun initializeCamera(
        cameraId: String,
        physicalCameraId: String?,
        pixelFormat: Int,
        previewSurface: android.view.Surface,
        rotation: Int
    ): android.util.Size {
        _uiState.value = CameraState.Initializing
        cameraRepository.updateDeviceRotation(rotation)
        val size = cameraRepository.initializeCamera(cameraId, physicalCameraId, pixelFormat, previewSurface)
        _uiState.value = CameraState.Ready()
        return size
    }

    fun updateParameters(parameters: CameraParameters) {
        val currentState = _uiState.value
        if (currentState is CameraState.Ready) {
            _uiState.value = CameraState.Ready(parameters)
        }
    }

    fun updateRotation(rotation: Int) {
        cameraRepository.updateDeviceRotation(rotation)
    }

    fun takePhoto(onPhotoSaved: (PhotoResult) -> Unit, onCaptureStarted: () -> Unit = {}) {
        val currentState = _uiState.value
        if (currentState !is CameraState.Ready) return
        
        viewModelScope.launch {
            val params = currentState.parameters
            
            // 1. Manejo del Temporizador
            if (params.timerSeconds > 0) {
                // Podríamos emitir un estado de "CountingDown" si fuera necesario
                delay(params.timerSeconds * 1000L)
            }

            _uiState.value = CameraState.Capturing
            try {
                // 2. Manejo de Ráfaga (Tomas múltiples)
                repeat(params.frameCount) { index ->
                    val result = capturePhotoUseCase(onCaptureStarted, params)
                    onPhotoSaved(result)
                }
                _uiState.value = CameraState.Ready(params)
            } catch (e: Exception) {
                _uiState.value = CameraState.Error("Error al capturar: ${e.message}", e)
            }
        }
    }

    fun runSensorProbe(cameraId: String, physicalCameraId: String?, onComplete: (Long) -> Unit) {
        viewModelScope.launch {
            // Se asume que la cámara ya está cerrada antes de llamar a esto desde el fragmento
            try {
                val maxNs = probeSensorUseCase(cameraId, physicalCameraId)
                onComplete(maxNs)
            } catch (e: Exception) {
                _uiState.value = CameraState.Error("Error durante el sondeo del sensor", e)
            }
        }
    }

    fun saveMaxExposureNs(cameraId: String, exposureNs: Long) {
        settingsRepository.saveMaxExposureNs(cameraId, exposureNs)
    }

    fun getMaxExposureNs(cameraId: String): Long {
        return settingsRepository.getMaxExposureNs(cameraId)
    }

    fun closeCamera() {
        cameraRepository.closeCamera()
        _uiState.value = CameraState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        closeCamera()
    }
}
