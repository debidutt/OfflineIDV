package com.ing.offlineidv.mrz.date

import com.ing.offlineidv.mrz.model.MrzExpiryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

public class MrzDateInterpreterTest {
    private val referenceDate = LocalDate.of(2026, 8, 2)
    private val interpreter = WindowedMrzDateInterpreter()

    @Test
    public fun `birth date resolves inside non-future window`() {
        val result = interpreter.interpretBirthDate("900101", referenceDate).asResolved()

        assertEquals(LocalDate.of(1990, 1, 1), result.date.preferredValue)
        assertFalse(result.date.isAmbiguous)
    }

    @Test
    public fun `birth century retains both plausible candidates`() {
        val result = interpreter.interpretBirthDate("200101", referenceDate).asAmbiguous()

        assertEquals(LocalDate.of(2020, 1, 1), result.date.preferredValue)
        assertEquals(listOf(LocalDate.of(1920, 1, 1)), result.date.alternativeValues)
    }

    @Test
    public fun `future birth is rejected when no non-future candidate meets policy`() {
        val strictInterpreter =
            WindowedMrzDateInterpreter(MrzDatePolicy(maximumBirthAgeYears = 90))
        val result = strictInterpreter.interpretBirthDate("270101", referenceDate).asFailure()

        assertEquals(MrzDateFailure.FUTURE_BIRTH_DATE, result.reason)
    }

    @Test
    public fun `malformed date is distinct from impossible date`() {
        val malformed = interpreter.interpretBirthDate("90A101", referenceDate).asFailure()
        val impossible = interpreter.interpretBirthDate("901332", referenceDate).asFailure()

        assertEquals(MrzDateFailure.MALFORMED, malformed.reason)
        assertEquals(MrzDateFailure.IMPOSSIBLE, impossible.reason)
    }

    @Test
    public fun `valid leap day resolves`() {
        val result = interpreter.interpretBirthDate("040229", referenceDate).asResolved()

        assertEquals(LocalDate.of(2004, 2, 29), result.date.preferredValue)
    }

    @Test
    public fun `invalid leap day is impossible`() {
        val result = interpreter.interpretBirthDate("030229", referenceDate).asFailure()

        assertEquals(MrzDateFailure.IMPOSSIBLE, result.reason)
    }

    @Test
    public fun `past expiry remains a resolved expired evidence status`() {
        val result = interpreter.interpretExpiryDate("250101", referenceDate).asResolved()

        assertEquals(LocalDate.of(2025, 1, 1), result.date.preferredValue)
        assertEquals(MrzExpiryStatus.EXPIRED, result.expiryStatus)
    }

    @Test
    public fun `expiry on reference date has explicit status`() {
        val result = interpreter.interpretExpiryDate("260802", referenceDate).asResolved()

        assertEquals(MrzExpiryStatus.EXPIRES_TODAY, result.expiryStatus)
    }

    @Test
    public fun `plausible future expiry is valid`() {
        val result = interpreter.interpretExpiryDate("301231", referenceDate).asResolved()

        assertEquals(LocalDate.of(2030, 12, 31), result.date.preferredValue)
        assertEquals(MrzExpiryStatus.VALID, result.expiryStatus)
    }

    @Test
    public fun `expiry century window crosses into next century`() {
        val result =
            interpreter.interpretExpiryDate("000101", LocalDate.of(2095, 6, 1)).asResolved()

        assertEquals(LocalDate.of(2100, 1, 1), result.date.preferredValue)
    }

    @Test
    public fun `expiry outside policy window retains adjacent centuries`() {
        val result = interpreter.interpretExpiryDate("750101", referenceDate).asAmbiguous()

        assertTrue(result.date.isAmbiguous)
        assertEquals(MrzExpiryStatus.UNKNOWN, result.expiryStatus)
    }

    @Test
    public fun `same input and reference date are deterministic`() {
        val first = interpreter.interpretExpiryDate("301231", referenceDate).asResolved()
        val second = interpreter.interpretExpiryDate("301231", referenceDate).asResolved()

        assertEquals(first.date, second.date)
        assertEquals(first.expiryStatus, second.expiryStatus)
    }

    private fun MrzDateInterpretation.asResolved(): MrzDateInterpretation.Resolved = this as MrzDateInterpretation.Resolved

    private fun MrzDateInterpretation.asAmbiguous(): MrzDateInterpretation.Ambiguous = this as MrzDateInterpretation.Ambiguous

    private fun MrzDateInterpretation.asFailure(): MrzDateInterpretation.Failure = this as MrzDateInterpretation.Failure
}
