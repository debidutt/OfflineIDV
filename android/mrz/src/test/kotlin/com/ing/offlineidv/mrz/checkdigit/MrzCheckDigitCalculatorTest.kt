package com.ing.offlineidv.mrz.checkdigit

import com.ing.offlineidv.mrz.fixtures.SyntheticTd3Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

public class MrzCheckDigitCalculatorTest {
    @Test
    public fun `character values follow ICAO mapping`() {
        assertEquals(0, MrzCheckDigitCalculator.characterValue('0'))
        assertEquals(9, MrzCheckDigitCalculator.characterValue('9'))
        assertEquals(10, MrzCheckDigitCalculator.characterValue('A'))
        assertEquals(35, MrzCheckDigitCalculator.characterValue('Z'))
        assertEquals(0, MrzCheckDigitCalculator.characterValue('<'))
        assertNull(MrzCheckDigitCalculator.characterValue('@'))
    }

    @Test
    public fun `weights repeat seven three one`() {
        assertEquals(listOf(7, 3, 1, 7, 3, 1, 7), (0..6).map(MrzCheckDigitCalculator::weightAt))
    }

    @Test
    public fun `synthetic document number has independently verified digit`() {
        assertDigit('5', "A12B34567")
    }

    @Test
    public fun `synthetic birth and expiry dates have independently verified digits`() {
        assertDigit('1', "900101")
        assertDigit('6', "301231")
    }

    @Test
    public fun `filler contributes zero without interrupting weights`() {
        assertDigit('5', "SYNTHETIC1<<<<")
    }

    @Test
    public fun `composite source has independently verified digit`() {
        assertDigit('8', "A12B34567590010113012316SYNTHETIC1<<<<5")
    }

    @Test
    public fun `unsupported input returns finite failure`() {
        assertSame(
            MrzCheckDigitCalculation.UnsupportedCharacter,
            MrzCheckDigitCalculator.calculate("ABC@"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    public fun `negative weight index is rejected`() {
        MrzCheckDigitCalculator.weightAt(-1)
    }

    private fun assertDigit(
        expected: Char,
        input: String,
    ) {
        val result = MrzCheckDigitCalculator.calculate(input)
        assertTrue(result is MrzCheckDigitCalculation.Success)
        assertEquals(expected, (result as MrzCheckDigitCalculation.Success).digit)
        assertEquals(expected, SyntheticTd3Fixtures.independentCheckDigit(input))
    }
}
