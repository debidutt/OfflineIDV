package com.ing.offlineidv.verification.demo

import com.ing.offlineidv.camera.demo.DemoDocumentCaptureBehavior
import com.ing.offlineidv.camera.demo.DemoDocumentQualityBehavior
import com.ing.offlineidv.camera.demo.FakeDocumentCaptureEngine
import com.ing.offlineidv.camera.demo.FakeDocumentQualityEngine
import com.ing.offlineidv.face.demo.DemoFaceMatchBehavior
import com.ing.offlineidv.face.demo.DemoSelfieCaptureBehavior
import com.ing.offlineidv.face.demo.DemoSelfieQualityBehavior
import com.ing.offlineidv.face.demo.FakeFaceMatchEngine
import com.ing.offlineidv.face.demo.FakeSelfieCaptureEngine
import com.ing.offlineidv.face.demo.FakeSelfieQualityEngine
import com.ing.offlineidv.nfc.demo.DemoChipValidationBehavior
import com.ing.offlineidv.nfc.demo.DemoNfcReadBehavior
import com.ing.offlineidv.nfc.demo.DemoPrintedChipComparisonBehavior
import com.ing.offlineidv.nfc.demo.FakeChipValidationEngine
import com.ing.offlineidv.nfc.demo.FakePassportNfcEngine
import com.ing.offlineidv.nfc.demo.FakePrintedChipComparisonEngine
import com.ing.offlineidv.ocr.demo.DemoOcrBehavior
import com.ing.offlineidv.ocr.demo.FakeOcrEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class FakeEnginePolicyIsolationTest {
    @Test
    public fun `fake engine APIs contain no verification policy state event effect or outcome type`() {
        fakeTypes().forEach { type ->
            val exposedTypes =
                buildList {
                    type.declaredFields.forEach { field -> add(field.type) }
                    type.declaredMethods.forEach { method ->
                        add(method.returnType)
                        addAll(method.parameterTypes)
                    }
                }

            assertTrue(
                "${type.name} exposes verification orchestration types",
                exposedTypes.none { exposed ->
                    exposed.name.startsWith("com.ing.offlineidv.verification")
                },
            )
        }
    }

    @Test
    public fun `fake behavior vocabularies contain no final product outcome`() {
        val forbiddenOutcomes =
            setOf(
                "VERIFIED",
                "REJECTED",
                "INCONCLUSIVE_OUTCOME",
                "CANCELLED",
                "EXPIRED",
            )
        behaviorTypes().forEach { type ->
            val constants = type.enumConstants?.map { it.toString() }.orEmpty()
            assertFalse("${type.name} contains a product outcome", constants.any { it in forbiddenOutcomes })
        }
    }

    private fun fakeTypes(): List<Class<*>> =
        listOf(
            FakeDocumentCaptureEngine::class.java,
            FakeDocumentQualityEngine::class.java,
            FakeOcrEngine::class.java,
            FakePassportNfcEngine::class.java,
            FakeChipValidationEngine::class.java,
            FakePrintedChipComparisonEngine::class.java,
            FakeSelfieCaptureEngine::class.java,
            FakeSelfieQualityEngine::class.java,
            FakeFaceMatchEngine::class.java,
        )

    private fun behaviorTypes(): List<Class<out Enum<*>>> =
        listOf(
            DemoDocumentCaptureBehavior::class.java,
            DemoDocumentQualityBehavior::class.java,
            DemoOcrBehavior::class.java,
            DemoNfcReadBehavior::class.java,
            DemoPrintedChipComparisonBehavior::class.java,
            DemoSelfieCaptureBehavior::class.java,
            DemoSelfieQualityBehavior::class.java,
            DemoFaceMatchBehavior::class.java,
        )
}
