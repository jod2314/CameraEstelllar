package com.stelllar.camera.presentation

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelllar.camera.domain.CameraParameters
import com.stelllar.camera.domain.CameraState
import com.stelllar.camera.domain.repository.CameraRepository
import com.stelllar.camera.domain.repository.PhotoResult
import com.stelllar.camera.domain.usecase.CapturePhotoUseCase
import com.stelllar.camera.domain.usecase.ProbeSensorUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val capturePhotoUseCase: CapturePhotoUseCase,
    private val probeSensorUseCase: ProbeSensorUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<CameraState>(CameraState.Idle)
    val uiState: StateFlow<CameraState> = _uiState.asStateFlow()

    fun initializeCamera(
        cameraId: String,
        physicalCameraId: String?,
        pixelFormat: Int,
        previewSurface: Surface,
        rotation: Int
    ) {
        viewModelScope.launch {
            _uiState.value = CameraState.Initializing
            try {
                cameraRepository.updateDeviceRotation(rotation)
                cameraRepository.initializeCamera(cameraId, physicalCameraId, pixelFormat, previewSurface)
                _uiState.value = CameraState.Ready()
            } catch (e: Exception) {
                _uiState.value = CameraState.Error("Fallo al inicializar la cámara", e)
            }
        }
    }

    fun updateRotation(rotation: Int) {
        cameraRepository.updateDeviceRotation(rotation)
    }

    fun takePhoto(onPhotoSaved: (PhotoResult) -> Unit, onCaptureStarted: () -> Unit = {}) {
        val currentState = _uiState.value
        if (currentState !is CameraState.Ready) return
        
        viewModelScope.launch {
            _uiState.value = CameraState.Capturing
            try {
                val result = capturePhotoUseCase(onCaptureStarted, currentState.parameters)
                onPhotoSaved(result)
                _uiState.value = CameraState.Ready(currentState.parameters)
            } catch (e: Exception) {
                _uiState.value = CameraState.Error("Error al capturar la foto: ${e.message}", e)
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

    fun closeCamera() {
        cameraRepository.closeCamera()
        _uiState.value = CameraState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        closeCamera()
    }
}
