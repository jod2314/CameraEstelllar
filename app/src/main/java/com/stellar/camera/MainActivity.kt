package com.stellar.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
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
 * MainActivity: Soporte Multi-Lente Profesional 2025.
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

        binding.captureButton.setOnClickListener {
            if (availableLenses.isEmpty()) return@setOnClickListener
            val activeLens = availableLenses[currentLensIndex]
            cameraController.takeBurst(binding.burstSeekBar.progress.coerceAtLeast(1), activeLens)
            Toast.makeText(this, "Capturando ráfaga científica...", Toast.LENGTH_SHORT).show()
        }

        binding.switchLensButton.setOnClickListener {
            rotateLens()
        }

        checkPermissions()
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
                    val iso = p.coerceAtLeast(100)
                    binding.isoLabel.text = "ISO: $iso"
                    state.iso = iso
                    cameraController.currentIso = iso
                }
                override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(s: android.widget.SeekBar?) { 
                    cameraController.updatePreviewSettings(lens) 
                }
            })

            // Exposure Config
            binding.exposureSeekBar.setOnSeekBarChangeListener(null)
            // Exposure Config: DESBLOQUEO TOTAL 60s
            val driverMaxSec = (lens.maxExposureNs / 1_000_000_000L).toInt()
            val maxSec = 60 // Forzamos 60s para intentar el bypass manual

            binding.exposureSeekBar.max = maxSec
            binding.exposureSeekBar.progress = (state.exposureNs / 1_000_000_000L).toInt().coerceIn(1, maxSec)
            binding.exposureLabel.text = "Exposición: ${(state.exposureNs/1e9).toInt()}s (Reportado: ${driverMaxSec}s)"

            binding.exposureSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, f: Boolean) {
                    val sec = p.coerceAtLeast(1)
                    binding.exposureLabel.text = "Exposición: ${sec}s"
                    state.exposureNs = sec * 1_000_000_000L
                    cameraController.currentExposureNs = state.exposureNs
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
        val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        if (perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startCameraFlow()
        } else {
            requestPermissionLauncher.launch(perms)
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
