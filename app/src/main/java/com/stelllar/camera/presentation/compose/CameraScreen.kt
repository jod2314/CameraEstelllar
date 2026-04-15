package com.stelllar.camera.presentation.compose

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.view.SurfaceHolder
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.stelllar.camera.R
import com.stelllar.camera.domain.CameraState
import com.stelllar.camera.presentation.CameraViewModel
import com.stelllar.camera.utils.AutoFitSurfaceView
import com.stelllar.camera.utils.getPreviewOutputSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    cameraId: String,
    physicalCameraId: String?,
    pixelFormat: Int,
    characteristics: CameraCharacteristics,
    orientation: Int,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Configuración de Exposición guardada
    val prefs = remember { context.getSharedPreferences("camera_test", Context.MODE_PRIVATE) }
    var savedMax by remember { mutableStateOf(prefs.getLong("max_exposure_ns_$cameraId", 0L)) }
    
    var testText by remember { 
        mutableStateOf(
            if (savedMax > 0) "Máxima real: ${savedMax / 1e9}s"
            else {
                val theoreticalMaxExp = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.upper ?: 0L
                val formattedExp = String.format("%.2f", theoreticalMaxExp / 1e9)
                "Máx. Oficial: ${formattedExp}s (No testeada)"
            }
        )
    }
    
    var isTesting by remember { mutableStateOf(false) }
    var surfaceProvider by remember { mutableStateOf<SurfaceHolder?>(null) }
    
    // Animación de Flash al capturar
    val flashAlpha = remember { Animatable(0f) }
    
    val captureEnabled = uiState is CameraState.Ready && !isTesting
    val testEnabled = !isTesting
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                AutoFitSurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            surfaceProvider = null
                        }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                        
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            val previewSize = getPreviewOutputSize(
                                display,
                                characteristics,
                                SurfaceHolder::class.java
                            )
                            setAspectRatio(previewSize.width, previewSize.height)
                            surfaceProvider = holder
                            
                            // Iniciar la cámara si no estamos en medio de un test
                            if (!isTesting) {
                                viewModel.initializeCamera(
                                    cameraId, physicalCameraId, pixelFormat, holder.surface, orientation
                                )
                            }
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Info overlay (Parte superior)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x80000000))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = testText, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    isTesting = true
                    testText = "Probando exposición... (no salir)"
                    viewModel.closeCamera()
                    
                    scope.launch(Dispatchers.IO) {
                        try {
                            delay(2000) // Reset del HAL
                            viewModel.runSensorProbe(cameraId, physicalCameraId) { maxNs ->
                                scope.launch(Dispatchers.Main) {
                                    if (maxNs > 0) {
                                        savedMax = maxNs
                                        prefs.edit().putLong("max_exposure_ns_$cameraId", maxNs).apply()
                                        testText = "Máxima real: ${maxNs / 1e9}s"
                                        onShowToast("Test Exitoso: ${maxNs / 1e9}s")
                                    } else {
                                        testText = "Fallo al detectar máximo"
                                        onShowToast("La cámara no soporta exposición prolongada")
                                    }
                                    
                                    delay(1000) // Reset del HAL post-prueba
                                    isTesting = false
                                    surfaceProvider?.surface?.let { surface ->
                                        viewModel.initializeCamera(
                                            cameraId, physicalCameraId, pixelFormat, surface, orientation
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                testText = "Error en prueba"
                                delay(1000)
                                isTesting = false
                                surfaceProvider?.surface?.let { surface ->
                                    viewModel.initializeCamera(
                                        cameraId, physicalCameraId, pixelFormat, surface, orientation
                                    )
                                }
                            }
                        }
                    }
                },
                enabled = testEnabled
            ) {
                Text("Test de Exposición Máxima")
            }
        }
        
        // Overlay de Flash animado
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = flashAlpha.value))
        )
        
        // Botón de Captura (Parte inferior)
        IconButton(
            onClick = {
                viewModel.takePhoto(
                    onCaptureStarted = {
                        scope.launch {
                            flashAlpha.snapTo(0.6f)
                            flashAlpha.animateTo(0f, animationSpec = tween(250))
                        }
                    },
                    onPhotoSaved = { photoResult ->
                        onShowToast("Foto guardada: ${photoResult.name}")
                    }
                )
            },
            enabled = captureEnabled,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .size(96.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_photo),
                contentDescription = "Capturar",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
