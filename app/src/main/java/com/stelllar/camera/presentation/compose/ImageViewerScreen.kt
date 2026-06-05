package com.stelllar.camera.presentation.compose

import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import java.io.File

@Composable
fun ImageViewerScreen(
    filePath: String,
    onBackClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Carga la imagen mediante el ImageView tradicional con Glide integrado en Compose
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { imageView ->
                Glide.with(imageView.context)
                    .load(File(filePath))
                    .into(imageView)
            },
            onRelease = { imageView ->
                Glide.with(imageView.context).clear(imageView)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Botón flotante para retroceder
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .padding(24.dp)
                .size(48.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = CircleShape
                )
                .align(Alignment.TopStart)
        ) {
            // Un icono simple de flecha atrás en texto / Componible, o un caracter Unicode elegante
            Text(
                text = "←",
                color = Color.White,
                style = LocalTextStyle.current.copy(
                    fontSize = 24.sp
                )
            )
        }
    }
}

