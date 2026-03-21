package com.stellar.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.stellar.camera.databinding.ActivityMainBinding

/**
 * MainActivity: Cimiento de CameraStellar v3.
 * Orquesta permisos, UI y ciclo de vida.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraController: CameraController
    
    // Gestor de permisos reactivo
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            startCameraFlow()
        } else {
            Toast.makeText(this, R.string.permission_rationale, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        cameraController = CameraController(this)

        binding.captureButton.setOnClickListener {
            cameraController.takeBurst(1) // Captura simple inicial
            Toast.makeText(this, "Capturando RAW...", Toast.LENGTH_SHORT).show()
        }

        checkPermissions()
    }

    private fun checkPermissions() {
        val permissionsToRequest = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startCameraFlow()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun startCameraFlow() {
        cameraController.startBackgroundThread()
        binding.statusText.text = "Cámara Iniciada - RAW Habilitado"
        
        // Esperar a que el SurfaceView esté listo
        binding.cameraPreview.holder.addCallback(object : android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                val manager = getSystemService(android.content.Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                cameraController.openCamera(manager, holder.surface)
            }
            override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                cameraController.closeCamera()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraController.stopBackgroundThread()
    }
}
