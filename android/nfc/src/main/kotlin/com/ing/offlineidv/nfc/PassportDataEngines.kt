package com.ing.offlineidv.nfc

/** Maps protected chip artifacts to finite data/security observations without policy. */
public object PassportChipValidationEngine : ChipValidationEngine {
    override fun validate(artifact: ChipDataArtifact): ChipValidationResult =
        ChipValidationResult.Validated(
            ChipValidationObservation(
                dg1Available = artifact.hasDg1,
                dg2Available = artifact.hasDg2,
                passiveAuthentication = artifact.passiveAuthentication,
                portrait = artifact.portrait(),
            ),
        )
}

/** Compares selected TD3 identity fields and returns no compared value or product decision. */
public object Td3PrintedChipComparisonEngine : PrintedChipComparisonEngine {
    override fun compare(
        printed: PrintedPassportData,
        chip: ChipDataArtifact,
    ): PrintedChipComparisonResult {
        val printedFields = printed.useValue(::extractTd3Fields)
        val chipFields = chip.useValue(::extractTd3Fields)
        if (printedFields == null || chipFields == null) return PrintedChipComparisonResult.Inconclusive
        return if (printedFields == chipFields) {
            PrintedChipComparisonResult.Match
        } else {
            PrintedChipComparisonResult.Mismatch
        }
    }

    private fun extractTd3Fields(value: String): Td3ComparisonFields? {
        val normalized = value.filterNot(Char::isWhitespace)
        if (normalized.length != TD3_LENGTH) return null
        val line2 = normalized.substring(TD3_LINE_LENGTH)
        return Td3ComparisonFields(
            documentNumber = line2.substring(0, 9),
            nationality = line2.substring(10, 13),
            dateOfBirth = line2.substring(13, 19),
            expiryDate = line2.substring(21, 27),
        )
    }

    private data class Td3ComparisonFields(
        val documentNumber: String,
        val nationality: String,
        val dateOfBirth: String,
        val expiryDate: String,
    ) {
        override fun toString(): String = "Td3ComparisonFields([REDACTED])"
    }

    private const val TD3_LINE_LENGTH: Int = 44
    private const val TD3_LENGTH: Int = TD3_LINE_LENGTH * 2
}
