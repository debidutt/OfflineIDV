package com.ing.offlineidv.nfc

/** Maps protected chip artifacts to finite data/security observations without policy. */
public object PassportChipValidationEngine : ChipValidationEngine {
    override fun validate(artifact: ChipDataArtifact): ChipValidationResult =
        ChipValidationResult.Validated(
            ChipValidationObservation(
                dg1Available = artifact.hasDg1,
                dg2Available = artifact.hasDg2,
                passiveAuthentication = artifact.passiveAuthentication,
                chipAuthentication = artifact.chipAuthentication,
                portrait = artifact.portrait(),
            ),
        )
}

/** Compares the selected TD1/TD3 identity fields and returns no value or product decision. */
public object MrzPrintedChipComparisonEngine : PrintedChipComparisonEngine {
    override fun compare(
        printed: PrintedPassportData,
        chip: ChipDataArtifact,
    ): PrintedChipComparisonResult {
        val printedFields = printed.fields()
        val chipFields = chip.useValue { value -> MrzComparisonFields.decode(value) }
        if (printedFields == null || chipFields == null) return PrintedChipComparisonResult.Inconclusive
        return if (printedFields == chipFields) {
            PrintedChipComparisonResult.Match
        } else {
            PrintedChipComparisonResult.Mismatch
        }
    }
}

/** Compatibility alias retained for existing callers; comparison is now MRZ-format-neutral. */
public object Td3PrintedChipComparisonEngine : PrintedChipComparisonEngine by MrzPrintedChipComparisonEngine
