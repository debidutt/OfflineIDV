package com.ing.offlineidv.ocr.mrz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

public class MrzCandidateExtractorTest {
    private val extractor = MrzCandidateExtractor()

    @Test
    public fun `clean OCR produces TD3 candidate`() {
        assertEquals(MRZ, extract(MRZ))
    }

    @Test
    public fun `split OCR fragments are joined cautiously`() {
        val split =
            "${LINE_1.take(22)}\n${LINE_1.drop(22)}\n" +
                "${LINE_2.take(22)}\n${LINE_2.drop(22)}"

        assertEquals(MRZ, extract(split))
    }

    @Test
    public fun `extra OCR whitespace is removed`() {
        val spaced = "  ${LINE_1.chunked(11).joinToString(" ")}  \n ${LINE_2.chunked(11).joinToString("  ")} "

        assertEquals(MRZ, extract(spaced))
    }

    @Test
    public fun `unrelated text is rejected`() {
        assertNull(extractor.extract("Welcome to the airport\nBoarding begins at ten"))
    }

    @Test
    public fun `passport shaped TD3 ranks above weaker candidate`() {
        val weakerFirst = "PAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        val weakerSecond = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA12345"

        assertEquals(MRZ, extract("$weakerFirst\n$weakerSecond\n$MRZ"))
    }

    @Test
    public fun `filler characters are preserved`() {
        val extracted = extract(MRZ)

        assertTrue(extracted.contains("<<"))
        assertEquals(MRZ.count { it == '<' }, extracted.count { it == '<' })
        assertFalse(extractor.extract(MRZ).toString().contains(LINE_1))
    }

    @Test
    public fun `extractor accepts invalid checksums and contains no validation implementation`() {
        val invalidChecksum = LINE_2.replaceRange(43, 44, "9")

        assertNotNull(extractor.extract("$LINE_1\n$invalidChecksum"))
        val source = locateSource().readText()
        listOf("MrzValidator", "MrzCheckDigit", "Td3MrzParser", "validate(", "parse(").forEach { token ->
            assertFalse("candidate extractor must not contain $token", source.contains(token))
        }
    }

    private fun extract(text: String): String = requireNotNull(extractor.extract(text)).useText { it.toString() }

    private fun locateSource(): File {
        var directory: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (directory != null) {
            val candidate =
                File(
                    directory,
                    "android/ocr/src/main/kotlin/com/ing/offlineidv/ocr/mrz/MrzCandidateExtractor.kt",
                )
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        error("Could not locate candidate extractor source.")
    }

    private companion object {
        const val LINE_1: String = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
        const val LINE_2: String = "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
        const val MRZ: String = "$LINE_1\n$LINE_2"
    }
}
