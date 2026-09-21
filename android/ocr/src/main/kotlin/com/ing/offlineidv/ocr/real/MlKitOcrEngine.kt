package com.ing.offlineidv.ocr.real

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ing.offlineidv.core.concurrency.CancellableOperation
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.OcrFailure
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.ocr.AsyncOcrEngine
import com.ing.offlineidv.ocr.OcrDocumentInput
import com.ing.offlineidv.ocr.OcrEngineResult
import com.ing.offlineidv.ocr.OcrImage
import com.ing.offlineidv.ocr.OcrImageSource
import com.ing.offlineidv.ocr.OcrTextArtifact
import java.util.concurrent.atomic.AtomicBoolean

/** Bundled, on-device ML Kit Latin text-recognition adapter with cancellation-safe delivery. */
public class MlKitOcrEngine(
    private val imageSource: OcrImageSource,
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
) : AsyncOcrEngine,
    AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun recognize(
        input: OcrDocumentInput,
        callback: (OcrEngineResult) -> Unit,
    ): CancellableOperation {
        val cancelled = AtomicBoolean(false)
        if (closed.get()) {
            callback(OcrEngineResult.Failed(IdvError.Ocr(OcrFailure.ENGINE_UNAVAILABLE)))
            return CancellableOperation.NONE
        }
        val loaded = imageSource.load(input.sourceToken)
        if (loaded is IdvResult.Failure) {
            callback(OcrEngineResult.Failed(loaded.error))
            return CancellableOperation.NONE
        }
        val ownedImage = (loaded as IdvResult.Success).value
        val bitmap = decode(ownedImage)
        if (bitmap == null) {
            ownedImage.clear()
            callback(OcrEngineResult.Failed(IdvError.Ocr(OcrFailure.RECOGNITION_FAILED)))
            return CancellableOperation.NONE
        }
        val task =
            try {
                recognizer.process(InputImage.fromBitmap(bitmap, ownedImage.rotationDegrees))
            } catch (_: RuntimeException) {
                bitmap.recycle()
                ownedImage.clear()
                callback(OcrEngineResult.Failed(IdvError.Ocr(OcrFailure.ENGINE_UNAVAILABLE)))
                return CancellableOperation.NONE
            }
        task
            .addOnSuccessListener { text ->
                if (!cancelled.get() && !closed.get()) {
                    callback(
                        OcrEngineResult.Recognized(
                            OcrTextArtifact(
                                text = text.text,
                                recognizedTextBlockCount = text.textBlocks.size,
                            ),
                        ),
                    )
                }
            }.addOnFailureListener {
                if (!cancelled.get() && !closed.get()) {
                    callback(OcrEngineResult.Failed(IdvError.Ocr(OcrFailure.RECOGNITION_FAILED)))
                }
            }.addOnCompleteListener {
                bitmap.recycle()
                ownedImage.clear()
            }
        return CancellableOperation { cancelled.set(true) }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) recognizer.close()
    }

    private fun decode(image: OcrImage): Bitmap? =
        try {
            image.useEncodedBytes { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        } catch (_: RuntimeException) {
            null
        }
}
