package com.ing.offlineidv.mrz.fixtures

/** Deterministic TD1 fixtures containing only conspicuously synthetic identity values. */
internal object SyntheticTd1Fixtures {
    val valid: String = build()

    fun build(
        documentCode: String = "I<",
        issuingState: String = "NLD",
        documentNumber: String = "X12T34567",
        birthDate: String = "900101",
        sex: Char = 'F',
        expiryDate: String = "300101",
        nationality: String = "UTO",
        upperOptionalData: String = "",
        middleOptionalData: String = "",
        name: String = "SYNTHETIC<<RESIDENT<TEST",
    ): String {
        val documentNumberField = documentNumber.padEnd(9, '<').take(9)
        val line1 =
            documentCode.padEnd(2, '<').take(2) +
                issuingState.padEnd(3, '<').take(3) +
                documentNumberField +
                independentCheckDigit(documentNumberField) +
                upperOptionalData.padEnd(15, '<').take(15)
        val middleWithoutComposite =
            birthDate +
                independentCheckDigit(birthDate) +
                sex +
                expiryDate +
                independentCheckDigit(expiryDate) +
                nationality.padEnd(3, '<').take(3) +
                middleOptionalData.padEnd(11, '<').take(11)
        val compositeSource =
            line1.substring(5, 30) +
                middleWithoutComposite.substring(0, 7) +
                middleWithoutComposite.substring(8, 15) +
                middleWithoutComposite.substring(18, 29)
        val line2 = middleWithoutComposite + independentCheckDigit(compositeSource)
        val line3 = name.padEnd(30, '<').take(30)
        check(line1.length == 30 && line2.length == 30 && line3.length == 30)
        return "$line1\n$line2\n$line3"
    }

    fun independentCheckDigit(input: CharSequence): Char {
        val weights = intArrayOf(7, 3, 1)
        val sum =
            input
                .mapIndexed { index, character ->
                    val value =
                        when (character) {
                            in '0'..'9' -> character - '0'
                            in 'A'..'Z' -> character - 'A' + 10
                            '<' -> 0
                            else -> error("Unsupported synthetic fixture character")
                        }
                    value * weights[index % weights.size]
                }.sum()
        return ('0'.code + (sum % 10)).toChar()
    }
}
