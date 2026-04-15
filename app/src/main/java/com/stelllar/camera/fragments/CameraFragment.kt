package com.stelllar.camera.fragments

import dagger.hilt.android.AndroidEntryPoint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.navArgs
import com.stelllar.camera.presentation.CameraViewModel
import com.stelllar.camera.presentation.compose.CameraScreen
import com.stelllar.camera.utils.OrientationLiveData

@AndroidEntryPoint
class CameraFragment : Fragment() {

    private val args: CameraFragmentArgs by navArgs()

    private val cameraManager: CameraManager by lazy {
        requireContext().applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(args.cameraId)
    }

    private lateinit var relativeOrientation: OrientationLiveData

    private val viewModel: CameraViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        relativeOrientation = OrientationLiveData(requireContext(), characteristics)
        
        relativeOrientation.observe(viewLifecycleOwner, Observer { orientation ->
            Log.d(TAG, "Orientation changed: $orientation")
            viewModel.updateRotation(orientation)
        })

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    CameraScreen(
                        viewModel = viewModel,
                        cameraId = args.cameraId,
                        physicalCameraId = args.physicalCameraId,
                        pixelFormat = args.pixelFormat,
                        characteristics = characteristics,
                        orientation = relativeOrientation.value ?: 0,
                        onShowToast = { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    companion object {
        private val TAG = CameraFragment::class.java.simpleName
    }
}
