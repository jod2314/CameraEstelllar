/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stelllar.camera.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.stelllar.camera.presentation.SelectorViewModel
import com.stelllar.camera.presentation.compose.CameraSelectorScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SelectorFragment : Fragment() {

    private val viewModel: SelectorViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Dispose the Composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    CameraSelectorScreen(
                        viewModel = viewModel,
                        onCameraSelected = { item ->
                            findNavController().navigate(
                                SelectorFragmentDirections.actionSelectorToCamera(
                                    item.logicalId, 
                                    item.format
                                ).setPhysicalCameraId(item.physicalId)
                            )
                        }
                    )
                }
            }
        }
    }
}

// Helper para el tema rápido (podría moverse a un archivo aparte)
@androidx.compose.runtime.Composable
fun MaterialTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(),
        content = content
    )
}
