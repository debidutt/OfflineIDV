package com.ing.offlineidv.demo

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.camera.core.Preview
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ing.offlineidv.ui.AtlasNfcAvailability
import com.ing.offlineidv.ui.AtlasRuntimeMode
import com.ing.offlineidv.ui.AtlasUiAction
import com.ing.offlineidv.ui.AtlasUiState

/** Android state-observation adapter; verification remains free of lifecycle and Compose types. */
internal class AtlasDemoViewModel(
    private val controller: AtlasDemoController,
    private val permissionGateway: CameraPermissionGateway,
) : ViewModel() {
    var state: AtlasUiState by mutableStateOf(controller.state)
        private set

    var runtimeMode: AtlasRuntimeMode by mutableStateOf(controller.runtimeMode)
        private set

    var nfcAvailability: AtlasNfcAvailability by mutableStateOf(AtlasNfcAvailability.UNKNOWN)
        private set

    private val observation = controller.observe { state = it }

    fun dispatch(action: AtlasUiAction) {
        controller.dispatch(action)
        runtimeMode = controller.runtimeMode
        refreshNfcAvailability()
    }

    fun attachPreview(surfaceProvider: Preview.SurfaceProvider?) {
        controller.attachPreview(surfaceProvider)
    }

    fun attachPermissionRequester(requester: () -> Unit) {
        permissionGateway.attach(requester)
    }

    fun detachPermissionRequester(requester: () -> Unit) {
        permissionGateway.detach(requester)
    }

    fun onCameraPermissionResult(granted: Boolean) {
        permissionGateway.complete(granted)
    }

    fun attachNfcHost(activity: Activity) {
        controller.attachNfcHost(activity)
        refreshNfcAvailability()
    }

    fun detachNfcHost(activity: Activity) {
        controller.detachNfcHost(activity)
    }

    fun acceptNfcIntent(intent: Intent) {
        controller.acceptNfcIntent(intent)
    }

    fun refreshNfcAvailability() {
        nfcAvailability =
            when (controller.nfcCapability()) {
                com.ing.offlineidv.nfc.NfcCapability.UNAVAILABLE -> AtlasNfcAvailability.UNAVAILABLE
                com.ing.offlineidv.nfc.NfcCapability.DISABLED -> AtlasNfcAvailability.DISABLED
                com.ing.offlineidv.nfc.NfcCapability.AVAILABLE -> AtlasNfcAvailability.AVAILABLE
                null -> AtlasNfcAvailability.UNKNOWN
            }
    }

    override fun onCleared() {
        observation.close()
        controller.close()
    }

    class Factory(
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val permissionGateway = CameraPermissionGateway()
            return AtlasDemoViewModel(
                AtlasDemoCompositionRoot.createController(context.applicationContext, permissionGateway),
                permissionGateway,
            ) as T
        }
    }
}
