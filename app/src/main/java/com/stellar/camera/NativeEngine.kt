package com.stellar.camera

import java.nio.ByteBuffer

/**
 * NativeEngine: Puente JNI hacia el motor de fotografía computacional en C++.
 */
object NativeEngine {
    init {
        System.loadLibrary("native-lib")
    }

    external fun getNativeVersion(): String
    
    /**
     * Pasa el buffer de la cámara directamente a C++ sin copias de memoria.
     */
    external fun processRawBuffer(buffer: ByteBuffer, width: Int, height: Int)
}
