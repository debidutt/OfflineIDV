package com.ing.offlineidv.verification.artifact

import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.session.IdvSessionId
import com.ing.offlineidv.nfc.ChipDataArtifact
import com.ing.offlineidv.nfc.ChipPortraitArtifact
import com.ing.offlineidv.nfc.PassportAccessKey
import com.ing.offlineidv.nfc.PrintedPassportData
import com.ing.offlineidv.ocr.OcrTextArtifact
import com.ing.offlineidv.verification.model.VerificationArtifactKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class SessionArtifactStoreTest {
    @Test
    public fun `artifact reference is scoped by issuing store identity`() {
        val first = SessionArtifactStore(session("first"))
        val second = SessionArtifactStore(session("second"))
        val reference = first.register(VerificationArtifactKind.OCR_RESULT, OcrTextArtifact("sensitive")).success()

        assertTrue(first.resolve(reference, VerificationArtifactKind.OCR_RESULT, OcrTextArtifact::class.java) is IdvResult.Success)
        assertTrue(second.resolve(reference, VerificationArtifactKind.OCR_RESULT, OcrTextArtifact::class.java) is IdvResult.Failure)
    }

    @Test
    public fun `wrong kind and type fail safely`() {
        val store = SessionArtifactStore(session("type"))
        val reference = store.register(VerificationArtifactKind.OCR_RESULT, OcrTextArtifact("sensitive")).success()

        assertTrue(store.resolve(reference, VerificationArtifactKind.DOCUMENT_CAPTURE, OcrTextArtifact::class.java) is IdvResult.Failure)
        assertTrue(store.resolve(reference, VerificationArtifactKind.OCR_RESULT, String::class.java) is IdvResult.Failure)
    }

    @Test
    public fun `cleanup removes artifacts and prevents registration`() {
        val store = SessionArtifactStore(session("clear"))
        val reference = store.register(VerificationArtifactKind.OCR_RESULT, OcrTextArtifact("sensitive")).success()

        store.clear()

        assertEquals(0, store.size)
        assertTrue(store.isCleared)
        assertTrue(store.resolve(reference, VerificationArtifactKind.OCR_RESULT, OcrTextArtifact::class.java) is IdvResult.Failure)
        assertTrue(store.register(VerificationArtifactKind.OCR_RESULT, OcrTextArtifact("other")) is IdvResult.Failure)
    }

    @Test
    public fun `cleanup is idempotent and string is payload free`() {
        val store = SessionArtifactStore(session("twice"))
        store.register(VerificationArtifactKind.OCR_RESULT, OcrTextArtifact("never-render-this"))

        store.clear()
        store.clear()

        assertTrue(store.isCleared)
        assertFalse(store.toString().contains("never-render-this"))
    }

    @Test
    public fun `NFC access material is session scoped`() {
        val first = SessionArtifactStore(session("nfc_key_first"))
        val second = SessionArtifactStore(session("nfc_key_second"))
        val reference = first.register(VerificationArtifactKind.MRZ_ACCESS_KEY, PassportAccessKey("sensitive-key")).success()

        assertTrue(first.resolve(reference, VerificationArtifactKind.MRZ_ACCESS_KEY, PassportAccessKey::class.java) is IdvResult.Success)
        assertTrue(second.resolve(reference, VerificationArtifactKind.MRZ_ACCESS_KEY, PassportAccessKey::class.java) is IdvResult.Failure)
    }

    @Test
    public fun `DG1 and DG2 artifacts remain behind typed opaque references`() {
        val store = SessionArtifactStore(session("nfc_data"))
        val chip = ChipDataArtifact("sensitive-dg1")
        val portrait = ChipPortraitArtifact("sensitive-dg2")
        val chipReference = store.register(VerificationArtifactKind.NFC_CHIP_DATA, chip).success()
        val portraitReference = store.register(VerificationArtifactKind.CHIP_PORTRAIT, portrait).success()

        assertTrue(store.resolve(chipReference, VerificationArtifactKind.NFC_CHIP_DATA, ChipDataArtifact::class.java) is IdvResult.Success)
        assertTrue(
            store.resolve(portraitReference, VerificationArtifactKind.CHIP_PORTRAIT, ChipPortraitArtifact::class.java) is
                IdvResult.Success,
        )
        assertFalse(chipReference.toString().contains("sensitive-dg1"))
        assertFalse(portraitReference.toString().contains("sensitive-dg2"))
    }

    @Test
    public fun `cleanup closes clearable NFC artifacts`() {
        val store = SessionArtifactStore(session("nfc_cleanup"))
        val key = PassportAccessKey("sensitive-key")
        val printed = PrintedPassportData("sensitive-printed")
        val chip = ChipDataArtifact("sensitive-chip")
        val portrait = ChipPortraitArtifact("sensitive-portrait")
        store.register(VerificationArtifactKind.MRZ_ACCESS_KEY, key)
        store.register(VerificationArtifactKind.MRZ_PRINTED_DATA, printed)
        store.register(VerificationArtifactKind.NFC_CHIP_DATA, chip)
        store.register(VerificationArtifactKind.CHIP_PORTRAIT, portrait)

        store.clear()
        store.clear()

        assertFalse(key.useValue { it }.contains("sensitive-key"))
        assertFalse(printed.useValue { it }.contains("sensitive-printed"))
        assertFalse(chip.useValue { it }.contains("sensitive-chip"))
        assertFalse(portrait.useValue { it }.contains("sensitive-portrait"))
        assertEquals(0, store.size)
    }

    private fun session(suffix: String): IdvSessionId = (IdvSessionId.parse("atlas_artifact_$suffix") as IdvResult.Success).value

    private fun <T> IdvResult<T>.success(): T = (this as IdvResult.Success).value
}
