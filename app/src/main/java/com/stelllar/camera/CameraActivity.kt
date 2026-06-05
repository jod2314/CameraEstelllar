package com.stelllar.camera

import android.content.Context
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.Observer
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stelllar.camera.presentation.CameraViewModel
import com.stelllar.camera.presentation.SelectorViewModel
import com.stelllar.camera.presentation.compose.CameraScreen
import com.stelllar.camera.presentation.compose.CameraSelectorScreen
import com.stelllar.camera.presentation.compose.ImageViewerScreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.stelllar.camera.presentation.compose.PermissionsScreen
import com.stelllar.camera.utils.OrientationLiveData
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CameraActivity : ComponentActivity() {

    private val cameraViewModel: CameraViewModel by viewModels()
    private val selectorViewModel: SelectorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val navController = rememberNavController()
            MaterialTheme(colorScheme = darkColorScheme()) {
                NavHost(navController = navController, startDestination = "permissions") {
                    composable("permissions") {
                        PermissionsScreen(
                            onPermissionsGranted = {
                                navController.navigate("selector") {
                                    popUpTo("permissions") { inclusive = true }
                                }
                            },
                            onShowToast = { msg ->
                                Toast.makeText(this@CameraActivity, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    composable("selector") {
                        CameraSelectorScreen(
                            viewModel = selectorViewModel,
                            onCameraSelected = { item ->
                                navController.navigate("camera/${item.logicalId}/${item.format}?physicalCameraId=${item.physicalId}")
                            }
                        )
                    }
                    composable(
                        route = "camera/{logicalCameraId}/{pixelFormat}?physicalCameraId={physicalCameraId}",
                        arguments = listOf(
                            navArgument("logicalCameraId") { type = NavType.StringType },
                            navArgument("pixelFormat") { type = NavType.IntType },
                            navArgument("physicalCameraId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        val logicalCameraId = backStackEntry.arguments?.getString("logicalCameraId") ?: ""
                        val pixelFormat = backStackEntry.arguments?.getInt("pixelFormat") ?: 0
                        val physicalCameraId = backStackEntry.arguments?.getString("physicalCameraId")
                        
                        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                        val characteristics = remember(logicalCameraId) {
                            cameraManager.getCameraCharacteristics(logicalCameraId)
                        }
                        val relativeOrientation = remember(logicalCameraId) {
                            OrientationLiveData(this@CameraActivity, characteristics)
                        }

                        var orientation by remember { mutableStateOf(0) }
                        DisposableEffect(relativeOrientation) {
                            val observer = Observer<Int> { value ->
                                orientation = value
                                cameraViewModel.updateRotation(value)
                            }
                            relativeOrientation.observeForever(observer)
                            onDispose {
                                relativeOrientation.removeObserver(observer)
                            }
                        }

                        CameraScreen(
                            viewModel = cameraViewModel,
                            cameraId = logicalCameraId,
                            physicalCameraId = physicalCameraId,
                            pixelFormat = pixelFormat,
                            characteristics = characteristics,
                            orientation = orientation,
                            onShowToast = { msg ->
                                Toast.makeText(this@CameraActivity, msg, Toast.LENGTH_SHORT).show()
                            },
                            onNavigateToViewer = { photoResult ->
                                navController.navigate("viewer/${Uri.encode(photoResult.uriString)}")
                            }
                        )
                    }
                    composable(
                        route = "viewer/{filePath}",
                        arguments = listOf(
                            navArgument("filePath") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
                        ImageViewerScreen(
                            filePath = filePath,
                            onBackClick = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.postDelayed({
            window.decorView.systemUiVisibility = FLAGS_FULLSCREEN
        }, IMMERSIVE_FLAG_TIMEOUT)
    }

    companion object {
        const val FLAGS_FULLSCREEN =
                View.SYSTEM_UI_FLAG_LOW_PROFILE or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        private const val IMMERSIVE_FLAG_TIMEOUT = 500L
    }
}
