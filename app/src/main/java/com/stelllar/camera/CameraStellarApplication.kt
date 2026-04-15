package com.stelllar.camera

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Clase principal de la aplicación, requerida por Hilt para la inyección de dependencias.
 */
@HiltAndroidApp
class CameraStellarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
