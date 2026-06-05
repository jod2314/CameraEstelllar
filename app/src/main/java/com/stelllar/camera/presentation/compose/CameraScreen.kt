package com.stelllar.camera.presentation.compose

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.util.Size
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
import com.stelllar.camera.domain.CameraParameters
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
    onShowToast: (String) -> Unit,
    onNavigateToViewer: (com.stelllar.camera.domain.repository.PhotoResult) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Identificador único para el sensor (físico si está disponible, de lo contrario lógico)
    val sensorId = physicalCameraId ?: cameraId
    
    // Configuración de Exposición guardada obtenida del ViewModel (Capa de negocio/datos)
    var savedMax by remember(sensorId) { mutableStateOf(viewModel.getMaxExposureNs(sensorId)) }
    
    // Rangos del hardware
    val isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: android.util.Range(100, 3200)
    val theoreticalMaxExp = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.upper ?: 100_000_000L
    val effectiveMaxExposure = if (savedMax > 0) savedMax else theoreticalMaxExp

    // Estados de los controles manuales
    var currentIso by remember(sensorId) { mutableStateOf(isoRange.lower.toFloat()) }
    var currentExposure by remember(sensorId) { mutableStateOf(effectiveMaxExposure.toFloat()) }
    var currentBurst by remember(sensorId) { mutableIntStateOf(1) }
    var currentTimer by remember(sensorId) { mutableIntStateOf(0) }

    // Sincronizar cambios con el ViewModel
    LaunchedEffect(currentIso, currentExposure, currentBurst, currentTimer) {
        viewModel.updateParameters(
            CameraParameters(
                iso = currentIso.toInt(),
                exposureTimeNs = currentExposure.toLong(),
                frameCount = currentBurst,
                timerSeconds = currentTimer
            )
        )
    }

    // Liberar la cámara de forma segura al desmontar o cambiar de sensor
    DisposableEffect(sensorId) {
        onDispose {
            viewModel.closeCamera()
        }
    }

    var isTesting by remember { mutableStateOf(false) }
    var surfaceProvider by remember { mutableStateOf<SurfaceHolder?>(null) }
    
    // Animación de Flash al capturar
    val flashAlpha = remember { Animatable(0f) }
    
    val captureEnabled = uiState is CameraState.Ready && !isTesting
    val testEnabled = !isTesting
    
    Box(modifier = Modifier.fillMaxSize()) {
        // ... (AndroidView sigue igual) ...
        AndroidView(
            factory = { ctx ->
                val view = AutoFitSurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            surfaceProvider = null
                        }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                        
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            surfaceProvider = holder
                            
                            if (!isTesting) {
                                scope.launch {
                                    try {
                                        val size = viewModel.initializeCamera(
                                            cameraId, physicalCameraId, pixelFormat, holder.surface, orientation
                                        )
                                        setAspectRatio(size.width, size.height)
                                    } catch (e: Exception) {
                                        onShowToast("Error al abrir cámara: ${e.message}")
                                    }
                                }
                            }
                        }
                    })
                }
                view
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // --- PANEL DE CONTROLES MANUALES (Overlay Derecho) ---
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp)
                .width(200.dp)
                .background(Color(0x99000000), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text("ISO: ${currentIso.toInt()}", color = Color.White, style = MaterialTheme.typography.labelSmall)
            Slider(
                value = currentIso,
                onValueChange = { currentIso = it },
                valueRange = isoRange.lower.toFloat()..isoRange.upper.toFloat(),
                modifier = Modifier.height(24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            val expSecs = currentExposure / 1e9
            Text("Obturación: ${String.format("%.2f", expSecs)}s", color = Color.White, style = MaterialTheme.typography.labelSmall)
            Slider(
                value = currentExposure,
                onValueChange = { currentExposure = it },
                valueRange = 1000000f..effectiveMaxExposure.toFloat(),
                modifier = Modifier.height(24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fotos: $currentBurst", color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = { if(currentBurst > 1) currentBurst-- }) {
                    Icon(painterResource(android.R.drawable.button_onoff_indicator_off), "Menos", tint = Color.White)
                }
                IconButton(onClick = { if(currentBurst < 100) currentBurst++ }) {
                    Icon(painterResource(android.R.drawable.button_onoff_indicator_on), "Más", tint = Color.White)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Timer: ${currentTimer}s", color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = { if(currentTimer > 0) currentTimer -= 2 }) {
                    Icon(painterResource(android.R.drawable.ic_menu_recent_history), "Menos", tint = Color.White)
                }
                IconButton(onClick = { if(currentTimer < 10) currentTimer += 2 }) {
                    Icon(painterResource(android.R.drawable.ic_menu_add), "Más", tint = Color.White)
                }
            }
        }

        // Info overlay (Panel de Telemetría Superior)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xB3000000)) // Fondo negro más sólido para legibilidad
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Info Oficial
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Límite Oficial", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    Text("${String.format("%.2f", theoreticalMaxExp / 1e9)}s", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
                
                // Info Bypass (Solo si existe)
                if (savedMax > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Bypass Alcanzado", color = Color(0xFF00FF00), style = MaterialTheme.typography.labelSmall)
                        Text("${String.format("%.2f", savedMax / 1e9)}s", color = Color(0xFF00FF00), style = MaterialTheme.typography.bodyLarge)
                    }
                }

                // Info ISO
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Rango ISO", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    Text("${isoRange.lower}-${isoRange.upper}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = {
                    isTesting = true
                    viewModel.closeCamera()
                    scope.launch {
                        try {
                            // Esperar a que la cámara libere los recursos nativos antes del sondeo
                            delay(1000)
                            viewModel.runSensorProbe(cameraId, physicalCameraId) { maxNs ->
                                if (maxNs > 0) {
                                    savedMax = maxNs
                                    viewModel.saveMaxExposureNs(sensorId, maxNs)
                                    currentExposure = maxNs.toFloat()
                                    onShowToast("Bypass Exitoso: ${maxNs / 1e9}s")
                                } else {
                                    onShowToast("Bypass no soportado")
                                }
                                isTesting = false
                                surfaceProvider?.surface?.let { surface ->
                                    scope.launch {
                                        try {
                                            viewModel.initializeCamera(cameraId, physicalCameraId, pixelFormat, surface, orientation)
                                        } catch (e: Exception) {
                                            onShowToast("Error al abrir cámara: ${e.message}")
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            isTesting = false
                        }
                    }
                },
                enabled = testEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = if(savedMax > 0) Color(0xFF333333) else MaterialTheme.colorScheme.primary),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Text(if(savedMax > 0) "Re-testear Bypass" else "Ejecutar Test de Bypass", style = MaterialTheme.typography.labelMedium)
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
                        onShowToast("Guardado: ${photoResult.name}")
                        onNavigateToViewer(photoResult)
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
