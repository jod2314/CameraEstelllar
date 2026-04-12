package com.stelllar.camera.domain.repository

data class PhotoResult(
    val uriString: String,
    val name: String,
    val format: Int,
    val orientation: Int
)
