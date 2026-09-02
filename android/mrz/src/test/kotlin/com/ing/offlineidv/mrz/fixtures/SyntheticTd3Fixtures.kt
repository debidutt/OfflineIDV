package com.ing.offlineidv.mrz.fixtures

/** Deterministic TD3 fixtures containing only conspicuously synthetic identities. */
internal object SyntheticTd3Fixtures {
    internal const val REFERENCE_YEAR: Int = 2026

    internal val valid: String =
        build(
            name = "TESTER<<SYNTHETIC<ALPHA",
            documentNumber = "A12B34567",
            birthDate = "900101",
            sex = 'F',
            expiryDate = "301231",
            optionalData = "SYNTHETIC1<<<<",
        )

    internal val fillerHeavy: String =
        build(
            name = "FILLER<<HEAVY",
            documentNumber = "B76543210",
            birthDate = "040229",
            sex = 'M',
            expiryDate = "280229",
            optionalData = "AB<<<<<<<<<<<C",
        )

    internal val noGivenNames: String =
        build(
            name = "SOLO<<",
            documentNumber = "C00000001",
            birthDate = "850630",
            sex = '<',
            expiryDate = "260802",
            optionalData = "<<<<<<<<<<<<<<",
        )

    internal fun build(
        documentCode: String = "P<",
        issuingState: String = "UTO",
        name: String = "TESTER<<SYNTHETIC<ALPHA",
        documentNumber: String = "A12B34567",
        nationality: String = "UTO",
        birthDate: String = "900101",
        sex: Char = 'F',
        expiryDate: String = "301231",
        optionalData: String = "SYNTHETIC1<<<<",
    ): String {
        require(documentCode.length == 2)
        require(issuingState.length == 3)
        require(name.length <= 39)
        require(documentNumber.length == 9)
        require(nationality.length == 3)
        require(birthDate.length == 6)
        require(expiryDate.length == 6)
        require(optionalData.length == 14)

        val line1 = documentCode + issuingState + name.padEnd(39, '<')
        val documentDigit = independentCheckDigit(documentNumber)
        val birthDigit = independentCheckDigit(birthDate)
        val expiryDigit = independentCheckDigit(expiryDate)
        val optionalDigit =
            if (optionalData.all { it == '<' }) '<' else independentCheckDigit(optionalData)
        val compositeSource =
            documentNumber +
                documentDigit +
                birthDate +
                birthDigit +
                expiryDate +
                expiryDigit +
                optionalData +
                optionalDigit
        val line2 =
            documentNumber +
                documentDigit +
                nationality +
                birthDate +
                birthDigit +
                sex +
                expiryDate +
                expiryDigit +
                optionalData +
                optionalDigit +
                independentCheckDigit(compositeSource)
        check(line1.length == 44 && line2.length == 44) { "Synthetic fixture layout is invalid." }
        return "$line1\n$line2"
    }

    internal fun lines(input: String): List<String> = input.split('\n')

    internal fun replace(
        input: String,
        lineIndex: Int,
        characterIndex: Int,
        replacement: Char,
    ): String {
        val lines = lines(input).toMutableList()
        lines[lineIndex] =
            lines[lineIndex].replaceRange(characterIndex, characterIndex + 1, replacement.toString())
        return lines.joinToString("\n")
    }

    internal fun addOcrSpaces(input: String): String = lines(input).joinToString("\n") { line -> line.chunked(4).joinToString(" ") }

    /** Independent test-fixture implementation; production code is not called. */
    internal fun independentCheckDigit(value: CharSequence): Char {
        val weights = intArrayOf(7, 3, 1)
        val sum =
            value
                .mapIndexed { index, character ->
                    val characterValue =
                        when (character) {
                            in '0'..'9' -> character - '0'
                            in 'A'..'Z' -> character - 'A' + 10
                            '<' -> 0
                            else -> error("Synthetic fixture contains an unsupported character.")
                        }
                    characterValue * weights[index % weights.size]
                }.sum()
        return ('0'.code + (sum % 10)).toChar()
    }
}
