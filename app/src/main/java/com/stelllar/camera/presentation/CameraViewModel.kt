package com.stelllar.camera.presentation

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelllar.camera.domain.CameraState
import com.stelllar.camera.domain.repository.CameraRepository
import com.stelllar.camera.domain.repository.PhotoResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraRepository: CameraRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CameraState>(CameraState.Idle)
    val uiState: StateFlow<CameraState> = _uiState.asStateFlow()

    fun initializeCamera(cameraId: String, pixelFormat: Int, surface: Surface, rotation: Int) {
        viewModelScope.launch {
            _uiState.value = CameraState.Initializing
            try {
                cameraRepository.initializeCamera(cameraId, pixelFormat, surface)
                cameraRepository.updateDeviceRotation(rotation)
                _uiState.value = CameraState.Ready
            } catch (e: Exception) {
                _uiState.value = CameraState.Error("Error al inicializar la cámara: ${e.message}", e)
            }
        }
    }

    fun updateRotation(rotation: Int) {
        cameraRepository.updateDeviceRotation(rotation)
    }

    fun takePhoto(onPhotoSaved: (PhotoResult) -> Unit, onCaptureStarted: () -> Unit = {}) {
        if (_uiState.value !is CameraState.Ready) return
        
        viewModelScope.launch {
            _uiState.value = CameraState.Capturing
            try {
                val result = cameraRepository.takePhoto(onCaptureStarted)
                onPhotoSaved(result)
                _uiState.value = CameraState.Ready
            } catch (e: Exception) {
                _uiState.value = CameraState.Error("Error al capturar la foto: ${e.message}", e)
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
