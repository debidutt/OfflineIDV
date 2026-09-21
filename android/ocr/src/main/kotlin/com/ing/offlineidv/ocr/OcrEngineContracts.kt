package com.ing.offlineidv.ocr

import com.ing.offlineidv.core.concurrency.CancellableOperation
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.security.Redaction

/** Opaque engine input derived from a captured document owned outside verification state. */
public class OcrDocumentInput(
    public val sourceToken: Int,
) {
    init {
        require(sourceToken > 0) { "sourceToken must be positive" }
    }

    override fun equals(other: Any?): Boolean = other is OcrDocumentInput && sourceToken == other.sourceToken

    override fun hashCode(): Int = sourceToken

    override fun toString(): String = "OcrDocumentInput(${Redaction.MARKER})"
}

/** OCR-derived text retained only behind a trusted artifact boundary. */
public class OcrTextArtifact(
    private val text: String,
    public val recognizedTextBlockCount: Int? = null,
) {
    init {
        require(recognizedTextBlockCount == null || recognizedTextBlockCount >= 0) {
            "recognizedTextBlockCount must not be negative"
        }
    }

    /** Supplies recognized text only to a trusted MRZ-extraction boundary. */
    public fun <R> useText(block: (CharSequence) -> R): R = block(text)

    override fun equals(other: Any?): Boolean = other is OcrTextArtifact && text == other.text

    override fun hashCode(): Int = text.hashCode()

    override fun toString(): String = "OcrTextArtifact(${Redaction.MARKER})"
}

/** Observation produced by an OCR engine without interpreting verification policy. */
public sealed interface OcrEngineResult {
    public data class Recognized(
        public val artifact: OcrTextArtifact,
    ) : OcrEngineResult

    public data class Failed(
        public val error: IdvError,
    ) : OcrEngineResult
}

/** External OCR boundary; MRZ extraction and validation remain separate. */
public fun interface OcrEngine {
    public fun recognize(input: OcrDocumentInput): OcrEngineResult
}

/** Non-blocking OCR boundary for on-device recognition adapters. */
public fun interface AsyncOcrEngine {
    /** Starts recognition and returns a handle that suppresses delivery after cancellation. */
    public fun recognize(
        input: OcrDocumentInput,
        callback: (OcrEngineResult) -> Unit,
    ): CancellableOperation
}

/** Encoded document image owned temporarily inside the OCR adapter boundary. */
public class OcrImage internal constructor(
    encodedBytes: ByteArray,
    public val rotationDegrees: Int,
) {
    private val encodedBytes: ByteArray = encodedBytes.copyOf()

    init {
        require(encodedBytes.isNotEmpty()) { "OCR image must not be empty." }
        require(rotationDegrees in setOf(0, 90, 180, 270)) { "Rotation must be a right angle." }
    }

    /** Supplies encoded bytes only to a trusted on-device OCR adapter. */
    public fun <R> useEncodedBytes(block: (ByteArray) -> R): R = block(encodedBytes)

    /** Zeroes this adapter-owned copy after recognition completes. */
    public fun clear() {
        encodedBytes.fill(0)
    }

    override fun toString(): String = "OcrImage(${Redaction.MARKER})"

    public companion object {
        /** Creates an OCR-owned defensive copy at a trusted platform composition boundary. */
        public fun copyOf(
            encodedBytes: ByteArray,
            rotationDegrees: Int,
        ): OcrImage = OcrImage(encodedBytes, rotationDegrees)
    }
}

/** Resolves opaque capture tokens without exposing camera or storage implementation types to OCR. */
public fun interface OcrImageSource {
    public fun load(sourceToken: Int): IdvResult<OcrImage>
}
