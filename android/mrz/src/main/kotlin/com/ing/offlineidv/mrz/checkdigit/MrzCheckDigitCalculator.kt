package com.ing.offlineidv.mrz.checkdigit

/** Result of calculating an ICAO 9303 check digit without throwing on untrusted input. */
public sealed interface MrzCheckDigitCalculation {
    public data class Success(
        public val digit: Char,
    ) : MrzCheckDigitCalculation

    public data object UnsupportedCharacter : MrzCheckDigitCalculation
}

/** Pure ICAO 9303 character-value and repeating-weight check-digit implementation. */
public object MrzCheckDigitCalculator {
    private val weights: IntArray = intArrayOf(7, 3, 1)

    /** Returns the ICAO value, or `null` when [character] is outside the MRZ character set. */
    public fun characterValue(character: Char): Int? =
        when (character) {
            in '0'..'9' -> character - '0'
            in 'A'..'Z' -> character - 'A' + 10
            '<' -> 0
            else -> null
        }

    /** Returns the repeating ICAO weight at a zero-based [index]. */
    public fun weightAt(index: Int): Int {
        require(index >= 0) { "index must not be negative" }
        return weights[index % weights.size]
    }

    /** Calculates the check digit for [input], rejecting unsupported characters safely. */
    public fun calculate(input: CharSequence): MrzCheckDigitCalculation {
        var sum = 0
        input.forEachIndexed { index, character ->
            val value = characterValue(character) ?: return MrzCheckDigitCalculation.UnsupportedCharacter
            sum += value * weightAt(index)
        }
        return MrzCheckDigitCalculation.Success(('0'.code + (sum % 10)).toChar())
    }
}
