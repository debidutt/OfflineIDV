package com.ing.offlineidv.camera.real

import com.ing.offlineidv.camera.DocumentCaptureArtifact
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.session.IdvSessionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class InMemoryCapturedImageStoreTest {
    @Test
    public fun `capture artifact is scoped to issuing session store`() {
        val first = InMemoryCapturedImageStore(session("first"))
        val second = InMemoryCapturedImageStore(session("second"))
        val artifact = first.store(first.sessionId, byteArrayOf(1, 2), 10, 10, 0).success()

        assertTrue(first.resolve(artifact) is IdvResult.Success)
        assertTrue(second.resolve(artifact) is IdvResult.Failure)
        assertTrue(first.store(second.sessionId, byteArrayOf(1), 10, 10, 0) is IdvResult.Failure)
    }

    @Test
    public fun `unknown source token fails safely`() {
        val store = InMemoryCapturedImageStore(session("unknown"))

        assertTrue(store.resolve(DocumentCaptureArtifact(99)) is IdvResult.Failure)
    }

    @Test
    public fun `cleanup zeroes and removes captured image`() {
        val store = InMemoryCapturedImageStore(session("clear"))
        val artifact = store.store(store.sessionId, byteArrayOf(8, 9), 10, 10, 90).success()
        val retained = store.resolve(artifact).success()

        store.clear(store.sessionId)

        assertEquals(0, store.size)
        assertTrue(store.isCleared)
        assertTrue(store.resolve(artifact) is IdvResult.Failure)
        assertTrue(retained.useEncodedBytes { bytes -> bytes.all { it == 0.toByte() } })
    }

    @Test
    public fun `cleanup is idempotent`() {
        val store = InMemoryCapturedImageStore(session("twice"))
        store.store(store.sessionId, byteArrayOf(1), 10, 10, 0)

        store.clear(store.sessionId)
        store.clear(store.sessionId)

        assertEquals(0, store.size)
        assertTrue(store.isCleared)
    }

    private fun session(suffix: String): IdvSessionId = (IdvSessionId.parse("atlas_store_$suffix") as IdvResult.Success).value

    private fun <T> IdvResult<T>.success(): T = (this as IdvResult.Success).value
}
