package com.ing.offlineidv.camera.real

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ing.offlineidv.camera.AsyncDocumentQualityEngine
import com.ing.offlineidv.camera.DocumentCaptureArtifact
import com.ing.offlineidv.camera.DocumentQualityResult
import com.ing.offlineidv.camera.quality.ImageQualityAnalyzer
import com.ing.offlineidv.camera.quality.ImageQualityAssessment
import com.ing.offlineidv.camera.quality.LumaImage
import com.ing.offlineidv.core.concurrency.CancellableOperation
import com.ing.offlineidv.core.error.CameraFailure
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.result.IdvResult
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToInt

/** Background image-quality adapter backed by the pure deterministic basic analyzer. */
public class CameraXDocumentQualityEngine(
    private val imageStore: InMemoryCapturedImageStore,
    private val executor: Executor,
    private val analyzer: ImageQualityAnalyzer = ImageQualityAnalyzer(),
) : AsyncDocumentQualityEngine {
    override fun evaluate(
        artifact: DocumentCaptureArtifact,
        callback: (DocumentQualityResult) -> Unit,
    ): CancellableOperation {
        val cancelled = AtomicBoolean(false)
        executor.execute {
            val result = analyze(artifact)
            if (!cancelled.get()) callback(result)
        }
        return CancellableOperation { cancelled.set(true) }
    }

    private fun analyze(artifact: DocumentCaptureArtifact): DocumentQualityResult {
        val resolved = imageStore.resolve(artifact)
        if (resolved is IdvResult.Failure) return DocumentQualityResult.Failed(resolved.error)
        val captured = (resolved as IdvResult.Success).value
        return try {
            captured.useEncodedBytes { bytes ->
                val decoded =
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: return@useEncodedBytes DocumentQualityResult.Failed(
                            IdvError.Camera(CameraFailure.QUALITY_REJECTED),
                        )
                decoded.useBitmap { bitmap ->
                    when (analyzer.analyze(bitmap.toLumaImage(captured.width, captured.height))) {
                        ImageQualityAssessment.Accepted -> DocumentQualityResult.Accepted
                        is ImageQualityAssessment.Rejected -> DocumentQualityResult.Rejected
                    }
                }
            }
        } catch (_: RuntimeException) {
            DocumentQualityResult.Failed(IdvError.Camera(CameraFailure.QUALITY_REJECTED))
        }
    }

    private fun Bitmap.toLumaImage(
        sourceWidth: Int,
        sourceHeight: Int,
    ): LumaImage {
        val scale = max(width, height).toDouble() / MAX_SAMPLE_EDGE
        val sampleWidth = if (scale <= 1.0) width else (width / scale).roundToInt().coerceAtLeast(1)
        val sampleHeight = if (scale <= 1.0) height else (height / scale).roundToInt().coerceAtLeast(1)
        val sampled =
            if (sampleWidth == width &&
                sampleHeight == height
            ) {
                this
            } else {
                Bitmap.createScaledBitmap(this, sampleWidth, sampleHeight, true)
            }
        return sampled.useBitmapUnless(this) { source ->
            val pixels = IntArray(sampleWidth * sampleHeight)
            source.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
            val luma =
                IntArray(pixels.size) { index ->
                    val pixel = pixels[index]
                    val red = pixel shr 16 and 0xff
                    val green = pixel shr 8 and 0xff
                    val blue = pixel and 0xff
                    (red * 299 + green * 587 + blue * 114) / 1_000
                }
            LumaImage(sourceWidth, sourceHeight, luma, sampleWidth, sampleHeight)
        }
    }

    private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T =
        try {
            block(this)
        } finally {
            recycle()
        }

    private inline fun <T> Bitmap.useBitmapUnless(
        original: Bitmap,
        block: (Bitmap) -> T,
    ): T =
        try {
            block(this)
        } finally {
            if (this !== original) recycle()
        }

    private companion object {
        const val MAX_SAMPLE_EDGE: Int = 256
    }
}
