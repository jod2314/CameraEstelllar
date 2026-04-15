package com.stelllar.camera.presentation.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stelllar.camera.domain.model.CameraInfo
import com.stelllar.camera.presentation.SelectorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraSelectorScreen(
    viewModel: SelectorViewModel,
    onCameraSelected: (CameraInfo) -> Unit
) {
    val cameras by viewModel.cameras.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Seleccionar Cámara", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(cameras) { camera ->
                CameraItem(camera = camera, onClick = { onCameraSelected(camera) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
fun CameraItem(camera: CameraInfo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(
            text = camera.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = camera.subtitle,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
