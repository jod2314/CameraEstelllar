package com.stelllar.camera.domain.repository

import java.io.File

data class PhotoResult(
    val file: File,
    val format: Int,
    val orientation: Int
)
