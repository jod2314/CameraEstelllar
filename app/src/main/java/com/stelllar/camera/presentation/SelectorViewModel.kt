package com.stelllar.camera.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelllar.camera.domain.model.CameraInfo
import com.stelllar.camera.domain.usecase.GetCamerasUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SelectorViewModel @Inject constructor(
    private val getCamerasUseCase: GetCamerasUseCase
) : ViewModel() {

    private val _cameras = MutableStateFlow<List<CameraInfo>>(emptyList())
    val cameras: StateFlow<List<CameraInfo>> = _cameras.asStateFlow()

    init {
        loadCameras()
    }

    fun loadCameras() {
        viewModelScope.launch {
            _cameras.value = getCamerasUseCase()
        }
    }
}
