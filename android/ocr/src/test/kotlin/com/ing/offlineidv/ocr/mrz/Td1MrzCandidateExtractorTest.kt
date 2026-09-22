package com.ing.offlineidv.ocr.mrz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class Td1MrzCandidateExtractorTest {
    private val extractor = Td1MrzCandidateExtractor()

    @Test
    public fun `clean three-line OCR produces a TD1 candidate`() {
        val candidate = requireNotNull(extractor.extract(TD1))

        assertEquals(listOf(30, 30, 30), candidate.lineLengths)
        assertEquals(TD1, candidate.useText { it.toString() })
        assertFalse(candidate.toString().contains("SYNTHETIC"))
    }

    @Test
    public fun `split OCR fragments are joined cautiously`() {
        val split =
            listOf(
                LINE_1.take(15),
                LINE_1.drop(15),
                LINE_2.take(15),
                LINE_2.drop(15),
                LINE_3.take(15),
                LINE_3.drop(15),
            ).joinToString("\n")

        assertEquals(TD1, requireNotNull(extractor.extract(split)).useText { it.toString() })
    }

    @Test
    public fun `unrelated OCR and two-line passports are rejected`() {
        assertNull(extractor.extract("Welcome to the airport\nBoarding begins soon"))
        assertNull(extractor.extract("P<UTOSYNTHETIC<<PERSON<<<<<<<<<<<<<<<<<<<<\nX12T345676UTO9001011F3001019<<<<<<<<<<<<<<02"))
    }

    @Test
    public fun `candidate ranking prefers exact thirty-character lines`() {
        val weakFirst = "$LINE_1<<<<"
        val weakSecond = "$LINE_2<<<<"
        val weakThird = "$LINE_3<<<<"

        val candidate = requireNotNull(extractor.extract("$weakFirst\n$weakSecond\n$weakThird\n$TD1"))

        assertTrue(candidate.lineLengths.all { it == 30 })
    }

    private companion object {
        const val LINE_1: String = "I<NLDX12T345676<<<<<<<<<<<<<<<"
        const val LINE_2: String = "9001011F3001019UTO<<<<<<<<<<<6"
        const val LINE_3: String = "SYNTHETIC<<RESIDENT<TEST<<<<<<"
        const val TD1: String = "$LINE_1\n$LINE_2\n$LINE_3"
    }
}
