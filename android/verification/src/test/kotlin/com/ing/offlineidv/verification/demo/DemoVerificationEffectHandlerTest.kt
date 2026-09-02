package com.ing.offlineidv.verification.demo

import com.ing.offlineidv.camera.DocumentCaptureEngine
import com.ing.offlineidv.camera.demo.DemoDocumentCaptureBehavior
import com.ing.offlineidv.camera.demo.DemoDocumentQualityBehavior
import com.ing.offlineidv.camera.demo.FakeDocumentCaptureEngine
import com.ing.offlineidv.camera.demo.FakeDocumentQualityEngine
import com.ing.offlineidv.core.error.CameraFailure
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.result.IdvResult
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
import com.ing.offlineidv.verification.fixtures.VerificationFixtures
import com.ing.offlineidv.verification.model.TransitionResult
import com.ing.offlineidv.verification.model.VerificationArtifactKind
import com.ing.offlineidv.verification.model.VerificationArtifactReference
import com.ing.offlineidv.verification.model.VerificationEffect
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.model.VerificationOperationToken
import com.ing.offlineidv.verification.model.VerificationStep
import com.ing.offlineidv.verification.orchestration.VerificationEventSink
import com.ing.offlineidv.verification.scheduling.DeterministicVerificationScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

public class DemoVerificationEffectHandlerTest {
    @Test
    public fun `initialization echoes exact operation token`() {
        val fixture = fixture()
        val operation = token(VerificationStep.INITIALIZATION, 4)

        fixture.handler.handle(
            VerificationEffect.InitializeSession(VerificationFixtures.sessionId, operation),
            fixture.sink,
        )

        val event = fixture.sink.events.single() as VerificationEvent.InitializationSucceeded
        assertSame(operation, event.operation)
    }

    @Test
    public fun `capture observation echoes exact operation and stores artifact externally`() {
        val fixture = fixture()
        val operation = token(VerificationStep.DOCUMENT_CAPTURE, 5)

        fixture.handler.handle(VerificationEffect.CaptureDocument(operation), fixture.sink)

        val event = fixture.sink.events.single() as VerificationEvent.DocumentCaptured
        assertSame(operation, event.operation)
        assertTrue(
            fixture.registry.resolve(
                event.documentReference,
                VerificationArtifactKind.DOCUMENT_CAPTURE,
                com.ing.offlineidv.camera.DocumentCaptureArtifact::class.java,
            ) is IdvResult.Success,
        )
    }

    @Test
    public fun `engine exception maps to predefined safe failure`() {
        val sensitiveMessage = "raw-passport-value"
        val throwing = DocumentCaptureEngine { throw IllegalStateException(sensitiveMessage) }
        val fixture = fixture(capture = throwing)

        fixture.handler.handle(
            VerificationEffect.CaptureDocument(token(VerificationStep.DOCUMENT_CAPTURE, 1)),
            fixture.sink,
        )

        val event = fixture.sink.events.single() as VerificationEvent.CaptureFailed
        assertEquals(IdvError.Camera(CameraFailure.CAPTURE_FAILED).code, event.error.code)
        assertEquals(IdvError.Camera(CameraFailure.CAPTURE_FAILED).category, event.error.category)
        assertFalse(event.toString().contains(sensitiveMessage))
    }

    @Test
    public fun `unknown artifact emits safe failure with exact operation`() {
        val fixture = fixture()
        val operation = token(VerificationStep.OCR, 9)
        val unknown =
            success(
                VerificationArtifactReference.parse(
                    VerificationArtifactKind.DOCUMENT_CAPTURE,
                    "foreign-reference-0001",
                ),
            )

        fixture.handler.handle(VerificationEffect.RunOcr(operation, unknown), fixture.sink)

        val event = fixture.sink.events.single() as VerificationEvent.OcrFailed
        assertSame(operation, event.operation)
        assertEquals("verification.artifact_reference_invalid", event.error.code)
    }

    @Test
    public fun `step timeout callback emits exact token only when fired`() {
        val fixture = fixture()
        val operation = token(VerificationStep.NFC_READ, 3)
        fixture.handler.handle(
            VerificationEffect.ScheduleTimeout(operation, Duration.ofSeconds(10)),
            fixture.sink,
        )

        assertTrue(fixture.sink.events.isEmpty())
        fixture.scheduler.fire(operation)

        val event = fixture.sink.events.single() as VerificationEvent.SessionTimedOut
        assertSame(operation, event.operation)
    }

    @Test
    public fun `session timeout callback emits exact session id`() {
        val fixture = fixture()
        val operation = token(VerificationStep.SESSION, 0)
        fixture.handler.handle(
            VerificationEffect.ScheduleTimeout(operation, Duration.ofSeconds(10)),
            fixture.sink,
        )

        fixture.scheduler.fire(operation)

        val event = fixture.sink.events.single() as VerificationEvent.SessionExpired
        assertSame(VerificationFixtures.sessionId, event.sessionId)
    }

    @Test
    public fun `cleanup clears artifacts scheduler and resources exactly once`() {
        var resourceClears = 0
        val registry = DemoArtifactRegistry(VerificationFixtures.sessionId)
        registry.register(VerificationArtifactKind.DOCUMENT_CAPTURE, Any())
        val scheduler = DeterministicVerificationScheduler()
        val operation = token(VerificationStep.OCR, 1)
        scheduler.schedule(operation, Duration.ofSeconds(1)) {}
        val fixture =
            fixture(
                registry = registry,
                scheduler = scheduler,
                resources = listOf(DemoSessionResource { resourceClears += 1 }),
            )
        val effect = VerificationEffect.ClearSensitiveSessionData(VerificationFixtures.sessionId)

        fixture.handler.handle(effect, fixture.sink)
        fixture.handler.handle(effect, fixture.sink)

        assertTrue(fixture.handler.cleanupComplete)
        assertTrue(registry.isCleared)
        assertEquals(0, scheduler.scheduledCount)
        assertEquals(1, resourceClears)
    }

    private fun fixture(
        capture: DocumentCaptureEngine = FakeDocumentCaptureEngine(DemoDocumentCaptureBehavior.SUCCEED),
        registry: DemoArtifactRegistry = DemoArtifactRegistry(VerificationFixtures.sessionId),
        scheduler: DeterministicVerificationScheduler = DeterministicVerificationScheduler(),
        resources: List<DemoSessionResource> = emptyList(),
    ): HandlerFixture {
        val sink = RecordingEventSink()
        val handler =
            DemoVerificationEffectHandler(
                documentCaptureEngine = capture,
                documentQualityEngine = FakeDocumentQualityEngine(DemoDocumentQualityBehavior.ACCEPT),
                ocrEngine = FakeOcrEngine(DemoOcrBehavior.VALID_TD3),
                nfcEngine = FakePassportNfcEngine(DemoNfcReadBehavior.SUCCESS),
                chipValidationEngine = FakeChipValidationEngine(DemoChipValidationBehavior()),
                printedChipComparisonEngine =
                    FakePrintedChipComparisonEngine(DemoPrintedChipComparisonBehavior.MATCH),
                selfieCaptureEngine = FakeSelfieCaptureEngine(DemoSelfieCaptureBehavior.SUCCEED),
                selfieQualityEngine = FakeSelfieQualityEngine(DemoSelfieQualityBehavior.ACCEPT),
                faceMatchEngine = FakeFaceMatchEngine(DemoFaceMatchBehavior.ACCEPT),
                mrzPipeline = DemoMrzPipeline(registry, DemoScenarioDefinition.REFERENCE_DATE),
                registry = registry,
                scheduler = scheduler,
                terminalSink = RecordingDemoTerminalResultSink(),
                sessionResources = resources,
            )
        return HandlerFixture(handler, registry, scheduler, sink)
    }

    private fun token(
        step: VerificationStep,
        generation: Int,
    ): VerificationOperationToken = VerificationOperationToken(VerificationFixtures.sessionId, step, generation)

    private fun <T> success(result: IdvResult<T>): T = (result as IdvResult.Success).value

    private data class HandlerFixture(
        val handler: DemoVerificationEffectHandler,
        val registry: DemoArtifactRegistry,
        val scheduler: DeterministicVerificationScheduler,
        val sink: RecordingEventSink,
    )

    private class RecordingEventSink : VerificationEventSink {
        val events = mutableListOf<VerificationEvent>()

        override fun dispatch(event: VerificationEvent): TransitionResult {
            events += event
            return TransitionResult(com.ing.offlineidv.verification.model.Idle)
        }
    }
}
