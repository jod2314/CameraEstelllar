package com.stellar.camera

import android.hardware.HardwareBuffer

/**
 * NativeEngine: Puente JNI hacia el motor de fotografía computacional en C++.
 * Optimizado para transferencia Zero-Copy mediante HardwareBuffer.
 */
object NativeEngine {
    init {
        System.loadLibrary("camerastellar")
    }

    external fun getNativeVersion(): String
    
    /**
     * Procesa una imagen RAW directamente desde la memoria del hardware (Zero-Copy).
     * @param buffer El buffer de hardware obtenido de Image.hardwareBuffer
     */
    external fun processHardwareBuffer(buffer: HardwareBuffer, timestamp: Long)
}
