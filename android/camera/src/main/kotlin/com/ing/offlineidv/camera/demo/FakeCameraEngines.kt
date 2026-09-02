package com.ing.offlineidv.camera.demo

import com.ing.offlineidv.camera.DocumentCaptureArtifact
import com.ing.offlineidv.camera.DocumentCaptureEngine
import com.ing.offlineidv.camera.DocumentCaptureRequest
import com.ing.offlineidv.camera.DocumentCaptureResult
import com.ing.offlineidv.camera.DocumentQualityEngine
import com.ing.offlineidv.camera.DocumentQualityResult
import com.ing.offlineidv.core.error.CameraFailure
import com.ing.offlineidv.core.error.IdvError

/** External capture observations available to the deterministic camera fake. */
public enum class DemoDocumentCaptureBehavior {
    SUCCEED,
    TECHNICAL_FAILURE,
}

/** External quality observations available to the deterministic quality fake. */
public enum class DemoDocumentQualityBehavior {
    ACCEPT,
    REJECT_ONCE_THEN_ACCEPT,
    ALWAYS_REJECT,
    TECHNICAL_FAILURE,
}

/** Explicit synthetic document capture implementation. */
public class FakeDocumentCaptureEngine(
    private val behavior: DemoDocumentCaptureBehavior,
) : DocumentCaptureEngine {
    private var calls: Int = 0

    override fun capture(request: DocumentCaptureRequest): DocumentCaptureResult {
        calls += 1
        return when (behavior) {
            DemoDocumentCaptureBehavior.SUCCEED -> {
                DocumentCaptureResult.Captured(DocumentCaptureArtifact(calls))
            }

            DemoDocumentCaptureBehavior.TECHNICAL_FAILURE -> {
                DocumentCaptureResult.Failed(IdvError.Camera(CameraFailure.CAPTURE_FAILED))
            }
        }
    }

    /** Clears deterministic per-session call state. */
    public fun reset() {
        calls = 0
    }
}

/** Explicit synthetic document-quality implementation. */
public class FakeDocumentQualityEngine(
    private val behavior: DemoDocumentQualityBehavior,
) : DocumentQualityEngine {
    private var calls: Int = 0

    override fun evaluate(artifact: DocumentCaptureArtifact): DocumentQualityResult {
        calls += 1
        return when (behavior) {
            DemoDocumentQualityBehavior.ACCEPT -> {
                DocumentQualityResult.Accepted
            }

            DemoDocumentQualityBehavior.REJECT_ONCE_THEN_ACCEPT -> {
                if (calls == 1) DocumentQualityResult.Rejected else DocumentQualityResult.Accepted
            }

            DemoDocumentQualityBehavior.ALWAYS_REJECT -> {
                DocumentQualityResult.Rejected
            }

            DemoDocumentQualityBehavior.TECHNICAL_FAILURE -> {
                DocumentQualityResult.Failed(IdvError.Camera(CameraFailure.QUALITY_REJECTED))
            }
        }
    }

    /** Clears deterministic per-session call state. */
    public fun reset() {
        calls = 0
    }
}
