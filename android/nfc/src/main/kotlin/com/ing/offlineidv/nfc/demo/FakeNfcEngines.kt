package com.ing.offlineidv.nfc.demo

import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.NfcFailure
import com.ing.offlineidv.nfc.ChipDataArtifact
import com.ing.offlineidv.nfc.ChipPortraitArtifact
import com.ing.offlineidv.nfc.ChipValidationEngine
import com.ing.offlineidv.nfc.ChipValidationObservation
import com.ing.offlineidv.nfc.ChipValidationResult
import com.ing.offlineidv.nfc.NfcReadRequest
import com.ing.offlineidv.nfc.NfcReadResult
import com.ing.offlineidv.nfc.PassiveAuthenticationObservation
import com.ing.offlineidv.nfc.PassportNfcEngine
import com.ing.offlineidv.nfc.PrintedChipComparisonEngine
import com.ing.offlineidv.nfc.PrintedChipComparisonResult
import com.ing.offlineidv.nfc.PrintedPassportData

/** External NFC read sequences available to the deterministic fake. */
public enum class DemoNfcReadBehavior {
    SUCCESS,
    TIMEOUT_THEN_SUCCESS,
    ALWAYS_TIMEOUT,
    UNAVAILABLE,
    TECHNICAL_FAILURE,
}

/** External chip observations available to the deterministic fake. */
public data class DemoChipValidationBehavior(
    public val dg1Available: Boolean = true,
    public val dg2Available: Boolean = true,
    public val passiveAuthentication: PassiveAuthenticationObservation = PassiveAuthenticationObservation.VALID,
    public val technicalFailure: Boolean = false,
)

/** External printed/chip observations available to the deterministic fake. */
public enum class DemoPrintedChipComparisonBehavior {
    MATCH,
    MISMATCH,
    INCONCLUSIVE,
    TECHNICAL_FAILURE,
}

/** Explicit synthetic NFC read implementation. */
public class FakePassportNfcEngine(
    private val behavior: DemoNfcReadBehavior,
) : PassportNfcEngine {
    private var calls: Int = 0

    override fun read(request: NfcReadRequest): NfcReadResult {
        calls += 1
        return when (behavior) {
            DemoNfcReadBehavior.SUCCESS -> {
                NfcReadResult.Read(ChipDataArtifact("synthetic-chip-$calls"))
            }

            DemoNfcReadBehavior.TIMEOUT_THEN_SUCCESS -> {
                if (calls == 1) NfcReadResult.Timeout else NfcReadResult.Read(ChipDataArtifact("synthetic-chip-$calls"))
            }

            DemoNfcReadBehavior.ALWAYS_TIMEOUT -> {
                NfcReadResult.Timeout
            }

            DemoNfcReadBehavior.UNAVAILABLE -> {
                NfcReadResult.Unavailable
            }

            DemoNfcReadBehavior.TECHNICAL_FAILURE -> {
                NfcReadResult.Failed(IdvError.Nfc(NfcFailure.READ_FAILED))
            }
        }
    }

    /** Clears deterministic per-session call state. */
    public fun reset() {
        calls = 0
    }
}

/** Explicit synthetic chip-validation implementation. */
public class FakeChipValidationEngine(
    private val behavior: DemoChipValidationBehavior,
) : ChipValidationEngine {
    override fun validate(artifact: ChipDataArtifact): ChipValidationResult =
        if (behavior.technicalFailure) {
            ChipValidationResult.Failed(IdvError.Nfc(NfcFailure.READ_FAILED))
        } else {
            ChipValidationResult.Validated(
                ChipValidationObservation(
                    dg1Available = behavior.dg1Available,
                    dg2Available = behavior.dg2Available,
                    passiveAuthentication = behavior.passiveAuthentication,
                    portrait =
                        if (behavior.dg2Available) {
                            ChipPortraitArtifact("synthetic-chip-portrait")
                        } else {
                            null
                        },
                ),
            )
        }

    /** The chip fake has no mutable session state, but exposes symmetric cleanup. */
    public fun reset() = Unit
}

/** Explicit synthetic printed/chip comparison implementation. */
public class FakePrintedChipComparisonEngine(
    private val behavior: DemoPrintedChipComparisonBehavior,
) : PrintedChipComparisonEngine {
    override fun compare(
        printed: PrintedPassportData,
        chip: ChipDataArtifact,
    ): PrintedChipComparisonResult =
        when (behavior) {
            DemoPrintedChipComparisonBehavior.MATCH -> {
                PrintedChipComparisonResult.Match
            }

            DemoPrintedChipComparisonBehavior.MISMATCH -> {
                PrintedChipComparisonResult.Mismatch
            }

            DemoPrintedChipComparisonBehavior.INCONCLUSIVE -> {
                PrintedChipComparisonResult.Inconclusive
            }

            DemoPrintedChipComparisonBehavior.TECHNICAL_FAILURE -> {
                PrintedChipComparisonResult.Failed(IdvError.Nfc(NfcFailure.READ_FAILED))
            }
        }

    /** The comparison fake has no mutable session state, but exposes symmetric cleanup. */
    public fun reset() = Unit
}
