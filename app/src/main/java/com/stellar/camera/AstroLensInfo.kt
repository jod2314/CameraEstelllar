package com.stellar.camera

/**
 * AstroLensInfo: Pasaporte de capacidades físicas del hardware para el motor 2025.
 */
data class AstroLensInfo(
    val logicalId: String,
    val physicalId: String?,
    val name: String,
    val maxExposureNs: Long,
    val maxAnalogIso: Int,
    val aperture: Float,
    val focalLength: Float,
    val hardwareLevel: Int,
    val supportsLowLightBoost: Boolean,
    val hasRaw: Boolean
)
