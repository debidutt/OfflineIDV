package com.ing.offlineidv.camera.real

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.ing.offlineidv.camera.AsyncCameraPreparationEngine
import com.ing.offlineidv.camera.AsyncDocumentCaptureEngine
import com.ing.offlineidv.camera.CameraPreparationResult
import com.ing.offlineidv.camera.DocumentCaptureRequest
import com.ing.offlineidv.camera.DocumentCaptureResult
import com.ing.offlineidv.core.concurrency.CancellableOperation
import com.ing.offlineidv.core.error.CameraFailure
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.result.IdvResult
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Lifecycle-bound CameraX adapter that returns only opaque, session-owned capture artifacts. */
public class CameraXDocumentCaptureEngine(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val imageStore: InMemoryCapturedImageStore,
    private val captureExecutor: Executor,
) : AsyncCameraPreparationEngine,
    AsyncDocumentCaptureEngine,
    AutoCloseable {
    private val applicationContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(applicationContext)
    private val providerFuture = ProcessCameraProvider.getInstance(applicationContext)

    @Volatile private var previewSurfaceProvider: Preview.SurfaceProvider? = null

    @Volatile private var preview: Preview? = null

    @Volatile private var imageCapture: ImageCapture? = null

    @Volatile private var provider: ProcessCameraProvider? = null

    private val closed = AtomicBoolean(false)

    /** Attaches or replaces the presentation-owned preview surface without exposing it to domain state. */
    public fun attachPreview(surfaceProvider: Preview.SurfaceProvider?) {
        previewSurfaceProvider = surfaceProvider
        mainExecutor.execute { if (!closed.get()) preview?.setSurfaceProvider(surfaceProvider) }
    }

    override fun prepare(callback: (CameraPreparationResult) -> Unit): CancellableOperation {
        val cancelled = AtomicBoolean(false)
        providerFuture.addListener(
            {
                if (cancelled.get() || closed.get()) return@addListener
                val result =
                    try {
                        val cameraProvider = providerFuture.get()
                        val previewUseCase = Preview.Builder().build()
                        val captureUseCase =
                            ImageCapture
                                .Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                        previewUseCase.setSurfaceProvider(previewSurfaceProvider)
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            previewUseCase,
                            captureUseCase,
                        )
                        provider = cameraProvider
                        preview = previewUseCase
                        imageCapture = captureUseCase
                        CameraPreparationResult.Ready
                    } catch (_: Exception) {
                        CameraPreparationResult.Failed(IdvError.Camera(CameraFailure.UNAVAILABLE))
                    }
                if (!cancelled.get() && !closed.get()) callback(result)
            },
            mainExecutor,
        )
        return CancellableOperation { cancelled.set(true) }
    }

    override fun capture(
        request: DocumentCaptureRequest,
        callback: (DocumentCaptureResult) -> Unit,
    ): CancellableOperation {
        val cancelled = AtomicBoolean(false)
        val capture = imageCapture
        if (capture == null || closed.get()) {
            callback(DocumentCaptureResult.Failed(IdvError.Camera(CameraFailure.UNAVAILABLE)))
            return CancellableOperation.NONE
        }
        capture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val result =
                        try {
                            if (cancelled.get() || closed.get()) return
                            val buffer = image.planes.firstOrNull()?.buffer
                            if (buffer == null || !buffer.hasRemaining()) {
                                DocumentCaptureResult.Failed(IdvError.Camera(CameraFailure.CAPTURE_FAILED))
                            } else {
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                when (
                                    val stored =
                                        imageStore.store(
                                            requestSessionId = request.sessionId,
                                            encodedBytes = bytes,
                                            width = image.width,
                                            height = image.height,
                                            rotationDegrees = image.imageInfo.rotationDegrees,
                                        )
                                ) {
                                    is IdvResult.Success -> DocumentCaptureResult.Captured(stored.value)
                                    is IdvResult.Failure -> DocumentCaptureResult.Failed(stored.error)
                                }
                            }
                        } catch (_: RuntimeException) {
                            DocumentCaptureResult.Failed(IdvError.Camera(CameraFailure.CAPTURE_FAILED))
                        } finally {
                            image.close()
                        }
                    if (!cancelled.get() && !closed.get()) callback(result)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (!cancelled.get() && !closed.get()) {
                        callback(DocumentCaptureResult.Failed(IdvError.Camera(CameraFailure.CAPTURE_FAILED)))
                    }
                }
            },
        )
        return CancellableOperation { cancelled.set(true) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        previewSurfaceProvider = null
        imageCapture = null
        preview = null
        mainExecutor.execute {
            provider?.unbindAll()
            provider = null
        }
    }
}
