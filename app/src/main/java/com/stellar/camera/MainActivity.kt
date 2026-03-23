package com.stellar.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.stellar.camera.databinding.ActivityMainBinding

/**
 * LensState: Almacena la configuración manual específica de un sensor.
 */
data class LensState(
    var iso: Int,
    var exposureNs: Long,
    var burstCount: Int = 1
)

/**
 * MainActivity: Soporte Multi-Lente Profesional 2025 con Gestión de Post-Procesado.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraController: CameraController
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) startCameraFlow() 
        else Toast.makeText(this, "Permisos insuficientes", Toast.LENGTH_LONG).show()
    }

    private var availableLenses = listOf<AstroLensInfo>()
    private var currentLensIndex = 0
    private val lensSettings = mutableMapOf<String, LensState>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        cameraController = CameraController(this)

        // Escuchar cuando el pipeline esté listo para la siguiente toma
        cameraController.onCaptureReadyListener = {
            setControlsEnabled(true)
            Toast.makeText(this, "Instrumento listo", Toast.LENGTH_SHORT).show()
        }

        binding.captureButton.setOnClickListener {
            if (availableLenses.isEmpty()) return@setOnClickListener
            val activeLens = availableLenses[currentLensIndex]
            setControlsEnabled(false)
            cameraController.takeBurst(binding.burstSeekBar.progress.coerceAtLeast(1), activeLens)
        }

        binding.switchLensButton.setOnClickListener {
            rotateLens()
        }

        checkPermissions()
    }

    private fun setControlsEnabled(enabled: Boolean) {
        runOnUiThread {
            binding.captureButton.isEnabled = enabled
            binding.switchLensButton.isEnabled = enabled
            binding.isoSeekBar.isEnabled = enabled
            binding.exposureSeekBar.isEnabled = enabled
            binding.burstSeekBar.isEnabled = enabled
            binding.captureButton.alpha = if (enabled) 1.0f else 0.5f
        }
    }

    private fun rotateLens() {
        if (availableLenses.isEmpty()) return
        currentLensIndex = (currentLensIndex + 1) % availableLenses.size
        val lens = availableLenses[currentLensIndex]
        
        setupUIForLens(lens)
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraController.openLens(lens, binding.cameraPreview.holder.surface)
        Toast.makeText(this, "Sensor: ${lens.name} Activo", Toast.LENGTH_SHORT).show()
    }

    private fun setupUIForLens(lens: AstroLensInfo) {
        val state = lensSettings.getOrPut(lens.logicalId + (lens.physicalId ?: "")) {
            LensState(iso = lens.maxAnalogIso / 2, exposureNs = 1_000_000_000L)
        }

        runOnUiThread {
            cameraController.currentIso = state.iso
            cameraController.currentExposureNs = state.exposureNs

            // ISO Config
            binding.isoSeekBar.setOnSeekBarChangeListener(null)
            binding.isoSeekBar.max = lens.maxAnalogIso
            binding.isoSeekBar.progress = state.iso
            binding.isoLabel.text = "ISO (Analógico): ${state.iso}"
            
            binding.isoSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, f: Boolean) {
                    val newIso = p.coerceAtLeast(100)
                    binding.isoLabel.text = "ISO: $newIso"
                    state.iso = newIso
                    cameraController.currentIso = newIso
                }
                override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(s: android.widget.SeekBar?) { 
                    cameraController.updatePreviewSettings() 
                }
            })

            // Exposure Config: DESBLOQUEO TOTAL 60s
            binding.exposureSeekBar.setOnSeekBarChangeListener(null)
            val driverMaxSec = (lens.maxExposureNs / 1_000_000_000L).toInt()
            val maxSec = 60 

            binding.exposureSeekBar.max = maxSec
            binding.exposureSeekBar.progress = (state.exposureNs / 1_000_000_000L).toInt().coerceIn(1, maxSec)
            binding.exposureLabel.text = "Exp: ${(state.exposureNs/1e9).toInt()}s (Driver Max: ${driverMaxSec}s)"

            binding.exposureSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, f: Boolean) {
                    val newSec = p.coerceAtLeast(1)
                    binding.exposureLabel.text = "Exposición: ${newSec}s"
                    val newNs = newSec * 1_000_000_000L
                    state.exposureNs = newNs
                    cameraController.currentExposureNs = newNs
                }
                override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
            })

            val hwStr = when(lens.hardwareLevel) {
                1 -> "FULL"
                3 -> "LEVEL_3"
                else -> "LIMITED"
            }
            binding.statusText.text = "${lens.name} | f/${lens.aperture} | $hwStr | RAW: ${lens.hasRaw}"
        }
    }

    private fun checkPermissions() {
        val perms = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        
        // HALLAZGO: Las notificaciones son un permiso runtime en Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startCameraFlow()
        } else {
            requestPermissionLauncher.launch(perms.toTypedArray())
        }
    }

    private fun startCameraFlow() {
        cameraController.startBackgroundThread()
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        availableLenses = cameraController.scanAvailableLenses(manager)
        if (availableLenses.isNotEmpty()) {
            val initial = availableLenses[currentLensIndex]
            setupUIForLens(initial)
            
            binding.cameraPreview.holder.addCallback(object : android.view.SurfaceHolder.Callback {
                override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                    cameraController.openLens(initial, holder.surface)
                }
                override fun surfaceChanged(h: android.view.SurfaceHolder, f: Int, w: Int, h2: Int) {}
                override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                    cameraController.closeCamera()
                }
            })
        }
    }

    override fun onPause() {
        cameraController.closeCamera()
        cameraController.stopBackgroundThread()
        super.onPause()
    }
}
