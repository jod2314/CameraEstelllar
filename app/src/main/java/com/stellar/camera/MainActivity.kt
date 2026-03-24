package com.stellar.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.stellar.camera.databinding.ActivityMainBinding

data class LensState(
    var iso: Int,
    var exposureNs: Long,
    var burstCount: Int = 1
)

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
    private var isSurfaceReady = false

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            isSurfaceReady = true
            tryOpenCurrentLens()
        }
        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, h2: Int) {}
        override fun surfaceDestroyed(holder: SurfaceHolder) {
            isSurfaceReady = false
            cameraController.closeCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        cameraController = CameraController(this)

        cameraController.onMetadataReceived = { iso, expNs ->
            runOnUiThread {
                binding.metadataText.text = "RAW FEEDBACK | ISO: $iso | Exp: ${String.format("%.3f", expNs/1e9)}s"
                binding.metadataText.setTextColor(if (iso == cameraController.currentIso) 0xFF00FF00.toInt() else 0xFFFFFFFF.toInt())
            }
        }

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

        binding.switchLensButton.setOnClickListener { rotateLens() }
        
        binding.closeAuditButton.setOnClickListener {
            binding.auditPanel.visibility = android.view.View.GONE
        }

        // Registrar el callback UNA SOLA VEZ
        binding.cameraPreview.holder.addCallback(surfaceCallback)
        
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
        if (availableLenses.isEmpty() || cameraController.isTransitioning) return
        currentLensIndex = (currentLensIndex + 1) % availableLenses.size
        tryOpenCurrentLens()
    }

    private fun tryOpenCurrentLens() {
        if (availableLenses.isEmpty() || !isSurfaceReady) return
        val lens = availableLenses[currentLensIndex]
        setupUIForLens(lens)
        cameraController.openLens(lens, binding.cameraPreview.holder.surface)
        Toast.makeText(this, "Sensor: ${lens.name}", Toast.LENGTH_SHORT).show()
    }

    private fun setupUIForLens(lens: AstroLensInfo) {
        val state = lensSettings.getOrPut(lens.logicalId + (lens.physicalId ?: "")) {
            LensState(iso = lens.maxAnalogIso / 2, exposureNs = 1_000_000_000L)
        }

        runOnUiThread {
            cameraController.currentIso = state.iso
            cameraController.currentExposureNs = state.exposureNs

            binding.isoSeekBar.setOnSeekBarChangeListener(null)
            binding.isoSeekBar.max = lens.maxAnalogIso
            binding.isoSeekBar.progress = state.iso
            binding.isoLabel.text = "ISO: ${state.iso}"
            
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

            binding.exposureSeekBar.setOnSeekBarChangeListener(null)
            val maxSec = 30 // BYPASS: Permitimos hasta 30s manuales para probar el hardware
            binding.exposureSeekBar.max = maxSec
            binding.exposureSeekBar.progress = (state.exposureNs / 1_000_000_000L).toInt().coerceIn(1, maxSec)
            binding.exposureLabel.text = "Exp: ${(state.exposureNs/1e9).toInt()}s"

            binding.exposureSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, f: Boolean) {
                    val newSec = p.coerceAtLeast(1)
                    binding.exposureLabel.text = "INYECTANDO: ${newSec}s"
                    state.exposureNs = newSec * 1_000_000_000L
                    cameraController.currentExposureNs = state.exposureNs
                }
                override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
            })

            binding.burstSeekBar.setOnSeekBarChangeListener(null)
            binding.burstSeekBar.max = 20
            binding.burstSeekBar.progress = state.burstCount.coerceIn(1, 20)
            binding.burstLabel.text = "Fotos en Ráfaga: ${state.burstCount}"

            binding.burstSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, f: Boolean) {
                    val count = p.coerceAtLeast(1)
                    binding.burstLabel.text = "Fotos en Ráfaga: $count"
                    state.burstCount = count
                }
                override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
            })

            val hwStr = when(lens.hardwareLevel) {
                1 -> "FULL"
                3 -> "LEVEL_3"
                else -> "LIMITED"
            }
            binding.statusText.text = "${lens.name} | f/${lens.aperture} | $hwStr"
        }
    }

    private fun checkPermissions() {
        val perms = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
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
        
        // Generar Informe de Auditoría
        val report = StringBuilder()
        report.append("Dispositivos de Imagen Detectados:\n")
        report.append("---------------------------------\n")
        availableLenses.forEachIndexed { index, lens ->
            val hw = when(lens.hardwareLevel) {
                1 -> "FULL"
                3 -> "LEVEL_3"
                2 -> "LEGACY"
                else -> "LIMITED"
            }
            val type = if (lens.isPhysical) "FÍSICO" else "LÓGICO"
            report.append("S$index: ${lens.name}\n")
            report.append("  > Tipo: $type\n")
            report.append("  > ID: L:${lens.logicalId} / P:${lens.physicalId ?: "None"}\n")
            report.append("  > Hardware Level: $hw\n")
            report.append("  > ISO Analógico Máx: ${lens.maxAnalogIso}\n")
            report.append("  > Exp. Máxima: ${String.format("%.1f", lens.maxExposureNs/1e9)}s\n")
            report.append("  > RAW Sensor: ${if(lens.hasRaw) "SI ✅" else "NO ❌"}\n")
            if (lens.vendorKeys.isNotEmpty()) {
                report.append("  > Vendor Tags: ${lens.vendorKeys.size} detectadas\n")
                val samsungExp = lens.vendorKeys.find { it.contains("exposure") || it.contains("shutter") }
                if (samsungExp != null) report.append("    [!] Llave de Exp. Extendida: SI\n")
            }
            report.append("---------------------------------\n")
        }
        
        runOnUiThread {
            binding.auditText.text = report.toString()
            if (availableLenses.isEmpty()) {
                binding.auditText.text = "ERROR: No se detectaron sensores compatibles con RAW/LEVEL_3"
                binding.auditText.setTextColor(0xFFFF0000.toInt())
            }
        }

        if (availableLenses.isNotEmpty()) {
            tryOpenCurrentLens()
        }
    }

    override fun onResume() {
        super.onResume()
        if (availableLenses.isNotEmpty() && isSurfaceReady) {
            tryOpenCurrentLens()
        }
    }

    override fun onPause() {
        cameraController.closeCamera()
        super.onPause()
    }

    override fun onDestroy() {
        cameraController.stopBackgroundThread()
        super.onDestroy()
    }
}
