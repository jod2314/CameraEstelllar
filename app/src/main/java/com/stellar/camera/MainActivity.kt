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
    
    // Gestor de permisos reactivo
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            setupCamera()
        } else {
            Toast.makeText(this, R.string.permission_rationale, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            setupCamera()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun setupCamera() {
        binding.statusText.text = "Cámara Lista - Fase 1 Completada"
        // TODO: En el Hito 1 inyectaremos el CameraController aquí
    }

    override fun onResume() {
        super.onResume()
        // El reinicio de cámara se manejará en el ViewModel en hitos posteriores
    }

    override fun onPause() {
        super.onPause()
        // La liberación de cámara se manejará en el ViewModel
    }
}
