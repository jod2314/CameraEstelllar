package com.stelllar.camera

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Clase principal de la aplicación, requerida por Hilt para la inyección de dependencias.
 */
@HiltAndroidApp
class CameraStellarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicialización global si es necesaria en el futuro
    }
}
