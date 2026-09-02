package com.ing.offlineidv.face.demo

import com.ing.offlineidv.core.error.FaceFailure
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.face.DocumentPortraitArtifact
import com.ing.offlineidv.face.FaceMatchEngine
import com.ing.offlineidv.face.FaceMatchResult
import com.ing.offlineidv.face.SelfieArtifact
import com.ing.offlineidv.face.SelfieCaptureEngine
import com.ing.offlineidv.face.SelfieCaptureRequest
import com.ing.offlineidv.face.SelfieCaptureResult
import com.ing.offlineidv.face.SelfieQualityEngine
import com.ing.offlineidv.face.SelfieQualityResult

/** External selfie-capture observations available to the deterministic fake. */
public enum class DemoSelfieCaptureBehavior {
    SUCCEED,
    TECHNICAL_FAILURE,
}

/** External selfie-quality observations available to the deterministic fake. */
public enum class DemoSelfieQualityBehavior {
    ACCEPT,
    REJECT_ONCE_THEN_ACCEPT,
    ALWAYS_REJECT,
    TECHNICAL_FAILURE,
}

/** External face observations available to the deterministic fake. */
public enum class DemoFaceMatchBehavior {
    ACCEPT,
    REJECT,
    INCONCLUSIVE,
    TECHNICAL_FAILURE,
}

/** Explicit synthetic selfie-capture implementation. */
public class FakeSelfieCaptureEngine(
    private val behavior: DemoSelfieCaptureBehavior,
) : SelfieCaptureEngine {
    private var calls: Int = 0

    override fun capture(request: SelfieCaptureRequest): SelfieCaptureResult {
        calls += 1
        return when (behavior) {
            DemoSelfieCaptureBehavior.SUCCEED -> {
                SelfieCaptureResult.Captured(SelfieArtifact("synthetic-selfie-$calls"))
            }

            DemoSelfieCaptureBehavior.TECHNICAL_FAILURE -> {
                SelfieCaptureResult.Failed(IdvError.Face(FaceFailure.NO_FACE))
            }
        }
    }

    /** Clears deterministic per-session call state. */
    public fun reset() {
        calls = 0
    }
}

/** Explicit synthetic selfie-quality implementation. */
public class FakeSelfieQualityEngine(
    private val behavior: DemoSelfieQualityBehavior,
) : SelfieQualityEngine {
    private var calls: Int = 0

    override fun evaluate(artifact: SelfieArtifact): SelfieQualityResult {
        calls += 1
        return when (behavior) {
            DemoSelfieQualityBehavior.ACCEPT -> {
                SelfieQualityResult.Accepted
            }

            DemoSelfieQualityBehavior.REJECT_ONCE_THEN_ACCEPT -> {
                if (calls == 1) SelfieQualityResult.Rejected else SelfieQualityResult.Accepted
            }

            DemoSelfieQualityBehavior.ALWAYS_REJECT -> {
                SelfieQualityResult.Rejected
            }

            DemoSelfieQualityBehavior.TECHNICAL_FAILURE -> {
                SelfieQualityResult.Failed(IdvError.Face(FaceFailure.QUALITY_REJECTED))
            }
        }
    }

    /** Clears deterministic per-session call state. */
    public fun reset() {
        calls = 0
    }
}

/** Explicit synthetic face-comparison implementation with no biometric algorithm or score. */
public class FakeFaceMatchEngine(
    private val behavior: DemoFaceMatchBehavior,
) : FaceMatchEngine {
    override fun compare(
        documentPortrait: DocumentPortraitArtifact,
        selfie: SelfieArtifact,
    ): FaceMatchResult =
        when (behavior) {
            DemoFaceMatchBehavior.ACCEPT -> {
                FaceMatchResult.Accepted
            }

            DemoFaceMatchBehavior.REJECT -> {
                FaceMatchResult.Rejected
            }

            DemoFaceMatchBehavior.INCONCLUSIVE -> {
                FaceMatchResult.Inconclusive
            }

            DemoFaceMatchBehavior.TECHNICAL_FAILURE -> {
                FaceMatchResult.Failed(IdvError.Face(FaceFailure.COMPARISON_FAILED))
            }
        }

    /** The face fake has no mutable session state, but exposes symmetric cleanup. */
    public fun reset() = Unit
}
