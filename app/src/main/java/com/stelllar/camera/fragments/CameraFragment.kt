package com.stelllar.camera.fragments

import dagger.hilt.android.AndroidEntryPoint
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.stelllar.camera.CameraActivity
import com.stelllar.camera.R
import com.stelllar.camera.databinding.FragmentCameraBinding
import com.stelllar.camera.domain.CameraState
import com.stelllar.camera.domain.repository.PhotoResult
import com.stelllar.camera.presentation.CameraViewModel
import com.stelllar.camera.utils.OrientationLiveData
import com.stelllar.camera.utils.getPreviewOutputSize
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CameraFragment : Fragment() {

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    private val args: CameraFragmentArgs by navArgs()

    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
    }

    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(args.cameraId)
    }

    private lateinit var relativeOrientation: OrientationLiveData

    private val viewModel: CameraViewModel by viewModels()

    private val animationTask: Runnable by lazy {
        Runnable {
            fragmentCameraBinding.overlay.background = Color.argb(150, 255, 255, 255).toDrawable()
            fragmentCameraBinding.overlay.postDelayed({
                fragmentCameraBinding.overlay.background = null
            }, CameraActivity.ANIMATION_FAST_MILLIS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        fragmentCameraBinding.captureButton.setOnApplyWindowInsetsListener { v, insets ->
            v.translationX = (-insets.systemWindowInsetRight).toFloat()
            v.translationY = (-insets.systemWindowInsetBottom).toFloat()
            insets.consumeSystemWindowInsets()
        }

        fragmentCameraBinding.viewFinder.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {
                val previewSize = getPreviewOutputSize(
                    fragmentCameraBinding.viewFinder.display,
                    characteristics,
                    SurfaceHolder::class.java
                )
                fragmentCameraBinding.viewFinder.setAspectRatio(previewSize.width, previewSize.height)

                view.post {
                    viewModel.initializeCamera(
                        args.cameraId,
                        args.pixelFormat,
                        fragmentCameraBinding.viewFinder.holder.surface,
                        relativeOrientation.value ?: 0
                    )
                }
            }
        })

        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
            observe(viewLifecycleOwner, Observer { orientation ->
                Log.d(TAG, "Orientation changed: $orientation")
                viewModel.updateRotation(orientation)
            })
        }

        setupObservers()

        fragmentCameraBinding.captureButton.setOnClickListener {
            it.isEnabled = false
            viewModel.takePhoto(
                onCaptureStarted = {
                    fragmentCameraBinding.viewFinder.post(animationTask)
                },
                onPhotoSaved = { photoResult ->
                    Log.d(TAG, "Image saved: ${photoResult.file.absolutePath}")
                    
                    lifecycleScope.launch {
                        navController.navigate(
                            CameraFragmentDirections
                                .actionCameraToJpegViewer(photoResult.file.absolutePath)
                                .setOrientation(photoResult.orientation)
                                .setDepth(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                        photoResult.format == ImageFormat.DEPTH_JPEG)
                        )
                    }
                }
            )
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is CameraState.Idle -> { /* UI base */ }
                        is CameraState.Initializing -> { /* Podría mostrar un loader */ }
                        is CameraState.Ready -> {
                            fragmentCameraBinding.captureButton.isEnabled = true
                        }
                        is CameraState.Capturing -> {
                            fragmentCameraBinding.captureButton.isEnabled = false
                        }
                        is CameraState.Error -> {
                            Log.e(TAG, state.message, state.cause)
                            fragmentCameraBinding.captureButton.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
    }

    companion object {
        private val TAG = CameraFragment::class.java.simpleName
    }
}
