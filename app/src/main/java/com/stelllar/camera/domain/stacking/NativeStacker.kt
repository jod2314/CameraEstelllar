package com.stelllar.camera.domain.stacking

import android.hardware.HardwareBuffer
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * Singleton object that handles native JNI communication with C++ OpenCV and Vulkan.
 * Implements Zero-Copy strategies for astrophotography stacking.
 */
object NativeStacker {

    init {
        try {
            System.loadLibrary("stelllar_native_stacker")
            Timber.i("Loaded native library: stelllar_native_stacker")
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "Failed to load native library")
        }
    }

    /**
     * Initializes the native stacking session.
     * Pre-allocates memory pools and sets up Vulkan context.
     * 
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @param isBayer True if the input is a RAW Bayer image (e.g., RGGB)
     */
    external fun initSession(width: Int, height: Int, isBayer: Boolean): Boolean

    /**
     * Adds a Dark Frame for calibration. (D_start or D_end).
     * The native engine averages them to create the Master Dark.
     * 
     * @param buffer DirectByteBuffer containing the raw pixel data
     * @param rowStride Row stride of the image plane
     */
    external fun addDarkFrame(buffer: ByteBuffer, rowStride: Int): Boolean

    /**
     * Completes the dark frame calibration setup (calculates the Master Dark).
     */
    external fun finalizeMasterDark(): Boolean

    /**
     * Processes a single Light Frame.
     * 1. Calibrates (subtracts Master Dark)
     * 2. Detects stars and calculates Homography (OpenCV CPU)
     * 3. Reprojects and accumulates in the stack (Vulkan GPU)
     * 
     * @param buffer DirectByteBuffer of the Light Frame
     * @param rowStride Row stride of the image plane
     * @return Boolean true if processing succeeded
     */
    external fun processLightFrame(buffer: ByteBuffer, rowStride: Int): Boolean

    /**
     * Finalizes the stacking process using Sigma-Clipping and returns
     * the result into a provided HardwareBuffer or DirectByteBuffer.
     * 
     * @param outBuffer A DirectByteBuffer to receive the stacked image
     */
    external fun finalizeStacking(outBuffer: ByteBuffer): Boolean

    /**
     * Frees all native resources, memory pools, and Vulkan context.
     */
    external fun releaseSession()
}
