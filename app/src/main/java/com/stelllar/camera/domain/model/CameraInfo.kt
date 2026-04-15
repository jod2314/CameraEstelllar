package com.stelllar.camera.domain.model

data class CameraInfo(
    val title: String,
    val subtitle: String,
    val logicalId: String,
    val format: Int,
    val physicalId: String? = null,
    val isLogical: Boolean = false
)
