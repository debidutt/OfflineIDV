@file:Suppress("FunctionName") // Jetpack Compose functions follow Android's PascalCase convention.

package com.ing.offlineidv.demo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.ing.offlineidv.ui.AtlasRuntimeMode
import com.ing.offlineidv.ui.AtlasVerifyApp

/** Launchable, offline-only host for the explicitly synthetic Project Atlas demo. */
public class MainActivity : ComponentActivity() {
    private val viewModel: AtlasDemoViewModel by lazy {
        ViewModelProvider(
            this,
            AtlasDemoViewModel.Factory(applicationContext),
        )[AtlasDemoViewModel::class.java]
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onCameraPermissionResult(
                granted,
            )
        }

    private val permissionRequester: () -> Unit = {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.onCameraPermissionResult(true)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.attachPermissionRequester(permissionRequester)
        setContent {
            AtlasVerifyApp(
                state = viewModel.state,
                onAction = viewModel::dispatch,
                runtimeMode = viewModel.runtimeMode,
                nfcAvailability = viewModel.nfcAvailability,
                cameraPreview =
                    if (viewModel.runtimeMode == AtlasRuntimeMode.REAL_ANDROID) {
                        { RealCameraPreview(viewModel) }
                    } else {
                        null
                    },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.attachNfcHost(this)
    }

    override fun onPause() {
        viewModel.detachNfcHost(this)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.acceptNfcIntent(intent)
    }

    override fun onDestroy() {
        viewModel.detachPermissionRequester(permissionRequester)
        super.onDestroy()
    }
}

/** Thin PreviewView host; CameraX remains owned by the real adapter and never by UI state. */
@Composable
private fun RealCameraPreview(viewModel: AtlasDemoViewModel) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                viewModel.attachPreview(surfaceProvider)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
    DisposableEffect(viewModel) {
        onDispose { viewModel.attachPreview(null) }
    }
}
