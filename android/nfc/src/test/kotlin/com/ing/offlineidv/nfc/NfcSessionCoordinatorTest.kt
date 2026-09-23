package com.ing.offlineidv.nfc

import com.ing.offlineidv.core.concurrency.CancellableOperation
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.NfcFailure
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.session.IdvSessionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

public class NfcSessionCoordinatorTest {
    @Test
    public fun `unavailable capability completes without discovery`() {
        val discovery = RecordingDiscovery()
        val results = mutableListOf<NfcReadResult>()

        coordinator(NfcCapability.UNAVAILABLE, discovery).read(request, results::add)

        assertEquals(listOf(NfcReadResult.Unavailable), results)
        assertTrue(discovery.callbacks.isEmpty())
    }

    @Test
    public fun `disabled capability is distinct and does not start discovery`() {
        val discovery = RecordingDiscovery()
        val results = mutableListOf<NfcReadResult>()

        coordinator(NfcCapability.DISABLED, discovery).read(request, results::add)

        assertEquals("nfc.disabled", (results.single() as NfcReadResult.Failed).error.code)
        assertTrue(discovery.callbacks.isEmpty())
    }

    @Test
    public fun `available capability starts one read for the requested session`() {
        val discovery = RecordingDiscovery()
        val results = mutableListOf<NfcReadResult>()

        coordinator(NfcCapability.AVAILABLE, discovery).read(request, results::add)

        assertEquals(1, discovery.callbacks.size)
        assertTrue(results.isEmpty())
    }

    @Test
    public fun `physical tag reports detection connection and reading once in order`() {
        val executor = QueuedExecutor()
        val discovery = RecordingDiscovery()
        val progress = mutableListOf<NfcReadProgress>()
        val results = mutableListOf<NfcReadResult>()
        val session =
            object : PassportChipSession {
                override fun read(accessKey: PassportAccessKey): NfcReadResult = error("progress overload expected")

                override fun read(
                    accessKey: PassportAccessKey,
                    progressObserver: NfcReadProgressObserver,
                ): NfcReadResult {
                    progressObserver.onProgress(NfcReadProgress.READING)
                    return NfcReadResult.Read(ChipDataArtifact("chip"))
                }
            }
        coordinator(NfcCapability.AVAILABLE, discovery, executor).read(
            request,
            NfcReadProgressObserver(progress::add),
            results::add,
        )

        assertTrue(progress.isEmpty())
        discovery.confirmReaderActive()
        assertEquals(listOf(NfcReadProgress.READER_ACTIVE), progress)
        discovery.callbacks.single()(NfcTagDiscoveryResult.Connected(session))
        assertEquals(listOf(NfcReadProgress.READER_ACTIVE, NfcReadProgress.TAG_DETECTED), progress)
        executor.runAll()

        assertEquals(
            listOf(
                NfcReadProgress.READER_ACTIVE,
                NfcReadProgress.TAG_DETECTED,
                NfcReadProgress.CONNECTING,
                NfcReadProgress.READING,
            ),
            progress,
        )
        assertTrue(results.single() is NfcReadResult.Read)
    }

    @Test
    public fun `unsupported tag maps to a predefined safe failure`() {
        val discovery = RecordingDiscovery()
        val results = mutableListOf<NfcReadResult>()
        coordinator(NfcCapability.AVAILABLE, discovery).read(request, results::add)

        discovery.callbacks.single()(NfcTagDiscoveryResult.UnsupportedTag)

        assertEquals("nfc.unsupported_tag", (results.single() as NfcReadResult.Failed).error.code)
    }

    @Test
    public fun `IsoDep absence maps to a distinct safe failure`() {
        val discovery = RecordingDiscovery()
        val results = mutableListOf<NfcReadResult>()
        coordinator(NfcCapability.AVAILABLE, discovery).read(request, results::add)

        discovery.callbacks.single()(NfcTagDiscoveryResult.IsoDepUnavailable)

        assertEquals("nfc.iso_dep_unavailable", (results.single() as NfcReadResult.Failed).error.code)
    }

    @Test
    public fun `duplicate tag is ignored and its session is closed`() {
        val executor = QueuedExecutor()
        val discovery = RecordingDiscovery()
        val coordinator = coordinator(NfcCapability.AVAILABLE, discovery, executor)
        val accepted = RecordingSession(NfcReadResult.Read(ChipDataArtifact("chip")))
        val duplicate = RecordingSession(NfcReadResult.Read(ChipDataArtifact("duplicate")))
        coordinator.read(request) {}

        discovery.callbacks.single()(NfcTagDiscoveryResult.Connected(accepted))
        discovery.callbacks.single()(NfcTagDiscoveryResult.Connected(duplicate))

        assertTrue(duplicate.closed)
        assertFalse(accepted.closed)
        executor.runAll()
        assertTrue(accepted.closed)
    }

    @Test
    public fun `callback from replaced read is stale and closes its session`() {
        val discovery = RecordingDiscovery()
        val coordinator = coordinator(NfcCapability.AVAILABLE, discovery)
        coordinator.read(request) {}
        val stale = discovery.callbacks.first()
        coordinator.read(request) {}
        val session = RecordingSession(NfcReadResult.Timeout)

        stale(NfcTagDiscoveryResult.Connected(session))

        assertTrue(session.closed)
        assertTrue(discovery.cancelled.first())
    }

    @Test
    public fun `cancellation closes active session and suppresses late completion`() {
        val executor = QueuedExecutor()
        val discovery = RecordingDiscovery()
        val coordinator = coordinator(NfcCapability.AVAILABLE, discovery, executor)
        val results = mutableListOf<NfcReadResult>()
        val session = RecordingSession(NfcReadResult.Read(ChipDataArtifact("chip")))
        val operation = coordinator.read(request, results::add)
        discovery.callbacks.single()(NfcTagDiscoveryResult.Connected(session))

        operation.cancel()
        executor.runAll()

        assertTrue(session.closed)
        assertTrue(results.isEmpty())
    }

    @Test
    public fun `tag lost result remains a feature observation`() {
        val result = executeSession(NfcReadResult.Failed(IdvError.Nfc(NfcFailure.TAG_LOST)))

        assertEquals("nfc.tag_lost", (result as NfcReadResult.Failed).error.code)
    }

    @Test
    public fun `transport timeout remains a feature observation`() {
        assertEquals(NfcReadResult.Timeout, executeSession(NfcReadResult.Timeout))
    }

    @Test
    public fun `unknown session exception maps to predefined technical failure`() {
        val discovery = RecordingDiscovery()
        val results = mutableListOf<NfcReadResult>()
        coordinator(NfcCapability.AVAILABLE, discovery).read(request, results::add)
        val throwing =
            object : PassportChipSession {
                override fun read(accessKey: PassportAccessKey): NfcReadResult = error("sensitive platform detail")

                override fun close() = Unit
            }

        discovery.callbacks.single()(NfcTagDiscoveryResult.Connected(throwing))

        assertEquals("nfc.technical_error", (results.single() as NfcReadResult.Failed).error.code)
    }

    @Test
    public fun `coordinator close cancels pending discovery idempotently`() {
        val discovery = RecordingDiscovery()
        val coordinator = coordinator(NfcCapability.AVAILABLE, discovery)
        coordinator.read(request) {}

        coordinator.close()
        coordinator.close()

        assertTrue(discovery.cancelled.single())
    }

    @Test
    public fun `synchronous discovery failure still cancels the subsequently attached handle`() {
        var cancelled = false
        val discovery =
            NfcTagDiscovery { callback ->
                callback(NfcTagDiscoveryResult.Failed(IdvError.Nfc(NfcFailure.TECHNICAL_ERROR)))
                CancellableOperation { cancelled = true }
            }
        val results = mutableListOf<NfcReadResult>()

        coordinator(NfcCapability.AVAILABLE, discovery).read(request, results::add)

        assertTrue(cancelled)
        assertEquals("nfc.technical_error", (results.single() as NfcReadResult.Failed).error.code)
    }

    private fun executeSession(result: NfcReadResult): NfcReadResult {
        val discovery = RecordingDiscovery()
        val results = mutableListOf<NfcReadResult>()
        coordinator(NfcCapability.AVAILABLE, discovery).read(request, results::add)
        discovery.callbacks.single()(NfcTagDiscoveryResult.Connected(RecordingSession(result)))
        return results.single()
    }

    private class RecordingDiscovery : NfcTagDiscovery {
        val callbacks = mutableListOf<(NfcTagDiscoveryResult) -> Unit>()
        val progressObservers = mutableListOf<NfcReadProgressObserver>()
        val cancelled = mutableListOf<Boolean>()

        override fun start(callback: (NfcTagDiscoveryResult) -> Unit): CancellableOperation {
            callbacks += callback
            cancelled += false
            val index = cancelled.lastIndex
            return CancellableOperation { cancelled[index] = true }
        }

        override fun start(
            progressObserver: NfcReadProgressObserver,
            callback: (NfcTagDiscoveryResult) -> Unit,
        ): CancellableOperation {
            progressObservers += progressObserver
            return start(callback)
        }

        fun confirmReaderActive() {
            progressObservers.single().onProgress(NfcReadProgress.READER_ACTIVE)
        }
    }

    private class RecordingSession(
        private val result: NfcReadResult,
    ) : PassportChipSession {
        var closed: Boolean = false

        override fun read(accessKey: PassportAccessKey): NfcReadResult = result

        override fun close() {
            closed = true
        }
    }

    private class QueuedExecutor : Executor {
        private val work = mutableListOf<Runnable>()

        override fun execute(command: Runnable) {
            work += command
        }

        fun runAll() {
            work.toList().also { work.clear() }.forEach(Runnable::run)
        }
    }

    private companion object {
        val request =
            NfcReadRequest(
                (IdvSessionId.parse("atlas_nfc_session") as IdvResult.Success).value,
                PassportAccessKey("A12B34567900101301231"),
            )

        fun coordinator(
            capability: NfcCapability,
            discovery: NfcTagDiscovery,
            executor: Executor = Executor(Runnable::run),
        ): NfcSessionCoordinator =
            NfcSessionCoordinator(
                capabilityDetector = NfcCapabilityDetector { capability },
                discovery = discovery,
                executor = executor,
            )
    }
}
